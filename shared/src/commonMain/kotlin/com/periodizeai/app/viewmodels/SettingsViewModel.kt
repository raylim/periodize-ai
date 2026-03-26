package com.periodizeai.app.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.periodizeai.app.repositories.*
import com.periodizeai.app.services.PeriodizationEngine
import com.periodizeai.app.utils.nowEpochMs
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class SettingsViewModel(
    private val planRepo: TrainingPlanRepository,
    private val sessionRepo: WorkoutSessionRepository,
    private val exerciseRepo: ExerciseRepository,
    private val profileRepo: UserProfileRepository,
) : ViewModel() {

    data class UiState(
        val profile: UserProfileData? = null,
        val activePlan: TrainingPlanData? = null,
        val allPlans: List<TrainingPlanData> = emptyList(),
        val exercises: List<ExerciseData> = emptyList(),
        val selectedTrainingDaysPerWeek: Int = 4,
        val isLoading: Boolean = true,
        val errorMessage: String? = null,
    ) {
        val weekEntries: List<Pair<TrainingBlockData, TrainingWeekData>> get() =
            activePlan?.blocks?.sortedBy { it.blockOrder }?.flatMap { block ->
                block.weeks.sortedBy { it.weekNumber }.map { Pair(block, it) }
            } ?: emptyList()

        val currentWeekIndex: Int get() {
            val currentWeek = activePlan?.currentBlock?.currentWeek ?: return 0
            return weekEntries.indexOfFirst { it.second.id == currentWeek.id }.coerceAtLeast(0)
        }

        val hasPendingDaysChange: Boolean get() =
            profile != null && selectedTrainingDaysPerWeek != profile.trainingDaysPerWeek.toInt()
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val profile = profileRepo.getProfile()
            val activePlan = planRepo.getActivePlan()
            val allPlans = planRepo.getAllPlans()
            val exercises = exerciseRepo.getAll()

            _uiState.value = UiState(
                profile = profile,
                activePlan = activePlan,
                allPlans = allPlans,
                exercises = exercises,
                selectedTrainingDaysPerWeek = profile?.trainingDaysPerWeek?.toInt() ?: 4,
                isLoading = false,
            )
        }
    }

    /** Advance the "current week" pointer by marking all weeks before [weekId] as completed
     *  and marking [weekId] and all subsequent weeks as not completed. */
    fun setCurrentWeek(weekId: String) {
        viewModelScope.launch {
            val plan = _uiState.value.activePlan ?: return@launch
            val allWeeks = _uiState.value.weekEntries
            val targetIdx = allWeeks.indexOfFirst { it.second.id == weekId }
            if (targetIdx < 0) return@launch

            for ((idx, entry) in allWeeks.withIndex()) {
                val (_, week) = entry
                val shouldBeCompleted = idx < targetIdx
                val updated = week.copy(isCompleted = shouldBeCompleted)
                planRepo.saveWeek(updated)
            }
            val refreshed = planRepo.getActivePlan()
            _uiState.value = _uiState.value.copy(activePlan = refreshed)
        }
    }

    fun setSelectedTrainingDays(daysPerWeek: Int) {
        _uiState.value = _uiState.value.copy(selectedTrainingDaysPerWeek = daysPerWeek)
    }

    fun applyTrainingDaysChange() {
        val state = _uiState.value
        val profile = state.profile ?: return
        if (!state.hasPendingDaysChange) return

        viewModelScope.launch {
            val updated = profile.copy(trainingDaysPerWeek = state.selectedTrainingDaysPerWeek.toLong())
            profileRepo.save(updated)
            _uiState.value = _uiState.value.copy(profile = updated)
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    fun regenerateEntirePlan() {
        viewModelScope.launch {
            val state = _uiState.value
            val profile = state.profile ?: return@launch
            val exercises = state.exercises.ifEmpty { exerciseRepo.getAll() }

            _uiState.value = state.copy(isLoading = true)

            try {
                val generated = PeriodizationEngine.shared.generatePlan(
                    trainingDays = profile.trainingDaysPerWeek.toInt(),
                    exercises = exercises,
                    profile = profile,
                )

                // Delete old plans
                planRepo.deleteAllPlans()

                // Persist the new plan
                val planId = Uuid.random().toString()
                val plan = TrainingPlanData(
                    id = planId,
                    name = generated.name,
                    isActive = true,
                    trainingDaysPerWeek = generated.trainingDaysPerWeek,
                    createdAt = nowEpochMs(),
                )
                planRepo.savePlan(plan)

                for (genBlock in generated.blocks) {
                    val blockId = Uuid.random().toString()
                    val block = TrainingBlockData(
                        id = blockId,
                        planId = planId,
                        phase = genBlock.phase,
                        blockOrder = genBlock.order,
                        mesocycleLength = genBlock.mesocycleLength,
                    )
                    planRepo.saveBlock(block)

                    for (genWeek in genBlock.weeks) {
                        val weekId = Uuid.random().toString()
                        val week = TrainingWeekData(
                            id = weekId,
                            blockId = blockId,
                            weekNumber = genWeek.weekNumber,
                            subPhase = genWeek.subPhase,
                        )
                        planRepo.saveWeek(week)

                        for (genWorkout in genWeek.workouts) {
                            val workoutId = Uuid.random().toString()
                            val workout = PlannedWorkoutData(
                                id = workoutId,
                                weekId = weekId,
                                dayNumber = genWorkout.dayNumber,
                                dayLabel = genWorkout.dayLabel,
                                focus = genWorkout.focus,
                                intensityTier = genWorkout.intensityTierRaw?.let {
                                    com.periodizeai.app.models.IntensityTier.from(it)
                                },
                            )
                            planRepo.saveWorkout(workout)

                            for (genEx in genWorkout.exercises) {
                                val peId = Uuid.random().toString()
                                val pe = PlannedExerciseData(
                                    id = peId,
                                    workoutId = workoutId,
                                    exerciseId = genEx.exerciseId,
                                    order = genEx.order,
                                    sets = genEx.sets,
                                    reps = genEx.reps,
                                    isAMRAP = genEx.isAMRAP,
                                    targetRPE = genEx.targetRPE,
                                    targetRIR = genEx.targetRIR,
                                    suggestedWeight = genEx.suggestedWeight,
                                    role = genEx.role,
                                    deloadSetWeights = genEx.perSetWeights,
                                    prescribedWarmupSetCount = genEx.prescribedWarmupSetCount,
                                    perSetTargetReps = genEx.perSetReps,
                                    amrapSetIndex = if (genEx.isAMRAP) genEx.sets - 1 else -1,
                                    dropPercentage = genEx.dropPercentage,
                                    intensityTier = genEx.intensityTierRaw?.let {
                                        com.periodizeai.app.models.IntensityTier.from(it)
                                    },
                                )
                                planRepo.savePlannedExercise(pe)
                            }
                        }
                    }
                }

                planRepo.activatePlan(planId)
                val freshPlan = planRepo.getActivePlan()
                _uiState.value = _uiState.value.copy(activePlan = freshPlan, isLoading = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to regenerate plan: ${e.message}",
                )
            }
        }
    }

    fun resetAllData() {
        viewModelScope.launch {
            sessionRepo.deleteAll()
            planRepo.deleteAllPlans()
            _uiState.value = UiState(
                profile = _uiState.value.profile,
                isLoading = false,
            )
        }
    }

    fun exportCsv(): String {
        val sessions = _uiState.value.let { state ->
            // We expose a snapshot; actual export should be done after load()
            emptyList<WorkoutSessionData>()
        }
        return buildString {
            appendLine("session_id,date_ms,duration_ms,exercise,set_number,weight,reps,rpe,is_warmup,notes")
        }
    }

    /** Suspending variant for export — call from a coroutine in the UI layer. */
    suspend fun exportCsvAsync(): String {
        val sessions = sessionRepo.getAll()
        return buildString {
            appendLine("session_id,date_ms,duration_ms,exercise,set_number,weight,reps,rpe,is_warmup,notes")
            for (session in sessions) {
                for (set in session.completedSets) {
                    val exerciseName = set.exercise?.name ?: set.exerciseId ?: ""
                    val escapedName = "\"${exerciseName.replace("\"", "\"\"")}\""
                    val notes = "\"${(session.notes ?: "").replace("\"", "\"\"")}\""
                    appendLine(
                        "${session.id},${session.date},${session.durationMs ?: ""},$escapedName,${set.setNumber},${set.weight},${set.reps},${set.rpe ?: ""},${set.isWarmup},$notes"
                    )
                }
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}
