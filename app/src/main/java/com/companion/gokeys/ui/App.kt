package com.companion.gokeys.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.companion.gokeys.R
import com.companion.gokeys.ui.screens.AutomationsScreen
import com.companion.gokeys.ui.screens.ConnectionScreen
import com.companion.gokeys.ui.screens.HelpScreen
import com.companion.gokeys.ui.screens.LoopMixScreen
import com.companion.gokeys.ui.screens.MacrosScreen
import com.companion.gokeys.ui.screens.MonitorScreen
import com.companion.gokeys.ui.screens.PatchesScreen
import com.companion.gokeys.ui.screens.PerformanceScreen
import com.companion.gokeys.ui.screens.ProfilesScreen
import com.companion.gokeys.viewmodel.CompanionViewModel

private data class NavItem(val route: String, val labelRes: Int, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val NAV_ITEMS = listOf(
    NavItem("connection", R.string.nav_connection, Icons.Default.Cable),
    NavItem("performance", R.string.nav_performance, Icons.Default.MusicNote),
    NavItem("loopmix", R.string.nav_loopmix, Icons.Default.Loop),
    NavItem("profiles", R.string.nav_profiles, Icons.Default.Person),
    NavItem("macros", R.string.nav_macros, Icons.Default.PlayArrow),
    NavItem("automations", R.string.nav_automations, Icons.Default.SmartToy),
    NavItem("monitor", R.string.nav_monitor, Icons.Default.GraphicEq),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(vm: CompanionViewModel) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_app), style = MaterialTheme.typography.titleLarge) },
                actions = {
                    IconButton(onClick = { nav.navigate("help") }) {
                        Icon(Icons.Default.HelpOutline, contentDescription = stringResource(R.string.cd_help))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
        bottomBar = {
            NavigationBar {
                NAV_ITEMS.forEach { item ->
                    NavigationBarItem(
                        selected = currentRoute == item.route,
                        onClick = {
                            nav.navigate(item.route) {
                                launchSingleTop = true
                                popUpTo(nav.graph.startDestinationId) { saveState = true }
                                restoreState = true
                            }
                        },
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = { Text(stringResource(item.labelRes), style = MaterialTheme.typography.labelSmall) },
                    )
                }
            }
        },
    ) { padding: PaddingValues ->
        NavHost(
            navController = nav,
            startDestination = "connection",
            modifier = Modifier.padding(padding),
        ) {
            composable("connection") { ConnectionScreen(vm) }
            composable("performance") {
                PerformanceScreen(vm)
            }
            composable(
                route = "patches/{part}",
                arguments = listOf(navArgument("part") { type = NavType.IntType }),
            ) { entry ->
                val part = entry.arguments?.getInt("part") ?: 0
                PatchesScreen(vm, partIndex = part, onPicked = { nav.popBackStack() })
            }
            composable("loopmix") { LoopMixScreen(vm) }
            composable("profiles") { ProfilesScreen(vm) }
            composable("macros") { MacrosScreen(vm) }
            composable("automations") { AutomationsScreen(vm) }
            composable("monitor") { MonitorScreen(vm) }
            composable("help") { HelpScreen() }
        }
    }
}
