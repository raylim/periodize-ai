package com.periodizeai.app.di

import com.periodizeai.app.viewmodels.UserProfileViewModel
import org.koin.compose.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.dsl.module

/** ViewModels registered here — shared across platforms */
val viewModelModule = module {
    viewModel { UserProfileViewModel(get()) }
}

/** Platform-specific modules are provided via expect/actual */
expect val platformModule: Module
