package com.companion.gokeys.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.companion.gokeys.R
import com.companion.gokeys.data.HiddenPatches
import com.companion.gokeys.data.Patch
import com.companion.gokeys.data.Patches
import com.companion.gokeys.ui.theme.Muted
import com.companion.gokeys.ui.theme.SurfaceVariant
import com.companion.gokeys.viewmodel.CompanionViewModel
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Inline patch library — search box, category chips and a scrollable patch
 * list with the custom drag scrollbar.  When the user taps a patch the
 * given [partIndex] is updated and the patch is sent to the keyboard
 * immediately via [CompanionViewModel.selectPatchForPart].
 *
 * The list area always reserves at least 50 % of the screen height so the
 * scrollbar is comfortable to use even on small phones.
 *
 * Search/category/scroll state is shared with the global [LibraryUiState]
 * so it is restored across launches just like the standalone screen.
 */
@Composable
fun PatchLibrary(
    vm: CompanionViewModel,
    partIndex: Int,
    modifier: Modifier = Modifier,
    /** Minimum height of the scrollable list. */
    minListHeight: Dp = (LocalConfiguration.current.screenHeightDp * 0.55f).dp,
    onPicked: (() -> Unit)? = null,
) {
    val state by vm.state.collectAsState()
    val lib = state.library

    val all = remember { Patches.ALL + HiddenPatches.ALL }
    val categories = remember(all) {
        listOf("ALL") + all.map { it.category }.toSortedSet().toList()
    }

    val filtered by remember(lib.searchQuery, lib.selectedCategory) {
        derivedStateOf {
            val q = lib.searchQuery.trim().lowercase()
            all.asSequence()
                .filter { lib.selectedCategory == "ALL" || it.category == lib.selectedCategory }
                .filter { q.isEmpty() || it.name.lowercase().contains(q) || it.category.lowercase().contains(q) }
                .toList()
        }
    }

    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = lib.scrollIndex,
        initialFirstVisibleItemScrollOffset = lib.scrollOffset,
    )
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .distinctUntilChanged()
            .debounce(150)
            .collect { (idx, off) ->
                vm.updateLibraryUi { it.copy(scrollIndex = idx, scrollOffset = off) }
            }
    }

    Column(modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = lib.searchQuery,
            onValueChange = { v -> vm.updateLibraryUi { it.copy(searchQuery = v) } },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text(stringResource(R.string.search_placeholder)) },
        )
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(categories) { cat ->
                val sel = lib.selectedCategory == cat
                Box(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (sel) MaterialTheme.colorScheme.primary else SurfaceVariant)
                        .clickable { vm.updateLibraryUi { it.copy(selectedCategory = cat) } }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text(
                        cat,
                        color = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.shown_count, filtered.size, all.size),
            color = Muted,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(minListHeight),
        ) {
            Row(Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxSize(),
                ) {
                    items(filtered, key = { "${it.msb}-${it.lsb}-${it.pc}-${it.name}" }) { p ->
                        val active = state.performance.parts[partIndex].let {
                            it.patchMsb == p.msb && it.patchLsb == p.lsb && it.patchPc == p.pc
                        }
                        PatchRow(p, active = active) {
                            vm.selectPatchForPart(partIndex, p.msb, p.lsb, p.pc)
                            onPicked?.invoke()
                        }
                    }
                }
                LazyColumnScrollbar(state = listState)
            }
        }
    }
}

@Composable
private fun PatchRow(p: Patch, active: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else SurfaceVariant)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text(p.category, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
        }
        Spacer(Modifier.padding(horizontal = 6.dp))
        Text(
            p.name,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
        )
        Text("${p.msb}/${p.lsb}/${p.pc}", color = Muted, style = MaterialTheme.typography.bodyMedium)
    }
}
