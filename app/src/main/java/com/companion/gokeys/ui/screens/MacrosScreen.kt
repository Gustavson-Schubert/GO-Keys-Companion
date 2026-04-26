package com.companion.gokeys.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.companion.gokeys.R
import com.companion.gokeys.ui.components.GhostButton
import com.companion.gokeys.ui.components.PrimaryButton
import com.companion.gokeys.ui.components.SectionCard
import com.companion.gokeys.ui.theme.Muted
import com.companion.gokeys.viewmodel.CompanionViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MacrosScreen(vm: CompanionViewModel) {
    val macros by vm.macros.collectAsState()
    val recording by vm.recording.collectAsState()
    var name by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize()) {
        SectionCard(title = stringResource(R.string.section_macros)) {
            Text(stringResource(R.string.macros_intro), color = Muted)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = name, onValueChange = { name = it },
                placeholder = { Text(stringResource(R.string.macro_name)) },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!recording) {
                    PrimaryButton(text = stringResource(R.string.btn_record), onClick = { vm.startMacroRecording() })
                } else {
                    PrimaryButton(text = stringResource(R.string.btn_stop_record), onClick = {
                        vm.stopMacroRecording(if (name.isBlank()) "Macro" else name); name = ""
                    })
                    GhostButton(text = stringResource(R.string.btn_cancel), onClick = { vm.cancelMacroRecording() })
                }
            }
            if (recording) Text(stringResource(R.string.recording), color = MaterialTheme.colorScheme.primary)
        }

        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
            items(macros, key = { it.id }) { m ->
                val df = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
                SectionCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(m.name, style = MaterialTheme.typography.titleMedium)
                            Text("${df.format(Date(m.createdAt))} • ${m.events.size} ev",
                                color = Muted, style = MaterialTheme.typography.bodyMedium)
                        }
                        PrimaryButton(text = stringResource(R.string.btn_play), onClick = { vm.playMacro(m.id) })
                        Spacer(Modifier.width(6.dp))
                        GhostButton(text = stringResource(R.string.btn_delete), onClick = { vm.deleteMacro(m.id) })
                    }
                }
            }
        }
    }
}
