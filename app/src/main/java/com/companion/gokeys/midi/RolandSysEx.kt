package com.companion.gokeys.midi

/**
 * Roland GO:KEYS / GO:PIANO SysEx and helper builder.
 *
 * Builds DT1 (Data Set 1) messages with proper Roland checksum, plus
 * convenience helpers for:
 *   - Bank Select + Program Change (standard)
 *   - All Notes Off, Reset Controllers, Universal Master Volume
 *   - GO:KEYS Performance Part / Zone enabling (real layering & split)
 *   - LoopMix style/key/variation via NRPN + DT1
 *   - Tempo nudge, Demo off
 *   - Hidden patch unlock (goplus address space)
 *   - Effects block writes (chorus / reverb type & level)
 *
 * Notes on layering on GO:KEYS / GO:PIANO:
 *
 * The factory firmware does NOT respond to MIDI Channel 2 unless the
 * Performance section is configured to expose a second Part. The original
 * companion app failed because it just sent program-change on ch2.
 *
 * The fix uses the Roland DT1 path. For the GO family the documented
 * Performance area is at base 0x10 00 00 00. Each Part block is 0x100 wide:
 *
 *   addr  +0x00 = Part Switch (0x00 off, 0x01 on)
 *   addr  +0x01 = MIDI Receive Channel (0..15 = ch1..ch16)
 *   addr  +0x02 = Bank Select MSB
 *   addr  +0x03 = Bank Select LSB
 *   addr  +0x04 = Program Number (0-based)
 *   addr  +0x07 = Volume
 *   addr  +0x08 = Pan
 *   addr  +0x09 = Reverb Send
 *   addr  +0x0A = Chorus Send
 *
 * Part 0 lives at 0x10000000, Part 1 (the layer/upper) at 0x10000100, etc.
 *
 * For SPLIT we additionally write the Zone block at 0x10005000 + part*0x10:
 *   +0x00 = Zone Switch
 *   +0x01 = Key Range Low (0..127)
 *   +0x02 = Key Range High (0..127)
 *   +0x03 = Octave Shift (offset 0x40 = 0)
 *
 * These addresses are derived from the public goplus / rolandgo-hacking
 * documentation. Even on units where the addresses differ slightly, the
 * DT1 framing, checksum, and flow are correct — the user can adjust the
 * address constants without rebuilding the rest of the app.
 */
object RolandSysEx {

    enum class Model(val id: Byte) { GP(0x3D), GK(0x3C) }

    // ---- Constants ---------------------------------------------------------

    val IDENTITY_REQUEST = byteArrayOf(0xF0.toByte(), 0x7E, 0x10, 0x06, 0x01, 0xF7.toByte())

    private const val PERF_PART_BASE = 0x10000000
    private const val PERF_PART_STRIDE = 0x100
    private const val PERF_ZONE_BASE = 0x10005000
    private const val PERF_ZONE_STRIDE = 0x10

    private const val LOOPMIX_BASE = 0x01000200
    private const val TEMPO_UP_ADDR = 0x01000504
    private const val TEMPO_DOWN_ADDR = 0x01000503
    private const val DEMO_OFF_ADDR = 0x0F002000

    // Effects block (System Common Effects)
    private const val EFFECT_BASE = 0x02000000
    private const val EFFECT_REVERB_TYPE = EFFECT_BASE + 0x10
    private const val EFFECT_REVERB_LEVEL = EFFECT_BASE + 0x11
    private const val EFFECT_CHORUS_TYPE = EFFECT_BASE + 0x20
    private const val EFFECT_CHORUS_LEVEL = EFFECT_BASE + 0x21

    // ---- Standard MIDI ------------------------------------------------------

    fun bankAndProgram(channel1Based: Int, msb: Int, lsb: Int, pc1Based: Int): List<ByteArray> {
        val ch = (channel1Based - 1) and 0x0F
        return listOf(
            byteArrayOf((0xB0 or ch).toByte(), 0x00, (msb and 0x7F).toByte()),
            byteArrayOf((0xB0 or ch).toByte(), 0x20, (lsb and 0x7F).toByte()),
            byteArrayOf((0xC0 or ch).toByte(), ((pc1Based - 1) and 0x7F).toByte()),
        )
    }

    fun cc(channel1Based: Int, ccNum: Int, value: Int): ByteArray {
        val ch = (channel1Based - 1) and 0x0F
        return byteArrayOf(
            (0xB0 or ch).toByte(),
            (ccNum and 0x7F).toByte(),
            (value and 0x7F).toByte(),
        )
    }

    fun noteOn(channel1Based: Int, note: Int, velocity: Int): ByteArray {
        val ch = (channel1Based - 1) and 0x0F
        return byteArrayOf(
            (0x90 or ch).toByte(),
            (note and 0x7F).toByte(),
            (velocity and 0x7F).toByte(),
        )
    }

    fun noteOff(channel1Based: Int, note: Int): ByteArray {
        val ch = (channel1Based - 1) and 0x0F
        return byteArrayOf((0x80 or ch).toByte(), (note and 0x7F).toByte(), 0x40)
    }

    fun universalMasterVolume(value14: Int): ByteArray {
        val v = value14.coerceIn(0, 16383)
        return byteArrayOf(
            0xF0.toByte(), 0x7F, 0x7F, 0x04, 0x01,
            (v and 0x7F).toByte(),
            ((v shr 7) and 0x7F).toByte(),
            0xF7.toByte(),
        )
    }

    fun allNotesOff(channel1Based: Int): ByteArray = cc(channel1Based, 123, 0)
    fun resetAllControllers(channel1Based: Int): ByteArray = cc(channel1Based, 121, 0)

    // ---- CC sound-shaping shortcuts (per rolandgo-hacking docs) -----------
    /** CC 5 — Portamento time (0..127). */
    fun ccPortamentoTime(ch: Int, v: Int) = cc(ch, 5, v)
    /** CC 7 — Volume. */
    fun ccVolume(ch: Int, v: Int) = cc(ch, 7, v)
    /** CC 10 — Pan. */
    fun ccPan(ch: Int, v: Int) = cc(ch, 10, v)
    /** CC 11 — Expression. */
    fun ccExpression(ch: Int, v: Int) = cc(ch, 11, v)
    /** CC 65 — Portamento on/off (>=64 = on). */
    fun ccPortamentoOnOff(ch: Int, on: Boolean) = cc(ch, 65, if (on) 127 else 0)
    /** CC 71 — Filter resonance. */
    fun ccResonance(ch: Int, v: Int) = cc(ch, 71, v)
    /** CC 72 — Release time. */
    fun ccRelease(ch: Int, v: Int) = cc(ch, 72, v)
    /** CC 73 — Attack time. */
    fun ccAttack(ch: Int, v: Int) = cc(ch, 73, v)
    /** CC 74 — Filter cutoff. */
    fun ccCutoff(ch: Int, v: Int) = cc(ch, 74, v)
    /** CC 75 — Decay time. */
    fun ccDecay(ch: Int, v: Int) = cc(ch, 75, v)
    /** CC 76 — Vibrato rate. */
    fun ccVibratoRate(ch: Int, v: Int) = cc(ch, 76, v)
    /** CC 77 — Vibrato depth. */
    fun ccVibratoDepth(ch: Int, v: Int) = cc(ch, 77, v)
    /** CC 78 — Vibrato delay. */
    fun ccVibratoDelay(ch: Int, v: Int) = cc(ch, 78, v)
    /** CC 91 — Reverb send. */
    fun ccReverbSend(ch: Int, v: Int) = cc(ch, 91, v)
    /** CC 93 — Chorus send. */
    fun ccChorusSend(ch: Int, v: Int) = cc(ch, 93, v)
    /** CC 126 — Mono Operation. */
    fun ccMonoMode(ch: Int) = cc(ch, 126, 0)
    /** CC 127 — Poly Operation. */
    fun ccPolyMode(ch: Int) = cc(ch, 127, 0)
    /** Pitch Bend (14-bit, -8192..+8191 mapped to 0..16383). */
    fun pitchBend(ch: Int, value14: Int): ByteArray {
        val v = (value14 + 8192).coerceIn(0, 16383)
        return byteArrayOf(
            (0xE0 or ((ch - 1) and 0x0F)).toByte(),
            (v and 0x7F).toByte(),
            ((v shr 7) and 0x7F).toByte(),
        )
    }
    /** Channel Aftertouch. */
    fun aftertouch(ch: Int, v: Int): ByteArray =
        byteArrayOf((0xD0 or ((ch - 1) and 0x0F)).toByte(), (v and 0x7F).toByte())
    /** GM2 mode bank-select shortcut. msb=0x79, lsb=0x00 (Melodic) or 0x78 (Drum). */
    fun gm2Bank(ch: Int, drum: Boolean = false): List<ByteArray> = listOf(
        cc(ch, 0, 0x79),
        cc(ch, 32, if (drum) 0x78 else 0x00),
    )

    // ---- NRPN ---------------------------------------------------------------

    /** NRPN: CC99=MSB, CC98=LSB, CC6=DataMSB, CC38=DataLSB. */
    fun nrpn(channel1Based: Int, msb: Int, lsb: Int, dataMsb: Int, dataLsb: Int = 0): List<ByteArray> =
        listOf(
            cc(channel1Based, 99, msb),
            cc(channel1Based, 98, lsb),
            cc(channel1Based, 6, dataMsb),
            cc(channel1Based, 38, dataLsb),
        )

    // ---- Performance Parts (REAL layering) ---------------------------------

    /** Configure a Part: turn it on, route to a MIDI channel, load a patch. */
    fun configurePart(
        model: Model,
        partIndex: Int,
        on: Boolean,
        midiChannel1Based: Int,
        msb: Int,
        lsb: Int,
        pc1Based: Int,
        volume: Int = 110,
        pan: Int = 64,
        reverb: Int = 40,
        chorus: Int = 0,
    ): List<ByteArray> {
        val base = PERF_PART_BASE + partIndex * PERF_PART_STRIDE
        return listOf(
            rolandDT1(model, base + 0x00, byteArrayOf(if (on) 0x01 else 0x00)),
            rolandDT1(model, base + 0x01, byteArrayOf(((midiChannel1Based - 1) and 0x0F).toByte())),
            rolandDT1(model, base + 0x02, byteArrayOf((msb and 0x7F).toByte())),
            rolandDT1(model, base + 0x03, byteArrayOf((lsb and 0x7F).toByte())),
            rolandDT1(model, base + 0x04, byteArrayOf(((pc1Based - 1) and 0x7F).toByte())),
            rolandDT1(model, base + 0x07, byteArrayOf((volume and 0x7F).toByte())),
            rolandDT1(model, base + 0x08, byteArrayOf((pan and 0x7F).toByte())),
            rolandDT1(model, base + 0x09, byteArrayOf((reverb and 0x7F).toByte())),
            rolandDT1(model, base + 0x0A, byteArrayOf((chorus and 0x7F).toByte())),
        )
    }

    fun setPartSwitch(model: Model, partIndex: Int, on: Boolean): ByteArray =
        rolandDT1(model, PERF_PART_BASE + partIndex * PERF_PART_STRIDE,
            byteArrayOf(if (on) 0x01 else 0x00))

    fun setPartVolume(model: Model, partIndex: Int, volume: Int): ByteArray =
        rolandDT1(model, PERF_PART_BASE + partIndex * PERF_PART_STRIDE + 0x07,
            byteArrayOf((volume and 0x7F).toByte()))

    fun setPartPan(model: Model, partIndex: Int, pan: Int): ByteArray =
        rolandDT1(model, PERF_PART_BASE + partIndex * PERF_PART_STRIDE + 0x08,
            byteArrayOf((pan and 0x7F).toByte()))

    fun setPartReverb(model: Model, partIndex: Int, level: Int): ByteArray =
        rolandDT1(model, PERF_PART_BASE + partIndex * PERF_PART_STRIDE + 0x09,
            byteArrayOf((level and 0x7F).toByte()))

    fun setPartChorus(model: Model, partIndex: Int, level: Int): ByteArray =
        rolandDT1(model, PERF_PART_BASE + partIndex * PERF_PART_STRIDE + 0x0A,
            byteArrayOf((level and 0x7F).toByte()))

    // ---- Split / Zones -----------------------------------------------------

    fun configureZone(
        model: Model,
        partIndex: Int,
        on: Boolean,
        keyLow: Int,
        keyHigh: Int,
        octaveShift: Int = 0,
    ): List<ByteArray> {
        val base = PERF_ZONE_BASE + partIndex * PERF_ZONE_STRIDE
        return listOf(
            rolandDT1(model, base + 0x00, byteArrayOf(if (on) 0x01 else 0x00)),
            rolandDT1(model, base + 0x01, byteArrayOf((keyLow.coerceIn(0, 127)).toByte())),
            rolandDT1(model, base + 0x02, byteArrayOf((keyHigh.coerceIn(0, 127)).toByte())),
            rolandDT1(model, base + 0x03, byteArrayOf(((octaveShift + 0x40) and 0x7F).toByte())),
        )
    }

    // ---- LoopMix (GO:KEYS) -------------------------------------------------

    /** LoopMix style change. NRPN MSB=0x01 LSB=0x10, data = style index. */
    fun loopMixStyle(channel1Based: Int, styleIdx: Int): List<ByteArray> =
        nrpn(channel1Based, 0x01, 0x10, styleIdx and 0x7F)

    /** LoopMix root key (chromatic 0..11). */
    fun loopMixKey(channel1Based: Int, keyIdx: Int): List<ByteArray> =
        nrpn(channel1Based, 0x01, 0x11, keyIdx and 0x0F)

    fun loopMixVariation(channel1Based: Int, variation: Int): List<ByteArray> =
        nrpn(channel1Based, 0x01, 0x12, variation and 0x7F)

    /** LoopMix Start/Stop via DT1 (matches goplus address). */
    fun loopMixStart(model: Model): ByteArray =
        rolandDT1(model, LOOPMIX_BASE + 0x00, byteArrayOf(0x01))
    fun loopMixStop(model: Model): ByteArray =
        rolandDT1(model, LOOPMIX_BASE + 0x00, byteArrayOf(0x00))

    // ---- Effects -----------------------------------------------------------

    fun setReverbType(model: Model, type: Int): ByteArray =
        rolandDT1(model, EFFECT_REVERB_TYPE, byteArrayOf((type and 0x7F).toByte()))

    fun setReverbLevel(model: Model, level: Int): ByteArray =
        rolandDT1(model, EFFECT_REVERB_LEVEL, byteArrayOf((level and 0x7F).toByte()))

    fun setChorusType(model: Model, type: Int): ByteArray =
        rolandDT1(model, EFFECT_CHORUS_TYPE, byteArrayOf((type and 0x7F).toByte()))

    fun setChorusLevel(model: Model, level: Int): ByteArray =
        rolandDT1(model, EFFECT_CHORUS_LEVEL, byteArrayOf((level and 0x7F).toByte()))

    // ---- Tempo / demo ------------------------------------------------------

    fun demoOff(model: Model): ByteArray = rolandDT1(model, DEMO_OFF_ADDR, byteArrayOf(0x00))
    fun tempoUp(model: Model): ByteArray = rolandDT1(model, TEMPO_UP_ADDR, byteArrayOf(0x01))
    fun tempoDown(model: Model): ByteArray = rolandDT1(model, TEMPO_DOWN_ADDR, byteArrayOf(0x01))

    /**
     * Selects a demo song (GO:KEYS, 0..4) and triggers play/stop.
     * Reproduces the exact two SysEx strings documented in rolandgo-hacking
     * (works against model id 0x28 / Juno-DS area; the GO firmware accepts it).
     */
    fun selectDemoSong(index: Int): List<ByteArray> {
        val idx = index.coerceIn(0, 4).toByte()
        val select = byteArrayOf(
            0xF0.toByte(), 0x41, 0x10, 0x00, 0x00, 0x00, 0x28, 0x12,
            0x01, 0x00, 0x02, 0x10, 0x00, 0x00, idx, 0x00, 0xF7.toByte(),
        )
        val playStop = byteArrayOf(
            0xF0.toByte(), 0x41, 0x10, 0x00, 0x00, 0x00, 0x28, 0x12,
            0x01, 0x00, 0x05, 0x05, 0x00, 0x00, 0xF7.toByte(),
        )
        return listOf(select, playStop)
    }

    /** GO:PIANO LoopMix one-shot (raw bytes from rolandgo-hacking README §5). */
    val LOOPMIX_GP_RAW: ByteArray = byteArrayOf(
        0xF0.toByte(), 0x41, 0x10, 0x00, 0x00, 0x00, 0x3C, 0x12,
        0x01, 0x00, 0x00, 0x19, 0x01, 0x00, 0xF7.toByte(),
    )

    // ---- Raw DT1 access (for hidden patches & user automation) -------------

    /** Public DT1 wrapper. addr is a 28-bit Roland address packed into Int. */
    fun dt1(model: Model, addr: Int, data: ByteArray): ByteArray =
        rolandDT1(model, addr, data)

    private fun rolandDT1(model: Model, addr: Int, data: ByteArray): ByteArray {
        val header = byteArrayOf(0xF0.toByte(), 0x41, 0x10, 0x00, 0x00, 0x00, model.id)
        val a = byteArrayOf(
            ((addr ushr 24) and 0xFF).toByte(),
            ((addr ushr 16) and 0xFF).toByte(),
            ((addr ushr 8) and 0xFF).toByte(),
            (addr and 0xFF).toByte(),
        )
        var sum = 0
        for (b in a) sum += b.toInt() and 0xFF
        for (b in data) sum += b.toInt() and 0xFF
        val cs = ((128 - (sum % 128)) and 0x7F).toByte()
        return header + byteArrayOf(0x12) + a + data + byteArrayOf(cs, 0xF7.toByte())
    }

    // ---- Utilities ---------------------------------------------------------

    fun bytesToHex(bytes: ByteArray, max: Int = 64): String {
        val sb = StringBuilder()
        val n = minOf(bytes.size, max)
        for (i in 0 until n) {
            if (i > 0) sb.append(' ')
            sb.append(String.format("%02X", bytes[i].toInt() and 0xFF))
        }
        if (bytes.size > max) sb.append(" …")
        return sb.toString()
    }

    /** Parse a hex string ("F0 41 10 ... F7") into bytes. */
    fun parseHex(text: String): ByteArray? {
        val cleaned = text.replace(",", " ").replace(":", " ").trim()
        if (cleaned.isEmpty()) return null
        val parts = cleaned.split(Regex("\\s+"))
        return try {
            ByteArray(parts.size) { i -> parts[i].toInt(16).toByte() }
        } catch (_: NumberFormatException) {
            null
        }
    }
}
