package com.periodizeai.app.di

import com.periodizeai.app.repositories.ExerciseRepository
import com.periodizeai.app.repositories.StickingPointRepository
import com.periodizeai.app.repositories.TrainingPlanRepository
import com.periodizeai.app.repositories.UserProfileRepository
import com.periodizeai.app.repositories.WorkoutSessionRepository
import com.periodizeai.app.viewmodels.AnalyticsViewModel
import com.periodizeai.app.viewmodels.DashboardViewModel
import com.periodizeai.app.viewmodels.HistoryViewModel
import com.periodizeai.app.viewmodels.OnboardingViewModel
import com.periodizeai.app.viewmodels.SettingsViewModel
import com.periodizeai.app.viewmodels.UserProfileViewModel
import com.periodizeai.app.viewmodels.WorkoutExecutionViewModel
import org.koin.compose.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.dsl.module

val repositoryModule = module {
    single { UserProfileRepository(get()) }
    single { ExerciseRepository(get()) }
    single { TrainingPlanRepository(db = get(), exerciseRepo = get()) }
    single { WorkoutSessionRepository(db = get(), exerciseRepo = get()) }
    single { StickingPointRepository(get()) }
}

val viewModelModule = module {
    viewModel { UserProfileViewModel(get()) }
    viewModel { HistoryViewModel(get()) }
    viewModel { AnalyticsViewModel(get()) }
    viewModel { DashboardViewModel(get(), get(), get(), get()) }
    viewModel { OnboardingViewModel(get(), get(), get(), get()) }
    viewModel { SettingsViewModel(get(), get(), get(), get()) }
    viewModel { (workoutId: String) ->
        WorkoutExecutionViewModel(workoutId, get(), get(), get(), get())
    }
}

/** Platform-specific modules are provided via expect/actual */
expect val platformModule: Module
