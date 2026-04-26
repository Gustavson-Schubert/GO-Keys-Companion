package com.companion.gokeys.midi

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.media.midi.MidiDevice
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiInputPort
import android.media.midi.MidiManager
import android.media.midi.MidiOutputPort
import android.media.midi.MidiReceiver
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.companion.gokeys.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class MidiTransport { USB, BLE }

enum class MidiDirection { OUT, IN }

data class MidiMonitorEntry(
    val timestamp: Long,
    val direction: MidiDirection,
    val bytes: ByteArray,
)

data class MidiDeviceItem(
    val id: String,
    val name: String,
    val transport: MidiTransport,
)

class MidiService(private val ctx: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val sendChannel = Channel<ByteArray>(capacity = 64)

    private val midiManager: MidiManager? =
        ctx.getSystemService(Context.MIDI_SERVICE) as? MidiManager
    private val btManager: BluetoothManager? =
        ctx.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager

    private var openDevice: MidiDevice? = null
    private var inputPort: MidiInputPort? = null
    private var outputPort: MidiOutputPort? = null
    private var bleClient: BleMidiClient? = null

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val _connectedDevice = MutableStateFlow<MidiDeviceItem?>(null)
    val connectedDevice: StateFlow<MidiDeviceItem?> = _connectedDevice.asStateFlow()

    private val _bleDevices = MutableStateFlow<List<MidiDeviceItem>>(emptyList())
    val bleDevices: StateFlow<List<MidiDeviceItem>> = _bleDevices.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private val _lastSent = MutableStateFlow<ByteArray?>(null)
    val lastSent: StateFlow<ByteArray?> = _lastSent.asStateFlow()

    private val _lastIncoming = MutableStateFlow<ByteArray?>(null)
    val lastIncoming: StateFlow<ByteArray?> = _lastIncoming.asStateFlow()

    /** Rolling MIDI monitor — last 200 events (sent + received) in chronological order. */
    private val _monitor = MutableStateFlow<List<MidiMonitorEntry>>(emptyList())
    val monitor: StateFlow<List<MidiMonitorEntry>> = _monitor.asStateFlow()

    /** Live event stream for automation engine and the monitor screen. */
    private val _events = MutableSharedFlow<MidiMonitorEntry>(extraBufferCapacity = 64)
    val events: SharedFlow<MidiMonitorEntry> = _events.asSharedFlow()

    private val _status = MutableStateFlow(ctx.getString(R.string.status_ready))
    val status: StateFlow<String> = _status.asStateFlow()

    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val errors: SharedFlow<String> = _errors.asSharedFlow()

    private fun pushMonitor(entry: MidiMonitorEntry) {
        val cur = _monitor.value
        val next = if (cur.size >= 200) cur.drop(cur.size - 199) + entry else cur + entry
        _monitor.value = next
        _events.tryEmit(entry)
    }

    fun clearMonitor() { _monitor.value = emptyList() }

    private var scanCallback: ScanCallback? = null

    init {
        scope.launch {
            for (msg in sendChannel) {
                val ble = bleClient
                if (ble != null) {
                    val ok = try { ble.send(msg) } catch (_: Throwable) { false }
                    if (ok) {
                        _lastSent.value = msg
                        pushMonitor(MidiMonitorEntry(System.currentTimeMillis(), MidiDirection.OUT, msg))
                        delay(if (msg.size > 8) 12 else 6)
                    } else {
                        _errors.tryEmit(ctx.getString(R.string.err_ble_write_failed))
                        handleWriteFailure()
                    }
                    continue
                }
                val port = inputPort ?: continue
                try {
                    port.send(msg, 0, msg.size)
                    _lastSent.value = msg
                    pushMonitor(MidiMonitorEntry(System.currentTimeMillis(), MidiDirection.OUT, msg))
                    delay(if (msg.size > 8) 8 else 4)
                } catch (e: Throwable) {
                    _errors.tryEmit(ctx.getString(
                        R.string.err_midi_write,
                        e.message ?: ctx.getString(R.string.err_unknown),
                    ))
                    handleWriteFailure()
                }
            }
        }
    }

    val isSupported: Boolean
        get() = ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_MIDI) && midiManager != null

    fun listUsbDevices(): List<MidiDeviceItem> {
        val mm = midiManager ?: return emptyList()
        return mm.devices
            .filter { it.type == MidiDeviceInfo.TYPE_USB }
            .map { info ->
                MidiDeviceItem(
                    id = "usb:" + info.id,
                    name = info.properties.getString(MidiDeviceInfo.PROPERTY_NAME)
                        ?: info.properties.getString(MidiDeviceInfo.PROPERTY_PRODUCT)
                        ?: "USB MIDI",
                    transport = MidiTransport.USB,
                )
            }
    }

    private fun missingBlePermission(): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_SCAN)
                != PackageManager.PERMISSION_GRANTED
            ) return Manifest.permission.BLUETOOTH_SCAN
            if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED
            ) return Manifest.permission.BLUETOOTH_CONNECT
        } else if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) return Manifest.permission.ACCESS_FINE_LOCATION
        return null
    }

    fun startBleScan() {
        val missing = missingBlePermission()
        if (missing != null) { _errors.tryEmit(ctx.getString(R.string.err_perm_missing, missing)); return }
        val adapter = btManager?.adapter
        if (adapter == null || !adapter.isEnabled) { _errors.tryEmit(ctx.getString(R.string.err_enable_bt)); return }
        val scanner = adapter.bluetoothLeScanner ?: run {
            _errors.tryEmit(ctx.getString(R.string.err_no_scanner)); return
        }
        stopBleScan()
        _bleDevices.value = emptyList()
        val seen = HashSet<String>()
        val cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val dev = result.device ?: return
                val addr = dev.address ?: return
                val record = result.scanRecord
                val advertisedName: String? = try {
                    dev.name ?: record?.deviceName
                } catch (_: SecurityException) { null }
                // Skip unnamed peripherals (BLE beacons, headphones broadcasting
                // without a name etc.) — keep the list focused on usable
                // keyboards. Also surface devices that advertise the MIDI BLE
                // service UUID even if their name resolution is delayed.
                val advertisesMidi = record?.serviceUuids?.any { it.uuid == MIDI_BLE_SERVICE } == true
                if (advertisedName.isNullOrBlank() && !advertisesMidi) return
                if (!seen.add(addr)) return
                val name = advertisedName?.takeIf { it.isNotBlank() } ?: "BLE MIDI"
                _bleDevices.value = _bleDevices.value + MidiDeviceItem(addr, name, MidiTransport.BLE)
            }
            override fun onScanFailed(errorCode: Int) {
                _errors.tryEmit(ctx.getString(R.string.err_scan_failed, errorCode))
                _scanning.value = false
            }
        }
        scanCallback = cb
        // NOTE: We deliberately do NOT filter by the MIDI BLE service UUID.
        // Many keyboards (incl. Roland GO:KEYS) only expose the service after
        // a GATT connection is established and do not include it in their BLE
        // advertisement. Filtering would hide them entirely on Android 12+,
        // where the system enforces filter matches strictly. Instead we scan
        // all devices and let the user pick the keyboard by name.
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .build()
        try {
            scanner.startScan(emptyList<ScanFilter>(), settings, cb)
            _scanning.value = true
        } catch (e: SecurityException) {
            _errors.tryEmit(e.message ?: ctx.getString(R.string.err_perm_required))
            _scanning.value = false
        }
    }

    fun stopBleScan() {
        val cb = scanCallback ?: return
        try { btManager?.adapter?.bluetoothLeScanner?.stopScan(cb) }
        catch (_: SecurityException) {} catch (_: IllegalStateException) {}
        scanCallback = null
        _scanning.value = false
    }

    fun connect(item: MidiDeviceItem) {
        val mm = midiManager ?: run { _errors.tryEmit(ctx.getString(R.string.err_midi_unavailable)); return }
        closeOpenDevice(silent = true)
        _status.value = ctx.getString(R.string.status_connecting, item.name)
        when (item.transport) {
            MidiTransport.USB -> {
                val rawId = item.id.removePrefix("usb:").toIntOrNull() ?: return
                val info = mm.devices.firstOrNull { it.id == rawId } ?: run {
                    _errors.tryEmit(ctx.getString(R.string.err_usb_not_found))
                    _status.value = ctx.getString(R.string.status_error)
                    return
                }
                mm.openDevice(info, { device ->
                    if (device == null) {
                        _errors.tryEmit(ctx.getString(R.string.err_usb_open_failed))
                        _status.value = ctx.getString(R.string.status_error)
                    } else wireDevice(item, device, info)
                }, mainHandler)
            }
            MidiTransport.BLE -> {
                val adapter = btManager?.adapter ?: return
                val bt: BluetoothDevice = try { adapter.getRemoteDevice(item.id) }
                catch (e: IllegalArgumentException) {
                    _errors.tryEmit(e.message ?: ctx.getString(R.string.err_invalid_ble_addr))
                    _status.value = ctx.getString(R.string.status_error)
                    return
                }
                openBleMidiDirect(item, bt)
            }
        }
    }

    /**
     * Open a Roland GO:KEYS / GO:PIANO over BLE MIDI by talking to the GATT
     * service directly. Android's own [MidiManager.openBluetoothDevice] is
     * unreliable on Android 14+ for these keyboards (it silently returns
     * null because the system bonds the Classic audio profile instead of the
     * BLE MIDI service). Talking to the standardized BLE MIDI service
     * ourselves works on every Android version and matches what dedicated
     * BLE MIDI utilities do.
     */
    private fun openBleMidiDirect(item: MidiDeviceItem, bt: BluetoothDevice) {
        _status.value = ctx.getString(R.string.status_connecting_ble, item.name)
        // Snapshot the localized "connecting…" prefixes so we can detect
        // whether the current status is still a connection attempt when an
        // error arrives, without depending on a specific language.
        val connectingPrefixCs = "Pripojuji"
        val connectingPrefixEn = "Connecting"
        val client = BleMidiClient(
            ctx = ctx,
            device = bt,
            onReady = {
                mainHandler.post {
                    _connectedDevice.value = item
                    _connected.value = true
                    _status.value = ctx.getString(R.string.status_connected_to, item.name)
                }
            },
            onIncoming = { data ->
                _lastIncoming.value = data
                pushMonitor(MidiMonitorEntry(System.currentTimeMillis(), MidiDirection.IN, data))
            },
            onError = { msg ->
                mainHandler.post {
                    _errors.tryEmit(msg)
                    val s = _status.value
                    if (s.startsWith(connectingPrefixCs) || s.startsWith(connectingPrefixEn)) {
                        _status.value = ctx.getString(R.string.status_error)
                    }
                }
            },
            onDisconnected = {
                mainHandler.post {
                    if (bleClient != null) {
                        bleClient = null
                        if (_connected.value) {
                            _connected.value = false
                            _connectedDevice.value = null
                            _status.value = ctx.getString(R.string.status_lost)
                        }
                    }
                }
            },
        )
        bleClient = client
        client.connect()
    }

    fun disconnect() = closeOpenDevice(silent = false)

    fun send(bytes: ByteArray) {
        val r = sendChannel.trySend(bytes)
        if (r.isFailure) _errors.tryEmit(ctx.getString(R.string.err_queue_full))
    }

    fun sendAll(messages: Collection<ByteArray>) { for (m in messages) send(m) }

    private fun wireDevice(item: MidiDeviceItem, device: MidiDevice, info: MidiDeviceInfo) {
        openDevice = device
        val outIdx = info.ports.firstOrNull { it.type == MidiDeviceInfo.PortInfo.TYPE_OUTPUT }?.portNumber
        val inIdx = info.ports.firstOrNull { it.type == MidiDeviceInfo.PortInfo.TYPE_INPUT }?.portNumber
        if (outIdx != null) {
            outputPort = device.openOutputPort(outIdx)
            outputPort?.connect(object : MidiReceiver() {
                override fun onSend(msg: ByteArray, offset: Int, count: Int, timestamp: Long) {
                    val data = ByteArray(count)
                    System.arraycopy(msg, offset, data, 0, count)
                    _lastIncoming.value = data
                    pushMonitor(MidiMonitorEntry(System.currentTimeMillis(), MidiDirection.IN, data))
                }
            })
        }
        if (inIdx != null) {
            inputPort = device.openInputPort(inIdx)
        }
        if (inputPort == null) {
            _errors.tryEmit(ctx.getString(R.string.err_no_input_port))
            _status.value = ctx.getString(R.string.status_readonly)
        } else {
            _status.value = ctx.getString(R.string.status_connected_to, item.name)
        }
        _connectedDevice.value = item
        _connected.value = true
    }

    private fun handleWriteFailure() {
        try { inputPort?.close() } catch (_: Throwable) {}
        inputPort = null
        if (_connected.value) {
            _connected.value = false
            _status.value = ctx.getString(R.string.status_lost)
        }
    }

    private fun closeOpenDevice(silent: Boolean) {
        try { inputPort?.close() } catch (_: Throwable) {}
        try { outputPort?.close() } catch (_: Throwable) {}
        try { openDevice?.close() } catch (_: Throwable) {}
        try { bleClient?.close() } catch (_: Throwable) {}
        inputPort = null
        outputPort = null
        openDevice = null
        bleClient = null
        if (_connected.value) {
            _connected.value = false
            _connectedDevice.value = null
            if (!silent) _status.value = ctx.getString(R.string.status_disconnected)
        }
    }
}
