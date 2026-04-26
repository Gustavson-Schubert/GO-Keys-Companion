package com.companion.gokeys.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.companion.gokeys.R
import com.companion.gokeys.data.LoopMix
import com.companion.gokeys.ui.components.LabeledSlider
import com.companion.gokeys.ui.components.PrimaryButton
import com.companion.gokeys.ui.components.SectionCard
import com.companion.gokeys.ui.theme.SurfaceVariant
import com.companion.gokeys.viewmodel.CompanionViewModel

@Composable
fun LoopMixScreen(vm: CompanionViewModel) {
    val state by vm.state.collectAsState()
    val lm = state.loopmix
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SectionCard(
            title = stringResource(R.string.section_loopmix),
            badge = stringResource(R.string.badge_keys),
        ) {
            Text(stringResource(R.string.loopmix_intro))
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.loopmix_model_note),
                color = com.companion.gokeys.ui.theme.Muted,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!lm.running) PrimaryButton(text = stringResource(R.string.btn_start), onClick = { vm.loopMixStart() })
                else PrimaryButton(text = stringResource(R.string.btn_stop), onClick = { vm.loopMixStop() })
            }
        }
        SectionCard(title = stringResource(R.string.loopmix_style)) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(LoopMix.STYLES.size) { i ->
                    val sel = i == lm.styleIdx
                    Box(
                        Modifier.clip(RoundedCornerShape(8.dp))
                            .background(if (sel) MaterialTheme.colorScheme.primary else SurfaceVariant)
                            .clickable { vm.loopMixSetStyle(i) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Text(LoopMix.STYLES[i],
                            color = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
        SectionCard(title = stringResource(R.string.loopmix_key)) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(LoopMix.KEYS.size) { i ->
                    val sel = i == lm.keyIdx
                    Box(
                        Modifier.clip(RoundedCornerShape(8.dp))
                            .background(if (sel) MaterialTheme.colorScheme.primary else SurfaceVariant)
                            .clickable { vm.loopMixSetKey(i) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Text(LoopMix.KEYS[i],
                            color = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
        SectionCard(title = stringResource(R.string.loopmix_variation)) {
            LabeledSlider(stringResource(R.string.loopmix_variation), lm.variation, range = 0..7,
                onValueChange = { vm.loopMixSetVariation(it) })
        }
    }
}
