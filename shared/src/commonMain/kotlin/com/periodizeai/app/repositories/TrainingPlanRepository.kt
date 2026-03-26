package com.periodizeai.app.repositories

import com.periodizeai.app.database.*
import com.periodizeai.app.models.*
import com.periodizeai.app.utils.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

// ── Domain models ─────────────────────────────────────────────────────────

data class TrainingPlanData(
    val id: String,
    val name: String,
    val isActive: Boolean = true,
    val trainingDaysPerWeek: Int = 4,
    val createdAt: Long = nowEpochMs(),
    val blocks: List<TrainingBlockData> = emptyList(),
) {
    val currentBlock get() = blocks.sortedBy { it.blockOrder }.firstOrNull { !it.isCompleted }
    val totalWeeks get() = blocks.sumOf { it.weeks.size }
    val completedWeeks get() = blocks.flatMap { it.weeks }.count { it.isCompleted }
}

data class TrainingBlockData(
    val id: String,
    val planId: String,
    val phase: BlockPhase,
    val blockOrder: Int,
    val isCompleted: Boolean = false,
    val mesocycleLength: Int = 4,
    val mevSetsJson: String? = null,
    val mrvSetsJson: String? = null,
    val weeks: List<TrainingWeekData> = emptyList(),
) {
    val currentWeek get() = weeks.sortedBy { it.weekNumber }.firstOrNull { !it.isCompleted }
}

data class TrainingWeekData(
    val id: String,
    val blockId: String,
    val weekNumber: Int,
    val subPhase: SubPhase = SubPhase.TRAINING,
    val isCompleted: Boolean = false,
    val workouts: List<PlannedWorkoutData> = emptyList(),
) {
    val sortedWorkouts get() = workouts.sortedBy { it.dayNumber }
    val completedWorkouts get() = workouts.count { it.isCompleted }
}

data class PlannedWorkoutData(
    val id: String,
    val weekId: String,
    val dayNumber: Int,
    val dayLabel: String = "",
    val focus: WorkoutFocus = WorkoutFocus.FULL_BODY,
    val isCompleted: Boolean = false,
    val isSkipped: Boolean = false,
    val intensityTier: IntensityTier? = null,
    val linkedSessionId: String? = null,
    val exercises: List<PlannedExerciseData> = emptyList(),
)

data class PlannedExerciseData(
    val id: String,
    val workoutId: String,
    val exerciseId: String?,
    val exercise: ExerciseData? = null,
    val order: Int,
    val sets: Int,
    val reps: Int,
    val isAMRAP: Boolean = false,
    val targetRPE: Double? = null,
    val targetRIR: Int? = null,
    val percentageOfMax: Double? = null,
    val suggestedWeight: Double? = null,
    val role: ExerciseRole = ExerciseRole.MAIN_LIFT,
    val isSwapped: Boolean = false,
    val deloadSetWeights: List<Double>? = null,
    val prescribedWarmupSetCount: Int = 0,
    val perSetTargetReps: List<Int>? = null,
    val amrapSetIndex: Int = -1,
    val dropPercentage: Double? = null,
    val intensityTier: IntensityTier? = null,
) {
    val workingSetCount get() = maxOf(sets - prescribedWarmupSetCount, 1)
}

// ── Mappers ───────────────────────────────────────────────────────────────

fun TrainingPlan.toDomain(blocks: List<TrainingBlockData> = emptyList()) = TrainingPlanData(
    id = id, name = name, isActive = isActive == 1L,
    trainingDaysPerWeek = trainingDaysPerWeek.toInt(),
    createdAt = createdAt, blocks = blocks,
)

fun TrainingBlock.toDomain(weeks: List<TrainingWeekData> = emptyList()) = TrainingBlockData(
    id = id, planId = planId, phase = BlockPhase.from(phaseRaw),
    blockOrder = blockOrder.toInt(), isCompleted = isCompleted == 1L,
    mesocycleLength = mesocycleLength.toInt(),
    mevSetsJson = mevSetsJson, mrvSetsJson = mrvSetsJson, weeks = weeks,
)

fun TrainingWeek.toDomain(workouts: List<PlannedWorkoutData> = emptyList()) = TrainingWeekData(
    id = id, blockId = blockId, weekNumber = weekNumber.toInt(),
    subPhase = SubPhase.from(subPhaseRaw), isCompleted = isCompleted == 1L, workouts = workouts,
)

fun PlannedWorkout.toDomain(exercises: List<PlannedExerciseData> = emptyList()) = PlannedWorkoutData(
    id = id, weekId = weekId, dayNumber = dayNumber.toInt(), dayLabel = dayLabel,
    focus = WorkoutFocus.from(focusRaw), isCompleted = isCompleted == 1L, isSkipped = isSkipped == 1L,
    intensityTier = intensityTierRaw?.let { IntensityTier.from(it) },
    linkedSessionId = linkedSessionId, exercises = exercises,
)

fun PlannedExercise.toDomain(exercise: ExerciseData? = null) = PlannedExerciseData(
    id = id, workoutId = workoutId, exerciseId = exerciseId, exercise = exercise,
    order = exerciseOrder.toInt(), sets = sets.toInt(), reps = reps.toInt(),
    isAMRAP = isAMRAP == 1L, targetRPE = targetRPE, targetRIR = targetRIR?.toInt(),
    percentageOfMax = percentageOfMax, suggestedWeight = suggestedWeight,
    role = ExerciseRole.from(roleRaw), isSwapped = isSwapped == 1L,
    deloadSetWeights = deloadSetWeightsRaw?.toDoubleList(),
    prescribedWarmupSetCount = prescribedWarmupSetCount.toInt(),
    perSetTargetReps = perSetTargetRepsRaw?.toIntList(),
    amrapSetIndex = amrapSetIndex.toInt(), dropPercentage = dropPercentage,
    intensityTier = intensityTierRaw?.let { IntensityTier.from(it) },
)

// ── Repository ────────────────────────────────────────────────────────────

class TrainingPlanRepository(
    private val db: PeriodizeAIDatabase,
    private val exerciseRepo: ExerciseRepository,
) {
    suspend fun getActivePlan(): TrainingPlanData? = withContext(Dispatchers.IO) {
        val plan = db.trainingPlanQueries.selectActive().executeAsOneOrNull() ?: return@withContext null
        plan.toDomain(blocksForPlan(plan.id))
    }

    suspend fun getAllPlans(): List<TrainingPlanData> = withContext(Dispatchers.IO) {
        db.trainingPlanQueries.selectAll().executeAsList().map { it.toDomain() }
    }

    suspend fun savePlan(plan: TrainingPlanData) = withContext(Dispatchers.IO) {
        db.trainingPlanQueries.insert(plan.id, plan.name, if (plan.isActive) 1L else 0L,
            plan.trainingDaysPerWeek.toLong(), plan.createdAt)
    }

    suspend fun activatePlan(id: String) = withContext(Dispatchers.IO) {
        db.trainingPlanQueries.setActive()
        db.trainingPlanQueries.activate(id)
    }

    suspend fun saveBlock(block: TrainingBlockData) = withContext(Dispatchers.IO) {
        db.trainingBlockQueries.insert(
            block.id, block.planId, block.phase.raw, block.blockOrder.toLong(),
            if (block.isCompleted) 1L else 0L, block.mesocycleLength.toLong(),
            block.mevSetsJson, block.mrvSetsJson,
        )
    }

    suspend fun saveWeek(week: TrainingWeekData) = withContext(Dispatchers.IO) {
        db.trainingWeekQueries.insert(
            week.id, week.blockId, week.weekNumber.toLong(), week.subPhase.raw,
            if (week.isCompleted) 1L else 0L,
        )
    }

    suspend fun saveWorkout(workout: PlannedWorkoutData) = withContext(Dispatchers.IO) {
        db.plannedWorkoutQueries.insert(
            workout.id, workout.weekId, workout.dayNumber.toLong(), workout.dayLabel,
            workout.focus.raw, if (workout.isCompleted) 1L else 0L,
            if (workout.isSkipped) 1L else 0L, workout.intensityTier?.raw, workout.linkedSessionId,
        )
    }

    suspend fun savePlannedExercise(pe: PlannedExerciseData) = withContext(Dispatchers.IO) {
        db.plannedExerciseQueries.insert(
            pe.id, pe.workoutId, pe.exerciseId, pe.order.toLong(), pe.sets.toLong(), pe.reps.toLong(),
            if (pe.isAMRAP) 1L else 0L, pe.targetRPE, pe.targetRIR?.toLong(),
            pe.percentageOfMax, pe.suggestedWeight, pe.role.raw,
            if (pe.isSwapped) 1L else 0L,
            pe.deloadSetWeights?.toDelimitedString(),
            pe.prescribedWarmupSetCount.toLong(),
            pe.perSetTargetReps?.toDelimitedString(),
            pe.amrapSetIndex.toLong(), pe.dropPercentage, pe.intensityTier?.raw,
        )
    }

    suspend fun markWeekCompleted(weekId: String) = withContext(Dispatchers.IO) {
        db.trainingWeekQueries.markCompleted(weekId)
    }

    suspend fun markBlockCompleted(blockId: String) = withContext(Dispatchers.IO) {
        db.trainingBlockQueries.markCompleted(blockId)
    }

    suspend fun markWorkoutCompleted(workoutId: String, sessionId: String) = withContext(Dispatchers.IO) {
        db.plannedWorkoutQueries.markCompleted(sessionId, workoutId)
    }

    suspend fun deletePlan(id: String) = withContext(Dispatchers.IO) {
        db.trainingPlanQueries.delete(id)
    }

    suspend fun deleteAllPlans() = withContext(Dispatchers.IO) {
        db.trainingPlanQueries.selectAll().executeAsList().forEach { plan ->
            db.trainingPlanQueries.delete(plan.id)
        }
    }

    private fun blocksForPlan(planId: String): List<TrainingBlockData> =
        db.trainingBlockQueries.selectByPlan(planId).executeAsList()
            .map { it.toDomain(weeksForBlock(it.id)) }

    private fun weeksForBlock(blockId: String): List<TrainingWeekData> =
        db.trainingWeekQueries.selectByBlock(blockId).executeAsList()
            .map { it.toDomain(workoutsForWeek(it.id)) }

    private fun workoutsForWeek(weekId: String): List<PlannedWorkoutData> =
        db.plannedWorkoutQueries.selectByWeek(weekId).executeAsList()
            .map { it.toDomain(exercisesForWorkout(it.id)) }

    private fun exercisesForWorkout(workoutId: String): List<PlannedExerciseData> =
        db.plannedExerciseQueries.selectByWorkout(workoutId).executeAsList().map { pe ->
            pe.toDomain(pe.exerciseId?.let { exerciseRepo.getByIdSync(it) })
        }
}
