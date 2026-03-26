package com.periodizeai.app.services

import com.periodizeai.app.models.LiftCategory
import com.periodizeai.app.models.MovementPattern
import com.periodizeai.app.models.MuscleGroup
import com.periodizeai.app.repositories.CompletedSetData
import com.periodizeai.app.repositories.ExerciseData
import kotlin.math.roundToInt

data class ReadinessInput(
    // General factors (1–5, higher = better)
    val sleep: Int = 5,
    val nutrition: Int = 5,
    val stress: Int = 5,      // 1 = very stressed, 5 = relaxed
    val energy: Int = 5,

    // Body-region soreness (1–5, 1 = fresh, 5 = very sore)
    val sorenessPecs: Int = 1,
    val sorenessLats: Int = 1,
    val sorenessLowerBack: Int = 1,
    val sorenessGlutesHams: Int = 1,
    val sorenessQuads: Int = 1,
)

data class LiftReadiness(
    val squat: Int,    // 0–100
    val bench: Int,    // 0–100
    val deadlift: Int, // 0–100
) {
    fun score(for_: LiftCategory): Int = when (for_) {
        LiftCategory.SQUAT    -> squat
        LiftCategory.BENCH    -> bench
        LiftCategory.DEADLIFT -> deadlift
    }
}

object ReadinessService {

    fun liftCategory(exercise: ExerciseData): LiftCategory = when (exercise.movementPattern) {
        MovementPattern.SQUAT,
        MovementPattern.ISOLATION_LEGS -> LiftCategory.SQUAT

        MovementPattern.HORIZONTAL_PUSH,
        MovementPattern.VERTICAL_PUSH  -> LiftCategory.BENCH

        MovementPattern.HINGE,
        MovementPattern.HORIZONTAL_PULL,
        MovementPattern.VERTICAL_PULL  -> LiftCategory.DEADLIFT

        MovementPattern.ISOLATION_ARMS -> {
            val pullMuscles = setOf(
                MuscleGroup.BICEPS, MuscleGroup.FOREARMS,
                MuscleGroup.LATS,   MuscleGroup.UPPER_BACK,
            )
            if (exercise.primaryMuscles.any { pullMuscles.contains(it) })
                LiftCategory.DEADLIFT
            else
                LiftCategory.BENCH
        }

        MovementPattern.CORE -> LiftCategory.DEADLIFT
    }

    fun calculateReadiness(
        input: ReadinessInput,
        recentSets: Map<LiftCategory, List<CompletedSetData>> = emptyMap(),
        exercises: Map<LiftCategory, ExerciseData?> = emptyMap(),
    ): LiftReadiness {
        val generalScore = generalSubScore(input)

        val squatSoreness    = sorenessSubScore(listOf(input.sorenessQuads, input.sorenessGlutesHams, input.sorenessLowerBack))
        val benchSoreness    = sorenessSubScore(listOf(input.sorenessPecs))
        val deadliftSoreness = sorenessSubScore(listOf(input.sorenessLowerBack, input.sorenessGlutesHams, input.sorenessLats))

        val squatSubjective    = generalScore * 0.6 + squatSoreness    * 0.4
        val benchSubjective    = generalScore * 0.6 + benchSoreness    * 0.4
        val deadliftSubjective = generalScore * 0.6 + deadliftSoreness * 0.4

        val squatObjective    = objectiveSubScore(recentSets[LiftCategory.SQUAT]    ?: emptyList(), exercises[LiftCategory.SQUAT])
        val benchObjective    = objectiveSubScore(recentSets[LiftCategory.BENCH]    ?: emptyList(), exercises[LiftCategory.BENCH])
        val deadliftObjective = objectiveSubScore(recentSets[LiftCategory.DEADLIFT] ?: emptyList(), exercises[LiftCategory.DEADLIFT])

        return LiftReadiness(
            squat    = clampScore(squatSubjective    * 0.7 + squatObjective    * 0.3),
            bench    = clampScore(benchSubjective    * 0.7 + benchObjective    * 0.3),
            deadlift = clampScore(deadliftSubjective * 0.7 + deadliftObjective * 0.3),
        )
    }

    fun rpeAdjustment(readinessScore: Int): Double = when (readinessScore) {
        in 80..100 ->  0.0
        in 60..79  -> -0.5
        in 40..59  -> -1.0
        in 20..39  -> -1.5
        else       -> -2.0
    }

    fun readinessLabel(score: Int): String = when (score) {
        in 80..100 -> "High"
        in 60..79  -> "Moderate"
        in 40..59  -> "Low"
        in 20..39  -> "Very Low"
        else       -> "Critical"
    }

    private fun generalSubScore(input: ReadinessInput): Double {
        val avg = (input.sleep + input.nutrition + input.stress + input.energy) / 4.0
        return (avg - 1.0) / 4.0 * 100.0
    }

    private fun sorenessSubScore(regions: List<Int>): Double {
        if (regions.isEmpty()) return 100.0
        val avg = regions.sum().toDouble() / regions.size.toDouble()
        return (5.0 - avg) / 4.0 * 100.0
    }

    private fun objectiveSubScore(sets: List<CompletedSetData>, exercise: ExerciseData?): Double {
        if (sets.size < 3) return 75.0

        val signals = mutableListOf<Double>()

        val rpeValues = sets.mapNotNull { it.rpe }
        if (rpeValues.isNotEmpty()) {
            val avgRPE = rpeValues.sum() / rpeValues.size.toDouble()
            val rpeScore = maxOf(0.0, minOf(100.0, (10.0 - avgRPE) / 4.0 * 100.0))
            signals.add(rpeScore)
        }

        val currentE1RM = exercise?.estimatedOneRepMax
        val historicalE1RM = OneRepMaxCalculator.estimateFromHistory(sets)
        if (currentE1RM != null && historicalE1RM != null && historicalE1RM > 0) {
            val ratio = currentE1RM / historicalE1RM
            val e1rmScore = maxOf(0.0, minOf(100.0, (ratio - 0.9) / 0.1 * 100.0))
            signals.add(e1rmScore)
        }

        if (signals.isEmpty()) return 75.0
        return signals.sum() / signals.size.toDouble()
    }

    private fun clampScore(value: Double): Int = maxOf(0, minOf(100, value.roundToInt()))
}
