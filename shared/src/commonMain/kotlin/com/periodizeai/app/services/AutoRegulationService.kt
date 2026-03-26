package com.periodizeai.app.services

import com.periodizeai.app.models.ExerciseRole
import com.periodizeai.app.models.LiftCategory
import com.periodizeai.app.models.MovementPattern
import com.periodizeai.app.models.WeightUnit
import com.periodizeai.app.repositories.CompletedSetData
import com.periodizeai.app.repositories.PlannedExerciseData
import com.periodizeai.app.repositories.WorkoutSessionData
import com.periodizeai.app.utils.Constants
import com.periodizeai.app.utils.HapticService
import com.periodizeai.app.utils.formattedWeight

data class AutoRegulationResult(
    /** (exerciseName, oldE1RM, newE1RM) */
    val e1RMUpdates: List<Triple<String, Double?, Double>>,
    /** (exerciseName, suggestion message) */
    val weightAdjustments: List<Pair<String, String>>,
    /** (exerciseName, prType, value) */
    val prs: List<Triple<String, String, String>>,
)

object AutoRegulationService {

    /**
     * Analyses a completed workout session and returns e1RM updates, weight adjustment
     * suggestions, and any personal records detected.
     *
     * The service is pure — it does not touch the database. The caller is responsible
     * for persisting the returned [AutoRegulationResult].
     *
     * @param session The completed workout session including all sets.
     * @param plannedExercises Optional planned exercises used for RPE/RIR targets.
     * @param unit Weight unit for formatted adjustment messages.
     * @param historicalSetsByExercise Historical working sets keyed by exercise name,
     *        used for PR detection. Provided by the caller from the repository.
     */
    fun processCompletedWorkout(
        session: WorkoutSessionData,
        plannedExercises: List<PlannedExerciseData>? = null,
        unit: WeightUnit = WeightUnit.LB,
        userBodyWeight: Double = 0.0,
        historicalSetsByExercise: Map<String, List<CompletedSetData>> = emptyMap(),
    ): AutoRegulationResult {
        val e1RMUpdates = mutableListOf<Triple<String, Double?, Double>>()
        val weightAdjustments = mutableListOf<Pair<String, String>>()
        val prs = mutableListOf<Triple<String, String, String>>()

        val readinessScores: Map<LiftCategory, Int?> = mapOf(
            LiftCategory.SQUAT to session.readinessSquatScore,
            LiftCategory.BENCH to session.readinessBenchScore,
            LiftCategory.DEADLIFT to session.readinessDeadliftScore,
        )

        val setsByExercise = session.completedSets.groupBy { set ->
            set.exercise?.name ?: set.exerciseId ?: "Unknown"
        }

        for ((exerciseName, sets) in setsByExercise) {
            val exercise = sets.firstOrNull()?.exercise
            val workingSets = sets.filter { !it.isWarmup }
            if (workingSets.isEmpty()) continue

            val newE1RM = OneRepMaxCalculator.bestEstimate(workingSets) ?: continue
            val oldE1RM = exercise?.estimatedOneRepMax

            val liftCategory = exercise?.movementPattern?.toLiftCategory()
            val readiness = liftCategory?.let { readinessScores[it] }

            val updatedE1RM = OneRepMaxCalculator.updateEstimate(oldE1RM, newE1RM, readiness)
            e1RMUpdates.add(Triple(exerciseName, oldE1RM, updatedE1RM))

            val plannedExercise = plannedExercises?.firstOrNull { planned ->
                planned.exerciseId != null && planned.exerciseId == exercise?.id
                    || planned.exercise?.name == exerciseName
            }

            if (plannedExercise != null) {
                val targetRPE = plannedExercise.targetRPE
                if (targetRPE != null) {
                    val actualAvgRPE = workingSets.mapNotNull { it.rpe }
                        .takeIf { it.isNotEmpty() }?.average()
                    if (actualAvgRPE != null) {
                        val diff = actualAvgRPE - targetRPE
                        when {
                            diff >= 1.0 -> weightAdjustments.add(
                                Pair(exerciseName, "Consider reducing weight next session")
                            )
                            diff <= -1.0 -> weightAdjustments.add(
                                Pair(exerciseName, "Consider increasing weight next session")
                            )
                        }
                    }
                }

                if (plannedExercise.role != ExerciseRole.MAIN_LIFT) {
                    progressAccessory(
                        sets = workingSets,
                        targetRIR = plannedExercise.targetRIR,
                        isBarbell = exercise?.isBarbell ?: true,
                    )?.let { weightAdjustments.add(Pair(exerciseName, it)) }
                }
            }

            val historicalSets = historicalSetsByExercise[exerciseName] ?: emptyList()
            prs.addAll(checkForPRs(exerciseName, workingSets, historicalSets))
        }

        return AutoRegulationResult(e1RMUpdates, weightAdjustments, prs)
    }

    private fun checkForPRs(
        exerciseName: String,
        currentSets: List<CompletedSetData>,
        historicalSets: List<CompletedSetData>,
    ): List<Triple<String, String, String>> {
        val prs = mutableListOf<Triple<String, String, String>>()

        val currentBestE1RM = OneRepMaxCalculator.bestEstimate(currentSets) ?: return prs
        val historicalBestE1RM = OneRepMaxCalculator.bestEstimate(historicalSets)

        if (historicalBestE1RM == null || currentBestE1RM > historicalBestE1RM) {
            HapticService.success()
            prs.add(Triple(exerciseName, "e1RM", currentBestE1RM.formattedWeight))
        }

        val currentBestWeight = currentSets.maxOfOrNull { it.weight } ?: return prs
        val currentBestReps = currentSets
            .filter { it.weight >= currentBestWeight - 0.01 }
            .maxOfOrNull { it.reps }
        val historicalBestReps = historicalSets
            .filter { it.weight >= currentBestWeight - 0.01 }
            .maxOfOrNull { it.reps }

        if (currentBestReps != null &&
            (historicalBestReps == null || currentBestReps > historicalBestReps)
        ) {
            prs.add(
                Triple(
                    exerciseName,
                    "Reps at ${currentBestWeight.formattedWeight}",
                    "$currentBestReps reps",
                )
            )
        }

        return prs
    }

    private fun progressAccessory(
        sets: List<CompletedSetData>,
        targetRIR: Int?,
        isBarbell: Boolean,
    ): String? {
        val target = targetRIR ?: 2
        val rirValues = sets.mapNotNull { it.rir }
        if (rirValues.isEmpty()) return null
        val avgRIR = rirValues.average()
        val increment = if (isBarbell) Constants.Progression.barbellAccessoryIncrement
                        else Constants.Progression.dumbbellAccessoryIncrement
        return when {
            avgRIR < target - 1 -> "Consider reducing weight by ${increment.formattedWeight}"
            avgRIR > target + 1 -> "Consider increasing weight by ${increment.formattedWeight}"
            else -> null
        }
    }

    private fun MovementPattern.toLiftCategory(): LiftCategory? = when (this) {
        MovementPattern.SQUAT -> LiftCategory.SQUAT
        MovementPattern.HINGE -> LiftCategory.DEADLIFT
        MovementPattern.HORIZONTAL_PUSH -> LiftCategory.BENCH
        else -> null
    }
}
