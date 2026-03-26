package com.periodizeai.app.services

import com.periodizeai.app.models.WeightUnit
import com.periodizeai.app.repositories.CompletedSetData
import com.periodizeai.app.repositories.ExerciseData
import com.periodizeai.app.utils.Constants
import com.periodizeai.app.utils.roundedToNearest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.round

object OneRepMaxCalculator {

    fun effectiveReps(reps: Int, rpe: Double? = null, rir: Int? = null): Double {
        if (rpe != null && rpe > 0) return reps.toDouble() + (10.0 - rpe)
        if (rir != null) return reps.toDouble() + rir.toDouble()
        return reps.toDouble()
    }

    fun calculate(weight: Double, reps: Int, rpe: Double? = null, rir: Int? = null): Double? {
        if (weight <= 0 || reps <= 0) return null
        val eff = effectiveReps(reps, rpe, rir)
        if (eff <= 0) return null
        if (eff <= 1) return weight
        return weight * (1.0 + eff / 30.0)
    }

    fun updateEstimate(current: Double?, new: Double, readinessScore: Int? = null): Double {
        if (current == null) return new
        val confidence: Double = when {
            readinessScore == null -> 1.0
            readinessScore >= 80 -> 1.0
            readinessScore >= 60 -> 0.8
            readinessScore >= 40 -> 0.6
            else -> 0.4
        }
        return if (new >= current) {
            val newWeight = 0.5 * confidence
            (1.0 - newWeight) * current + newWeight * new
        } else {
            val newWeight = Constants.Progression.e1RMNewWeight * confidence
            (1.0 - newWeight) * current + newWeight * new
        }
    }

    /** Returns the best estimated 1RM computed from the given non-warmup sets. */
    fun bestEstimate(from: List<CompletedSetData>): Double? =
        from.filter { !it.isWarmup && it.weight > 0 && it.reps > 0 }
            .mapNotNull { calculate(it.weight, it.reps, it.rpe, it.rir) }
            .maxOrNull()

    /**
     * Weighted-average e1RM estimate from historical sets.
     * Sessions are grouped by calendar day; the best e1RM from each day is time-decayed
     * with a 90-day half-life before computing a weighted mean.
     */
    fun estimateFromHistory(sets: List<CompletedSetData>, limit: Int = Int.MAX_VALUE): Double? {
        val workingSets = sets.filter { !it.isWarmup && it.weight > 0 && it.reps > 0 }
        if (workingSets.isEmpty()) return null

        val byDay = workingSets.groupBy { set ->
            set.completedAt?.let { ms ->
                Instant.fromEpochMilliseconds(ms)
                    .toLocalDateTime(TimeZone.currentSystemDefault())
                    .date
            }
        }

        data class SessionPeak(val peakMs: Long, val e1rm: Double)

        val sessionPeaks = byDay.entries
            .mapNotNull { (_, daySets) ->
                val peakMs = daySets.mapNotNull { it.completedAt }.maxOrNull() ?: return@mapNotNull null
                val bestE1RM = daySets
                    .mapNotNull { calculate(it.weight, it.reps, it.rpe, it.rir) }
                    .maxOrNull() ?: return@mapNotNull null
                SessionPeak(peakMs, bestE1RM)
            }
            .sortedByDescending { it.peakMs }
            .take(limit)

        if (sessionPeaks.isEmpty()) return null

        val nowMs = Clock.System.now().toEpochMilliseconds()
        var weightedSum = 0.0
        var totalWeight = 0.0
        for (peak in sessionPeaks) {
            val daysAgo = (nowMs - peak.peakMs) / 86_400_000.0
            val w = 0.5.pow(daysAgo / 90.0)
            weightedSum += w * peak.e1rm
            totalWeight += w
        }
        return if (totalWeight > 0) weightedSum / totalWeight else null
    }

    /**
     * Generates a warmup ramp from bar weight up to (but not including) [workingWeight].
     * Returns a list of (weight, reps) pairs.
     */
    fun warmupRamp(
        workingWeight: Double,
        barWeight: Double = 45.0,
        unit: WeightUnit = WeightUnit.LB,
    ): List<Pair<Double, Int>> {
        if (workingWeight <= barWeight * 1.5) return listOf(Pair(barWeight, 10))
        val roundTo = if (unit == WeightUnit.LB) 5.0 else 2.5
        val sets = mutableListOf(Pair(barWeight, 10))
        val increments = listOf(
            Pair(0.40, 8),
            Pair(0.60, 5),
            Pair(0.75, 3),
            Pair(0.90, 1),
        )
        for ((pct, reps) in increments) {
            val w = (workingWeight * pct).roundedToNearest(roundTo)
            if (w > barWeight && w < workingWeight) sets.add(Pair(w, reps))
        }
        return sets
    }

    /**
     * Suggests a working weight for [exercise] at the given [reps] and [rir].
     * Uses the working max if available, otherwise derives one from e1RM at 90%.
     */
    fun suggestWeight(exercise: ExerciseData, reps: Int, rir: Int = 2): Double? {
        val base = exercise.workingMax
            ?: exercise.estimatedOneRepMax?.let { it * 0.90 }
            ?: return null
        val eff = effectiveReps(reps, rir = rir)
        val percentage = 1.0 / (1.0 + eff / 30.0)
        val roundTo = if (exercise.isBarbell) 5.0 else 2.5
        return (base * percentage).roundedToNearest(roundTo)
    }

    /**
     * Returns the plates needed per side for [totalWeight], or null if the
     * combination cannot be achieved with [availablePlates].
     */
    fun plateMath(
        totalWeight: Double,
        barWeight: Double = 45.0,
        availablePlates: List<Double>,
    ): List<Double>? {
        if (totalWeight <= barWeight) return null
        val perSide = (totalWeight - barWeight) / 2.0
        if (perSide <= 0) return emptyList()
        val sortedPlates = availablePlates.sortedDescending()
        var remaining = perSide
        val plates = mutableListOf<Double>()
        for (plate in sortedPlates) {
            while (remaining >= plate - 0.01) {
                plates.add(plate)
                remaining -= plate
            }
        }
        if (remaining > 0.1) return null
        return plates
    }
}
