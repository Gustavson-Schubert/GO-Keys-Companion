package com.companion.gokeys.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.companion.gokeys.R
import com.companion.gokeys.data.Automation
import com.companion.gokeys.ui.components.GhostButton
import com.companion.gokeys.ui.components.PrimaryButton
import com.companion.gokeys.ui.components.SectionCard
import com.companion.gokeys.ui.theme.Muted
import com.companion.gokeys.ui.theme.SurfaceVariant
import com.companion.gokeys.viewmodel.CompanionViewModel
import java.util.UUID

@Composable
fun AutomationsScreen(vm: CompanionViewModel) {
    val automations by vm.automations.collectAsState()
    var name by remember { mutableStateOf("") }
    var triggerType by remember { mutableStateOf("noteOn") }
    var triggerHex by remember { mutableStateOf("") }
    var paramA by remember { mutableStateOf("60") }
    var paramB by remember { mutableStateOf("60") }
    var actionType by remember { mutableStateOf("panic") }
    var actionHex by remember { mutableStateOf("") }
    var actionA by remember { mutableStateOf("0") }

    Column(Modifier.fillMaxSize()) {
        SectionCard(title = stringResource(R.string.section_automations_new)) {
            Text(stringResource(R.string.automations_intro), color = Muted)
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(value = name, onValueChange = { name = it },
                placeholder = { Text(stringResource(R.string.automation_name)) },
                singleLine = true, modifier = Modifier.fillMaxWidth())

            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.trigger), color = Muted)
            ChipRow(listOf("noteOn", "cc", "sysex", "patch"), triggerType) { triggerType = it }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(value = paramA, onValueChange = { paramA = it },
                    label = { Text("A") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(value = paramB, onValueChange = { paramB = it },
                    label = { Text("B") }, singleLine = true, modifier = Modifier.weight(1f))
            }
            if (triggerType == "sysex") OutlinedTextField(value = triggerHex, onValueChange = { triggerHex = it },
                placeholder = { Text("F0 41 10 …") }, singleLine = true, modifier = Modifier.fillMaxWidth())

            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.action), color = Muted)
            ChipRow(listOf("panic", "send", "patch", "loopmix", "macro"), actionType) { actionType = it }
            OutlinedTextField(value = actionA, onValueChange = { actionA = it },
                label = { Text(stringResource(R.string.action_param)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            if (actionType in setOf("send", "patch", "macro")) OutlinedTextField(value = actionHex, onValueChange = { actionHex = it },
                placeholder = { Text(stringResource(R.string.action_hex_or_id)) }, singleLine = true, modifier = Modifier.fillMaxWidth())

            Spacer(Modifier.height(8.dp))
            PrimaryButton(text = stringResource(R.string.btn_save), onClick = {
                if (name.isBlank()) return@PrimaryButton
                vm.upsertAutomation(Automation(
                    id = UUID.randomUUID().toString(),
                    name = name.trim(),
                    triggerType = triggerType,
                    triggerParamA = paramA.toIntOrNull() ?: 0,
                    triggerParamB = paramB.toIntOrNull() ?: 0,
                    triggerHex = triggerHex,
                    actionType = actionType,
                    actionParamA = actionA.toIntOrNull() ?: 0,
                    actionHex = actionHex,
                ))
                name = ""; triggerHex = ""; actionHex = ""
            })
        }

        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
            items(automations, key = { it.id }) { a ->
                SectionCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(a.name, style = MaterialTheme.typography.titleMedium)
                            Text("${a.triggerType} → ${a.actionType}",
                                color = Muted, style = MaterialTheme.typography.bodyMedium)
                        }
                        Switch(checked = a.enabled, onCheckedChange = { vm.toggleAutomation(a.id) })
                        Spacer(Modifier.width(6.dp))
                        GhostButton(text = stringResource(R.string.btn_delete), onClick = { vm.deleteAutomation(a.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ChipRow(opts: List<String>, sel: String, onPick: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        opts.forEach { v ->
            val isSel = v == sel
            Box(
                Modifier.clip(RoundedCornerShape(8.dp))
                    .background(if (isSel) MaterialTheme.colorScheme.primary else SurfaceVariant)
                    .clickable { onPick(v) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(v, color = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}
