package com.johndev.verset.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.johndev.verset.data.Prefs
import com.johndev.verset.repository.BibleRepository
import com.johndev.verset.repository.SyncRepository
import com.johndev.verset.ui.screens.MyVersesScreen
import com.johndev.verset.ui.screens.ReaderScreen
import com.johndev.verset.ui.screens.SettingsScreen

private data class Tab(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val tabs = listOf(
    Tab("reader", "Read", Icons.Filled.MenuBook),
    Tab("myVerses", "My Verses", Icons.Filled.List),
    Tab("settings", "Settings", Icons.Filled.Settings)
)

@Composable
fun VersetNavGraph(
    repository: BibleRepository,
    syncRepository: SyncRepository,
    prefs: Prefs,
    onDarkModeChange: (follow: Boolean, dark: Boolean) -> Unit
) {
    val navController: NavHostController = rememberNavController()

    Scaffold(
        bottomBar = {
            val backStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = backStackEntry?.destination?.route
            NavigationBar {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = currentRoute == tab.route,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "reader",
            modifier = androidx.compose.ui.Modifier.padding(padding)
        ) {
            composable("reader") {
                ReaderScreen(repository = repository, prefs = prefs)
            }
            composable("myVerses") {
                MyVersesScreen(repository = repository)
            }
            composable("settings") {
                SettingsScreen(
                    prefs = prefs,
                    syncRepository = syncRepository,
                    onDarkModeChange = onDarkModeChange
                )
            }
        }
    }
}
