package com.companion.gokeys.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.companion.gokeys.R
import com.companion.gokeys.midi.MidiDeviceItem
import com.companion.gokeys.midi.MidiTransport
import com.companion.gokeys.ui.components.GhostButton
import com.companion.gokeys.ui.components.PrimaryButton
import com.companion.gokeys.ui.components.SectionCard
import com.companion.gokeys.ui.components.StatusDot
import com.companion.gokeys.ui.theme.Border
import com.companion.gokeys.ui.theme.Muted
import com.companion.gokeys.ui.theme.SurfaceVariant
import com.companion.gokeys.viewmodel.CompanionViewModel

@Composable
fun ConnectionScreen(vm: CompanionViewModel) {
    val state by vm.state.collectAsState()
    val connected by vm.service.connected.collectAsState()
    val device by vm.service.connectedDevice.collectAsState()
    val ble by vm.service.bleDevices.collectAsState()
    val scanning by vm.service.scanning.collectAsState()
    val status by vm.service.status.collectAsState()

    var usbList by remember { mutableStateOf<List<MidiDeviceItem>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        usbList = vm.refreshUsb()
        vm.service.errors.collect { error = it }
    }

    val btDeniedMsg = stringResource(R.string.err_bt_perm_denied)
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        if (granted.values.all { it }) vm.startScan() else error = btDeniedMsg
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusDot(connected)
            Spacer(Modifier.size(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (connected) (device?.name ?: stringResource(R.string.status_connected))
                    else stringResource(R.string.status_disconnected),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(status, color = Muted, style = MaterialTheme.typography.bodyMedium)
            }
            if (connected) {
                GhostButton(text = stringResource(R.string.btn_disconnect), onClick = { vm.disconnect() })
            }
        }

        SectionCard(title = stringResource(R.string.section_model)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("GP" to stringResource(R.string.model_gp_label),
                       "GK" to stringResource(R.string.model_gk_label)).forEach { pair ->
                    val sel = state.model == pair.first
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (sel) MaterialTheme.colorScheme.primary else SurfaceVariant)
                            .clickable { vm.setModel(pair.first) }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    ) {
                        Text(
                            pair.second,
                            color = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
            }
        }

        SectionCard(title = stringResource(R.string.section_usb)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostButton(text = stringResource(R.string.btn_refresh_usb),
                    onClick = { usbList = vm.refreshUsb() })
            }
            Spacer(Modifier.size(8.dp))
            if (usbList.isEmpty()) {
                Text(stringResource(R.string.empty_devices), color = Muted)
            } else {
                usbList.forEach { d -> DeviceRow(d, connected = device?.id == d.id, onClick = { vm.connect(d) }) }
            }
        }

        SectionCard(title = stringResource(R.string.section_ble)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!scanning) {
                    PrimaryButton(text = stringResource(R.string.btn_scan_ble), onClick = {
                        val needed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
                        else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
                        permLauncher.launch(needed)
                    })
                } else {
                    GhostButton(text = stringResource(R.string.btn_stop_scan), onClick = { vm.stopScan() })
                }
            }
            Spacer(Modifier.size(8.dp))
            if (ble.isEmpty()) {
                Text(stringResource(R.string.empty_devices), color = Muted)
            } else {
                ble.forEach { d -> DeviceRow(d, connected = device?.id == d.id, onClick = { vm.connect(d) }) }
            }
        }

        if (error != null) {
            Text(
                stringResource(R.string.error_prefix, error.orEmpty()),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(12.dp),
            )
        }
    }
}

@Composable
private fun DeviceRow(d: MidiDeviceItem, connected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (connected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else SurfaceVariant)
            .clickable { onClick() }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (d.transport == MidiTransport.USB) "🔌" else "📶",
            modifier = Modifier.padding(end = 8.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(d.name, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium)
            Text(d.id, color = Muted, style = MaterialTheme.typography.bodyMedium)
        }
        if (connected) Text(stringResource(R.string.status_connected), color = MaterialTheme.colorScheme.primary)
    }
}
