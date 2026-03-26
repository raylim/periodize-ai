package com.periodizeai.app.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.periodizeai.app.repositories.*
import com.periodizeai.app.services.AutoRegulationService
import com.periodizeai.app.services.ReadinessInput
import com.periodizeai.app.services.ReadinessService
import com.periodizeai.app.utils.nowEpochMs
import com.periodizeai.app.widget.WidgetRefreshService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class WorkoutExecutionViewModel(
    private val workoutId: String,
    private val planRepo: TrainingPlanRepository,
    private val sessionRepo: WorkoutSessionRepository,
    private val exerciseRepo: ExerciseRepository,
    private val profileRepo: UserProfileRepository,
) : ViewModel() {

    data class SetEntry(
        val id: String = Uuid.random().toString(),
        val targetReps: Int,
        val targetWeight: Double,
        val targetRpe: Double?,
        val completedReps: Int? = null,
        val completedWeight: Double? = null,
        val completedRpe: Double? = null,
        val notes: String = "",
        val isCompleted: Boolean = false,
        val isWarmup: Boolean = false,
    )

    data class ExerciseEntry(
        val id: String,
        val exercise: ExerciseData,
        val plannedExercise: PlannedExerciseData,
        val sets: List<SetEntry>,
        val notes: String = "",
        val isExpanded: Boolean = true,
    )

    data class ReadinessState(
        val sleep: Int = 5,
        val nutrition: Int = 5,
        val stress: Int = 5,
        val energy: Int = 5,
        val sorenessPecs: Int = 1,
        val sorenessLats: Int = 1,
        val sorenessLowerBack: Int = 1,
        val sorenessGlutesHams: Int = 1,
        val sorenessQuads: Int = 1,
    )

    data class UiState(
        val plannedWorkout: PlannedWorkoutData? = null,
        val exerciseEntries: List<ExerciseEntry> = emptyList(),
        val readinessState: ReadinessState = ReadinessState(),
        val isLoading: Boolean = true,
        val isFinished: Boolean = false,
        val errorMessage: String? = null,
        // Rest timer
        val restTimerActive: Boolean = false,
        val restTimerRemainingMs: Long = 0L,
        val restTimerDurationMs: Long = 0L,
        // Workout tracking
        val startTimeMs: Long = nowEpochMs(),
        val notes: String = "",
    ) {
        val completedSetCount: Int get() = exerciseEntries.sumOf { e -> e.sets.count { it.isCompleted } }
        val totalSetCount: Int get() = exerciseEntries.sumOf { it.sets.size }
        val progressFraction: Double get() = if (totalSetCount > 0) completedSetCount.toDouble() / totalSetCount else 0.0
        val allSetsCompleted: Boolean get() = completedSetCount >= totalSetCount && totalSetCount > 0
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var restTimerJob: Job? = null

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val plan = planRepo.getActivePlan()
            val workout = plan?.blocks
                ?.flatMap { it.weeks }
                ?.flatMap { it.workouts }
                ?.firstOrNull { it.id == workoutId }

            if (workout == null) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Workout not found",
                )
                return@launch
            }

            val entries = workout.exercises
                .sortedBy { it.order }
                .mapNotNull { planned ->
                    val exercise = planned.exercise ?: return@mapNotNull null
                    val warmupCount = planned.prescribedWarmupSetCount
                    val workingCount = planned.workingSetCount
                    val allSets = buildList {
                        repeat(warmupCount) { i ->
                            add(SetEntry(
                                targetReps = planned.reps,
                                targetWeight = planned.suggestedWeight?.let { it * 0.6 } ?: 0.0,
                                targetRpe = null,
                                isWarmup = true,
                            ))
                        }
                        val perSetReps = planned.perSetTargetReps
                        repeat(workingCount) { i ->
                            val targetReps = perSetReps?.getOrNull(i) ?: planned.reps
                            add(SetEntry(
                                targetReps = targetReps,
                                targetWeight = planned.suggestedWeight ?: 0.0,
                                targetRpe = planned.targetRPE,
                            ))
                        }
                    }
                    ExerciseEntry(
                        id = planned.id,
                        exercise = exercise,
                        plannedExercise = planned,
                        sets = allSets,
                    )
                }

            _uiState.value = _uiState.value.copy(
                plannedWorkout = workout,
                exerciseEntries = entries,
                isLoading = false,
                startTimeMs = nowEpochMs(),
            )
        }
    }

    // ── Set completion ────────────────────────────────────────────────────

    fun completeSet(
        exerciseEntryId: String,
        setId: String,
        reps: Int,
        weight: Double,
        rpe: Double?,
        notes: String = "",
    ) {
        _uiState.value = _uiState.value.copy(
            exerciseEntries = _uiState.value.exerciseEntries.map { entry ->
                if (entry.id != exerciseEntryId) return@map entry
                entry.copy(sets = entry.sets.map { set ->
                    if (set.id != setId) return@map set
                    set.copy(
                        completedReps = reps,
                        completedWeight = weight,
                        completedRpe = rpe,
                        notes = notes,
                        isCompleted = true,
                    )
                })
            }
        )
    }

    fun uncompleteSet(exerciseEntryId: String, setId: String) {
        _uiState.value = _uiState.value.copy(
            exerciseEntries = _uiState.value.exerciseEntries.map { entry ->
                if (entry.id != exerciseEntryId) return@map entry
                entry.copy(sets = entry.sets.map { set ->
                    if (set.id != setId) return@map set
                    set.copy(isCompleted = false)
                })
            }
        )
    }

    fun updateSetWeight(exerciseEntryId: String, setId: String, weight: Double) {
        _uiState.value = _uiState.value.copy(
            exerciseEntries = _uiState.value.exerciseEntries.map { entry ->
                if (entry.id != exerciseEntryId) return@map entry
                entry.copy(sets = entry.sets.map { set ->
                    if (set.id != setId) return@map set
                    set.copy(completedWeight = weight)
                })
            }
        )
    }

    fun toggleExerciseExpanded(exerciseEntryId: String) {
        _uiState.value = _uiState.value.copy(
            exerciseEntries = _uiState.value.exerciseEntries.map { entry ->
                if (entry.id != exerciseEntryId) entry
                else entry.copy(isExpanded = !entry.isExpanded)
            }
        )
    }

    fun updateExerciseNotes(exerciseEntryId: String, notes: String) {
        _uiState.value = _uiState.value.copy(
            exerciseEntries = _uiState.value.exerciseEntries.map { entry ->
                if (entry.id != exerciseEntryId) entry else entry.copy(notes = notes)
            }
        )
    }

    fun updateWorkoutNotes(notes: String) {
        _uiState.value = _uiState.value.copy(notes = notes)
    }

    // ── Readiness ─────────────────────────────────────────────────────────

    fun updateReadiness(update: ReadinessState.() -> ReadinessState) {
        _uiState.value = _uiState.value.copy(readinessState = _uiState.value.readinessState.update())
    }

    // ── Rest timer ────────────────────────────────────────────────────────

    fun startRestTimer(durationMs: Long) {
        restTimerJob?.cancel()
        _uiState.value = _uiState.value.copy(
            restTimerActive = true,
            restTimerDurationMs = durationMs,
            restTimerRemainingMs = durationMs,
        )
        restTimerJob = viewModelScope.launch {
            while (_uiState.value.restTimerRemainingMs > 0) {
                delay(1000L)
                val remaining = _uiState.value.restTimerRemainingMs - 1000L
                _uiState.value = _uiState.value.copy(
                    restTimerRemainingMs = remaining.coerceAtLeast(0L),
                    restTimerActive = remaining > 0,
                )
            }
        }
    }

    fun cancelRestTimer() {
        restTimerJob?.cancel()
        restTimerJob = null
        _uiState.value = _uiState.value.copy(restTimerActive = false, restTimerRemainingMs = 0L)
    }

    // ── Finish workout ────────────────────────────────────────────────────

    @OptIn(ExperimentalUuidApi::class)
    fun finishWorkout() {
        viewModelScope.launch {
            val state = _uiState.value
            val workout = state.plannedWorkout ?: return@launch
            val profile = profileRepo.getProfile()

            val now = nowEpochMs()
            val durationMs = now - state.startTimeMs
            val sessionId = Uuid.random().toString()
            val readiness = state.readinessState

            // Compute readiness scores
            val readinessInput = ReadinessInput(
                sleep = readiness.sleep,
                nutrition = readiness.nutrition,
                stress = readiness.stress,
                energy = readiness.energy,
                sorenessPecs = readiness.sorenessPecs,
                sorenessLats = readiness.sorenessLats,
                sorenessLowerBack = readiness.sorenessLowerBack,
                sorenessGlutesHams = readiness.sorenessGlutesHams,
                sorenessQuads = readiness.sorenessQuads,
            )
            val liftReadiness = ReadinessService.calculateReadiness(readinessInput)

            // Build CompletedSetData list
            val completedSets = mutableListOf<CompletedSetData>()
            var globalOrder = 0
            for (entry in state.exerciseEntries) {
                for ((setIdx, set) in entry.sets.withIndex()) {
                    if (!set.isCompleted) continue
                    completedSets.add(CompletedSetData(
                        id = Uuid.random().toString(),
                        sessionId = sessionId,
                        exerciseId = entry.exercise.id,
                        exercise = entry.exercise,
                        setNumber = setIdx + 1,
                        order = globalOrder++,
                        weight = set.completedWeight ?: set.targetWeight,
                        reps = set.completedReps ?: set.targetReps,
                        rpe = set.completedRpe,
                        isWarmup = set.isWarmup,
                        completedAt = now,
                    ))
                }
            }

            val session = WorkoutSessionData(
                id = sessionId,
                date = state.startTimeMs,
                durationMs = durationMs,
                notes = state.notes.ifBlank { null },
                isCompleted = true,
                linkedPlannedWorkoutId = workoutId,
                completedSets = completedSets,
                readinessSquatScore = liftReadiness.squat,
                readinessBenchScore = liftReadiness.bench,
                readinessDeadliftScore = liftReadiness.deadlift,
                readinessSleep = readiness.sleep,
                readinessNutrition = readiness.nutrition,
                readinessStress = readiness.stress,
                readinessEnergy = readiness.energy,
                readinessSorenessPecs = readiness.sorenessPecs,
                readinessSorenessLats = readiness.sorenessLats,
                readinessSorenessLowerBack = readiness.sorenessLowerBack,
                readinessSorenessGlutesHams = readiness.sorenessGlutesHams,
                readinessSorenessQuads = readiness.sorenessQuads,
            )

            // Save session
            sessionRepo.saveSession(session)
            for (set in completedSets) sessionRepo.saveSet(set)
            sessionRepo.markCompleted(sessionId, durationMs)

            // Mark planned workout completed
            planRepo.markWorkoutCompleted(workoutId, sessionId)

            // Run auto-regulation
            val plannedExercises = workout.exercises
            val result = AutoRegulationService.processCompletedWorkout(
                session = session,
                plannedExercises = plannedExercises,
                unit = profile?.weightUnit ?: com.periodizeai.app.models.WeightUnit.LB,
                userBodyWeight = profile?.userBodyWeight ?: 0.0,
            )

            // Persist e1RM updates
            for ((name, _, newE1RM) in result.e1RMUpdates) {
                val exercise = exerciseRepo.getByName(name) ?: continue
                val newWorkingMax = newE1RM * 0.9
                exerciseRepo.updateE1RM(exercise.id, newE1RM, newWorkingMax)
            }

            _uiState.value = _uiState.value.copy(isFinished = true)
            WidgetRefreshService.refresh()
        }
    }

    override fun onCleared() {
        super.onCleared()
        restTimerJob?.cancel()
    }
}
