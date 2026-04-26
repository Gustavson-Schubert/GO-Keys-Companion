package com.companion.gokeys.automation

import com.companion.gokeys.data.Automation
import com.companion.gokeys.data.Macro
import com.companion.gokeys.midi.MidiDirection
import com.companion.gokeys.midi.MidiMonitorEntry
import com.companion.gokeys.midi.MidiService
import com.companion.gokeys.midi.RolandSysEx
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Listens to MidiService incoming events and runs declarative automations.
 *
 * The engine is intentionally simple: pattern -> action. It supports note,
 * CC, SysEx and patch-change triggers and these actions: send raw bytes,
 * play a saved macro, panic, switch a patch on a part, toggle LoopMix.
 */
class AutomationEngine(
    private val midi: MidiService,
    private val playMacro: (Macro) -> Unit,
    private val sendPatch: (part: Int, msb: Int, lsb: Int, pc: Int) -> Unit,
    private val onPanic: () -> Unit,
    private val onLoopMix: (Boolean) -> Unit,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var listenerJob: Job? = null

    private val _automations = MutableStateFlow<List<Automation>>(emptyList())
    private val _macros = MutableStateFlow<List<Macro>>(emptyList())

    private val _lastFired = MutableStateFlow<String?>(null)
    val lastFired: StateFlow<String?> = _lastFired.asStateFlow()

    fun setAutomations(list: List<Automation>) { _automations.value = list }
    fun setMacros(list: List<Macro>) { _macros.value = list }

    fun start() {
        if (listenerJob != null) return
        listenerJob = scope.launch {
            midi.events.collect { entry ->
                if (entry.direction == MidiDirection.IN) handleIncoming(entry)
            }
        }
    }

    fun stop() {
        listenerJob?.cancel()
        listenerJob = null
    }

    fun shutdown() { stop(); scope.cancel() }

    private fun handleIncoming(entry: MidiMonitorEntry) {
        val bytes = entry.bytes
        if (bytes.isEmpty()) return
        val status = bytes[0].toInt() and 0xFF
        val channel = (status and 0x0F) + 1
        val msgType = status and 0xF0
        for (rule in _automations.value) {
            if (!rule.enabled) continue
            val match = when (rule.triggerType) {
                "noteOn" -> msgType == 0x90 && bytes.size >= 3 &&
                    (rule.triggerChannel == 0 || rule.triggerChannel == channel) &&
                    (bytes[1].toInt() and 0x7F) in rule.triggerParamA..rule.triggerParamB &&
                    (bytes[2].toInt() and 0x7F) > 0
                "cc" -> msgType == 0xB0 && bytes.size >= 3 &&
                    (rule.triggerChannel == 0 || rule.triggerChannel == channel) &&
                    (bytes[1].toInt() and 0x7F) == rule.triggerParamA &&
                    (bytes[2].toInt() and 0x7F) >= rule.triggerParamB
                "sysex" -> bytes.isNotEmpty() && bytes[0] == 0xF0.toByte() &&
                    matchPrefix(bytes, rule.triggerHex)
                "patch" -> false  // patch trigger is fired explicitly by VM
                else -> false
            }
            if (match) fire(rule)
        }
    }

    /** Called by ViewModel when a patch is selected, so "patch" triggers work. */
    fun onPatchSelected(part: Int, msb: Int, lsb: Int, pc: Int) {
        for (rule in _automations.value) {
            if (!rule.enabled || rule.triggerType != "patch") continue
            if (rule.triggerParamA == msb && rule.triggerParamB == lsb && rule.triggerParamC == pc) {
                fire(rule)
            }
        }
    }

    private fun matchPrefix(data: ByteArray, hexPrefix: String): Boolean {
        val prefix = RolandSysEx.parseHex(hexPrefix) ?: return false
        if (data.size < prefix.size) return false
        for (i in prefix.indices) if (data[i] != prefix[i]) return false
        return true
    }

    private fun fire(rule: Automation) {
        _lastFired.value = rule.name
        scope.launch {
            when (rule.actionType) {
                "send" -> RolandSysEx.parseHex(rule.actionHex)?.let { midi.send(it) }
                "panic" -> onPanic()
                "loopmix" -> onLoopMix(rule.actionParamA == 1)
                "patch" -> {
                    val parts = rule.actionHex.trim().split(Regex("\\s+"))
                    if (parts.size >= 3) {
                        val msb = parts[0].toIntOrNull() ?: return@launch
                        val lsb = parts[1].toIntOrNull() ?: return@launch
                        val pc = parts[2].toIntOrNull() ?: return@launch
                        sendPatch(rule.actionParamA, msb, lsb, pc)
                    }
                }
                "macro" -> {
                    val m = _macros.value.firstOrNull { it.id == rule.actionHex.trim() }
                    if (m != null) playMacro(m)
                }
            }
        }
    }
}

/** Plays a stored macro using its recorded relative timings. */
suspend fun playMacroOn(midi: MidiService, macro: Macro) {
    var prev = 0L
    for (e in macro.events) {
        val wait = e.deltaMs - prev
        if (wait > 0) delay(wait)
        midi.send(e.bytes)
        prev = e.deltaMs
    }
}
