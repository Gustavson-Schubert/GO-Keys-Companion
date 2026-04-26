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

@Composable
fun ProfilesScreen(vm: CompanionViewModel) {
    val profiles by vm.profiles.collectAsState()
    var name by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        SectionCard(title = stringResource(R.string.section_profiles)) {
            Text(stringResource(R.string.profiles_intro), color = Muted)
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(value = name, onValueChange = { name = it },
                placeholder = { Text(stringResource(R.string.profile_name)) },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = desc, onValueChange = { desc = it },
                placeholder = { Text(stringResource(R.string.profile_desc)) },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            PrimaryButton(text = stringResource(R.string.btn_save), onClick = {
                if (name.isNotBlank()) { vm.saveProfile(name, desc); name = ""; desc = "" }
            })
        }
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
            items(profiles, key = { it.id }) { p ->
                SectionCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(p.name, style = MaterialTheme.typography.titleMedium)
                            if (p.description.isNotBlank())
                                Text(p.description, color = Muted, style = MaterialTheme.typography.bodyMedium)
                        }
                        PrimaryButton(text = stringResource(R.string.btn_load), onClick = { vm.loadProfile(p.id) })
                        Spacer(Modifier.width(6.dp))
                        GhostButton(text = stringResource(R.string.btn_delete), onClick = { vm.deleteProfile(p.id) })
                    }
                }
            }
        }
    }
}
