package com.companion.gokeys.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.companion.gokeys.automation.AutomationEngine
import com.companion.gokeys.automation.playMacroOn
import com.companion.gokeys.data.AppState
import com.companion.gokeys.data.Automation
import com.companion.gokeys.data.LibraryUiState
import com.companion.gokeys.data.LoopMixConfig
import com.companion.gokeys.data.Macro
import com.companion.gokeys.data.MacroEvent
import com.companion.gokeys.data.MasterConfig
import com.companion.gokeys.data.PartConfig
import com.companion.gokeys.data.PerformanceConfig
import com.companion.gokeys.data.Profile
import com.companion.gokeys.data.Repository
import com.companion.gokeys.data.ZoneConfig
import com.companion.gokeys.midi.MidiDirection
import com.companion.gokeys.midi.MidiMonitorEntry
import com.companion.gokeys.midi.MidiDeviceItem
import com.companion.gokeys.midi.MidiService
import com.companion.gokeys.midi.RolandSysEx
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class CompanionViewModel(app: Application) : AndroidViewModel(app) {

    val service = MidiService(app.applicationContext)
    private val repo = Repository(app.applicationContext)

    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state.asStateFlow()

    private val _profiles = MutableStateFlow<List<Profile>>(emptyList())
    val profiles: StateFlow<List<Profile>> = _profiles.asStateFlow()

    private val _macros = MutableStateFlow<List<Macro>>(emptyList())
    val macros: StateFlow<List<Macro>> = _macros.asStateFlow()

    private val _automations = MutableStateFlow<List<Automation>>(emptyList())
    val automations: StateFlow<List<Automation>> = _automations.asStateFlow()

    // ---- Macro recording ---------------------------------------------------

    private val _recording = MutableStateFlow(false)
    val recording: StateFlow<Boolean> = _recording.asStateFlow()

    private var recordStart: Long = 0L
    private val recordedEvents = mutableListOf<MacroEvent>()

    val automationEngine = AutomationEngine(
        midi = service,
        playMacro = { m -> viewModelScope.launch { playMacroOn(service, m) } },
        sendPatch = { part, msb, lsb, pc -> selectPatchForPart(part, msb, lsb, pc) },
        onPanic = { panic() },
        onLoopMix = { run -> if (run) loopMixStart() else loopMixStop() },
    )

    init {
        viewModelScope.launch { repo.stateFlow.collect { _state.value = it } }
        viewModelScope.launch {
            repo.profilesFlow.collect { _profiles.value = it }
        }
        viewModelScope.launch {
            repo.macrosFlow.collect {
                _macros.value = it
                automationEngine.setMacros(it)
            }
        }
        viewModelScope.launch {
            repo.automationsFlow.collect {
                _automations.value = it
                automationEngine.setAutomations(it)
            }
        }
        viewModelScope.launch {
            service.events.collect { handleEventForRecording(it) }
        }
        automationEngine.start()
    }

    override fun onCleared() {
        automationEngine.shutdown()
        super.onCleared()
    }

    // ---- State helpers -----------------------------------------------------

    private fun model(): RolandSysEx.Model =
        if (_state.value.model == "GK") RolandSysEx.Model.GK else RolandSysEx.Model.GP

    private fun update(transform: (AppState) -> AppState) {
        val next = transform(_state.value)
        _state.value = next
        viewModelScope.launch { repo.saveState(next) }
    }

    fun setModel(m: String) = update { it.copy(model = m) }

    fun updateMaster(t: (MasterConfig) -> MasterConfig) =
        update { it.copy(master = t(it.master)) }

    fun updateLibraryUi(t: (LibraryUiState) -> LibraryUiState) =
        update { it.copy(library = t(it.library)) }

    fun updateLoopMix(t: (LoopMixConfig) -> LoopMixConfig) =
        update { it.copy(loopmix = t(it.loopmix)) }

    fun updatePerformance(t: (PerformanceConfig) -> PerformanceConfig) =
        update { it.copy(performance = t(it.performance)) }

    fun updatePart(index: Int, t: (PartConfig) -> PartConfig) =
        updatePerformance { perf ->
            val list = perf.parts.toMutableList()
            list[index] = t(list[index])
            perf.copy(parts = list)
        }

    fun updateZone(index: Int, t: (ZoneConfig) -> ZoneConfig) =
        updatePerformance { perf ->
            val list = perf.zones.toMutableList()
            list[index] = t(list[index])
            perf.copy(zones = list)
        }

    fun setPartEnabled(index: Int, enabled: Boolean) {
        updatePerformance { perf ->
            val list = perf.partsEnabled.toMutableList()
            list[index] = enabled
            perf.copy(partsEnabled = list)
        }
        // Push real Performance Part Switch over SysEx — this is the fix for
        // layering not affecting the keyboard. The keyboard ignores ch2 alone;
        // it only acts as a layer when its Part Switch is on.
        service.send(RolandSysEx.setPartSwitch(model(), index, enabled))
        if (enabled) sendPart(index) else service.send(RolandSysEx.allNotesOff(_state.value.performance.parts[index].channel))
    }

    fun setZoneEnabled(index: Int, enabled: Boolean) {
        updateZone(index) { it.copy(enabled = enabled) }
        val z = _state.value.performance.zones[index]
        service.sendAll(RolandSysEx.configureZone(model(), index, enabled, z.keyLow, z.keyHigh))
    }

    // ---- MIDI transport ----------------------------------------------------

    fun refreshUsb(): List<MidiDeviceItem> = service.listUsbDevices()
    fun startScan() = service.startBleScan()
    fun stopScan() = service.stopBleScan()
    fun disconnect() = service.disconnect()
    fun send(bytes: ByteArray) = service.send(bytes)

    fun connect(item: MidiDeviceItem) {
        service.connect(item)
        service.send(RolandSysEx.IDENTITY_REQUEST)
    }

    // ---- Patch / Part actions ---------------------------------------------

    fun sendPart(index: Int) {
        val p = _state.value.performance.parts[index]
        // Always reconfigure via Performance SysEx + standard PC. Both paths
        // give us redundancy: even if the SysEx address is wrong on a unit,
        // the standard CC/PC still loads the patch on its MIDI channel.
        service.sendAll(
            RolandSysEx.configurePart(
                model = model(),
                partIndex = index,
                on = _state.value.performance.partsEnabled[index],
                midiChannel1Based = p.channel,
                msb = p.patchMsb,
                lsb = p.patchLsb,
                pc1Based = p.patchPc,
                volume = p.volume,
                pan = p.pan,
                reverb = p.reverb,
                chorus = p.chorus,
            )
        )
        service.send(RolandSysEx.cc(p.channel, 121, 0))
        service.sendAll(RolandSysEx.bankAndProgram(p.channel, p.patchMsb, p.patchLsb, p.patchPc))
        service.send(RolandSysEx.cc(p.channel, 7, p.volume))
        service.send(RolandSysEx.cc(p.channel, 91, p.reverb))
        service.send(RolandSysEx.cc(p.channel, 93, p.chorus))
    }

    fun selectPatchForPart(index: Int, msb: Int, lsb: Int, pc: Int) {
        updatePart(index) { it.copy(patchMsb = msb, patchLsb = lsb, patchPc = pc) }
        sendPart(index)
        automationEngine.onPatchSelected(index, msb, lsb, pc)
    }

    fun pushVolume(index: Int) {
        val p = _state.value.performance.parts[index]
        service.send(RolandSysEx.setPartVolume(model(), index, p.volume))
        service.send(RolandSysEx.cc(p.channel, 7, p.volume))
    }

    fun pushPan(index: Int) {
        val p = _state.value.performance.parts[index]
        service.send(RolandSysEx.setPartPan(model(), index, p.pan))
        service.send(RolandSysEx.cc(p.channel, 10, p.pan))
    }

    fun pushReverb(index: Int) {
        val p = _state.value.performance.parts[index]
        service.send(RolandSysEx.setPartReverb(model(), index, p.reverb))
        service.send(RolandSysEx.cc(p.channel, 91, p.reverb))
    }

    fun pushChorus(index: Int) {
        val p = _state.value.performance.parts[index]
        service.send(RolandSysEx.setPartChorus(model(), index, p.chorus))
        service.send(RolandSysEx.cc(p.channel, 93, p.chorus))
    }

    fun pushZone(index: Int) {
        val z = _state.value.performance.zones[index]
        service.sendAll(RolandSysEx.configureZone(model(), index, z.enabled, z.keyLow, z.keyHigh))
    }

    // ---- Sound shaping (CC) -----------------------------------------------

    fun pushExpression(index: Int) {
        val p = _state.value.performance.parts[index]
        service.send(RolandSysEx.ccExpression(p.channel, p.expression))
    }
    fun pushCutoff(index: Int) {
        val p = _state.value.performance.parts[index]
        service.send(RolandSysEx.ccCutoff(p.channel, p.cutoff))
    }
    fun pushResonance(index: Int) {
        val p = _state.value.performance.parts[index]
        service.send(RolandSysEx.ccResonance(p.channel, p.resonance))
    }
    fun pushAttack(index: Int) {
        val p = _state.value.performance.parts[index]
        service.send(RolandSysEx.ccAttack(p.channel, p.attack))
    }
    fun pushDecay(index: Int) {
        val p = _state.value.performance.parts[index]
        service.send(RolandSysEx.ccDecay(p.channel, p.decay))
    }
    fun pushRelease(index: Int) {
        val p = _state.value.performance.parts[index]
        service.send(RolandSysEx.ccRelease(p.channel, p.release))
    }
    fun pushVibratoRate(index: Int) {
        val p = _state.value.performance.parts[index]
        service.send(RolandSysEx.ccVibratoRate(p.channel, p.vibratoRate))
    }
    fun pushVibratoDepth(index: Int) {
        val p = _state.value.performance.parts[index]
        service.send(RolandSysEx.ccVibratoDepth(p.channel, p.vibratoDepth))
    }
    fun pushVibratoDelay(index: Int) {
        val p = _state.value.performance.parts[index]
        service.send(RolandSysEx.ccVibratoDelay(p.channel, p.vibratoDelay))
    }
    fun pushPortamentoTime(index: Int) {
        val p = _state.value.performance.parts[index]
        service.send(RolandSysEx.ccPortamentoTime(p.channel, p.portamentoTime))
    }
    fun pushPortamentoOnOff(index: Int) {
        val p = _state.value.performance.parts[index]
        service.send(RolandSysEx.ccPortamentoOnOff(p.channel, p.portamentoOn))
    }
    fun pushMonoMode(index: Int) {
        val p = _state.value.performance.parts[index]
        if (p.mono) service.send(RolandSysEx.ccMonoMode(p.channel))
        else service.send(RolandSysEx.ccPolyMode(p.channel))
    }
    /**
     * Reset every sound-shaping CC for the given part to its documented
     * neutral default AND push each one to the keyboard so the audible
     * sound actually changes.  The previous implementation only sent
     * `resetAllControllers` (which the GO keyboards interpret loosely)
     * plus three CCs — leaving cutoff / resonance / envelope / vibrato /
     * portamento-time silently out of sync between the UI sliders and
     * the engine.
     */
    fun resetPartCC(index: Int) {
        updatePart(index) {
            it.copy(
                expression = 127, cutoff = 64, resonance = 64,
                attack = 64, decay = 64, release = 64,
                vibratoRate = 64, vibratoDepth = 64, vibratoDelay = 64,
                portamentoTime = 0, portamentoOn = false, mono = false,
            )
        }
        val p = _state.value.performance.parts[index]
        service.send(RolandSysEx.resetAllControllers(p.channel))
        service.send(RolandSysEx.ccExpression(p.channel, p.expression))
        service.send(RolandSysEx.ccCutoff(p.channel, p.cutoff))
        service.send(RolandSysEx.ccResonance(p.channel, p.resonance))
        service.send(RolandSysEx.ccAttack(p.channel, p.attack))
        service.send(RolandSysEx.ccDecay(p.channel, p.decay))
        service.send(RolandSysEx.ccRelease(p.channel, p.release))
        service.send(RolandSysEx.ccVibratoRate(p.channel, p.vibratoRate))
        service.send(RolandSysEx.ccVibratoDepth(p.channel, p.vibratoDepth))
        service.send(RolandSysEx.ccVibratoDelay(p.channel, p.vibratoDelay))
        service.send(RolandSysEx.ccPortamentoTime(p.channel, p.portamentoTime))
        service.send(RolandSysEx.ccPortamentoOnOff(p.channel, p.portamentoOn))
        service.send(RolandSysEx.ccPolyMode(p.channel))
    }

    // ---- Per-slider reset helpers ----------------------------------------
    // Each helper restores a single CC to its documented default and
    // immediately pushes the change to the keyboard, so the inline ↺ button
    // next to a slider has an audible effect without the user having to
    // move the slider afterwards.

    fun resetVolume(index: Int) { updatePart(index) { it.copy(volume = 110) }; pushVolume(index) }
    fun resetPan(index: Int) { updatePart(index) { it.copy(pan = 64) }; pushPan(index) }
    fun resetReverb(index: Int) { updatePart(index) { it.copy(reverb = 40) }; pushReverb(index) }
    fun resetChorus(index: Int) { updatePart(index) { it.copy(chorus = 0) }; pushChorus(index) }

    fun resetExpression(index: Int) { updatePart(index) { it.copy(expression = 127) }; pushExpression(index) }
    fun resetCutoff(index: Int) { updatePart(index) { it.copy(cutoff = 64) }; pushCutoff(index) }
    fun resetResonance(index: Int) { updatePart(index) { it.copy(resonance = 64) }; pushResonance(index) }
    fun resetAttack(index: Int) { updatePart(index) { it.copy(attack = 64) }; pushAttack(index) }
    fun resetDecay(index: Int) { updatePart(index) { it.copy(decay = 64) }; pushDecay(index) }
    fun resetRelease(index: Int) { updatePart(index) { it.copy(release = 64) }; pushRelease(index) }
    fun resetVibratoRate(index: Int) { updatePart(index) { it.copy(vibratoRate = 64) }; pushVibratoRate(index) }
    fun resetVibratoDepth(index: Int) { updatePart(index) { it.copy(vibratoDepth = 64) }; pushVibratoDepth(index) }
    fun resetVibratoDelay(index: Int) { updatePart(index) { it.copy(vibratoDelay = 64) }; pushVibratoDelay(index) }
    fun resetPortamentoTime(index: Int) { updatePart(index) { it.copy(portamentoTime = 0) }; pushPortamentoTime(index) }

    // ---- Demo songs --------------------------------------------------------

    fun playDemoSong(index: Int) {
        service.sendAll(RolandSysEx.selectDemoSong(index))
    }

    // ---- Master / Effects --------------------------------------------------

    fun pushMasterVolume() {
        val v = _state.value.master.masterVolume.coerceIn(0, 127)
        service.send(RolandSysEx.universalMasterVolume((v shl 7) or v))
    }

    fun pushReverbType() = service.send(RolandSysEx.setReverbType(model(), _state.value.master.reverbType))
    fun pushChorusType() = service.send(RolandSysEx.setChorusType(model(), _state.value.master.chorusType))

    fun panic() {
        for (ch in 1..16) {
            service.send(RolandSysEx.allNotesOff(ch))
            service.send(RolandSysEx.resetAllControllers(ch))
        }
    }

    fun tempoUp() = service.send(RolandSysEx.tempoUp(model()))
    fun tempoDown() = service.send(RolandSysEx.tempoDown(model()))
    fun demoOff() = service.send(RolandSysEx.demoOff(model()))

    // ---- LoopMix -----------------------------------------------------------

    fun loopMixStart() {
        updateLoopMix { it.copy(running = true) }
        val lm = _state.value.loopmix
        service.sendAll(RolandSysEx.loopMixStyle(1, lm.styleIdx))
        service.sendAll(RolandSysEx.loopMixKey(1, lm.keyIdx))
        service.sendAll(RolandSysEx.loopMixVariation(1, lm.variation))
        if (model() == RolandSysEx.Model.GP) {
            // GO:PIANO uses a documented one-shot LoopMix trigger SysEx.
            service.send(RolandSysEx.LOOPMIX_GP_RAW)
        } else {
            service.send(RolandSysEx.loopMixStart(model()))
        }
    }
    fun loopMixStop() {
        updateLoopMix { it.copy(running = false) }
        service.send(RolandSysEx.loopMixStop(model()))
    }
    fun loopMixSetStyle(i: Int) {
        updateLoopMix { it.copy(styleIdx = i) }
        service.sendAll(RolandSysEx.loopMixStyle(1, i))
    }
    fun loopMixSetKey(i: Int) {
        updateLoopMix { it.copy(keyIdx = i) }
        service.sendAll(RolandSysEx.loopMixKey(1, i))
    }
    fun loopMixSetVariation(i: Int) {
        updateLoopMix { it.copy(variation = i) }
        service.sendAll(RolandSysEx.loopMixVariation(1, i))
    }

    fun sendAllToKeyboard() {
        _state.value.performance.parts.indices.forEach { sendPart(it) }
        _state.value.performance.zones.indices.forEach { pushZone(it) }
        pushMasterVolume()
        pushReverbType()
        pushChorusType()
    }

    // ---- Profiles ----------------------------------------------------------

    fun saveProfile(name: String, description: String = "") {
        if (name.isBlank()) return
        val p = Profile(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            createdAt = System.currentTimeMillis(),
            state = _state.value,
            description = description,
        )
        val next = listOf(p) + _profiles.value
        _profiles.value = next
        viewModelScope.launch { repo.saveProfiles(next) }
    }

    fun deleteProfile(id: String) {
        val next = _profiles.value.filterNot { it.id == id }
        _profiles.value = next
        viewModelScope.launch { repo.saveProfiles(next) }
    }

    fun loadProfile(id: String) {
        val p = _profiles.value.firstOrNull { it.id == id } ?: return
        val nextState = p.state.copy(activeProfileId = p.id)
        _state.value = nextState
        viewModelScope.launch { repo.saveState(nextState) }
        sendAllToKeyboard()
    }

    // ---- Macros ------------------------------------------------------------

    fun startMacroRecording() {
        recordedEvents.clear()
        recordStart = System.currentTimeMillis()
        _recording.value = true
    }

    fun stopMacroRecording(name: String): Macro? {
        if (!_recording.value) return null
        _recording.value = false
        if (name.isBlank() || recordedEvents.isEmpty()) return null
        val macro = Macro(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            createdAt = System.currentTimeMillis(),
            events = recordedEvents.toList(),
        )
        val next = listOf(macro) + _macros.value
        _macros.value = next
        viewModelScope.launch { repo.saveMacros(next) }
        recordedEvents.clear()
        return macro
    }

    fun cancelMacroRecording() {
        _recording.value = false
        recordedEvents.clear()
    }

    fun deleteMacro(id: String) {
        val next = _macros.value.filterNot { it.id == id }
        _macros.value = next
        viewModelScope.launch { repo.saveMacros(next) }
    }

    fun playMacro(id: String) {
        val m = _macros.value.firstOrNull { it.id == id } ?: return
        viewModelScope.launch { playMacroOn(service, m) }
    }

    private fun handleEventForRecording(entry: MidiMonitorEntry) {
        if (!_recording.value) return
        if (entry.direction != MidiDirection.OUT) return  // record only outgoing
        recordedEvents.add(MacroEvent(entry.timestamp - recordStart, entry.bytes))
    }

    // ---- Automations -------------------------------------------------------

    fun upsertAutomation(a: Automation) {
        val list = _automations.value.toMutableList()
        val idx = list.indexOfFirst { it.id == a.id }
        if (idx >= 0) list[idx] = a else list.add(0, a)
        _automations.value = list
        viewModelScope.launch { repo.saveAutomations(list) }
    }

    fun deleteAutomation(id: String) {
        val next = _automations.value.filterNot { it.id == id }
        _automations.value = next
        viewModelScope.launch { repo.saveAutomations(next) }
    }

    fun toggleAutomation(id: String) {
        val list = _automations.value.map {
            if (it.id == id) it.copy(enabled = !it.enabled) else it
        }
        _automations.value = list
        viewModelScope.launch { repo.saveAutomations(list) }
    }

    class Factory(private val app: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
            CompanionViewModel(app) as T
    }
}
