package com.periodizeai.app.services

import com.periodizeai.app.models.BlockPhase
import com.periodizeai.app.models.LiftCategory
import com.periodizeai.app.models.MuscleGroup
import com.periodizeai.app.repositories.UserProfileData

data class VolumeAlert(
    val muscleGroup: MuscleGroup,
    val weeklySets: Int,
    val targetRange: IntRange,
    val status: VolumeAlert.VolumeStatus,
) {
    enum class VolumeStatus { UNDER_VOLUME, OPTIMAL, OVER_VOLUME }
}

object VolumeValidationService {

    /**
     * Validates weekly volume per muscle group against MEV/MRV bounds derived from
     * [VolumeCalculator] for the given [phase] and [profile].
     *
     * @param setsPerMuscle Map of muscle group to number of working sets planned per week.
     * @param phase Current training block phase.
     * @param profile Athlete profile used to compute individualized volume bounds.
     * @return A [VolumeAlert] for every muscle group present in [setsPerMuscle].
     */
    fun validateWeeklyVolume(
        setsPerMuscle: Map<MuscleGroup, Int>,
        phase: BlockPhase,
        profile: UserProfileData,
    ): List<VolumeAlert> {
        val bounds = VolumeCalculator.adjustedVolume(phase, profile)
        return setsPerMuscle.map { (muscle, sets) ->
            val range = targetRange(muscle, bounds)
            val status = when {
                sets < range.first -> VolumeAlert.VolumeStatus.UNDER_VOLUME
                sets > range.last  -> VolumeAlert.VolumeStatus.OVER_VOLUME
                else               -> VolumeAlert.VolumeStatus.OPTIMAL
            }
            VolumeAlert(muscleGroup = muscle, weeklySets = sets, targetRange = range, status = status)
        }
    }

    fun hasVolumeIssues(
        setsPerMuscle: Map<MuscleGroup, Int>,
        phase: BlockPhase,
        profile: UserProfileData,
    ): Boolean = validateWeeklyVolume(setsPerMuscle, phase, profile)
        .any { it.status != VolumeAlert.VolumeStatus.OPTIMAL }

    // Maps each muscle group to the lift-category bounds that best represent its training stimulus.
    private fun targetRange(muscle: MuscleGroup, bounds: List<LiftVolumeBounds>): IntRange {
        val category = when (muscle) {
            MuscleGroup.QUADS,
            MuscleGroup.GLUTES,
            MuscleGroup.ADDUCTORS,
            MuscleGroup.HIP_FLEXORS,
            MuscleGroup.CALVES       -> LiftCategory.SQUAT

            MuscleGroup.CHEST,
            MuscleGroup.SHOULDERS,
            MuscleGroup.TRICEPS      -> LiftCategory.BENCH

            MuscleGroup.HAMSTRINGS,
            MuscleGroup.UPPER_BACK,
            MuscleGroup.LATS,
            MuscleGroup.REAR_DELTS,
            MuscleGroup.BICEPS,
            MuscleGroup.FOREARMS,
            MuscleGroup.CORE         -> LiftCategory.DEADLIFT
        }
        val b = bounds.find { it.lift == category } ?: return 6..16
        return b.mev.toInt()..b.mrv.toInt()
    }
}
