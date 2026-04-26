package com.companion.gokeys.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.companion.gokeys.R
import com.companion.gokeys.ui.components.PatchLibrary
import com.companion.gokeys.ui.components.SectionCard
import com.companion.gokeys.viewmodel.CompanionViewModel

/**
 * Stand-alone library screen kept for backwards compatibility with the
 * `patches/{part}` deep link.  The Performance screen now embeds the
 * library inline for every part, so this screen is rarely reached.
 */
@Composable
fun PatchesScreen(vm: CompanionViewModel, partIndex: Int, onPicked: () -> Unit) {
    val screenH = LocalConfiguration.current.screenHeightDp
    Column(Modifier.fillMaxSize().padding(bottom = 8.dp)) {
        SectionCard(title = stringResource(R.string.section_library_for, partLabel(partIndex))) {
            PatchLibrary(
                vm = vm,
                partIndex = partIndex,
                minListHeight = (screenH * 0.7f).dp,
                onPicked = onPicked,
            )
        }
    }
}

@Composable
private fun partLabel(index: Int): String = when (index) {
    0 -> stringResource(R.string.part_main)
    1 -> stringResource(R.string.part_layer)
    2 -> stringResource(R.string.part_split_lower)
    else -> stringResource(R.string.part_extra, index + 1)
}
