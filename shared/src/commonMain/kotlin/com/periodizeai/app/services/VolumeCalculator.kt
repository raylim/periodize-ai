package com.periodizeai.app.services

import com.periodizeai.app.models.BlockPhase
import com.periodizeai.app.models.DietStatus
import com.periodizeai.app.models.LiftCategory
import com.periodizeai.app.models.RecoveryRating
import com.periodizeai.app.models.Sex
import com.periodizeai.app.models.StrengthLevel
import com.periodizeai.app.models.TrainingGoal
import com.periodizeai.app.models.WeightUnit
import com.periodizeai.app.repositories.UserProfileData
import kotlin.math.roundToInt

data class LiftVolumeBounds(
    val lift: LiftCategory,
    val mev: Double,
    val mrv: Double,
) {
    val gap: Double get() = mrv - mev
}

object VolumeCalculator {

    data class PhaseBaseline(
        val sqMEV: Double, val sqMRV: Double,
        val bpMEV: Double, val bpMRV: Double,
        val dlMEV: Double, val dlMRV: Double,
    )

    data class VolumeFactor(
        val label: String,
        val value: String,
        val mevEffect: Double,
        val mrvEffect: Double,
    ) {
        val isNeutral: Boolean get() = mevEffect == 0.0 && mrvEffect == 0.0
    }

    private val baselines: Map<BlockPhase, PhaseBaseline> = mapOf(
        BlockPhase.HYPERTROPHY to PhaseBaseline(sqMEV = 7.5, sqMRV = 14.0, bpMEV = 9.0,  bpMRV = 17.0, dlMEV = 5.5, dlMRV = 11.0),
        BlockPhase.STRENGTH    to PhaseBaseline(sqMEV = 5.5, sqMRV = 9.0,  bpMEV = 8.0,  bpMRV = 11.0, dlMEV = 4.5, dlMRV = 7.0),
        BlockPhase.PEAKING     to PhaseBaseline(sqMEV = 4.5, sqMRV = 6.0,  bpMEV = 6.5,  bpMRV = 8.5,  dlMEV = 2.5, dlMRV = 4.5),
    )

    // MARK: - Adjusted Volume

    fun adjustedVolume(phase: BlockPhase, profile: UserProfileData): List<LiftVolumeBounds> {
        val baseline = baselines[phase] ?: return emptyList()
        val mevAdj = mevAdjustment(profile)
        val mrvAdj = mrvAdjustment(profile)

        val goalMEVAdj: Double
        val goalMRVAdj: Double
        when (profile.goal) {
            TrainingGoal.HYPERTROPHY    -> { goalMEVAdj =  1.0; goalMRVAdj =  2.0 }
            TrainingGoal.GENERAL_FITNESS -> { goalMEVAdj = -1.0; goalMRVAdj = -1.0 }
            else                         -> { goalMEVAdj =  0.0; goalMRVAdj =  0.0 }
        }

        val sqMEV = maxOf(1.0, baseline.sqMEV + mevAdj + goalMEVAdj)
        val bpMEV = maxOf(1.0, baseline.bpMEV + mevAdj + goalMEVAdj)
        val dlMEV = maxOf(1.0, baseline.dlMEV + mevAdj + goalMEVAdj)
        return listOf(
            LiftVolumeBounds(LiftCategory.SQUAT,    sqMEV, maxOf(sqMEV + 2, baseline.sqMRV + mrvAdj + goalMRVAdj)),
            LiftVolumeBounds(LiftCategory.BENCH,    bpMEV, maxOf(bpMEV + 2, baseline.bpMRV + mrvAdj + goalMRVAdj)),
            LiftVolumeBounds(LiftCategory.DEADLIFT, dlMEV, maxOf(dlMEV + 2, baseline.dlMRV + mrvAdj + goalMRVAdj)),
        )
    }

    // MARK: - MEV Adjustment Factors

    fun mevAdjustment(profile: UserProfileData): Double {
        var adj = 0.0
        adj += mevSexAdjustment(profile.sex)
        adj += mevBodyweightAdjustment(profile.userBodyWeight, profile.sex, profile.weightUnit)
        adj += mevHeightAdjustment(profile.userHeight, profile.weightUnit)
        adj += mevStrengthLevelAdjustment(profile.strengthLevel)
        adj += mevTrainingAgeAdjustment(profile.trainingAge.toInt())
        adj += mevAgeAdjustment(profile.userAge.toInt())
        adj += mevDietAdjustment(profile.dietStatus)
        adj += mevSleepAdjustment(profile.sleepQuality)
        adj += mevStressAdjustment(profile.stressLevel)
        return adj
    }

    fun mevSexAdjustment(sex: Sex): Double = if (sex == Sex.FEMALE) 1.5 else 0.0

    fun mevBodyweightAdjustment(bw: Double, sex: Sex, unit: WeightUnit): Double {
        val kg = if (unit == WeightUnit.KG) bw else bw / 2.205
        return if (sex == Sex.MALE) {
            when {
                kg < 75  ->  1.5
                kg < 100 ->  0.5
                kg < 125 -> -0.5
                else     -> -1.5
            }
        } else {
            when {
                kg < 57 ->  1.5
                kg < 75 ->  0.5
                kg < 90 -> -0.5
                else    -> -1.5
            }
        }
    }

    fun mevHeightAdjustment(height: Double, unit: WeightUnit): Double {
        val cm = if (unit == WeightUnit.KG) height else height * 2.54
        return when {
            cm < 160 ->  1.0
            cm < 173 ->  0.5
            cm < 183 -> -0.5
            else     -> -1.0
        }
    }

    fun mevStrengthLevelAdjustment(level: StrengthLevel): Double = when (level) {
        StrengthLevel.BEGINNER     -> -0.5
        StrengthLevel.INTERMEDIATE ->  0.0
        StrengthLevel.ADVANCED     ->  0.5
        StrengthLevel.ELITE        ->  1.0
    }

    fun mevTrainingAgeAdjustment(years: Int): Double = when {
        years < 2  -> -0.5
        years < 5  ->  0.0
        years < 10 ->  0.5
        else       ->  1.0
    }

    fun mevAgeAdjustment(age: Int): Double = when {
        age < 19 -> -1.0
        age < 30 -> -0.5
        age < 40 ->  0.0
        age < 50 ->  0.5
        else     ->  1.0
    }

    fun mevDietAdjustment(status: DietStatus): Double = when (status) {
        DietStatus.SURPLUS     -> -0.5
        DietStatus.MAINTENANCE ->  0.0
        DietStatus.DEFICIT     ->  0.5
    }

    fun mevSleepAdjustment(quality: RecoveryRating): Double = when (quality) {
        RecoveryRating.EXCELLENT     -> -0.5
        RecoveryRating.GOOD          -> -0.25
        RecoveryRating.AVERAGE       ->  0.0
        RecoveryRating.BELOW_AVERAGE ->  0.25
        RecoveryRating.POOR          ->  0.5
    }

    fun mevStressAdjustment(level: RecoveryRating): Double = when (level) {
        RecoveryRating.EXCELLENT     -> -0.5
        RecoveryRating.GOOD          -> -0.25
        RecoveryRating.AVERAGE       ->  0.0
        RecoveryRating.BELOW_AVERAGE ->  0.25
        RecoveryRating.POOR          ->  0.5
    }

    // MARK: - MRV Adjustment Factors

    fun mrvAdjustment(profile: UserProfileData): Double {
        var adj = 0.0
        adj += mrvSexAdjustment(profile.sex)
        adj += mrvBodyweightAdjustment(profile.userBodyWeight, profile.sex, profile.weightUnit)
        adj += mrvHeightAdjustment(profile.userHeight, profile.weightUnit)
        adj += mrvStrengthLevelAdjustment(profile.strengthLevel)
        adj += mrvTrainingAgeAdjustment(profile.trainingAge.toInt())
        adj += mrvAgeAdjustment(profile.userAge.toInt())
        adj += mrvDietAdjustment(profile.dietStatus)
        adj += mrvSleepAdjustment(profile.sleepQuality)
        adj += mrvStressAdjustment(profile.stressLevel)
        return adj
    }

    fun mrvSexAdjustment(sex: Sex): Double = if (sex == Sex.FEMALE) 3.0 else 0.0

    fun mrvBodyweightAdjustment(bw: Double, sex: Sex, unit: WeightUnit): Double {
        val kg = if (unit == WeightUnit.KG) bw else bw / 2.205
        return if (sex == Sex.MALE) {
            when {
                kg < 75  ->  3.0
                kg < 100 ->  1.0
                kg < 125 -> -1.0
                else     -> -3.0
            }
        } else {
            when {
                kg < 57 ->  3.0
                kg < 75 ->  1.0
                kg < 90 -> -1.0
                else    -> -3.0
            }
        }
    }

    fun mrvHeightAdjustment(height: Double, unit: WeightUnit): Double {
        val cm = if (unit == WeightUnit.KG) height else height * 2.54
        return when {
            cm < 160 ->  2.0
            cm < 173 ->  1.0
            cm < 183 -> -1.0
            else     -> -2.0
        }
    }

    fun mrvStrengthLevelAdjustment(level: StrengthLevel): Double = when (level) {
        StrengthLevel.BEGINNER     ->  1.0
        StrengthLevel.INTERMEDIATE ->  0.0
        StrengthLevel.ADVANCED     -> -1.0
        StrengthLevel.ELITE        -> -3.0
    }

    fun mrvTrainingAgeAdjustment(years: Int): Double = when {
        years < 2  ->  2.0
        years < 5  ->  2.0
        years < 10 ->  0.0
        else       -> -2.0
    }

    fun mrvAgeAdjustment(age: Int): Double = when {
        age < 19 ->  2.0
        age < 30 ->  1.0
        age < 40 ->  0.0
        age < 50 -> -1.0
        else     -> -3.0
    }

    fun mrvDietAdjustment(status: DietStatus): Double = when (status) {
        DietStatus.SURPLUS     ->  1.0
        DietStatus.MAINTENANCE ->  0.0
        DietStatus.DEFICIT     -> -2.0
    }

    fun mrvSleepAdjustment(quality: RecoveryRating): Double = when (quality) {
        RecoveryRating.EXCELLENT     ->  1.5
        RecoveryRating.GOOD          ->  1.0
        RecoveryRating.AVERAGE       ->  0.0
        RecoveryRating.BELOW_AVERAGE -> -1.0
        RecoveryRating.POOR          -> -2.0
    }

    fun mrvStressAdjustment(level: RecoveryRating): Double = when (level) {
        RecoveryRating.EXCELLENT     ->  1.0
        RecoveryRating.GOOD          ->  0.5
        RecoveryRating.AVERAGE       ->  0.0
        RecoveryRating.BELOW_AVERAGE -> -1.0
        RecoveryRating.POOR          -> -2.0
    }

    // MARK: - Mesocycle Length

    fun mesocycleLength(bounds: List<LiftVolumeBounds>): Int {
        if (bounds.isEmpty()) return 4
        val avgGap = bounds.sumOf { it.gap } / bounds.size.toDouble()
        return when {
            avgGap >= 7 -> 9
            avgGap >= 5 -> 7
            avgGap >= 4 -> 6
            avgGap >= 3 -> 5
            else        -> 4
        }
    }

    fun mesocycleLength(bounds: List<LiftVolumeBounds>, phase: BlockPhase, profile: UserProfileData): Int {
        val raw = mesocycleLength(bounds)
        val lo = minimumMesocycleLength(phase, profile.strengthLevel)
        val hi = maximumMesocycleLength(phase, profile.strengthLevel)
        return maxOf(lo, minOf(hi, raw))
    }

    fun hypertrophyMesocycleCount(bounds: List<LiftVolumeBounds>, profile: UserProfileData): Int {
        if (bounds.isEmpty()) return 1
        val avgGap = bounds.sumOf { it.gap } / bounds.size.toDouble()
        if (avgGap < 5.0) return 1
        return when (profile.strengthLevel) {
            StrengthLevel.BEGINNER -> 1
            StrengthLevel.INTERMEDIATE, StrengthLevel.ADVANCED, StrengthLevel.ELITE -> 2
        }
    }

    fun minimumMesocycleLength(phase: BlockPhase, level: StrengthLevel): Int = when (phase) {
        BlockPhase.HYPERTROPHY -> 5
        BlockPhase.STRENGTH -> when (level) {
            StrengthLevel.BEGINNER     -> 4
            StrengthLevel.INTERMEDIATE -> 5
            StrengthLevel.ADVANCED     -> 5
            StrengthLevel.ELITE        -> 6
        }
        BlockPhase.PEAKING -> when (level) {
            StrengthLevel.BEGINNER     -> 3
            StrengthLevel.INTERMEDIATE -> 3
            StrengthLevel.ADVANCED     -> 4
            StrengthLevel.ELITE        -> 5
        }
        else -> 4
    }

    fun maximumMesocycleLength(phase: BlockPhase, level: StrengthLevel): Int = when (phase) {
        BlockPhase.HYPERTROPHY -> when (level) {
            StrengthLevel.BEGINNER     -> 7
            StrengthLevel.INTERMEDIATE -> 7
            StrengthLevel.ADVANCED     -> 6
            StrengthLevel.ELITE        -> 5
        }
        BlockPhase.STRENGTH -> when (level) {
            StrengthLevel.BEGINNER     -> 5
            StrengthLevel.INTERMEDIATE -> 6
            StrengthLevel.ADVANCED     -> 7
            StrengthLevel.ELITE        -> 9
        }
        BlockPhase.PEAKING -> when (level) {
            StrengthLevel.BEGINNER     -> 4
            StrengthLevel.INTERMEDIATE -> 5
            StrengthLevel.ADVANCED     -> 6
            StrengthLevel.ELITE        -> 9
        }
        else -> 7
    }

    fun transitionalWeekCount(profile: UserProfileData): Int = when (profile.strengthLevel) {
        StrengthLevel.BEGINNER                                       -> 0
        StrengthLevel.INTERMEDIATE                                   -> 1
        StrengthLevel.ADVANCED, StrengthLevel.ELITE                  -> 2
    }

    fun bridgePhaseLength(): Int = 3

    // MARK: - Weekly Volume Ramp

    fun setsForWeek(weekIndex: Int, trainingWeeks: Int, mev: Double, mrv: Double): Int {
        if (trainingWeeks <= 1) return mev.roundToInt()
        val fraction = weekIndex.toDouble() / (trainingWeeks - 1).toDouble()
        val sets = mev + fraction * (mrv - mev)
        return maxOf(1, sets.roundToInt())
    }

    fun deloadSets(mev: Double): Int = maxOf(1, (mev * 0.5).roundToInt())

    // MARK: - Human-Readable Factor Summary

    fun adjustmentFactors(profile: UserProfileData): List<VolumeFactor> {
        val factors = mutableListOf<VolumeFactor>()

        val sexMev = mevSexAdjustment(profile.sex)
        val sexMrv = mrvSexAdjustment(profile.sex)
        if (sexMev != 0.0 || sexMrv != 0.0) {
            factors.add(VolumeFactor(
                label = "Sex",
                value = if (profile.sex == Sex.FEMALE) "Female" else "Male",
                mevEffect = sexMev,
                mrvEffect = sexMrv,
            ))
        }

        val slMev = mevStrengthLevelAdjustment(profile.strengthLevel)
        val slMrv = mrvStrengthLevelAdjustment(profile.strengthLevel)
        factors.add(VolumeFactor(
            label = "Strength level",
            value = profile.strengthLevel.name.lowercase().replaceFirstChar { it.uppercase() },
            mevEffect = slMev,
            mrvEffect = slMrv,
        ))

        val taMev = mevTrainingAgeAdjustment(profile.trainingAge.toInt())
        val taMrv = mrvTrainingAgeAdjustment(profile.trainingAge.toInt())
        factors.add(VolumeFactor(
            label = "Training age",
            value = "${profile.trainingAge} yr${if (profile.trainingAge == 1L) "" else "s"}",
            mevEffect = taMev,
            mrvEffect = taMrv,
        ))

        val ageMev = mevAgeAdjustment(profile.userAge.toInt())
        val ageMrv = mrvAgeAdjustment(profile.userAge.toInt())
        factors.add(VolumeFactor(
            label = "Age",
            value = "${profile.userAge}",
            mevEffect = ageMev,
            mrvEffect = ageMrv,
        ))

        val dietMev = mevDietAdjustment(profile.dietStatus)
        val dietMrv = mrvDietAdjustment(profile.dietStatus)
        factors.add(VolumeFactor(
            label = "Diet",
            value = profile.dietStatus.name.lowercase().replaceFirstChar { it.uppercase() },
            mevEffect = dietMev,
            mrvEffect = dietMrv,
        ))

        val sleepMev = mevSleepAdjustment(profile.sleepQuality)
        val sleepMrv = mrvSleepAdjustment(profile.sleepQuality)
        factors.add(VolumeFactor(
            label = "Sleep quality",
            value = profile.sleepQuality.raw,
            mevEffect = sleepMev,
            mrvEffect = sleepMrv,
        ))

        val stressMev = mevStressAdjustment(profile.stressLevel)
        val stressMrv = mrvStressAdjustment(profile.stressLevel)
        factors.add(VolumeFactor(
            label = "Stress level",
            value = profile.stressLevel.raw,
            mevEffect = stressMev,
            mrvEffect = stressMrv,
        ))

        return factors
    }
}
