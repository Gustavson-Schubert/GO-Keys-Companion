package com.companion.gokeys.data

import kotlinx.serialization.Serializable

@Serializable
data class PartConfig(
    val channel: Int = 1,
    val patchMsb: Int = 87,
    val patchLsb: Int = 64,
    val patchPc: Int = 1,
    val volume: Int = 110,
    val pan: Int = 64,
    val reverb: Int = 40,
    val chorus: Int = 0,
    val octave: Int = 0,
    // ---- Real-time CC controls (Roland GO docs §4) ----
    val expression: Int = 127,        // CC11
    val cutoff: Int = 64,             // CC74 (relative; 64 = neutral)
    val resonance: Int = 64,          // CC71
    val attack: Int = 64,             // CC73
    val decay: Int = 64,              // CC75
    val release: Int = 64,            // CC72
    val vibratoRate: Int = 64,        // CC76
    val vibratoDepth: Int = 64,       // CC77
    val vibratoDelay: Int = 64,       // CC78
    val portamentoTime: Int = 0,      // CC5
    val portamentoOn: Boolean = false, // CC65 (>=64 = on)
    val mono: Boolean = false,        // CC126/127
)

@Serializable
data class ZoneConfig(
    val enabled: Boolean = false,
    val keyLow: Int = 0,
    val keyHigh: Int = 127,
)

@Serializable
data class MasterConfig(
    val masterVolume: Int = 110,
    val tempoNudge: Int = 0,
    val reverbType: Int = 4,
    val chorusType: Int = 2,
)

@Serializable
data class LoopMixConfig(
    val styleIdx: Int = 0,
    val keyIdx: Int = 0,
    val variation: Int = 0,
    val running: Boolean = false,
)

/**
 * A keyboard part. The Roland GO Performance section can host four parts;
 * we expose the upper limit for full Layer + Split (4 zones).
 */
@Serializable
data class PerformanceConfig(
    val partsEnabled: List<Boolean> = listOf(true, false, false, false),
    val parts: List<PartConfig> = listOf(
        PartConfig(channel = 1),
        PartConfig(channel = 2, patchMsb = 87, patchLsb = 67, patchPc = 27),
        PartConfig(channel = 3, patchMsb = 87, patchLsb = 65, patchPc = 1),
        PartConfig(channel = 4, patchMsb = 87, patchLsb = 65, patchPc = 9),
    ),
    val zones: List<ZoneConfig> = listOf(
        ZoneConfig(enabled = false, keyLow = 0, keyHigh = 127),
        ZoneConfig(enabled = false, keyLow = 0, keyHigh = 127),
        ZoneConfig(enabled = false, keyLow = 0, keyHigh = 127),
        ZoneConfig(enabled = false, keyLow = 0, keyHigh = 127),
    ),
)

@Serializable
data class LibraryUiState(
    /** Last-used patch category — restored across launches. */
    val selectedCategory: String = "ALL",
    val searchQuery: String = "",
    /** Pixel scroll position of the patch list — restored across launches. */
    val scrollIndex: Int = 0,
    val scrollOffset: Int = 0,
    val editingPart: Int = 0,
)

@Serializable
data class AppState(
    val model: String = "GK",
    val performance: PerformanceConfig = PerformanceConfig(),
    val master: MasterConfig = MasterConfig(),
    val loopmix: LoopMixConfig = LoopMixConfig(),
    val library: LibraryUiState = LibraryUiState(),
    /** Active profile id, if any. */
    val activeProfileId: String? = null,
)

@Serializable
data class Profile(
    val id: String,
    val name: String,
    val createdAt: Long,
    val state: AppState,
    /** Optional descriptive note shown in the profile picker. */
    val description: String = "",
)

@Serializable
data class MacroEvent(
    /** Milliseconds from macro start. */
    val deltaMs: Long,
    val bytes: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MacroEvent) return false
        if (deltaMs != other.deltaMs) return false
        return bytes.contentEquals(other.bytes)
    }
    override fun hashCode(): Int = 31 * deltaMs.hashCode() + bytes.contentHashCode()
}

@Serializable
data class Macro(
    val id: String,
    val name: String,
    val createdAt: Long,
    val events: List<MacroEvent>,
)

/**
 * Lightweight automation rule.
 *
 * Trigger types:
 *   "noteOn" — note in [paramA..paramB] on channel C
 *   "cc"     — controller `paramA` value crossing threshold paramB
 *   "sysex"  — incoming SysEx whose first N bytes match a hex prefix
 *   "patch"  — when a specific patch is selected (paramA=msb, paramB=lsb, paramC=pc)
 *
 * Action types:
 *   "patch"   — switch a part to a patch (paramA=part, hex = "<msb> <lsb> <pc>")
 *   "macro"   — play a saved macro (hex = macro id)
 *   "send"    — send raw MIDI bytes (hex = the bytes)
 *   "panic"   — all-notes-off everywhere
 *   "loopmix" — start/stop LoopMix (paramA = 1 or 0)
 */
@Serializable
data class Automation(
    val id: String,
    val name: String,
    val enabled: Boolean = true,
    val triggerType: String,
    val triggerChannel: Int = 0,        // 0 = any
    val triggerParamA: Int = 0,
    val triggerParamB: Int = 0,
    val triggerParamC: Int = 0,
    val triggerHex: String = "",
    val actionType: String,
    val actionParamA: Int = 0,
    val actionHex: String = "",
)
