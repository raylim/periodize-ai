package com.periodizeai.app.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.periodizeai.app.models.*
import com.periodizeai.app.repositories.*
import com.periodizeai.app.services.*
import com.periodizeai.app.utils.nowEpochMs
import com.periodizeai.app.utils.roundedToNearest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class OnboardingViewModel(
    private val profileRepo: UserProfileRepository,
    private val exerciseRepo: ExerciseRepository,
    private val planRepo: TrainingPlanRepository,
    private val sessionRepo: WorkoutSessionRepository,
) : ViewModel() {

    enum class OnboardingStep {
        WELCOME,
        CSV_IMPORT,
        TRAINING_DAYS,
        GOAL,
        BODY_WEIGHT,
        ABOUT_YOU,
        STRENGTH_LEVEL,
        STICKING_POINTS,
        GENERATING,
        COMPLETE,
    }

    data class UiState(
        val currentStep: OnboardingStep = OnboardingStep.WELCOME,
        val trainingDays: Int = 3,
        val selectedGoal: TrainingGoal = TrainingGoal.POWERLIFTING,
        val selectedUnit: WeightUnit = WeightUnit.LB,
        val bodyWeight: Double = 175.0,
        val isImporting: Boolean = false,
        val importResult: ImportResult? = null,
        val importError: String? = null,
        val isGeneratingPlan: Boolean = false,
        val hasMeetDate: Boolean = false,
        val meetDateMs: Long? = null,
        val selectedStrengthLevel: StrengthLevel = StrengthLevel.INTERMEDIATE,
        val suggestedStrengthLevel: StrengthLevel? = null,
        val selectedSex: UserSex = UserSex.MALE,
        val dateOfBirthMs: Long? = null,
        val userHeight: Double = 69.0,
        val trainingAgeYears: Int = 3,
        val deadliftStance: DeadliftStance = DeadliftStance.CONVENTIONAL,
        val squatStickingPoint: StickingPoint? = null,
        val benchStickingPoint: StickingPoint? = null,
        val deadliftStickingPoint: StickingPoint? = null,
        val planWeeksDescription: String = "",
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    // ── Mutators ──────────────────────────────────────────────────────────

    fun setTrainingDays(days: Int) = _uiState.update { it.copy(trainingDays = days, planWeeksDescription = computePlanWeeks(it.copy(trainingDays = days))) }
    fun setGoal(goal: TrainingGoal) = _uiState.update { it.copy(selectedGoal = goal) }
    fun setUnit(unit: WeightUnit) = _uiState.update { it.copy(selectedUnit = unit) }
    fun setBodyWeight(bw: Double) = _uiState.update { it.copy(bodyWeight = bw) }
    fun setHasMeetDate(has: Boolean) = _uiState.update { it.copy(hasMeetDate = has, planWeeksDescription = computePlanWeeks(it.copy(hasMeetDate = has))) }
    fun setMeetDateMs(ms: Long?) = _uiState.update { it.copy(meetDateMs = ms) }
    fun setStrengthLevel(level: StrengthLevel) = _uiState.update { it.copy(selectedStrengthLevel = level) }
    fun setSex(sex: UserSex) = _uiState.update { it.copy(selectedSex = sex) }
    fun setDateOfBirthMs(ms: Long?) = _uiState.update { it.copy(dateOfBirthMs = ms) }
    fun setUserHeight(h: Double) = _uiState.update { it.copy(userHeight = h) }
    fun setTrainingAgeYears(years: Int) = _uiState.update { it.copy(trainingAgeYears = years) }
    fun setDeadliftStance(stance: DeadliftStance) = _uiState.update { it.copy(deadliftStance = stance) }
    fun setSquatStickingPoint(sp: StickingPoint?) = _uiState.update { it.copy(squatStickingPoint = sp) }
    fun setBenchStickingPoint(sp: StickingPoint?) = _uiState.update { it.copy(benchStickingPoint = sp) }
    fun setDeadliftStickingPoint(sp: StickingPoint?) = _uiState.update { it.copy(deadliftStickingPoint = sp) }

    // ── Navigation ────────────────────────────────────────────────────────

    fun advance() {
        val next = OnboardingStep.entries.getOrNull(_uiState.value.currentStep.ordinal + 1) ?: return
        _uiState.update { it.copy(currentStep = next) }
    }

    // ── CSV Import ────────────────────────────────────────────────────────

    fun importCsv(content: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true, importError = null) }

            val catalogExercises = ExerciseCatalogService.getCatalog()
            val (result, sessions) = CsvImportService.importCsv(content, catalogExercises)

            // Persist catalog exercises so e1RM updates have a target
            for (ex in catalogExercises) {
                exerciseRepo.save(ex)
            }

            // Save imported sessions and their sets
            for (session in sessions) {
                sessionRepo.saveSession(session)
                for (set in session.completedSets) {
                    sessionRepo.saveSet(set)
                }
            }

            // Update e1RMs on exercises based on best estimates from imported sets
            val setsByExercise = sessions.flatMap { it.completedSets }.groupBy { it.exerciseId }
            for ((exerciseId, sets) in setsByExercise) {
                if (exerciseId == null) continue
                val bestE1rm = OneRepMaxCalculator.bestEstimate(sets) ?: continue
                val workingMax = (bestE1rm * 0.9).roundedToNearest(5.0)
                exerciseRepo.updateE1RM(exerciseId, bestE1rm, workingMax)
            }

            val importError = if (result.errors.size > result.setsImported) {
                "Many import errors occurred. Check your CSV format."
            } else null

            val updatedState = _uiState.value.copy(
                isImporting = false,
                importResult = result,
                importError = importError,
            )
            _uiState.value = updatedState

            computeStrengthLevelSuggestion()
        }
    }

    /** Ensures catalog exercises are persisted without requiring a CSV. */
    fun skipCsvImport() {
        viewModelScope.launch {
            val catalogExercises = ExerciseCatalogService.getCatalog()
            for (ex in catalogExercises) {
                exerciseRepo.save(ex)
            }
        }
    }

    // ── Strength Level Suggestion ─────────────────────────────────────────

    fun computeStrengthLevelSuggestion() {
        viewModelScope.launch {
            val exercises = exerciseRepo.getAll()
            val state = _uiState.value
            val bwInLb = if (state.selectedUnit == WeightUnit.KG) state.bodyWeight * 2.20462 else state.bodyWeight

            fun e1rmFor(keyword: String): Double? =
                exercises.firstOrNull { it.name.lowercase().contains(keyword) }?.estimatedOneRepMax

            val squatE1rm = e1rmFor("squat")
            val benchE1rm = e1rmFor("bench")
            val deadliftE1rm = e1rmFor("deadlift")

            if (squatE1rm == null && benchE1rm == null && deadliftE1rm == null) return@launch

            val suggested = StrengthLevel.compute(
                squatE1RM = squatE1rm,
                benchE1RM = benchE1rm,
                deadliftE1RM = deadliftE1rm,
                bodyWeight = bwInLb,
            )
            _uiState.update { it.copy(suggestedStrengthLevel = suggested, selectedStrengthLevel = suggested) }
        }
    }

    // ── Plan Generation ───────────────────────────────────────────────────

    fun generatePlanAndFinish() {
        viewModelScope.launch {
            _uiState.update { it.copy(isGeneratingPlan = true) }

            val state = _uiState.value
            val profileId = randomId()
            val profile = buildProfileData(profileId, state)

            profileRepo.save(profile)

            val exercises = exerciseRepo.getAll()

            // Ensure working max is set for exercises with an e1RM
            for (ex in exercises) {
                val e1rm = ex.estimatedOneRepMax ?: continue
                if (ex.workingMax == null) {
                    exerciseRepo.updateE1RM(ex.id, e1rm, (e1rm * 0.9).roundedToNearest(5.0))
                }
            }

            val refreshedExercises = exerciseRepo.getAll()
            val generated = PeriodizationEngine.shared.generatePlan(
                trainingDays = state.trainingDays,
                exercises = refreshedExercises,
                profile = profile,
            )

            // Inline save of GeneratedPlan
            val planId = randomId()
            planRepo.savePlan(
                TrainingPlanData(
                    id = planId,
                    name = generated.name,
                    isActive = true,
                    trainingDaysPerWeek = generated.trainingDaysPerWeek,
                    createdAt = nowEpochMs(),
                )
            )
            planRepo.activatePlan(planId)

            for ((blockIdx, genBlock) in generated.blocks.withIndex()) {
                val blockId = randomId()
                planRepo.saveBlock(
                    TrainingBlockData(
                        id = blockId,
                        planId = planId,
                        phase = genBlock.phase,
                        blockOrder = blockIdx,
                        isCompleted = false,
                        mesocycleLength = genBlock.mesocycleLength,
                        mevSetsJson = mapToJson(genBlock.mevByLift),
                        mrvSetsJson = mapToJson(genBlock.mrvByLift),
                    )
                )

                for (genWeek in genBlock.weeks) {
                    val weekId = randomId()
                    planRepo.saveWeek(
                        TrainingWeekData(
                            id = weekId,
                            blockId = blockId,
                            weekNumber = genWeek.weekNumber,
                            subPhase = genWeek.subPhase,
                            isCompleted = false,
                        )
                    )

                    for (genWorkout in genWeek.workouts) {
                        val workoutId = randomId()
                        planRepo.saveWorkout(
                            PlannedWorkoutData(
                                id = workoutId,
                                weekId = weekId,
                                dayNumber = genWorkout.dayNumber,
                                dayLabel = genWorkout.dayLabel,
                                focus = genWorkout.focus,
                                isCompleted = false,
                                isSkipped = false,
                                intensityTier = genWorkout.intensityTierRaw?.let { IntensityTier.from(it) },
                            )
                        )

                        for (genEx in genWorkout.exercises) {
                            planRepo.savePlannedExercise(
                                PlannedExerciseData(
                                    id = randomId(),
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
                                    prescribedWarmupSetCount = genEx.prescribedWarmupSetCount,
                                    perSetTargetReps = genEx.perSetReps,
                                    dropPercentage = genEx.dropPercentage,
                                    intensityTier = genEx.intensityTierRaw?.let { IntensityTier.from(it) },
                                )
                            )
                        }
                    }
                }
            }

            _uiState.update { it.copy(isGeneratingPlan = false, currentStep = OnboardingStep.COMPLETE) }
        }
    }

    // ── Complete Onboarding ───────────────────────────────────────────────

    fun completeOnboarding() {
        viewModelScope.launch {
            val profile = profileRepo.getProfile() ?: return@launch
            profileRepo.markOnboardingComplete(profile.id)
        }
    }

    // ── Plan weeks description ────────────────────────────────────────────

    fun refreshPlanWeeksDescription() {
        _uiState.update { it.copy(planWeeksDescription = computePlanWeeks(it)) }
    }

    // ── Private helpers ───────────────────────────────────────────────────

    private fun computePlanWeeks(state: UiState): String {
        val profileForCalc = buildProfileData(randomId(), state)
        val phases = PeriodizationEngine.shared.determinePhaseLengths(profileForCalc)
        val transitional = VolumeCalculator.transitionalWeekCount(profileForCalc)
        val bridge = if (state.hasMeetDate) VolumeCalculator.bridgePhaseLength() else 0
        val totalWeeks = phases.sumOf { it.second } + transitional + (if (state.hasMeetDate) 2 else 0) + bridge
        return "$totalWeeks-week"
    }

    private fun buildProfileData(id: String, state: UiState): UserProfileData {
        val age = computeAge(state.dateOfBirthMs)
        return UserProfileData(
            id = id,
            name = "",
            weightUnit = state.selectedUnit,
            goal = state.selectedGoal,
            hasCompletedOnboarding = false,
            hasImportedCSV = state.importResult != null,
            userBodyWeight = state.bodyWeight,
            barbellWeight = if (state.selectedUnit == WeightUnit.KG) 20.0 else 45.0,
            availablePlatesLb = listOf(45.0, 35.0, 25.0, 10.0, 5.0, 2.5),
            availablePlatesKg = listOf(20.0, 15.0, 10.0, 5.0, 2.5, 1.25),
            restTimerMainLift = 300L,
            restTimerCompound = 180L,
            restTimerIsolation = 90L,
            healthKitEnabled = false,
            syncBodyWeightFromHealth = false,
            deadliftStance = state.deadliftStance,
            meetDateMs = if (state.hasMeetDate) state.meetDateMs else null,
            sex = state.selectedSex,
            userHeight = state.userHeight,
            userAge = age,
            dateOfBirthMs = state.dateOfBirthMs,
            trainingAge = state.trainingAgeYears.toLong(),
            strengthLevel = state.selectedStrengthLevel,
            dietStatus = DietStatus.MAINTENANCE,
            sleepQuality = RecoveryRating.AVERAGE,
            stressLevel = RecoveryRating.AVERAGE,
            trainingDaysPerWeek = state.trainingDays.toLong(),
            createdAt = nowEpochMs(),
        )
    }

    private fun computeAge(dateOfBirthMs: Long?): Long {
        if (dateOfBirthMs == null) return 28L
        val ageMs = nowEpochMs() - dateOfBirthMs
        return (ageMs / (365.25 * 24 * 3600 * 1000)).toLong().coerceAtLeast(1L)
    }

    private fun mapToJson(map: Map<String, Double>): String =
        "{" + map.entries.joinToString(",") { (k, v) -> "\"$k\":$v" } + "}"

    private fun randomId(): String = buildString {
        val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
        repeat(16) { append(chars[kotlin.random.Random.nextInt(chars.length)]) }
    }
}
