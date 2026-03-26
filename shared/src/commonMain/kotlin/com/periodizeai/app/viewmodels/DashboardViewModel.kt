package com.periodizeai.app.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.periodizeai.app.models.*
import com.periodizeai.app.repositories.*
import com.periodizeai.app.services.VolumeCalculator
import com.periodizeai.app.utils.nowEpochMs
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class DashboardViewModel(
    private val planRepo: TrainingPlanRepository,
    private val sessionRepo: WorkoutSessionRepository,
    private val exerciseRepo: ExerciseRepository,
    private val profileRepo: UserProfileRepository,
) : ViewModel() {

    data class MevMrvLiftData(
        val liftName: String,
        val lift: LiftCategory,
        val mev: Int,
        val mrv: Int,
        val currentSets: Int,
    ) {
        val status: String get() = when {
            currentSets < mev -> "under"
            currentSets > mrv -> "over"
            else -> "optimal"
        }
    }

    data class WeekBarData(
        val id: String,
        val globalIndex: Int,
        val phase: BlockPhase,
        val subPhase: SubPhase,
        val blockId: String,
        val isCompleted: Boolean,
        val isCurrent: Boolean,
        val heightFraction: Double,
        val isNewBlock: Boolean,
    )

    data class UiState(
        val activePlan: TrainingPlanData? = null,
        val currentBlock: TrainingBlockData? = null,
        val currentWeek: TrainingWeekData? = null,
        val selectedWeekId: String? = null,
        val userProfile: UserProfileData? = null,
        val recentSessions: List<WorkoutSessionData> = emptyList(),
        val mainLiftE1RMs: List<Pair<String, Double>> = emptyList(),
        val mevMrvData: List<MevMrvLiftData> = emptyList(),
        val isLoading: Boolean = true,
    ) {
        val allWeeks: List<Pair<TrainingBlockData, TrainingWeekData>> get() =
            activePlan?.blocks?.sortedBy { it.blockOrder }?.flatMap { block ->
                block.weeks.sortedBy { it.weekNumber }.map { Pair(block, it) }
            } ?: emptyList()

        val displayedWeek: TrainingWeekData? get() =
            if (selectedWeekId != null) allWeeks.firstOrNull { it.second.id == selectedWeekId }?.second
            else currentWeek

        val displayedBlock: TrainingBlockData? get() {
            val w = displayedWeek ?: return null
            return allWeeks.firstOrNull { it.second.id == w.id }?.first
        }

        val isShowingCurrentWeek: Boolean get() =
            selectedWeekId == null || selectedWeekId == currentWeek?.id

        val displayedWeekIndex: Int get() {
            val w = displayedWeek ?: return 0
            return (allWeeks.indexOfFirst { it.second.id == w.id } + 1).coerceAtLeast(1)
        }

        val phaseBadge: String get() {
            val block = displayedBlock ?: return "No Plan"
            val week = displayedWeek ?: return "No Plan"
            val weekLabel = if (week.subPhase != SubPhase.TRAINING) week.subPhase.raw
                            else "Week ${week.weekNumber}"
            return "${block.phase.shortName} - $weekLabel"
        }

        val progressFraction: Double get() {
            val plan = activePlan ?: return 0.0
            val total = plan.totalWeeks
            return if (total > 0) plan.completedWeeks.toDouble() / total else 0.0
        }

        val progressText: String get() {
            val plan = activePlan ?: return ""
            return "Week $displayedWeekIndex of ${plan.totalWeeks}"
        }

        fun weeksUntilMeet(nowMs: Long): Int? {
            val meetMs = userProfile?.meetDateMs ?: return null
            if (meetMs <= nowMs) return null
            return ((meetMs - nowMs) / (7L * 86_400_000L)).toInt()
        }

        val canSelectPrevious: Boolean get() {
            val w = displayedWeek ?: return false
            return allWeeks.firstOrNull()?.second?.id != w.id
        }

        val canSelectNext: Boolean get() {
            val w = displayedWeek ?: return false
            return allWeeks.lastOrNull()?.second?.id != w.id
        }

        fun todaysWorkout(): PlannedWorkoutData? {
            if (!isShowingCurrentWeek) return null
            return displayedWeek?.sortedWorkouts?.firstOrNull { !it.isCompleted }
        }
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
            val plan = planRepo.getActivePlan()
            val currentBlock = plan?.currentBlock
            val currentWeek = currentBlock?.currentWeek
            val recentSessions = sessionRepo.getRecent(5)

            val mainLiftNames = listOf("Comp Squat", "Comp Bench Press", "Comp Deadlift", "Military Press")
            val exercises = exerciseRepo.getAll()
            val mainLiftE1RMs = mainLiftNames.mapNotNull { name ->
                val ex = exercises.firstOrNull { it.name == name }
                val e1rm = ex?.estimatedOneRepMax ?: return@mapNotNull null
                Pair(name, e1rm)
            }

            val mevMrv = computeMevMrv(plan, currentBlock, currentWeek, profile)

            _uiState.value = UiState(
                activePlan = plan,
                currentBlock = currentBlock,
                currentWeek = currentWeek,
                selectedWeekId = null,
                userProfile = profile,
                recentSessions = recentSessions,
                mainLiftE1RMs = mainLiftE1RMs,
                mevMrvData = mevMrv,
                isLoading = false,
            )
        }
    }

    private fun computeMevMrv(
        plan: TrainingPlanData?,
        block: TrainingBlockData?,
        week: TrainingWeekData?,
        profile: UserProfileData?,
    ): List<MevMrvLiftData> {
        val phase = block?.phase ?: return emptyList()
        if (profile == null) return emptyList()
        val bounds = VolumeCalculator.adjustedVolume(phase, profile)

        val liftToMuscle = mapOf(
            LiftCategory.SQUAT to MuscleGroup.QUADS,
            LiftCategory.BENCH to MuscleGroup.CHEST,
            LiftCategory.DEADLIFT to MuscleGroup.HAMSTRINGS,
        )
        val setsPerMuscle = mutableMapOf<MuscleGroup, Int>()
        val workouts = week?.sortedWorkouts ?: emptyList()
        for (workout in workouts) {
            for (ex in workout.exercises) {
                for (muscle in (ex.exercise?.primaryMuscles ?: emptyList())) {
                    setsPerMuscle[muscle] = (setsPerMuscle[muscle] ?: 0) + ex.workingSetCount
                }
            }
        }

        val liftNames = mapOf(
            LiftCategory.SQUAT to "Squat",
            LiftCategory.BENCH to "Bench",
            LiftCategory.DEADLIFT to "Deadlift",
        )
        return bounds.mapNotNull { b ->
            val muscle = liftToMuscle[b.lift] ?: return@mapNotNull null
            val name = liftNames[b.lift] ?: return@mapNotNull null
            MevMrvLiftData(
                liftName = name,
                lift = b.lift,
                mev = b.mev.toInt(),
                mrv = b.mrv.toInt(),
                currentSets = setsPerMuscle[muscle] ?: 0,
            )
        }
    }

    fun selectPreviousWeek() {
        val state = _uiState.value
        val weeks = state.allWeeks
        val current = state.displayedWeek ?: return
        val idx = weeks.indexOfFirst { it.second.id == current.id }
        if (idx > 0) _uiState.value = state.copy(selectedWeekId = weeks[idx - 1].second.id)
    }

    fun selectNextWeek() {
        val state = _uiState.value
        val weeks = state.allWeeks
        val current = state.displayedWeek ?: return
        val idx = weeks.indexOfFirst { it.second.id == current.id }
        if (idx >= 0 && idx < weeks.size - 1)
            _uiState.value = state.copy(selectedWeekId = weeks[idx + 1].second.id)
    }

    fun resetToCurrentWeek() {
        _uiState.value = _uiState.value.copy(selectedWeekId = null)
    }

    fun progressBars(): List<WeekBarData> {
        val state = _uiState.value
        state.activePlan ?: return emptyList()
        val allWeeks = state.allWeeks
        val bars = mutableListOf<WeekBarData>()
        var lastBlockId: String? = null
        var trainingWeekIdx = 0

        for ((idx, entry) in allWeeks.withIndex()) {
            val (block, week) = entry
            val isNewBlock = block.id != lastBlockId
            if (isNewBlock) { trainingWeekIdx = 0; lastBlockId = block.id }

            val trainingCount = block.weeks.count { it.subPhase == SubPhase.TRAINING }
            val heightFraction = computeBarHeight(week, trainingWeekIdx, trainingCount)

            bars.add(WeekBarData(
                id = week.id,
                globalIndex = idx,
                phase = block.phase,
                subPhase = week.subPhase,
                blockId = block.id,
                isCompleted = week.isCompleted,
                isCurrent = week.id == state.currentWeek?.id,
                heightFraction = heightFraction,
                isNewBlock = isNewBlock,
            ))
            if (week.subPhase == SubPhase.TRAINING) trainingWeekIdx++
        }
        return bars
    }

    private fun computeBarHeight(
        week: TrainingWeekData,
        trainingIdx: Int,
        trainingCount: Int,
    ): Double = when (week.subPhase) {
        SubPhase.DELOAD -> 0.22
        SubPhase.TRANSITIONAL -> 0.38
        SubPhase.OPENERS, SubPhase.TAPER -> 0.45
        SubPhase.TRAINING -> {
            val t = if (trainingCount <= 1) 0.0
                     else trainingIdx.toDouble() / (trainingCount - 1)
            val rpe = 7.0 + t * 3.0
            0.35 + ((rpe - 7.0) / 3.0).coerceIn(0.0, 1.0) * 0.65
        }
    }
}
