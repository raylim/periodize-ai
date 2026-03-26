package com.periodizeai.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.periodizeai.app.navigation.Screen
import com.periodizeai.app.ui.exerciselibrary.CreateExerciseScreen
import com.periodizeai.app.ui.exerciselibrary.ExerciseLibraryScreen
import com.periodizeai.app.ui.onboarding.OnboardingScreen
import com.periodizeai.app.ui.planeditor.PlanEditorScreen
import com.periodizeai.app.ui.root.MainScaffold
import com.periodizeai.app.ui.workout.WorkoutExecutionScreen
import com.periodizeai.app.ui.theme.PeriodizeAITheme
import com.periodizeai.app.viewmodels.UserProfileViewModel
import org.koin.compose.viewmodel.koinViewModel

/**
 * App root composable — entry point for both iOS and Android.
 * Gates between Onboarding and the main tab scaffold.
 */
@Composable
fun App() {
    PeriodizeAITheme {
        val navController = rememberNavController()
        val userProfileViewModel = koinViewModel<UserProfileViewModel>()
        val hasCompletedOnboarding by userProfileViewModel.hasCompletedOnboarding.collectAsState()

        val startDestination = if (hasCompletedOnboarding) Screen.Dashboard.route
                               else Screen.Onboarding.route

        NavHost(navController = navController, startDestination = startDestination) {
            composable(Screen.Onboarding.route) {
                OnboardingScreen(
                    onComplete = {
                        navController.navigate(Screen.Dashboard.route) {
                            popUpTo(Screen.Onboarding.route) { inclusive = true }
                        }
                    }
                )
            }

            composable(Screen.Dashboard.route) { MainScaffold(navController) }
            composable(Screen.Workout.route)   { MainScaffold(navController) }
            composable(Screen.History.route)   { MainScaffold(navController) }
            composable(Screen.Analytics.route) { MainScaffold(navController) }
            composable(Screen.Settings.route)  { MainScaffold(navController) }

            composable(Screen.ExerciseLibrary.route) { ExerciseLibraryScreen(navController) }
            composable(Screen.CreateExercise.route) { CreateExerciseScreen(navController) }
            composable(Screen.PlanEditor.route) { PlanEditorScreen(navController) }

            // Deep link: periodizeai://workout/{uuid}
            composable(
                route = Screen.WorkoutDetail.route,
                arguments = Screen.WorkoutDetail.arguments,
                deepLinks = listOf(
                    androidx.navigation.navDeepLink {
                        uriPattern = Screen.WorkoutDetail.deepLinkUri
                    }
                )
            ) { backStackEntry ->
                val workoutId = backStackEntry.arguments?.getString(Screen.WorkoutDetail.ARG) ?: ""
                WorkoutExecutionScreen(workoutId = workoutId, navController = navController)
            }
        }
    }
}
