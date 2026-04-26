package com.companion.gokeys.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.companion.gokeys.R
import com.companion.gokeys.midi.MidiDirection
import com.companion.gokeys.midi.RolandSysEx
import com.companion.gokeys.ui.components.GhostButton
import com.companion.gokeys.ui.components.SectionCard
import com.companion.gokeys.ui.theme.Muted
import com.companion.gokeys.ui.theme.SurfaceVariant
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.companion.gokeys.viewmodel.CompanionViewModel

@Composable
fun MonitorScreen(vm: CompanionViewModel) {
    val entries by vm.service.monitor.collectAsState()
    val state = rememberLazyListState()
    LaunchedEffect(entries.size) {
        if (entries.isNotEmpty()) state.animateScrollToItem(entries.size - 1)
    }
    val df = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()) }
    Column(Modifier.fillMaxSize()) {
        SectionCard(title = stringResource(R.string.monitor_label)) {
            Row {
                Text(stringResource(R.string.monitor_count, entries.size), color = Muted, modifier = Modifier.weight(1f))
                GhostButton(text = stringResource(R.string.btn_clear), onClick = { vm.service.clearMonitor() })
            }
        }
        LazyColumn(state = state, modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
            items(entries, key = { it.timestamp.toString() + it.bytes.contentHashCode() }) { e ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 2.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(SurfaceVariant)
                        .padding(8.dp),
                ) {
                    Text(
                        if (e.direction == MidiDirection.OUT) "▶" else "◀",
                        color = if (e.direction == MidiDirection.OUT) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.tertiary,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(df.format(Date(e.timestamp)), color = Muted, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.width(8.dp))
                    Text(RolandSysEx.bytesToHex(e.bytes), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
