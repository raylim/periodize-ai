package com.periodizeai.app.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.periodizeai.app.repositories.CompletedSetData
import com.periodizeai.app.repositories.WorkoutSessionData
import com.periodizeai.app.repositories.WorkoutSessionRepository
import com.periodizeai.app.services.OneRepMaxCalculator
import com.periodizeai.app.utils.nowEpochMs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class AnalyticsViewModel(
    private val sessionRepository: WorkoutSessionRepository,
) : ViewModel() {

    enum class TimeRange(val label: String) {
        ONE_MONTH("1M"),
        THREE_MONTHS("3M"),
        SIX_MONTHS("6M"),
        ONE_YEAR("1Y"),
        ALL_TIME("All");

        fun startEpochMs(): Long? {
            val now = nowEpochMs()
            return when (this) {
                ONE_MONTH -> now - 30L * 86_400_000L
                THREE_MONTHS -> now - 90L * 86_400_000L
                SIX_MONTHS -> now - 180L * 86_400_000L
                ONE_YEAR -> now - 365L * 86_400_000L
                ALL_TIME -> null
            }
        }
    }

    data class E1RMDataPoint(val date: Long, val e1rm: Double, val exerciseName: String)
    data class VolumeDataPoint(val weekStart: Long, val muscleGroup: String, val sets: Int)
    data class FrequencyDataPoint(val date: Long, val sessionCount: Int)
    data class DurationDataPoint(val date: Long, val durationMs: Long)
    data class RPEDataPoint(val rpe: Double, val count: Int)
    data class CalendarDataPoint(val date: Long, val setCount: Int)

    data class UiState(
        val e1RMData: List<E1RMDataPoint> = emptyList(),
        val volumeData: List<VolumeDataPoint> = emptyList(),
        val frequencyData: List<FrequencyDataPoint> = emptyList(),
        val durationData: List<DurationDataPoint> = emptyList(),
        val rpeData: List<RPEDataPoint> = emptyList(),
        val calendarData: List<CalendarDataPoint> = emptyList(),
        val selectedExercise: String = "Comp Squat",
        val availableExercises: List<String> = emptyList(),
        val selectedTimeRange: TimeRange = TimeRange.THREE_MONTHS,
        val isLoading: Boolean = false,
    )

    private val _uiState = MutableStateFlow(UiState(isLoading = true))
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val allSessions = sessionRepository.getAll().filter { it.isCompleted }
            val exercises = allSessions
                .flatMap { it.completedSets.mapNotNull { s -> s.exercise?.name } }
                .distinct()
                .sorted()
            val selectedEx = if (exercises.contains(_uiState.value.selectedExercise))
                _uiState.value.selectedExercise
            else exercises.firstOrNull() ?: "Comp Squat"

            _uiState.value = _uiState.value.copy(
                availableExercises = exercises,
                selectedExercise = selectedEx,
            )
            reloadAll(allSessions)
        }
    }

    fun selectExercise(name: String) {
        _uiState.value = _uiState.value.copy(selectedExercise = name)
        viewModelScope.launch {
            val allSessions = sessionRepository.getAll().filter { it.isCompleted }
            reloadAll(allSessions)
        }
    }

    fun selectTimeRange(range: TimeRange) {
        _uiState.value = _uiState.value.copy(selectedTimeRange = range)
        viewModelScope.launch {
            val allSessions = sessionRepository.getAll().filter { it.isCompleted }
            reloadAll(allSessions)
        }
    }

    private fun reloadAll(allSessions: List<WorkoutSessionData>) {
        val rangeStart = _uiState.value.selectedTimeRange.startEpochMs()
        val filtered = if (rangeStart != null) allSessions.filter { it.date >= rangeStart } else allSessions
        val allSets = filtered.flatMap { it.completedSets }

        _uiState.value = _uiState.value.copy(
            e1RMData = computeE1RM(allSets),
            volumeData = computeVolume(filtered),
            frequencyData = computeFrequency(filtered),
            durationData = computeDuration(filtered),
            rpeData = computeRPE(allSets),
            calendarData = computeCalendar(filtered),
            isLoading = false,
        )
    }

    private fun computeE1RM(sets: List<CompletedSetData>): List<E1RMDataPoint> {
        val exerciseName = _uiState.value.selectedExercise
        val relevant = sets
            .filter { !it.isWarmup && it.exercise?.name == exerciseName }
            .sortedBy { it.completedAt ?: 0L }
        var running = 0.0
        val points = mutableListOf<E1RMDataPoint>()
        for (set in relevant) {
            val date = set.completedAt ?: continue
            val e1rm = OneRepMaxCalculator.calculate(set.weight, set.reps, set.rpe, set.rir) ?: continue
            running = OneRepMaxCalculator.updateEstimate(if (running > 0) running else null, e1rm)
            points.add(E1RMDataPoint(date, running, exerciseName))
        }
        return if (points.size > 50) {
            val step = points.size / 50
            (0 until points.size step step).map { points[it] }
        } else points
    }

    private fun computeVolume(sessions: List<WorkoutSessionData>): List<VolumeDataPoint> {
        val weeklyVolume = mutableMapOf<Long, MutableMap<String, Int>>()
        for (session in sessions) {
            val wk = weekStart(session.date)
            for (set in session.completedSets.filter { !it.isWarmup }) {
                for (muscle in (set.exercise?.primaryMuscles ?: emptyList())) {
                    val muscleMap = weeklyVolume.getOrPut(wk) { mutableMapOf() }
                    muscleMap[muscle.raw] = (muscleMap[muscle.raw] ?: 0) + 1
                }
            }
        }
        return weeklyVolume.flatMap { (wk, muscles) ->
            muscles.map { (m, s) -> VolumeDataPoint(wk, m, s) }
        }.sortedBy { it.weekStart }
    }

    private fun computeFrequency(sessions: List<WorkoutSessionData>): List<FrequencyDataPoint> {
        val grouped = sessions.groupBy { weekStart(it.date) }
        return grouped.map { (wk, ss) -> FrequencyDataPoint(wk, ss.size) }.sortedBy { it.date }
    }

    private fun computeDuration(sessions: List<WorkoutSessionData>): List<DurationDataPoint> {
        return sessions.mapNotNull { s ->
            val d = s.durationMs ?: return@mapNotNull null
            DurationDataPoint(s.date, d)
        }.sortedBy { it.date }
    }

    private fun computeRPE(sets: List<CompletedSetData>): List<RPEDataPoint> {
        val counts = mutableMapOf<Double, Int>()
        for (set in sets.filter { !it.isWarmup }) {
            val rpe = set.rpe ?: continue
            val bucket = ((rpe * 2).roundToInt() / 2.0)
            counts[bucket] = (counts[bucket] ?: 0) + 1
        }
        return counts.map { RPEDataPoint(it.key, it.value) }.sortedBy { it.rpe }
    }

    private fun computeCalendar(sessions: List<WorkoutSessionData>): List<CalendarDataPoint> {
        val grouped = sessions.groupBy { dayStart(it.date) }
        return grouped.map { (day, ss) ->
            val totalSets = ss.sumOf { it.completedSets.count { s -> !s.isWarmup } }
            CalendarDataPoint(day, totalSets)
        }.sortedBy { it.date }
    }

    private fun weekStart(epochMs: Long): Long {
        val dayMs = 86_400_000L
        val days = epochMs / dayMs
        val dow = ((days + 3) % 7).toInt()
        return (days - dow) * dayMs
    }

    private fun dayStart(epochMs: Long): Long = (epochMs / 86_400_000L) * 86_400_000L
}
