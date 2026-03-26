package com.periodizeai.app.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.periodizeai.app.database.PeriodizeAIDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Minimal ViewModel to drive the onboarding gate in App.kt.
 * Expanded with full profile CRUD in Phase 4.
 */
class UserProfileViewModel(
    private val database: PeriodizeAIDatabase,
) : ViewModel() {

    private val _hasCompletedOnboarding = MutableStateFlow(false)
    val hasCompletedOnboarding: StateFlow<Boolean> = _hasCompletedOnboarding.asStateFlow()

    init {
        viewModelScope.launch {
            val profile = database.userProfileQueries.selectFirst().executeAsOneOrNull()
            _hasCompletedOnboarding.value = profile?.hasCompletedOnboarding == 1L
        }
    }
}
