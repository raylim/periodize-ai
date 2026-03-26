package com.periodizeai.app.ui.root

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.periodizeai.app.navigation.Screen
import com.periodizeai.app.ui.analytics.AnalyticsScreen
import com.periodizeai.app.ui.dashboard.DashboardScreen
import com.periodizeai.app.ui.history.HistoryScreen
import com.periodizeai.app.ui.settings.SettingsScreen
import com.periodizeai.app.ui.workout.WorkoutScreen

private data class TabItem(
    val screen: Screen,
    val label: String,
    val icon: @Composable () -> Unit,
)

private val tabs = listOf(
    TabItem(Screen.Dashboard, "Dashboard") { Icon(Icons.Filled.Home,          "Dashboard") },
    TabItem(Screen.Workout,   "Workout")   { Icon(Icons.Filled.FitnessCenter, "Workout") },
    TabItem(Screen.History,   "History")   { Icon(Icons.Filled.History,       "History") },
    TabItem(Screen.Analytics, "Analytics") { Icon(Icons.Filled.BarChart,      "Analytics") },
    TabItem(Screen.Settings,  "Settings")  { Icon(Icons.Filled.Settings,      "Settings") },
)

@Composable
fun MainScaffold(rootNavController: NavController) {
    val tabNavController = rememberNavController()
    val backStackEntry by tabNavController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = currentRoute == tab.screen.route,
                        onClick  = {
                            tabNavController.navigate(tab.screen.route) {
                                popUpTo(tabNavController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState    = true
                            }
                        },
                        icon  = tab.icon,
                        label = { Text(tab.label) },
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController    = tabNavController,
            startDestination = Screen.Dashboard.route,
            modifier         = Modifier.padding(innerPadding),
        ) {
            composable(Screen.Dashboard.route) { DashboardScreen(rootNavController) }
            composable(Screen.Workout.route)   { WorkoutScreen(rootNavController) }
            composable(Screen.History.route)   { HistoryScreen(rootNavController) }
            composable(Screen.Analytics.route) { AnalyticsScreen() }
            composable(Screen.Settings.route)  { SettingsScreen(rootNavController) }
        }
    }
}
