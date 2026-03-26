package com.periodizeai.app.services

import com.periodizeai.app.models.*
import com.periodizeai.app.repositories.*
import com.periodizeai.app.utils.nowEpochMs
import kotlin.random.Random

object PlanPersistenceHelper {

    /**
     * Converts a GeneratedPlan to TrainingPlanData + nested records and
     * saves them all via the repository. Deactivates any existing active plan first.
     * Returns the saved TrainingPlanData.
     */
    suspend fun saveGeneratedPlan(
        plan: GeneratedPlan,
        repo: TrainingPlanRepository,
        exerciseRepo: ExerciseRepository,
    ): TrainingPlanData {
        val planId = randomId()
        val planData = TrainingPlanData(
            id = planId,
            name = plan.name,
            isActive = true,
            trainingDaysPerWeek = plan.trainingDaysPerWeek,
            createdAt = nowEpochMs(),
            blocks = emptyList(),
        )
        repo.savePlan(planData)
        repo.activatePlan(planId)

        val allExercises = exerciseRepo.getAll()
        val exerciseById = allExercises.associateBy { it.id }
        val exerciseByName = allExercises.associateBy { it.name }

        val blocksData = mutableListOf<TrainingBlockData>()

        for (genBlock in plan.blocks) {
            val blockId = randomId()
            val mevJson = genBlock.mevByLift.entries.joinToString(",") { "${it.key}:${it.value}" }
            val mrvJson = genBlock.mrvByLift.entries.joinToString(",") { "${it.key}:${it.value}" }

            val blockData = TrainingBlockData(
                id = blockId,
                planId = planId,
                phase = genBlock.phase,
                blockOrder = genBlock.order,
                isCompleted = false,
                mesocycleLength = genBlock.mesocycleLength,
                mevSetsJson = mevJson,
                mrvSetsJson = mrvJson,
                weeks = emptyList(),
            )
            repo.saveBlock(blockData)

            val weeksData = mutableListOf<TrainingWeekData>()

            for (genWeek in genBlock.weeks) {
                val weekId = randomId()
                val weekData = TrainingWeekData(
                    id = weekId,
                    blockId = blockId,
                    weekNumber = genWeek.weekNumber,
                    subPhase = genWeek.subPhase,
                    isCompleted = false,
                    workouts = emptyList(),
                )
                repo.saveWeek(weekData)

                val workoutsData = mutableListOf<PlannedWorkoutData>()

                for (genWorkout in genWeek.workouts) {
                    val workoutId = randomId()
                    val intensityTier = genWorkout.intensityTierRaw?.let { raw ->
                        IntensityTier.entries.firstOrNull { it.raw == raw }
                    }
                    val workoutData = PlannedWorkoutData(
                        id = workoutId,
                        weekId = weekId,
                        dayNumber = genWorkout.dayNumber,
                        dayLabel = genWorkout.dayLabel,
                        focus = genWorkout.focus,
                        isCompleted = false,
                        intensityTier = intensityTier,
                    )
                    repo.saveWorkout(workoutData)

                    val exercisesData = mutableListOf<PlannedExerciseData>()

                    for (genEx in genWorkout.exercises) {
                        val exercise = exerciseById[genEx.exerciseId]
                            ?: exerciseByName[genEx.exerciseName]
                        val peId = randomId()
                        val tier = genEx.intensityTierRaw?.let { raw ->
                            IntensityTier.entries.firstOrNull { it.raw == raw }
                        }
                        val pe = PlannedExerciseData(
                            id = peId,
                            workoutId = workoutId,
                            exerciseId = genEx.exerciseId,
                            exercise = exercise,
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
                            amrapSetIndex = -1,
                            dropPercentage = genEx.dropPercentage,
                            intensityTier = tier,
                        )
                        repo.savePlannedExercise(pe)
                        exercisesData.add(pe)
                    }
                    workoutsData.add(workoutData.copy(exercises = exercisesData))
                }
                weeksData.add(weekData.copy(workouts = workoutsData))
            }
            blocksData.add(blockData.copy(weeks = weeksData))
        }

        return planData.copy(blocks = blocksData)
    }

    private fun randomId(): String = buildString {
        val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
        repeat(16) { append(chars[Random.nextInt(chars.length)]) }
    }
}
