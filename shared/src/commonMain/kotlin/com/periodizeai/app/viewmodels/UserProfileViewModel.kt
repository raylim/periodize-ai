package com.periodizeai.app.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.periodizeai.app.repositories.UserProfileData
import com.periodizeai.app.repositories.UserProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class UserProfileViewModel(
    private val repository: UserProfileRepository,
) : ViewModel() {

    private val _profile = MutableStateFlow<UserProfileData?>(null)
    val profile: StateFlow<UserProfileData?> = _profile.asStateFlow()

    private val _hasCompletedOnboarding = MutableStateFlow(false)
    val hasCompletedOnboarding: StateFlow<Boolean> = _hasCompletedOnboarding.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        viewModelScope.launch {
            reload()
        }
    }

    suspend fun reload() {
        _isLoading.value = true
        val p = repository.getProfile()
        _profile.value = p
        _hasCompletedOnboarding.value = p?.hasCompletedOnboarding ?: false
        _isLoading.value = false
    }

    fun completeOnboarding() {
        viewModelScope.launch {
            val p = _profile.value ?: repository.getProfile() ?: return@launch
            repository.markOnboardingComplete(p.id)
            _hasCompletedOnboarding.value = true
            reload()
        }
    }

    fun updateProfile(updated: UserProfileData) {
        viewModelScope.launch {
            repository.save(updated)
            _profile.value = updated
        }
    }
}
