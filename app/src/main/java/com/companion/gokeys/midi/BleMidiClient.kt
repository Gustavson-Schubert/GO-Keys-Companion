package com.companion.gokeys.midi

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import com.companion.gokeys.R
import java.util.UUID

internal val MIDI_BLE_SERVICE: UUID =
    UUID.fromString("03B80E5A-EDE8-4B33-A751-6CE34EC4C700")
private val MIDI_BLE_DATA_CHAR: UUID =
    UUID.fromString("7772E5DB-3868-4112-A1A9-F2669D106BF3")
private val CCC_DESCRIPTOR: UUID =
    UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")

/**
 * Direct BLE MIDI 1.0 client over GATT. Bypasses [android.media.midi.MidiManager.openBluetoothDevice],
 * which is broken on Android 14+ for many keyboards (incl. Roland GO series):
 * the system call silently returns null because Android prefers to bond the
 * device's Bluetooth Classic audio profile instead of its BLE MIDI service.
 *
 * This implements the BLE MIDI 1.0 packet format from the spec:
 * https://www.midi.org/specifications/midi-transports-specifications/bluetooth-midi
 */
@SuppressLint("MissingPermission")
class BleMidiClient(
    private val ctx: Context,
    private val device: BluetoothDevice,
    private val onReady: () -> Unit,
    private val onIncoming: (ByteArray) -> Unit,
    private val onError: (String) -> Unit,
    private val onDisconnected: () -> Unit,
) {

    private var gatt: BluetoothGatt? = null
    private var dataChar: BluetoothGattCharacteristic? = null
    private var mtu: Int = 23 // BLE default; usable payload = mtu - 3

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) g.requestMtu(67)
                        else g.discoverServices()
                    } catch (_: SecurityException) {
                        onError(ctx.getString(R.string.err_ble_perm))
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    cleanup()
                    onDisconnected()
                }
            }
        }

        override fun onMtuChanged(g: BluetoothGatt, newMtu: Int, status: Int) {
            mtu = newMtu
            try { g.discoverServices() } catch (_: SecurityException) {
                onError(ctx.getString(R.string.err_ble_perm))
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val service = g.getService(MIDI_BLE_SERVICE)
            if (service == null) {
                onError(ctx.getString(R.string.err_no_ble_service))
                try { g.disconnect() } catch (_: Throwable) {}
                return
            }
            val ch = service.getCharacteristic(MIDI_BLE_DATA_CHAR)
            if (ch == null) {
                onError(ctx.getString(R.string.err_no_ble_char))
                try { g.disconnect() } catch (_: Throwable) {}
                return
            }
            dataChar = ch
            try {
                g.setCharacteristicNotification(ch, true)
                val descriptor = ch.getDescriptor(CCC_DESCRIPTOR)
                if (descriptor != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        g.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                    } else {
                        @Suppress("DEPRECATION")
                        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        @Suppress("DEPRECATION")
                        g.writeDescriptor(descriptor)
                    }
                }
            } catch (_: SecurityException) {
                onError(ctx.getString(R.string.err_ble_perm))
                return
            }
            onReady()
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(g: BluetoothGatt, ch: BluetoothGattCharacteristic) {
            handleIncoming(ch.value ?: return)
        }

        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            ch: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            handleIncoming(value)
        }
    }

    fun connect() {
        try {
            gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                device.connectGatt(ctx, false, callback, BluetoothDevice.TRANSPORT_LE)
            } else {
                @Suppress("DEPRECATION")
                device.connectGatt(ctx, false, callback)
            }
        } catch (e: SecurityException) {
            onError(e.message ?: ctx.getString(R.string.err_ble_perm))
        }
    }

    fun close() {
        try { gatt?.disconnect() } catch (_: Throwable) {}
        cleanup()
    }

    private fun cleanup() {
        try { gatt?.close() } catch (_: Throwable) {}
        gatt = null
        dataChar = null
    }

    /**
     * Send one MIDI message wrapped in a BLE MIDI packet.
     * Handles channel-voice messages and SysEx that fit within the negotiated MTU.
     */
    fun send(midi: ByteArray): Boolean {
        if (midi.isEmpty()) return false
        val ch = dataChar ?: return false
        val g = gatt ?: return false
        val packet = wrap(midi)
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val rc = g.writeCharacteristic(
                    ch, packet, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                )
                rc == BluetoothGatt.GATT_SUCCESS
            } else {
                @Suppress("DEPRECATION")
                ch.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                @Suppress("DEPRECATION")
                ch.value = packet
                @Suppress("DEPRECATION")
                g.writeCharacteristic(ch)
            }
        } catch (_: SecurityException) { false }
    }

    private fun wrap(msg: ByteArray): ByteArray {
        val now = (System.currentTimeMillis() and 0x1FFF).toInt()
        val header = (0x80 or ((now shr 7) and 0x3F)).toByte()
        val ts = (0x80 or (now and 0x7F)).toByte()
        val isSysExStart = msg[0] == 0xF0.toByte()
        val isSysExEnd = msg[msg.size - 1] == 0xF7.toByte()
        return when {
            isSysExStart && isSysExEnd -> {
                // [header][ts][F0..data..][ts][F7]
                val body = msg.copyOfRange(0, msg.size - 1)
                val out = ByteArray(2 + body.size + 2)
                out[0] = header
                out[1] = ts
                System.arraycopy(body, 0, out, 2, body.size)
                out[2 + body.size] = ts
                out[2 + body.size + 1] = 0xF7.toByte()
                out
            }
            else -> {
                val out = ByteArray(2 + msg.size)
                out[0] = header
                out[1] = ts
                System.arraycopy(msg, 0, out, 2, msg.size)
                out
            }
        }
    }

    // ---- Incoming parser ----------------------------------------------------

    private val sysexBuf = ArrayList<Byte>()
    private var sysexActive = false
    private var runningStatus = 0

    private fun handleIncoming(packet: ByteArray) {
        if (packet.size < 2) return
        // packet[0] = header byte (0x80..0xBF), ignored for parsing.
        var i = 1
        while (i < packet.size) {
            val b = packet[i].toInt() and 0xFF
            if (b and 0x80 != 0) {
                // Timestamp byte. The next byte determines the kind of MIDI data.
                i++
                if (i >= packet.size) break
                val nb = packet[i].toInt() and 0xFF
                if (nb == 0xF0) {
                    sysexActive = true
                    sysexBuf.clear()
                    sysexBuf.add(nb.toByte())
                    i++
                } else if (nb == 0xF7) {
                    if (sysexActive) {
                        sysexBuf.add(nb.toByte())
                        emit(sysexBuf.toByteArray())
                        sysexBuf.clear()
                        sysexActive = false
                    }
                    i++
                } else if (nb and 0x80 != 0) {
                    // Channel voice / system status byte.
                    runningStatus = nb
                    sysexActive = false
                    val len = midiLen(nb)
                    val end = (i + len).coerceAtMost(packet.size)
                    if (end - i >= 1) emit(packet.copyOfRange(i, end))
                    i = end
                } else {
                    // Timestamp prefix on a data byte using running status.
                    if (runningStatus != 0) emitWithRunningStatus(packet, i)
                    i += midiLenPayload(runningStatus)
                }
            } else {
                if (sysexActive) {
                    sysexBuf.add(b.toByte())
                    i++
                } else if (runningStatus != 0) {
                    emitWithRunningStatus(packet, i)
                    i += midiLenPayload(runningStatus)
                } else {
                    i++
                }
            }
        }
    }

    private fun emit(msg: ByteArray) {
        try { onIncoming(msg) } catch (_: Throwable) {}
    }

    private fun emitWithRunningStatus(packet: ByteArray, dataStart: Int) {
        val payload = midiLenPayload(runningStatus)
        if (dataStart + payload > packet.size) return
        val out = ByteArray(1 + payload)
        out[0] = runningStatus.toByte()
        System.arraycopy(packet, dataStart, out, 1, payload)
        emit(out)
    }

    private fun midiLen(status: Int): Int = 1 + midiLenPayload(status)

    private fun midiLenPayload(status: Int): Int = when (status and 0xF0) {
        0x80, 0x90, 0xA0, 0xB0, 0xE0 -> 2
        0xC0, 0xD0 -> 1
        0xF0 -> when (status) {
            0xF1, 0xF3 -> 1
            0xF2 -> 2
            else -> 0
        }
        else -> 0
    }
}
