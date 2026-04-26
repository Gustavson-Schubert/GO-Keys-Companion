package com.companion.gokeys.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.companion.gokeys.R
import com.companion.gokeys.ui.components.SectionCard

@Composable
fun HelpScreen() {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SectionCard(title = stringResource(R.string.help_conn_title)) {
            Text(stringResource(R.string.help_conn_usb))
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.help_conn_ble))
        }
        SectionCard(title = stringResource(R.string.help_layer_title)) {
            Text(stringResource(R.string.help_layer_explain))
        }
        SectionCard(title = stringResource(R.string.help_loopmix_title)) {
            Text(stringResource(R.string.help_loopmix_explain))
        }
        SectionCard(title = stringResource(R.string.help_macro_title)) {
            Text(stringResource(R.string.help_macro_explain))
        }
        SectionCard(title = stringResource(R.string.help_automation_title)) {
            Text(stringResource(R.string.help_automation_explain))
        }
        SectionCard(title = stringResource(R.string.help_trouble_title)) {
            Text(stringResource(R.string.help_trouble_explain))
        }
    }
}
