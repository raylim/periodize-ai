package com.periodizeai.app.wear.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.periodizeai.app.wear.data.WearDataStore

sealed class WearScreen(val route: String) {
    object Home : WearScreen("home")
    object Workout : WearScreen("workout")
    object RestTimer : WearScreen("rest_timer")
}

@Composable
fun WearApp(dataStore: WearDataStore) {
    val navController = rememberSwipeDismissableNavController()
    val currentWorkout by dataStore.currentWorkout.collectAsState()

    SwipeDismissableNavHost(
        navController = navController,
        startDestination = WearScreen.Home.route,
    ) {
        composable(WearScreen.Home.route) {
            WearHomeScreen(
                workout = currentWorkout,
                onStartWorkout = { navController.navigate(WearScreen.Workout.route) },
            )
        }
        composable(WearScreen.Workout.route) {
            currentWorkout?.let { workout ->
                WearWorkoutScreen(
                    workout = workout,
                    onRestTimer = { navController.navigate(WearScreen.RestTimer.route) },
                    onFinish = { navController.popBackStack(WearScreen.Home.route, false) },
                )
            }
        }
        composable(WearScreen.RestTimer.route) {
            WearRestTimerScreen(
                totalSeconds = 120,
                onFinished = { navController.popBackStack() },
            )
        }
    }
}
