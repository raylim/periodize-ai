package com.periodizeai.app.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.periodizeai.app.repositories.WorkoutSessionData
import com.periodizeai.app.repositories.WorkoutSessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HistoryViewModel(
    private val sessionRepository: WorkoutSessionRepository,
) : ViewModel() {

    data class UiState(
        val sessions: List<WorkoutSessionData> = emptyList(),
        val filterExercise: String? = null,
        val isLoading: Boolean = false,
    ) {
        val filteredSessions: List<WorkoutSessionData>
            get() {
                val filterEx = filterExercise
                if (filterEx.isNullOrBlank()) return sessions
                return sessions.filter { session ->
                    session.completedSets.any { it.exercise?.name == filterEx }
                }
            }

        val groupedByWeek: List<Pair<Long, List<WorkoutSessionData>>>
            get() {
                val grouped = filteredSessions.groupBy { weekStart(it.date) }
                return grouped.entries
                    .map { Pair(it.key, it.value.sortedByDescending { s -> s.date }) }
                    .sortedByDescending { it.first }
            }

        val uniqueExerciseNames: List<String>
            get() {
                val names = sessions
                    .flatMap { s -> s.completedSets.mapNotNull { it.exercise?.name } }
                    .toSet()
                return names.sorted()
            }

        private fun weekStart(epochMs: Long): Long {
            val dayMs = 86_400_000L
            val days = epochMs / dayMs
            val dayOfWeek = ((days + 3) % 7).toInt()
            return (days - dayOfWeek) * dayMs
        }
    }

    private val _uiState = MutableStateFlow(UiState(isLoading = true))
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val sessions = sessionRepository.getAll()
                .filter { it.isCompleted }
                .sortedByDescending { it.date }
                .take(500)
            _uiState.value = _uiState.value.copy(sessions = sessions, isLoading = false)
        }
    }

    fun setFilterExercise(name: String?) {
        _uiState.value = _uiState.value.copy(filterExercise = name)
    }
}
