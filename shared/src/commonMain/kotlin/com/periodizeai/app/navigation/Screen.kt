package com.periodizeai.app.navigation

import androidx.navigation.NavType
import androidx.navigation.navArgument

sealed class Screen(val route: String) {
    // Root gates
    data object Onboarding : Screen("onboarding")

    // Main tabs (shell)
    data object Dashboard : Screen("dashboard")
    data object Workout : Screen("workout")
    data object History : Screen("history")
    data object Analytics : Screen("analytics")
    data object Settings : Screen("settings")

    // Nested / detail
    data object WorkoutExecution : Screen("workout_execution")
    data object WorkoutSummary : Screen("workout_summary")
    data object ExerciseLibrary : Screen("exercise_library")
    data object CreateExercise : Screen("create_exercise")
    data object PlanEditor : Screen("plan_editor")
    data object PlatesEditor : Screen("plates_editor")
    data object StickingPointSelection : Screen("sticking_point_selection")

    // Deep link target — periodizeai://workout/{uuid}
    data object WorkoutDetail : Screen("workout_detail/{workoutId}") {
        const val ARG = "workoutId"
        fun createRoute(workoutId: String) = "workout_detail/$workoutId"
        val arguments = listOf(navArgument(ARG) { type = NavType.StringType })
        const val deepLinkUri = "periodizeai://workout/{workoutId}"
    }
}

/** Bottom nav tabs shown in MainScaffold */
val bottomNavTabs = listOf(
    Screen.Dashboard,
    Screen.Workout,
    Screen.History,
    Screen.Analytics,
    Screen.Settings,
)
