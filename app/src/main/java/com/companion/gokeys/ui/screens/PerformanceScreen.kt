package com.companion.gokeys.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.companion.gokeys.R
import com.companion.gokeys.data.PartConfig
import com.companion.gokeys.data.Patches
import com.companion.gokeys.ui.components.ExpandableSectionCard
import com.companion.gokeys.ui.components.LabeledSlider
import com.companion.gokeys.ui.components.PatchLibrary
import com.companion.gokeys.ui.components.PrimaryButton
import com.companion.gokeys.ui.components.SectionCard
import com.companion.gokeys.ui.theme.Muted
import com.companion.gokeys.viewmodel.CompanionViewModel

/**
 * Performance screen.
 *
 *  - The MAIN channel is always expanded and the sound library is shown
 *    inline (no Pick-Sound round-trip).  The list reserves > 50 % of the
 *    window height so the scrollbar is comfortable.
 *  - Layer / Split / extra parts and the Master / Demo sections are
 *    collapsible.  Each part's "Sound shaping" CC panel lives *inside*
 *    that part's expand area so it folds away with the rest of the part.
 *  - Demo songs are tagged GO:KEYS — per the rolandgo-hacking documentation
 *    the five-demo set is a GO:KEYS feature; GO:PIANO ignores those
 *    addresses.
 */
@Composable
fun PerformanceScreen(vm: CompanionViewModel) {
    val state by vm.state.collectAsState()
    val perf = state.performance
    val screenH = LocalConfiguration.current.screenHeightDp

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
    ) {
        SectionCard(title = stringResource(R.string.section_performance)) {
            Text(stringResource(R.string.performance_intro), color = Muted)
        }

        for (i in perf.parts.indices) {
            val title = when (i) {
                0 -> stringResource(R.string.part_main)
                1 -> stringResource(R.string.part_layer)
                2 -> stringResource(R.string.part_split_lower)
                else -> stringResource(R.string.part_extra, i + 1)
            }
            ExpandableSectionCard(
                title = title,
                stateKey = "perf-part-$i",
                initiallyExpanded = (i == 0),
            ) {
                PartBody(
                    vm = vm,
                    partIndex = i,
                    title = title,
                    isMain = i == 0,
                    minListHeight = (screenH * 0.55f),
                )
            }
        }

        ExpandableSectionCard(
            title = stringResource(R.string.section_master),
            stateKey = "perf-master",
            initiallyExpanded = false,
        ) {
            LabeledSlider(
                stringResource(R.string.slider_master_vol), state.master.masterVolume,
                onValueChange = { v -> vm.updateMaster { it.copy(masterVolume = v) } },
                onValueChangeFinished = { vm.pushMasterVolume() },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton(text = stringResource(R.string.btn_tempo_down), onClick = { vm.tempoDown() })
                PrimaryButton(text = stringResource(R.string.btn_tempo_up), onClick = { vm.tempoUp() })
                Box(Modifier.weight(1f))
                PrimaryButton(text = stringResource(R.string.btn_panic), onClick = { vm.panic() })
            }
        }

        ExpandableSectionCard(
            title = stringResource(R.string.section_demo),
            badge = stringResource(R.string.badge_keys),
            stateKey = "perf-demo",
            initiallyExpanded = false,
        ) {
            Text(stringResource(R.string.demo_intro), color = Muted)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (d in 0..4) {
                    PrimaryButton(text = stringResource(R.string.demo_song, d + 1), onClick = { vm.playDemoSong(d) })
                }
            }
            Spacer(Modifier.height(6.dp))
            PrimaryButton(text = stringResource(R.string.btn_demo_off), onClick = { vm.demoOff() })
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun PartBody(
    vm: CompanionViewModel,
    partIndex: Int,
    title: String,
    isMain: Boolean,
    minListHeight: Float,
) {
    val state by vm.state.collectAsState()
    val perf = state.performance
    val enabled = perf.partsEnabled[partIndex]
    val part = perf.parts[partIndex]
    val patch = Patches.find(part.patchMsb, part.patchLsb, part.patchPc)
    val zone = perf.zones[partIndex]

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.part_active), Modifier.weight(1f))
        Switch(checked = enabled, onCheckedChange = { vm.setPartEnabled(partIndex, it) })
    }
    Spacer(Modifier.height(6.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.part_channel, part.channel), Modifier.weight(1f), color = Muted)
        Text(stringResource(R.string.part_patch, patch?.name ?: "—"))
    }
    Spacer(Modifier.height(8.dp))

    // Inline sound library — always shown for the main channel and inside
    // each part's expand area for the layered/split parts.
    Text(
        stringResource(R.string.section_library_for, title),
        style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
    )
    Spacer(Modifier.height(6.dp))
    PatchLibrary(
        vm = vm,
        partIndex = partIndex,
        minListHeight = minListHeight.dp,
    )

    Spacer(Modifier.height(12.dp))
    LabeledSlider(
        stringResource(R.string.slider_volume), part.volume,
        onValueChange = { v -> vm.updatePart(partIndex) { it.copy(volume = v) } },
        onValueChangeFinished = { vm.pushVolume(partIndex) },
        onReset = { vm.resetVolume(partIndex) },
    )
    LabeledSlider(
        stringResource(R.string.slider_pan), part.pan, range = 0..127,
        onValueChange = { v -> vm.updatePart(partIndex) { it.copy(pan = v) } },
        onValueChangeFinished = { vm.pushPan(partIndex) },
        onReset = { vm.resetPan(partIndex) },
    )
    LabeledSlider(
        stringResource(R.string.slider_reverb), part.reverb,
        onValueChange = { v -> vm.updatePart(partIndex) { it.copy(reverb = v) } },
        onValueChangeFinished = { vm.pushReverb(partIndex) },
        onReset = { vm.resetReverb(partIndex) },
    )
    LabeledSlider(
        stringResource(R.string.slider_chorus), part.chorus,
        onValueChange = { v -> vm.updatePart(partIndex) { it.copy(chorus = v) } },
        onValueChangeFinished = { vm.pushChorus(partIndex) },
        onReset = { vm.resetChorus(partIndex) },
    )

    // Split / Zone
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.zone_split), Modifier.weight(1f))
        Switch(checked = zone.enabled, onCheckedChange = { vm.setZoneEnabled(partIndex, it) })
    }
    if (zone.enabled) {
        LabeledSlider(
            stringResource(R.string.zone_low, midiNote(zone.keyLow)), zone.keyLow, range = 0..127,
            onValueChange = { v -> vm.updateZone(partIndex) { it.copy(keyLow = v) } },
            onValueChangeFinished = { vm.pushZone(partIndex) },
        )
        LabeledSlider(
            stringResource(R.string.zone_high, midiNote(zone.keyHigh)), zone.keyHigh, range = 0..127,
            onValueChange = { v -> vm.updateZone(partIndex) { it.copy(keyHigh = v) } },
            onValueChangeFinished = { vm.pushZone(partIndex) },
        )
    }

    Spacer(Modifier.height(12.dp))
    SoundShapingPanel(vm = vm, partIndex = partIndex, part = part)
}

@Composable
private fun SoundShapingPanel(vm: CompanionViewModel, partIndex: Int, part: PartConfig) {
    ExpandableSectionCard(
        title = stringResource(R.string.section_sound_shaping),
        stateKey = "perf-shape-$partIndex",
        initiallyExpanded = false,
    ) {
        Text(stringResource(R.string.sound_shaping_intro), color = Muted)
        Spacer(Modifier.height(6.dp))
        LabeledSlider(
            stringResource(R.string.slider_expression), part.expression,
            onValueChange = { v -> vm.updatePart(partIndex) { it.copy(expression = v) } },
            onValueChangeFinished = { vm.pushExpression(partIndex) },
            onReset = { vm.resetExpression(partIndex) },
        )
        LabeledSlider(
            stringResource(R.string.slider_cutoff), part.cutoff,
            onValueChange = { v -> vm.updatePart(partIndex) { it.copy(cutoff = v) } },
            onValueChangeFinished = { vm.pushCutoff(partIndex) },
            onReset = { vm.resetCutoff(partIndex) },
        )
        LabeledSlider(
            stringResource(R.string.slider_resonance), part.resonance,
            onValueChange = { v -> vm.updatePart(partIndex) { it.copy(resonance = v) } },
            onValueChangeFinished = { vm.pushResonance(partIndex) },
            onReset = { vm.resetResonance(partIndex) },
        )
        LabeledSlider(
            stringResource(R.string.slider_attack), part.attack,
            onValueChange = { v -> vm.updatePart(partIndex) { it.copy(attack = v) } },
            onValueChangeFinished = { vm.pushAttack(partIndex) },
            onReset = { vm.resetAttack(partIndex) },
        )
        LabeledSlider(
            stringResource(R.string.slider_decay), part.decay,
            onValueChange = { v -> vm.updatePart(partIndex) { it.copy(decay = v) } },
            onValueChangeFinished = { vm.pushDecay(partIndex) },
            onReset = { vm.resetDecay(partIndex) },
        )
        LabeledSlider(
            stringResource(R.string.slider_release), part.release,
            onValueChange = { v -> vm.updatePart(partIndex) { it.copy(release = v) } },
            onValueChangeFinished = { vm.pushRelease(partIndex) },
            onReset = { vm.resetRelease(partIndex) },
        )
        LabeledSlider(
            stringResource(R.string.slider_vibrato_rate), part.vibratoRate,
            onValueChange = { v -> vm.updatePart(partIndex) { it.copy(vibratoRate = v) } },
            onValueChangeFinished = { vm.pushVibratoRate(partIndex) },
            onReset = { vm.resetVibratoRate(partIndex) },
        )
        LabeledSlider(
            stringResource(R.string.slider_vibrato_depth), part.vibratoDepth,
            onValueChange = { v -> vm.updatePart(partIndex) { it.copy(vibratoDepth = v) } },
            onValueChangeFinished = { vm.pushVibratoDepth(partIndex) },
            onReset = { vm.resetVibratoDepth(partIndex) },
        )
        LabeledSlider(
            stringResource(R.string.slider_vibrato_delay), part.vibratoDelay,
            onValueChange = { v -> vm.updatePart(partIndex) { it.copy(vibratoDelay = v) } },
            onValueChangeFinished = { vm.pushVibratoDelay(partIndex) },
            onReset = { vm.resetVibratoDelay(partIndex) },
        )
        LabeledSlider(
            stringResource(R.string.slider_portamento_time), part.portamentoTime,
            onValueChange = { v -> vm.updatePart(partIndex) { it.copy(portamentoTime = v) } },
            onValueChangeFinished = { vm.pushPortamentoTime(partIndex) },
            onReset = { vm.resetPortamentoTime(partIndex) },
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.toggle_portamento), Modifier.weight(1f))
            Switch(checked = part.portamentoOn, onCheckedChange = {
                vm.updatePart(partIndex) { p -> p.copy(portamentoOn = it) }
                vm.pushPortamentoOnOff(partIndex)
            })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.toggle_mono), Modifier.weight(1f))
            Switch(checked = part.mono, onCheckedChange = {
                vm.updatePart(partIndex) { p -> p.copy(mono = it) }
                vm.pushMonoMode(partIndex)
            })
        }
        Spacer(Modifier.height(6.dp))
        PrimaryButton(text = stringResource(R.string.btn_reset_cc), onClick = { vm.resetPartCC(partIndex) })
    }
}

private fun midiNote(n: Int): String {
    val names = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    val oct = n / 12 - 1
    return "${names[n % 12]}$oct"
}
