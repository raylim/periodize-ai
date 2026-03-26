package com.periodizeai.app.services

import com.periodizeai.app.models.*
import com.periodizeai.app.repositories.ExerciseData
import com.periodizeai.app.repositories.UserProfileData
import com.periodizeai.app.utils.nowEpochMs
import com.periodizeai.app.utils.roundedToNearest
import kotlin.math.max
import kotlin.math.min

// ── Output data structures ────────────────────────────────────────────────

data class GeneratedSet(
    val weight: Double?,
    val reps: Int,
    val isWarmup: Boolean = false,
)

data class GeneratedExercise(
    val exerciseId: String,
    val exerciseName: String,
    val role: ExerciseRole,
    val order: Int,
    val sets: Int,
    val reps: Int,
    val targetRPE: Double?,
    val targetRIR: Int?,
    val suggestedWeight: Double?,
    val dropPercentage: Double?,
    val perSetWeights: List<Double>?,   // replaces deloadSetWeights
    val perSetReps: List<Int>?,         // replaces perSetTargetReps
    val prescribedWarmupSetCount: Int = 0,
    val intensityTierRaw: String?,
    val isAMRAP: Boolean = false,
)

data class GeneratedWorkout(
    val dayNumber: Int,
    val dayLabel: String,
    val focus: WorkoutFocus,
    val intensityTierRaw: String?,
    val exercises: List<GeneratedExercise>,
)

data class GeneratedWeek(
    val weekNumber: Int,
    val subPhase: SubPhase,
    val workouts: List<GeneratedWorkout>,
)

data class GeneratedBlock(
    val phase: BlockPhase,
    val order: Int,
    val mesocycleLength: Int,
    val mevByLift: Map<String, Double>,   // LiftCategory.raw → mev sets
    val mrvByLift: Map<String, Double>,
    val weeks: List<GeneratedWeek>,
)

data class GeneratedPlan(
    val name: String,
    val trainingDaysPerWeek: Int,
    val blocks: List<GeneratedBlock>,
)

// ── Support structures ────────────────────────────────────────────────────

data class AccessoryPrescription(val sets: Int, val repsRange: IntRange, val rir: Int)

data class PhaseLoadingTemplate(
    val baseRepTarget: Int,
    val baseDropPercent: Double,
    val baseRPEProgression: List<Double>,
)

// ── Main engine ───────────────────────────────────────────────────────────

class PeriodizationEngine {

    companion object {
        val shared = PeriodizationEngine()

        // ── Phase loading templates ───────────────────────────────────────
        private val loadingTemplates: Map<BlockPhase, PhaseLoadingTemplate> = mapOf(
            BlockPhase.HYPERTROPHY to PhaseLoadingTemplate(
                baseRepTarget = 8,
                baseDropPercent = 0.10,
                baseRPEProgression = listOf(7.0, 8.0, 9.0, 10.0),
            ),
            BlockPhase.STRENGTH to PhaseLoadingTemplate(
                baseRepTarget = 5,
                baseDropPercent = 0.10,
                baseRPEProgression = listOf(7.0, 8.0, 9.0, 10.0),
            ),
            BlockPhase.PEAKING to PhaseLoadingTemplate(
                baseRepTarget = 3,
                baseDropPercent = 0.08,
                baseRPEProgression = listOf(8.0, 8.5, 9.0, 9.5),
            ),
            BlockPhase.BRIDGE to PhaseLoadingTemplate(
                baseRepTarget = 10,
                baseDropPercent = 0.0,
                baseRPEProgression = listOf(5.0, 6.0, 6.5),
            ),
        )

        fun rpeProgression(phase: BlockPhase, trainingWeeks: Int): List<Double> {
            val template = loadingTemplates[phase] ?: return List(trainingWeeks) { 8.0 }
            val base = template.baseRPEProgression
            if (trainingWeeks == 0) return emptyList()
            if (trainingWeeks == base.size) return base
            return (0 until trainingWeeks).map { i ->
                val fraction = if (trainingWeeks == 1) 0.0 else i.toDouble() / (trainingWeeks - 1).toDouble()
                val pos = fraction * (base.size - 1).toDouble()
                val low = pos.toInt()
                val high = min(low + 1, base.size - 1)
                val t = pos - low.toDouble()
                base[low] + t * (base[high] - base[low])
            }
        }

        fun peakingReps(weekIndex: Int, trainingWeekCount: Int): Int {
            if (trainingWeekCount <= 1) return 3
            val fraction = weekIndex.toDouble() / (trainingWeekCount - 1).toDouble()
            return when {
                fraction < 0.33 -> 3
                fraction < 0.66 -> 2
                else -> 1
            }
        }

        fun warmupRamp(phase: BlockPhase): List<Pair<Double, Int>> = when (phase) {
            BlockPhase.HYPERTROPHY, BlockPhase.BRIDGE ->
                listOf(0.40 to 8, 0.55 to 5)
            BlockPhase.STRENGTH ->
                listOf(0.40 to 5, 0.55 to 3, 0.65 to 2)
            BlockPhase.PEAKING ->
                listOf(0.40 to 5, 0.50 to 3, 0.60 to 2, 0.70 to 1)
            BlockPhase.MEET_PREP ->
                listOf(0.40 to 5, 0.50 to 3, 0.60 to 2, 0.70 to 1, 0.80 to 1)
        }

        // ── Preferred variation rankings (phase-aware) ────────────────────
        private fun preferredVariationRanks(phase: BlockPhase): Map<String, Int> {
            val squatRanked: List<String>
            val hingeRanked: List<String>
            val benchRanked: List<String>
            when (phase) {
                BlockPhase.HYPERTROPHY, BlockPhase.BRIDGE -> {
                    squatRanked = listOf(
                        "Pause Squat", "Front Squat", "High Bar Squat", "Safety Bar Squat",
                        "Pause High Bar Squat", "Belt Squat", "Tempo Squat", "Box Squat",
                        "Pin Squat", "Zercher Squat",
                    )
                    hingeRanked = listOf(
                        "Pause Off Floor Deadlift", "Conv Deficit Deadlift", "Sumo Deficit Deadlift",
                        "Halting Conventional Deadlift", "Halting Sumo Deadlift",
                        "Double Overhand Deficit Deadlift", "RDLs", "Stiff Leg Deadlift",
                        "Trap Bar Deadlift", "Good Mornings",
                    )
                    benchRanked = listOf(
                        "Pause Bench Press", "3 Count Pause Bench", "Incline Bench Press",
                        "Wide Grip Bench Press", "Spoto Press", "Tempo Bench Press",
                        "Close Grip Bench Press", "Floor Press", "Larsen Press",
                    )
                }
                BlockPhase.STRENGTH -> {
                    squatRanked = listOf(
                        "Pause Squat", "High Bar Squat", "Safety Bar Squat", "Pause High Bar Squat",
                        "Box Squat", "Front Squat", "Tempo Squat", "Pin Squat", "Zercher Squat", "Belt Squat",
                    )
                    hingeRanked = listOf(
                        "Pause Off Floor Deadlift", "Conv Deficit Deadlift", "Sumo Deficit Deadlift",
                        "Block Pull", "Halting Conventional Deadlift", "Halting Sumo Deadlift",
                        "Double Overhand Deficit Deadlift", "RDLs", "Stiff Leg Deadlift",
                        "Trap Bar Deadlift", "Good Mornings",
                    )
                    benchRanked = listOf(
                        "Pause Bench Press", "Close Grip Bench Press", "Spoto Press",
                        "Incline Bench Press", "3 Count Pause Bench", "JM Press",
                        "Floor Press", "Tempo Bench Press", "Wide Grip Bench Press",
                        "Pin Press", "Larsen Press",
                    )
                }
                BlockPhase.PEAKING, BlockPhase.MEET_PREP -> {
                    squatRanked = listOf(
                        "Box Squat", "Pin Squat", "Pause Squat", "High Bar Squat",
                        "Safety Bar Squat", "Pause High Bar Squat", "Tempo Squat",
                        "Front Squat", "Zercher Squat",
                    )
                    hingeRanked = listOf(
                        "Block Pull", "Reverse Band Deadlift", "Pause Off Floor Deadlift",
                        "Conv Deficit Deadlift", "Sumo Deficit Deadlift",
                        "Halting Conventional Deadlift", "Halting Sumo Deadlift",
                        "Double Overhand Deficit Deadlift", "Trap Bar Deadlift", "RDLs", "Good Mornings",
                    )
                    benchRanked = listOf(
                        "Floor Press", "Close Grip Bench Press", "Pin Press",
                        "Spoto Press", "Pause Bench Press", "JM Press",
                        "Wide Grip Bench Press", "Incline Bench Press",
                        "3 Count Pause Bench", "Tempo Bench Press", "Larsen Press",
                    )
                }
            }
            val allRanked = listOf(
                squatRanked, hingeRanked, benchRanked,
                listOf("Military Press", "Push Press", "Landmine Press"),
            )
            val dict = mutableMapOf<String, Int>()
            for (group in allRanked) {
                group.forEachIndexed { i, name -> dict[name] = i }
            }
            return dict
        }

        fun preferredVariationRank(
            name: String,
            phase: BlockPhase?,
            goal: TrainingGoal = TrainingGoal.POWERLIFTING,
        ): Int {
            if (goal == TrainingGoal.HYPERTROPHY || goal == TrainingGoal.GENERAL_FITNESS) {
                val hypertrophySquatOrder = listOf(
                    "Belt Squat", "High Bar Squat", "Pause High Bar Squat",
                    "Front Squat", "Safety Bar Squat", "Tempo Squat",
                    "Pause Squat", "Zercher Squat", "Box Squat", "Pin Squat",
                )
                val idx = hypertrophySquatOrder.indexOf(name)
                if (idx >= 0) return idx
            }
            val lookupPhase = phase ?: BlockPhase.STRENGTH
            return preferredVariationRanks(lookupPhase)[name] ?: Int.MAX_VALUE
        }
    }

    // ── Per-block exercise rotation tracking ──────────────────────────────
    private var usedExercisesPerPattern: MutableMap<String, MutableSet<String>> = mutableMapOf()

    // ── Inner data classes ────────────────────────────────────────────────

    data class DayConfig(
        val label: String,
        val focus: WorkoutFocus,
        val mainPattern: MovementPattern,
        var compoundAccessories: MutableList<MovementPattern>,
        var isolationAccessories: MutableList<MovementPattern>,
        val isVariationDay: Boolean,
    )

    data class LiftIntensity(
        val lift: LiftCategory,
        val tier: IntensityTier,
    )

    // ── Default profile helper ────────────────────────────────────────────

    private fun defaultProfile() = UserProfileData(
        id = "", name = "", weightUnit = WeightUnit.LB,
        goal = TrainingGoal.POWERLIFTING,
        hasCompletedOnboarding = false, hasImportedCSV = false,
        userBodyWeight = 180.0, barbellWeight = 45.0,
        availablePlatesLb = listOf(45.0, 35.0, 25.0, 10.0, 5.0, 2.5),
        availablePlatesKg = listOf(20.0, 15.0, 10.0, 5.0, 2.5, 1.25),
        restTimerMainLift = 180L, restTimerCompound = 120L, restTimerIsolation = 60L,
        healthKitEnabled = false, syncBodyWeightFromHealth = false,
        deadliftStance = DeadliftStance.CONVENTIONAL, meetDateMs = null,
        sex = Sex.MALE, userHeight = 175.0, userAge = 25L, dateOfBirthMs = null,
        trainingAge = 2L, strengthLevel = StrengthLevel.INTERMEDIATE,
        dietStatus = DietStatus.MAINTENANCE,
        sleepQuality = RecoveryRating.AVERAGE, stressLevel = RecoveryRating.AVERAGE,
        trainingDaysPerWeek = 4L, createdAt = 0L,
    )

    private fun effectiveProfile(profile: UserProfileData?) = profile ?: defaultProfile()

    // ── Undulating intensity assignment ───────────────────────────────────

    private fun undulatingIntensities(
        trainingDays: Int,
        profile: UserProfileData?,
    ): List<List<LiftIntensity>> = when (trainingDays) {
        2 -> listOf(
            listOf(LiftIntensity(LiftCategory.SQUAT, IntensityTier.HEAVY), LiftIntensity(LiftCategory.BENCH, IntensityTier.HEAVY), LiftIntensity(LiftCategory.DEADLIFT, IntensityTier.MEDIUM)),
            listOf(LiftIntensity(LiftCategory.SQUAT, IntensityTier.MEDIUM), LiftIntensity(LiftCategory.BENCH, IntensityTier.MEDIUM), LiftIntensity(LiftCategory.DEADLIFT, IntensityTier.HEAVY)),
        )
        3 -> listOf(
            listOf(LiftIntensity(LiftCategory.SQUAT, IntensityTier.HEAVY), LiftIntensity(LiftCategory.BENCH, IntensityTier.MEDIUM), LiftIntensity(LiftCategory.DEADLIFT, IntensityTier.LIGHT)),
            listOf(LiftIntensity(LiftCategory.SQUAT, IntensityTier.LIGHT), LiftIntensity(LiftCategory.BENCH, IntensityTier.LIGHT), LiftIntensity(LiftCategory.DEADLIFT, IntensityTier.HEAVY)),
            listOf(LiftIntensity(LiftCategory.SQUAT, IntensityTier.MEDIUM), LiftIntensity(LiftCategory.BENCH, IntensityTier.HEAVY), LiftIntensity(LiftCategory.DEADLIFT, IntensityTier.MEDIUM)),
        )
        4 -> listOf(
            listOf(LiftIntensity(LiftCategory.SQUAT, IntensityTier.HEAVY), LiftIntensity(LiftCategory.DEADLIFT, IntensityTier.MEDIUM)),
            listOf(LiftIntensity(LiftCategory.BENCH, IntensityTier.HEAVY)),
            listOf(LiftIntensity(LiftCategory.SQUAT, IntensityTier.MEDIUM), LiftIntensity(LiftCategory.DEADLIFT, IntensityTier.HEAVY)),
            listOf(LiftIntensity(LiftCategory.BENCH, IntensityTier.MEDIUM)),
        )
        5 -> listOf(
            listOf(LiftIntensity(LiftCategory.SQUAT, IntensityTier.HEAVY), LiftIntensity(LiftCategory.DEADLIFT, IntensityTier.LIGHT)),
            listOf(LiftIntensity(LiftCategory.BENCH, IntensityTier.HEAVY)),
            listOf(LiftIntensity(LiftCategory.BENCH, IntensityTier.MEDIUM)),
            listOf(LiftIntensity(LiftCategory.BENCH, IntensityTier.LIGHT)),
            listOf(LiftIntensity(LiftCategory.SQUAT, IntensityTier.MEDIUM), LiftIntensity(LiftCategory.DEADLIFT, IntensityTier.HEAVY)),
        )
        6 -> listOf(
            listOf(LiftIntensity(LiftCategory.SQUAT, IntensityTier.HEAVY), LiftIntensity(LiftCategory.DEADLIFT, IntensityTier.LIGHT)),
            listOf(LiftIntensity(LiftCategory.BENCH, IntensityTier.HEAVY)),
            listOf(LiftIntensity(LiftCategory.BENCH, IntensityTier.MEDIUM)),
            listOf(LiftIntensity(LiftCategory.SQUAT, IntensityTier.MEDIUM), LiftIntensity(LiftCategory.DEADLIFT, IntensityTier.HEAVY)),
            listOf(LiftIntensity(LiftCategory.BENCH, IntensityTier.LIGHT)),
            listOf(LiftIntensity(LiftCategory.SQUAT, IntensityTier.LIGHT)),
        )
        else -> List(trainingDays) {
            listOf(
                LiftIntensity(LiftCategory.SQUAT, IntensityTier.HEAVY),
                LiftIntensity(LiftCategory.BENCH, IntensityTier.HEAVY),
                LiftIntensity(LiftCategory.DEADLIFT, IntensityTier.HEAVY),
            )
        }
    }

    private fun intensityTierFor(
        lift: LiftCategory,
        dayIndex: Int,
        dayIntensities: List<List<LiftIntensity>>,
    ): IntensityTier {
        if (dayIndex >= dayIntensities.size) return IntensityTier.HEAVY
        return dayIntensities[dayIndex].firstOrNull { it.lift == lift }?.tier ?: IntensityTier.MEDIUM
    }

    // ── Phase length determination ─────────────────────────────────────────

    fun determinePhaseLengths(profile: UserProfileData?): List<Pair<BlockPhase, Int>> {
        val p = effectiveProfile(profile)
        val hypBounds = VolumeCalculator.adjustedVolume(BlockPhase.HYPERTROPHY, p)
        val strBounds = VolumeCalculator.adjustedVolume(BlockPhase.STRENGTH, p)
        val peakBounds = VolumeCalculator.adjustedVolume(BlockPhase.PEAKING, p)

        val meetDateMs = profile?.meetDateMs
        if (meetDateMs != null && meetDateMs > nowEpochMs()) {
            val weeksAvailable = max(
                ((meetDateMs - nowEpochMs()) / (7L * 24 * 3_600_000L)).toInt(), 8
            )
            val meetPrepWeeks = 2
            val peakMeso = min(VolumeCalculator.mesocycleLength(peakBounds, BlockPhase.PEAKING, p), 5)
            val strMeso = min(
                VolumeCalculator.mesocycleLength(strBounds, BlockPhase.STRENGTH, p),
                max(weeksAvailable - meetPrepWeeks - peakMeso - 4, 4),
            )
            val hypMeso = max(weeksAvailable - meetPrepWeeks - peakMeso - strMeso, 4)
            return listOf(
                BlockPhase.HYPERTROPHY to hypMeso,
                BlockPhase.STRENGTH to strMeso,
                BlockPhase.PEAKING to peakMeso,
            )
        }

        val hypCount = VolumeCalculator.hypertrophyMesocycleCount(hypBounds, p)
        val singleHypLen = VolumeCalculator.mesocycleLength(hypBounds, BlockPhase.HYPERTROPHY, p)
        val perHypLen = if (hypCount > 1)
            VolumeCalculator.minimumMesocycleLength(BlockPhase.HYPERTROPHY, p.strengthLevel)
        else singleHypLen
        val strLen = VolumeCalculator.mesocycleLength(strBounds, BlockPhase.STRENGTH, p)
        val peakLen = VolumeCalculator.mesocycleLength(peakBounds, BlockPhase.PEAKING, p)

        val goal = profile?.goal ?: TrainingGoal.POWERLIFTING
        return when (goal) {
            TrainingGoal.POWERLIFTING -> buildList {
                repeat(hypCount) { add(BlockPhase.HYPERTROPHY to perHypLen) }
                add(BlockPhase.STRENGTH to strLen)
                add(BlockPhase.PEAKING to peakLen)
            }
            TrainingGoal.STRENGTH -> buildList {
                repeat(hypCount) { add(BlockPhase.HYPERTROPHY to perHypLen) }
                add(BlockPhase.STRENGTH to (strLen + peakLen))
            }
            TrainingGoal.HYPERTROPHY -> buildList {
                val extraHypLen = VolumeCalculator.minimumMesocycleLength(BlockPhase.HYPERTROPHY, p.strengthLevel)
                repeat(hypCount + 1) { add(BlockPhase.HYPERTROPHY to extraHypLen) }
                add(BlockPhase.STRENGTH to max(strLen - 2, 4))
            }
            TrainingGoal.GENERAL_FITNESS -> listOf(
                BlockPhase.HYPERTROPHY to max(perHypLen - 1, 4),
                BlockPhase.STRENGTH to max(strLen - 1, 3),
            )
        }
    }

    // ── Generate full plan ────────────────────────────────────────────────

    fun generatePlan(
        trainingDays: Int,
        exercises: List<ExerciseData>,
        profile: UserProfileData? = null,
    ): GeneratedPlan {
        val p = effectiveProfile(profile)
        val hasMeetDate = profile?.meetDateMs != null && profile.meetDateMs > nowEpochMs()
        val phases = determinePhaseLengths(profile)
        val transitionalCount = VolumeCalculator.transitionalWeekCount(p)
        val bridgeLength = if (hasMeetDate) VolumeCalculator.bridgePhaseLength() else 0
        val totalWeeks = phases.sumOf { it.second } + transitionalCount + (if (hasMeetDate) 2 else 0) + bridgeLength

        usedExercisesPerPattern = mutableMapOf()
        val blocks = mutableListOf<GeneratedBlock>()

        for ((index, phaseAndLength) in phases.withIndex()) {
            val (phase, mesoLength) = phaseAndLength
            val volumeBounds = VolumeCalculator.adjustedVolume(phase, p)
            val phaseTransitionalCount = if (index == 0 && phase == BlockPhase.HYPERTROPHY)
                VolumeCalculator.transitionalWeekCount(p) else 0
            val totalMesoLength = mesoLength + phaseTransitionalCount
            val trainingWeekCount = mesoLength - 1

            val rpeProgressionList = rpeProgression(phase, trainingWeekCount)
            var rpeWeekIdx = 0
            val blockExercises = mutableMapOf<String, MutableSet<String>>()
            val weeks = mutableListOf<GeneratedWeek>()

            for (weekIdx in 1..totalMesoLength) {
                val isDeload = weekIdx == totalMesoLength
                val isTransitional = !isDeload && weekIdx <= phaseTransitionalCount
                val subPhase = when {
                    isDeload -> SubPhase.DELOAD
                    isTransitional -> SubPhase.TRANSITIONAL
                    else -> SubPhase.TRAINING
                }

                val weekRPE: Double
                if (isDeload) {
                    weekRPE = 5.0
                } else if (isTransitional) {
                    val fraction = if (phaseTransitionalCount > 1)
                        (weekIdx - 1).toDouble() / (phaseTransitionalCount - 1).toDouble() else 0.0
                    weekRPE = 5.0 + fraction * 1.0
                } else {
                    weekRPE = rpeProgressionList.getOrNull(rpeWeekIdx) ?: 8.0
                    rpeWeekIdx++
                }

                val workouts = generateWeekWorkouts(
                    phase = phase,
                    weekIndex = max(0, rpeWeekIdx - 1),
                    trainingWeekCount = trainingWeekCount,
                    isDeload = isDeload,
                    isTransitional = isTransitional,
                    weekRPE = weekRPE,
                    trainingDays = trainingDays,
                    exercises = exercises,
                    volumeBounds = volumeBounds,
                    profile = profile,
                )
                for (workout in workouts) {
                    for (ex in workout.exercises) {
                        val pattern = exercises.firstOrNull { it.id == ex.exerciseId }
                            ?.movementPattern?.raw ?: continue
                        blockExercises.getOrPut(pattern) { mutableSetOf() }.add(ex.exerciseName)
                    }
                }
                weeks.add(GeneratedWeek(weekNumber = weekIdx, subPhase = subPhase, workouts = workouts))
            }

            for ((pattern, names) in blockExercises) {
                usedExercisesPerPattern.getOrPut(pattern) { mutableSetOf() }.addAll(names)
            }

            blocks.add(GeneratedBlock(
                phase = phase,
                order = index,
                mesocycleLength = totalMesoLength,
                mevByLift = volumeBounds.associate { it.lift.raw to it.mev },
                mrvByLift = volumeBounds.associate { it.lift.raw to it.mrv },
                weeks = weeks,
            ))
        }

        // Meet prep + bridge blocks
        if (hasMeetDate) {
            val meetBlockWeeks = mutableListOf<GeneratedWeek>()
            for ((idx, subPhase) in listOf(SubPhase.OPENERS, SubPhase.TAPER).withIndex()) {
                val workouts = generateMeetPrepWorkouts(subPhase, exercises, profile)
                meetBlockWeeks.add(GeneratedWeek(weekNumber = idx + 1, subPhase = subPhase, workouts = workouts))
            }
            blocks.add(GeneratedBlock(
                phase = BlockPhase.MEET_PREP,
                order = phases.size,
                mesocycleLength = 2,
                mevByLift = emptyMap(),
                mrvByLift = emptyMap(),
                weeks = meetBlockWeeks,
            ))

            val bridgeBounds = VolumeCalculator.adjustedVolume(BlockPhase.HYPERTROPHY, p)
            val bridgeRPEList = listOf(5.0, 6.0, 6.5)
            val bridgeWeeks = mutableListOf<GeneratedWeek>()
            for (weekIdx in 1..bridgeLength) {
                val weekRPE = bridgeRPEList.getOrNull(weekIdx - 1) ?: 6.0
                val workouts = generateWeekWorkouts(
                    phase = BlockPhase.BRIDGE,
                    weekIndex = weekIdx - 1,
                    trainingWeekCount = bridgeLength,
                    isDeload = false,
                    isTransitional = false,
                    weekRPE = weekRPE,
                    trainingDays = trainingDays,
                    exercises = exercises,
                    volumeBounds = bridgeBounds,
                    profile = profile,
                )
                bridgeWeeks.add(GeneratedWeek(weekNumber = weekIdx, subPhase = SubPhase.TRAINING, workouts = workouts))
            }
            blocks.add(GeneratedBlock(
                phase = BlockPhase.BRIDGE,
                order = phases.size + 1,
                mesocycleLength = bridgeLength,
                mevByLift = bridgeBounds.associate { it.lift.raw to it.mev },
                mrvByLift = bridgeBounds.associate { it.lift.raw to it.mrv },
                weeks = bridgeWeeks,
            ))
        }

        return GeneratedPlan(
            name = "$totalWeeks-Week Periodization",
            trainingDaysPerWeek = trainingDays,
            blocks = blocks,
        )
    }

    // ── Regenerate from current week ──────────────────────────────────────

    fun regenerateFromCurrentWeek(
        plan: GeneratedPlan,
        trainingDays: Int,
        exercises: List<ExerciseData>,
        profile: UserProfileData? = null,
        /** blockOrder → set of completed weekNumbers */
        completedWeeksByBlock: Map<Int, Set<Int>> = emptyMap(),
    ): GeneratedPlan {
        usedExercisesPerPattern = mutableMapOf()
        val p = effectiveProfile(profile)

        val newBlocks = plan.blocks.map { block ->
            val completedWeeks = completedWeeksByBlock[block.order] ?: emptySet()
            val volumeBounds = VolumeCalculator.adjustedVolume(block.phase, p)
            val blockTransitionalCount = block.weeks.count { it.subPhase == SubPhase.TRANSITIONAL }
            val trainingWeekCount = when (block.phase) {
                BlockPhase.MEET_PREP, BlockPhase.BRIDGE -> block.mesocycleLength
                else -> block.mesocycleLength - 1 - blockTransitionalCount
            }
            val rpeProgressionList = rpeProgression(block.phase, trainingWeekCount)
            var rpeWeekIdx = 0
            val blockExercises = mutableMapOf<String, MutableSet<String>>()

            val newWeeks = block.weeks.map { week ->
                if (week.weekNumber in completedWeeks) {
                    if (week.subPhase == SubPhase.TRAINING) rpeWeekIdx++
                    for (workout in week.workouts) {
                        for (ex in workout.exercises) {
                            val pattern = exercises.firstOrNull { it.id == ex.exerciseId }
                                ?.movementPattern?.raw ?: continue
                            blockExercises.getOrPut(pattern) { mutableSetOf() }.add(ex.exerciseName)
                        }
                    }
                    week
                } else {
                    val newWorkouts = if (block.phase == BlockPhase.MEET_PREP) {
                        generateMeetPrepWorkouts(week.subPhase, exercises, profile)
                    } else {
                        val isDeload = week.subPhase == SubPhase.DELOAD
                        val isTransitional = week.subPhase == SubPhase.TRANSITIONAL
                        val bridgeRPEs = listOf(5.0, 6.0, 6.5)

                        val weekRPE: Double
                        if (isDeload) {
                            weekRPE = 5.0
                        } else if (isTransitional) {
                            val fraction = if (blockTransitionalCount > 1)
                                (week.weekNumber - 1).toDouble() / (blockTransitionalCount - 1).toDouble() else 0.0
                            weekRPE = 5.0 + fraction * 1.0
                        } else if (block.phase == BlockPhase.BRIDGE) {
                            weekRPE = bridgeRPEs.getOrNull(week.weekNumber - 1) ?: 6.0
                        } else {
                            weekRPE = rpeProgressionList.getOrNull(rpeWeekIdx) ?: 8.0
                            rpeWeekIdx++
                        }

                        val trainingWeekIndex = if (isTransitional) 0 else max(0, rpeWeekIdx - 1)
                        generateWeekWorkouts(
                            phase = block.phase,
                            weekIndex = trainingWeekIndex,
                            trainingWeekCount = trainingWeekCount,
                            isDeload = isDeload,
                            isTransitional = isTransitional,
                            weekRPE = weekRPE,
                            trainingDays = trainingDays,
                            exercises = exercises,
                            volumeBounds = volumeBounds,
                            profile = profile,
                        )
                    }
                    for (workout in newWorkouts) {
                        for (ex in workout.exercises) {
                            val pattern = exercises.firstOrNull { it.id == ex.exerciseId }
                                ?.movementPattern?.raw ?: continue
                            blockExercises.getOrPut(pattern) { mutableSetOf() }.add(ex.exerciseName)
                        }
                    }
                    week.copy(workouts = newWorkouts)
                }
            }

            for ((pattern, names) in blockExercises) {
                usedExercisesPerPattern.getOrPut(pattern) { mutableSetOf() }.addAll(names)
            }
            block.copy(weeks = newWeeks)
        }

        return plan.copy(trainingDaysPerWeek = trainingDays, blocks = newBlocks)
    }

    // ── Block-wide exercise swap ───────────────────────────────────────────

    fun applySwapToBlock(
        plan: GeneratedPlan,
        inBlockOrder: Int,
        fromWeekNumber: Int,
        targetDayNumber: Int,
        exerciseOrder: Int,
        oldExerciseName: String,
        newExercise: ExerciseData,
    ): GeneratedPlan {
        val newBlocks = plan.blocks.map { block ->
            if (block.order != inBlockOrder) return@map block
            val newWeeks = block.weeks.map { week ->
                if (week.weekNumber <= fromWeekNumber) return@map week
                val newWorkouts = week.workouts.map { workout ->
                    if (workout.dayNumber != targetDayNumber) return@map workout
                    val newExercises = workout.exercises.map { ex ->
                        if (ex.order != exerciseOrder || ex.exerciseName != oldExerciseName) return@map ex
                        val roundTo = if (newExercise.isBarbell) 5.0 else 2.5
                        val newWeight = when {
                            ex.role == ExerciseRole.MAIN_LIFT -> {
                                val e1rm = newExercise.estimatedOneRepMax
                                val rpe = ex.targetRPE
                                if (e1rm != null && rpe != null) {
                                    val effectiveReps = ex.reps.toDouble() + (10.0 - rpe)
                                    val w = if (effectiveReps > 1) e1rm / (1.0 + effectiveReps / 30.0) else e1rm
                                    w.roundedToNearest(roundTo)
                                } else null
                            }
                            else -> OneRepMaxCalculator.suggestWeight(newExercise, ex.reps, ex.targetRIR ?: 2)
                        }
                        ex.copy(
                            exerciseId = newExercise.id,
                            exerciseName = newExercise.name,
                            suggestedWeight = newWeight,
                        )
                    }
                    workout.copy(exercises = newExercises)
                }
                week.copy(workouts = newWorkouts)
            }
            block.copy(weeks = newWeeks)
        }
        return plan.copy(blocks = newBlocks)
    }

    // ── Generate week workouts ────────────────────────────────────────────

    private fun generateWeekWorkouts(
        phase: BlockPhase,
        weekIndex: Int,
        trainingWeekCount: Int,
        isDeload: Boolean,
        isTransitional: Boolean = false,
        weekRPE: Double,
        trainingDays: Int,
        exercises: List<ExerciseData>,
        volumeBounds: List<LiftVolumeBounds>,
        profile: UserProfileData? = null,
    ): List<GeneratedWorkout> {
        val goal = profile?.goal ?: TrainingGoal.POWERLIFTING
        val distribution = workoutDistribution(trainingDays).map { adjustAccessories(it, goal, trainingDays) }
        val dayIntensities = undulatingIntensities(trainingDays, profile)

        val weekVolume: Map<LiftCategory, Int> = volumeBounds.associate { bounds ->
            val sets = when {
                isDeload -> VolumeCalculator.deloadSets(bounds.mev)
                isTransitional -> max(1, bounds.mev.toInt())
                else -> VolumeCalculator.setsForWeek(weekIndex, trainingWeekCount, bounds.mev, bounds.mrv)
            }
            bounds.lift to sets
        }

        val workouts = mutableListOf<GeneratedWorkout>()
        val weekSubPhase = when {
            isDeload -> SubPhase.DELOAD
            isTransitional -> SubPhase.TRANSITIONAL
            else -> SubPhase.TRAINING
        }

        for ((dayIndex, dayConfig) in distribution.withIndex()) {
            val mainLiftCategory = liftCategoryFor(dayConfig.mainPattern)
            val dayTier = mainLiftCategory?.let { intensityTierFor(it, dayIndex, dayIntensities) }
                ?: IntensityTier.HEAVY
            var exerciseOrder = 0
            val genExercises = mutableListOf<GeneratedExercise>()

            val useVariation = isVariationDay(phase, dayConfig, goal)

            // Main lift
            val mainLift = selectExercise(
                pattern = dayConfig.mainPattern,
                role = ExerciseRole.MAIN_LIFT,
                exercises = exercises,
                isVariationDay = useVariation,
                phase = phase,
                profile = profile,
            )
            if (mainLift != null) {
                val totalLiftSets = mainLiftCategory?.let { weekVolume[it] } ?: 4
                val daySets = distributeSetsForDay(totalLiftSets, dayTier, trainingDays, mainLiftCategory)
                val genEx = createMainLiftPrescription(
                    exercise = mainLift,
                    phase = phase,
                    weekIndex = weekIndex,
                    trainingWeekCount = trainingWeekCount,
                    isDeload = isDeload,
                    isTransitional = isTransitional,
                    weekRPE = weekRPE,
                    intensityTier = dayTier,
                    targetWorkingSets = daySets,
                    order = exerciseOrder,
                )
                genExercises.add(genEx)
                exerciseOrder++
            }

            // Compound accessories
            for (pattern in dayConfig.compoundAccessories) {
                val accessory = selectExercise(
                    pattern = pattern,
                    role = ExerciseRole.COMPOUND_ACCESSORY,
                    exercises = exercises,
                    excluding = genExercises.map { it.exerciseName },
                    phase = phase,
                    profile = profile,
                )
                if (accessory != null) {
                    val prescription = accessoryPrescription(phase, weekSubPhase, isCompound = true, goal = goal, trainingDays = trainingDays)
                    val reps = clampedReps(prescription, accessory)
                    val weight = calculateAccessoryWeight(accessory, reps, prescription.rir)
                    genExercises.add(GeneratedExercise(
                        exerciseId = accessory.id,
                        exerciseName = accessory.name,
                        role = ExerciseRole.COMPOUND_ACCESSORY,
                        order = exerciseOrder,
                        sets = prescription.sets,
                        reps = reps,
                        targetRPE = null,
                        targetRIR = prescription.rir,
                        suggestedWeight = weight,
                        dropPercentage = null,
                        perSetWeights = null,
                        perSetReps = null,
                        intensityTierRaw = null,
                    ))
                    exerciseOrder++
                }
            }

            // Isolation accessories
            for (pattern in dayConfig.isolationAccessories) {
                val accessory = selectExercise(
                    pattern = pattern,
                    role = ExerciseRole.ISOLATION_ACCESSORY,
                    exercises = exercises,
                    excluding = genExercises.map { it.exerciseName },
                )
                if (accessory != null) {
                    val prescription = accessoryPrescription(phase, weekSubPhase, isCompound = false, goal = goal, trainingDays = trainingDays)
                    val reps = clampedReps(prescription, accessory)
                    val weight = calculateAccessoryWeight(accessory, reps, prescription.rir)
                    genExercises.add(GeneratedExercise(
                        exerciseId = accessory.id,
                        exerciseName = accessory.name,
                        role = ExerciseRole.ISOLATION_ACCESSORY,
                        order = exerciseOrder,
                        sets = prescription.sets,
                        reps = reps,
                        targetRPE = null,
                        targetRIR = prescription.rir,
                        suggestedWeight = weight,
                        dropPercentage = null,
                        perSetWeights = null,
                        perSetReps = null,
                        intensityTierRaw = null,
                    ))
                    exerciseOrder++
                }
            }

            workouts.add(GeneratedWorkout(
                dayNumber = dayIndex + 1,
                dayLabel = dayConfig.label,
                focus = dayConfig.focus,
                intensityTierRaw = dayTier.raw,
                exercises = genExercises,
            ))
        }

        return workouts
    }

    // ── Volume distribution per day ───────────────────────────────────────

    private fun distributeSetsForDay(
        totalWeeklySets: Int,
        tier: IntensityTier,
        trainingDays: Int,
        liftCategory: LiftCategory?,
    ): Int {
        val daysWithLift = when {
            trainingDays <= 3 -> 1
            trainingDays == 4 -> 2
            else -> 2
        }
        if (daysWithLift <= 1) return max(1, totalWeeklySets)
        val tierShare = when (tier) {
            IntensityTier.HEAVY -> 0.6
            IntensityTier.MEDIUM -> 0.4
            IntensityTier.LIGHT -> 0.3
        }
        return max(1, (totalWeeklySets.toDouble() * tierShare).toInt())
    }

    // ── Variation day logic ───────────────────────────────────────────────

    private fun isVariationDay(
        phase: BlockPhase,
        dayConfig: DayConfig,
        goal: TrainingGoal = TrainingGoal.POWERLIFTING,
    ): Boolean = when (phase) {
        BlockPhase.BRIDGE -> true
        BlockPhase.PEAKING, BlockPhase.MEET_PREP ->
            goal == TrainingGoal.HYPERTROPHY || goal == TrainingGoal.GENERAL_FITNESS
        else -> {
            if (goal == TrainingGoal.HYPERTROPHY || goal == TrainingGoal.GENERAL_FITNESS) true
            else dayConfig.isVariationDay
        }
    }

    // ── Working max helper ────────────────────────────────────────────────

    private fun effectiveWorkingMax(exercise: ExerciseData): Double? =
        exercise.workingMax ?: exercise.estimatedOneRepMax?.let { it * 0.90 }

    private fun estimateWeight(e1rm: Double, reps: Int, rpe: Double, roundTo: Double): Double {
        val effectiveReps = reps.toDouble() + (10.0 - rpe)
        if (effectiveReps <= 1) return e1rm.roundedToNearest(roundTo)
        return (e1rm / (1.0 + effectiveReps / 30.0)).roundedToNearest(roundTo)
    }

    // ── Main lift prescription ────────────────────────────────────────────

    private fun createMainLiftPrescription(
        exercise: ExerciseData,
        phase: BlockPhase,
        weekIndex: Int,
        trainingWeekCount: Int,
        isDeload: Boolean,
        isTransitional: Boolean = false,
        weekRPE: Double,
        intensityTier: IntensityTier,
        targetWorkingSets: Int,
        order: Int,
    ): GeneratedExercise {
        val roundTo = if (exercise.isBarbell) 5.0 else 2.5
        val template = loadingTemplates[phase]
        val dayRPE = if (isDeload || isTransitional)
            min(weekRPE, 6.5)
        else
            max(5.0, weekRPE + intensityTier.rpeOffset)
        val dropPercent = template?.baseDropPercent ?: 0.10

        if (isDeload) {
            val deloadRamp = listOf(0.50 to 5, 0.55 to 5, 0.60 to 5)
            val wm = effectiveWorkingMax(exercise)
            val perSetWeights = wm?.let { wmVal -> deloadRamp.map { (pct, _) -> (wmVal * pct).roundedToNearest(roundTo) } }
            val perSetReps = deloadRamp.map { (_, r) -> r }
            return GeneratedExercise(
                exerciseId = exercise.id,
                exerciseName = exercise.name,
                role = ExerciseRole.MAIN_LIFT,
                order = order,
                sets = deloadRamp.size,
                reps = 5,
                targetRPE = 5.0,
                targetRIR = null,
                suggestedWeight = perSetWeights?.last(),
                dropPercentage = null,
                perSetWeights = perSetWeights,
                perSetReps = perSetReps,
                intensityTierRaw = intensityTier.raw,
            )
        }

        if (isTransitional) {
            val warmupRampList = warmupRamp(BlockPhase.HYPERTROPHY)
            val warmupCount = warmupRampList.size
            val workingSetCount = 3
            val totalSets = warmupCount + workingSetCount
            val e1rm = exercise.estimatedOneRepMax
            val topWeight = e1rm?.let { estimateWeight(it, 12, dayRPE, roundTo) }
            val perSetWeights = topWeight?.let { top ->
                val ws = warmupRampList.map { (pct, _) -> (top * pct / 0.70).roundedToNearest(roundTo) }
                ws + List(workingSetCount) { top }
            }
            val perSetReps = warmupRampList.map { (_, r) -> r } + List(workingSetCount) { 12 }
            return GeneratedExercise(
                exerciseId = exercise.id,
                exerciseName = exercise.name,
                role = ExerciseRole.MAIN_LIFT,
                order = order,
                sets = totalSets,
                reps = 12,
                targetRPE = dayRPE,
                targetRIR = null,
                suggestedWeight = topWeight,
                dropPercentage = null,
                perSetWeights = perSetWeights,
                perSetReps = perSetReps,
                prescribedWarmupSetCount = warmupCount,
                intensityTierRaw = intensityTier.raw,
            )
        }

        // Standard training week
        val repTarget = if (phase == BlockPhase.PEAKING)
            peakingReps(weekIndex, trainingWeekCount)
        else
            template?.baseRepTarget ?: 8

        val warmupRampList = warmupRamp(phase)
        val warmupCount = warmupRampList.size
        val dropSetCount = if (dropPercent > 0) max(targetWorkingSets - 1, 1) else 0
        val totalSets = warmupCount + 1 + dropSetCount

        val e1rm = exercise.estimatedOneRepMax
        val perSetWeights: List<Double>?
        val perSetReps: List<Int>?
        val suggestedWeight: Double?

        if (e1rm != null) {
            val topWeight = estimateWeight(e1rm, repTarget, dayRPE, roundTo)
            val dropWeight = if (dropPercent > 0)
                (topWeight * (1.0 - dropPercent)).roundedToNearest(roundTo)
            else topWeight

            suggestedWeight = topWeight
            val ws = mutableListOf<Double>()
            val rs = mutableListOf<Int>()
            for ((pct, r) in warmupRampList) {
                ws.add((topWeight * pct / 0.70).roundedToNearest(roundTo))
                rs.add(r)
            }
            ws.add(topWeight); rs.add(repTarget)
            repeat(dropSetCount) { ws.add(dropWeight); rs.add(repTarget) }
            perSetWeights = ws
            perSetReps = rs
        } else {
            suggestedWeight = null
            perSetWeights = null
            perSetReps = null
        }

        return GeneratedExercise(
            exerciseId = exercise.id,
            exerciseName = exercise.name,
            role = ExerciseRole.MAIN_LIFT,
            order = order,
            sets = totalSets,
            reps = repTarget,
            targetRPE = dayRPE,
            targetRIR = null,
            suggestedWeight = suggestedWeight,
            dropPercentage = dropPercent,
            perSetWeights = perSetWeights,
            perSetReps = perSetReps,
            prescribedWarmupSetCount = warmupCount,
            intensityTierRaw = intensityTier.raw,
        )
    }

    // ── Frequency tier ────────────────────────────────────────────────────

    private fun frequencyTier(days: Int): Int = when {
        days <= 3 -> 0
        days == 4 -> 1
        else -> 2
    }

    // ── Goal-aware accessory count adjustment ─────────────────────────────

    private fun adjustAccessories(config: DayConfig, goal: TrainingGoal, trainingDays: Int): DayConfig {
        val adjusted = config.copy(
            compoundAccessories = config.compoundAccessories.toMutableList(),
            isolationAccessories = config.isolationAccessories.toMutableList(),
        )
        when (goal) {
            TrainingGoal.POWERLIFTING -> {
                if (trainingDays <= 4 && adjusted.isolationAccessories.isNotEmpty()) {
                    adjusted.isolationAccessories.removeLast()
                }
            }
            TrainingGoal.HYPERTROPHY -> {
                if (trainingDays >= 4) {
                    val extra = when (config.focus) {
                        WorkoutFocus.LOWER_BODY, WorkoutFocus.LEG_DAY,
                        WorkoutFocus.HINGE_FOCUS, WorkoutFocus.SQUAT_FOCUS ->
                            MovementPattern.ISOLATION_LEGS
                        WorkoutFocus.UPPER_BODY, WorkoutFocus.PUSH_DAY, WorkoutFocus.PULL_DAY,
                        WorkoutFocus.UPPER_PUSH, WorkoutFocus.UPPER_PULL ->
                            MovementPattern.ISOLATION_ARMS
                        else -> MovementPattern.CORE
                    }
                    adjusted.isolationAccessories.add(extra)
                }
            }
            TrainingGoal.STRENGTH, TrainingGoal.GENERAL_FITNESS -> { /* no change */ }
        }
        return adjusted
    }

    // ── Accessory prescription (phase-based) ──────────────────────────────

    fun accessoryPrescription(
        phase: BlockPhase,
        subPhase: SubPhase,
        isCompound: Boolean,
        goal: TrainingGoal = TrainingGoal.POWERLIFTING,
        trainingDays: Int = 4,
    ): AccessoryPrescription {
        val tier = frequencyTier(trainingDays)

        if (subPhase == SubPhase.DELOAD)
            return AccessoryPrescription(sets = 2, repsRange = 10..12, rir = 4)
        if (subPhase == SubPhase.TRANSITIONAL)
            return if (isCompound)
                AccessoryPrescription(sets = 3, repsRange = 10..15, rir = 3)
            else
                AccessoryPrescription(sets = 3, repsRange = 12..20, rir = 3)

        val base = when (phase) {
            BlockPhase.HYPERTROPHY ->
                if (isCompound) AccessoryPrescription(sets = listOf(4, 3, 3)[tier], repsRange = 8..12, rir = 2)
                else AccessoryPrescription(sets = listOf(4, 3, 3)[tier], repsRange = 12..15, rir = 2)
            BlockPhase.STRENGTH ->
                if (isCompound) AccessoryPrescription(sets = listOf(3, 3, 2)[tier], repsRange = 6..8, rir = 3)
                else AccessoryPrescription(sets = listOf(3, 3, 2)[tier], repsRange = 10..12, rir = 3)
            BlockPhase.PEAKING, BlockPhase.MEET_PREP ->
                if (isCompound) AccessoryPrescription(sets = listOf(2, 2, 2)[tier], repsRange = 5..8, rir = 3)
                else AccessoryPrescription(sets = 2, repsRange = 8..10, rir = 3)
            BlockPhase.BRIDGE ->
                if (isCompound) AccessoryPrescription(sets = listOf(4, 3, 3)[tier], repsRange = 10..15, rir = 3)
                else AccessoryPrescription(sets = listOf(4, 3, 3)[tier], repsRange = 12..20, rir = 3)
        }

        return when (goal) {
            TrainingGoal.HYPERTROPHY -> when (phase) {
                BlockPhase.STRENGTH ->
                    if (isCompound) AccessoryPrescription(sets = base.sets, repsRange = 8..12, rir = 2)
                    else AccessoryPrescription(sets = base.sets, repsRange = 12..15, rir = 2)
                BlockPhase.PEAKING, BlockPhase.MEET_PREP ->
                    if (isCompound) AccessoryPrescription(sets = listOf(3, 2, 2)[tier], repsRange = 10..12, rir = 2)
                    else AccessoryPrescription(sets = listOf(3, 2, 2)[tier], repsRange = 12..15, rir = 2)
                else -> base
            }
            TrainingGoal.GENERAL_FITNESS ->
                if (isCompound) AccessoryPrescription(sets = base.sets, repsRange = 10..15, rir = 2)
                else AccessoryPrescription(sets = base.sets, repsRange = 15..20, rir = 2)
            else -> base
        }
    }

    private fun clampedReps(prescription: AccessoryPrescription, exercise: ExerciseData): Int {
        val waveLower = prescription.repsRange.first
        return max(exercise.minReps, min(waveLower, exercise.maxReps))
    }

    private fun calculateAccessoryWeight(exercise: ExerciseData, reps: Int, rir: Int): Double? =
        OneRepMaxCalculator.suggestWeight(exercise, reps, rir)

    // ── Lift category for movement pattern ───────────────────────────────

    private fun liftCategoryFor(pattern: MovementPattern): LiftCategory? = when (pattern) {
        MovementPattern.SQUAT -> LiftCategory.SQUAT
        MovementPattern.HORIZONTAL_PUSH -> LiftCategory.BENCH
        MovementPattern.HINGE -> LiftCategory.DEADLIFT
        else -> null
    }

    // ── isCompLike helper ─────────────────────────────────────────────────

    private fun isCompLike(
        exercise: ExerciseData,
        stanceCompName: String?,
        stanceNames: Set<String>,
        stickingPoint: StickingPoint?,
    ): Boolean {
        if (exercise.name.startsWith("Comp") || exercise.name == stanceCompName) return true
        if (stanceNames.contains(exercise.name)) {
            if (stickingPoint != null && exercise.addressesStickingPoints.contains(stickingPoint)) return false
            return true
        }
        return false
    }

    // ── Exercise selection (with rotation) ────────────────────────────────

    private fun selectExercise(
        pattern: MovementPattern,
        role: ExerciseRole,
        exercises: List<ExerciseData>,
        excluding: List<String> = emptyList(),
        isVariationDay: Boolean = false,
        phase: BlockPhase? = null,
        profile: UserProfileData? = null,
    ): ExerciseData? {
        val candidates = exercises.filter { ex ->
            ex.movementPattern == pattern &&
                !excluding.contains(ex.name) &&
                (role != ExerciseRole.MAIN_LIFT || ex.isBarbell)
        }

        val previouslyUsed = usedExercisesPerPattern[pattern.raw] ?: emptySet()
        val goal = profile?.goal ?: TrainingGoal.POWERLIFTING
        val userStickingPoint: StickingPoint? = null   // wired in Phase 4
        val stanceCompName = profile?.deadliftStance?.compEquivalentName
        val stanceNames: Set<String> = setOf("Conventional Deadlift", "Sumo Deadlift")

        return candidates.sortedWith(Comparator { a, b ->
            if (role == ExerciseRole.MAIN_LIFT) {
                if (isVariationDay) {
                    val aIsCompLike = isCompLike(a, stanceCompName, stanceNames, userStickingPoint)
                    val bIsCompLike = isCompLike(b, stanceCompName, stanceNames, userStickingPoint)
                    if (aIsCompLike != bIsCompLike) return@Comparator if (!aIsCompLike) -1 else 1

                    if (userStickingPoint != null) {
                        val aTargets = a.addressesStickingPoints.contains(userStickingPoint)
                        val bTargets = b.addressesStickingPoints.contains(userStickingPoint)
                        if (aTargets != bTargets) return@Comparator if (aTargets) -1 else 1
                    }

                    val aRank = preferredVariationRank(a.name, phase, goal)
                    val bRank = preferredVariationRank(b.name, phase, goal)
                    if (aRank != bRank) return@Comparator aRank - bRank
                } else {
                    val aIsComp = a.name.startsWith("Comp") || a.name == stanceCompName
                    val bIsComp = b.name.startsWith("Comp") || b.name == stanceCompName
                    if (aIsComp != bIsComp) return@Comparator if (aIsComp) -1 else 1
                }
            }

            val aHasHistory = a.estimatedOneRepMax != null
            val bHasHistory = b.estimatedOneRepMax != null
            if (aHasHistory != bHasHistory) return@Comparator if (aHasHistory) -1 else 1

            if (role != ExerciseRole.MAIN_LIFT) {
                val aUsedBefore = previouslyUsed.contains(a.name)
                val bUsedBefore = previouslyUsed.contains(b.name)
                if (aUsedBefore != bUsedBefore) return@Comparator if (!aUsedBefore) -1 else 1

                if (userStickingPoint != null) {
                    val aTargets = a.addressesStickingPoints.contains(userStickingPoint)
                    val bTargets = b.addressesStickingPoints.contains(userStickingPoint)
                    if (aTargets != bTargets) return@Comparator if (aTargets) -1 else 1
                }
            }

            a.name.compareTo(b.name)
        }).firstOrNull()
    }

    // ── Workout distribution ──────────────────────────────────────────────

    fun workoutDistribution(days: Int): List<DayConfig> = when (days) {
        2 -> twoDay()
        3 -> threeDay()
        4 -> fourDay()
        5 -> fiveDay()
        6 -> sixDay()
        else -> threeDay()
    }

    private fun twoDay() = listOf(
        DayConfig("Day A - Full Body", WorkoutFocus.FULL_BODY, MovementPattern.SQUAT,
            mutableListOf(MovementPattern.HORIZONTAL_PUSH, MovementPattern.HORIZONTAL_PULL),
            mutableListOf(MovementPattern.ISOLATION_ARMS, MovementPattern.CORE),
            isVariationDay = true),
        DayConfig("Day B - Full Body", WorkoutFocus.FULL_BODY, MovementPattern.HINGE,
            mutableListOf(MovementPattern.VERTICAL_PUSH, MovementPattern.VERTICAL_PULL),
            mutableListOf(MovementPattern.ISOLATION_ARMS, MovementPattern.ISOLATION_LEGS),
            isVariationDay = false),
    )

    private fun threeDay() = listOf(
        DayConfig("Day A", WorkoutFocus.FULL_BODY, MovementPattern.SQUAT,
            mutableListOf(MovementPattern.HORIZONTAL_PUSH, MovementPattern.HORIZONTAL_PULL),
            mutableListOf(MovementPattern.ISOLATION_ARMS, MovementPattern.CORE),
            isVariationDay = true),
        DayConfig("Day B", WorkoutFocus.FULL_BODY, MovementPattern.HINGE,
            mutableListOf(MovementPattern.VERTICAL_PUSH, MovementPattern.VERTICAL_PULL),
            mutableListOf(MovementPattern.ISOLATION_ARMS, MovementPattern.ISOLATION_LEGS),
            isVariationDay = true),
        DayConfig("Day C", WorkoutFocus.FULL_BODY, MovementPattern.HINGE,
            mutableListOf(MovementPattern.HORIZONTAL_PUSH, MovementPattern.HORIZONTAL_PULL),
            mutableListOf(MovementPattern.HORIZONTAL_PULL, MovementPattern.CORE),
            isVariationDay = false),
    )

    private fun fourDay() = listOf(
        DayConfig("Lower A", WorkoutFocus.LOWER_BODY, MovementPattern.SQUAT,
            mutableListOf(MovementPattern.HINGE),
            mutableListOf(MovementPattern.ISOLATION_LEGS, MovementPattern.CORE),
            isVariationDay = true),
        DayConfig("Upper A", WorkoutFocus.UPPER_BODY, MovementPattern.HORIZONTAL_PUSH,
            mutableListOf(MovementPattern.HORIZONTAL_PULL, MovementPattern.VERTICAL_PULL),
            mutableListOf(MovementPattern.ISOLATION_ARMS, MovementPattern.ISOLATION_ARMS),
            isVariationDay = true),
        DayConfig("Lower B", WorkoutFocus.LOWER_BODY, MovementPattern.HINGE,
            mutableListOf(MovementPattern.SQUAT),
            mutableListOf(MovementPattern.ISOLATION_LEGS, MovementPattern.ISOLATION_LEGS),
            isVariationDay = false),
        DayConfig("Upper B", WorkoutFocus.UPPER_BODY, MovementPattern.VERTICAL_PUSH,
            mutableListOf(MovementPattern.HORIZONTAL_PUSH, MovementPattern.HORIZONTAL_PULL),
            mutableListOf(MovementPattern.ISOLATION_ARMS, MovementPattern.ISOLATION_ARMS),
            isVariationDay = false),
    )

    private fun fiveDay() = listOf(
        DayConfig("Lower A", WorkoutFocus.LOWER_BODY, MovementPattern.SQUAT,
            mutableListOf(MovementPattern.HINGE),
            mutableListOf(MovementPattern.ISOLATION_LEGS, MovementPattern.CORE),
            isVariationDay = true),
        DayConfig("Upper A", WorkoutFocus.UPPER_BODY, MovementPattern.HORIZONTAL_PUSH,
            mutableListOf(MovementPattern.HORIZONTAL_PULL),
            mutableListOf(MovementPattern.ISOLATION_ARMS),
            isVariationDay = true),
        DayConfig("Push", WorkoutFocus.PUSH_DAY, MovementPattern.VERTICAL_PUSH,
            mutableListOf(MovementPattern.HORIZONTAL_PUSH),
            mutableListOf(MovementPattern.ISOLATION_ARMS),
            isVariationDay = true),
        DayConfig("Pull", WorkoutFocus.PULL_DAY, MovementPattern.HORIZONTAL_PULL,
            mutableListOf(MovementPattern.VERTICAL_PULL),
            mutableListOf(MovementPattern.ISOLATION_ARMS, MovementPattern.ISOLATION_ARMS),
            isVariationDay = false),
        DayConfig("Legs", WorkoutFocus.LEG_DAY, MovementPattern.HINGE,
            mutableListOf(MovementPattern.SQUAT),
            mutableListOf(MovementPattern.ISOLATION_LEGS, MovementPattern.ISOLATION_LEGS, MovementPattern.CORE),
            isVariationDay = false),
    )

    private fun sixDay() = listOf(
        DayConfig("Lower A", WorkoutFocus.LOWER_BODY, MovementPattern.SQUAT,
            mutableListOf(MovementPattern.HINGE),
            mutableListOf(MovementPattern.ISOLATION_LEGS),
            isVariationDay = true),
        DayConfig("Push A", WorkoutFocus.PUSH_DAY, MovementPattern.HORIZONTAL_PUSH,
            mutableListOf(MovementPattern.VERTICAL_PUSH),
            mutableListOf(MovementPattern.ISOLATION_ARMS),
            isVariationDay = true),
        DayConfig("Pull A", WorkoutFocus.PULL_DAY, MovementPattern.HORIZONTAL_PULL,
            mutableListOf(MovementPattern.VERTICAL_PULL),
            mutableListOf(MovementPattern.ISOLATION_ARMS),
            isVariationDay = true),
        DayConfig("Lower B", WorkoutFocus.LOWER_BODY, MovementPattern.HINGE,
            mutableListOf(MovementPattern.SQUAT),
            mutableListOf(MovementPattern.ISOLATION_LEGS, MovementPattern.CORE),
            isVariationDay = false),
        DayConfig("Push B", WorkoutFocus.PUSH_DAY, MovementPattern.VERTICAL_PUSH,
            mutableListOf(MovementPattern.HORIZONTAL_PUSH),
            mutableListOf(MovementPattern.ISOLATION_ARMS),
            isVariationDay = false),
        DayConfig("Pull B", WorkoutFocus.PULL_DAY, MovementPattern.VERTICAL_PULL,
            mutableListOf(MovementPattern.HORIZONTAL_PULL),
            mutableListOf(MovementPattern.ISOLATION_ARMS),
            isVariationDay = false),
    )

    // ── Meet prep workout generation ──────────────────────────────────────

    private fun generateMeetPrepWorkouts(
        subPhase: SubPhase,
        exercises: List<ExerciseData>,
        profile: UserProfileData?,
    ): List<GeneratedWorkout> = when (subPhase) {
        SubPhase.OPENERS -> generateOpenersWeek(exercises, profile)
        SubPhase.TAPER -> generateMeetWeek(exercises, profile)
        else -> emptyList()
    }

    private fun generateOpenersWeek(
        exercises: List<ExerciseData>,
        profile: UserProfileData?,
    ): List<GeneratedWorkout> {
        val compSquat = selectExercise(MovementPattern.SQUAT, ExerciseRole.MAIN_LIFT, exercises, isVariationDay = false, profile = profile)
        val compBench = selectExercise(MovementPattern.HORIZONTAL_PUSH, ExerciseRole.MAIN_LIFT, exercises, isVariationDay = false, profile = profile)
        val compDeadlift = selectExercise(MovementPattern.HINGE, ExerciseRole.MAIN_LIFT, exercises, isVariationDay = false, profile = profile)

        val w1Exercises = mutableListOf<GeneratedExercise>()
        compSquat?.let { w1Exercises.add(createOpenerRamp(it, 0)) }
        compBench?.let { w1Exercises.add(createTechniqueSets(it, 1)) }

        val w2Exercises = mutableListOf<GeneratedExercise>()
        compBench?.let { w2Exercises.add(createOpenerRamp(it, 0)) }
        compSquat?.let { w2Exercises.add(createTechniqueSets(it, 1)) }

        val w3Exercises = mutableListOf<GeneratedExercise>()
        compDeadlift?.let { w3Exercises.add(createOpenerRamp(it, 0)) }
        compBench?.let { w3Exercises.add(createTechniqueSets(it, 1)) }

        return listOf(
            GeneratedWorkout(1, "Squat Openers", WorkoutFocus.LOWER_BODY, null, w1Exercises),
            GeneratedWorkout(2, "Bench Openers", WorkoutFocus.UPPER_BODY, null, w2Exercises),
            GeneratedWorkout(3, "Deadlift Openers", WorkoutFocus.LOWER_BODY, null, w3Exercises),
        )
    }

    private fun createOpenerRamp(exercise: ExerciseData, order: Int): GeneratedExercise {
        val rampSteps = listOf(0.50 to 5, 0.60 to 3, 0.70 to 3, 0.80 to 2, 0.85 to 1, 0.90 to 1)
        val wm = effectiveWorkingMax(exercise)
        val roundTo = if (exercise.isBarbell) 5.0 else 2.5
        val perSetWeights = wm?.let { wmVal -> rampSteps.map { (pct, _) -> (wmVal * pct).roundedToNearest(roundTo) } }
        val perSetReps = rampSteps.map { (_, r) -> r }
        return GeneratedExercise(
            exerciseId = exercise.id,
            exerciseName = exercise.name,
            role = ExerciseRole.MAIN_LIFT,
            order = order,
            sets = rampSteps.size,
            reps = 1,
            targetRPE = 9.0,
            targetRIR = null,
            suggestedWeight = wm?.let { (it * 0.90).roundedToNearest(roundTo) },
            dropPercentage = null,
            perSetWeights = perSetWeights,
            perSetReps = perSetReps,
            prescribedWarmupSetCount = 4,
            intensityTierRaw = null,
        )
    }

    private fun createTechniqueSets(exercise: ExerciseData, order: Int): GeneratedExercise {
        val wm = effectiveWorkingMax(exercise)
        val roundTo = if (exercise.isBarbell) 5.0 else 2.5
        val weight = wm?.let { (it * 0.60).roundedToNearest(roundTo) }
        return GeneratedExercise(
            exerciseId = exercise.id,
            exerciseName = exercise.name,
            role = ExerciseRole.COMPOUND_ACCESSORY,
            order = order,
            sets = 3,
            reps = 3,
            targetRPE = 6.0,
            targetRIR = null,
            suggestedWeight = weight,
            dropPercentage = null,
            perSetWeights = weight?.let { w -> List(3) { w } },
            perSetReps = listOf(3, 3, 3),
            intensityTierRaw = null,
        )
    }

    private fun generateMeetWeek(
        exercises: List<ExerciseData>,
        profile: UserProfileData?,
    ): List<GeneratedWorkout> {
        val compSquat = selectExercise(MovementPattern.SQUAT, ExerciseRole.MAIN_LIFT, exercises, isVariationDay = false, profile = profile)
        val compBench = selectExercise(MovementPattern.HORIZONTAL_PUSH, ExerciseRole.MAIN_LIFT, exercises, isVariationDay = false, profile = profile)
        val compDeadlift = selectExercise(MovementPattern.HINGE, ExerciseRole.MAIN_LIFT, exercises, isVariationDay = false, profile = profile)

        val w1Exercises = mutableListOf<GeneratedExercise>()
        var order = 0
        for (exercise in listOfNotNull(compSquat, compBench)) {
            val wm = effectiveWorkingMax(exercise)
            val roundTo = if (exercise.isBarbell) 5.0 else 2.5
            val weight = wm?.let { (it * 0.50).roundedToNearest(roundTo) }
            w1Exercises.add(GeneratedExercise(
                exerciseId = exercise.id,
                exerciseName = exercise.name,
                role = ExerciseRole.MAIN_LIFT,
                order = order,
                sets = 2,
                reps = 2,
                targetRPE = 5.0,
                targetRIR = null,
                suggestedWeight = weight,
                dropPercentage = null,
                perSetWeights = weight?.let { w -> listOf(w, w) },
                perSetReps = listOf(2, 2),
                intensityTierRaw = null,
            ))
            order++
        }

        val w2Exercises = mutableListOf<GeneratedExercise>()
        order = 0
        for (exercise in listOfNotNull(compSquat, compBench, compDeadlift)) {
            w2Exercises.add(createMeetDayAttempts(exercise, order))
            order++
        }

        return listOf(
            GeneratedWorkout(1, "Technique", WorkoutFocus.FULL_BODY, null, w1Exercises),
            GeneratedWorkout(2, "Meet Day", WorkoutFocus.FULL_BODY, null, w2Exercises),
        )
    }

    private fun createMeetDayAttempts(exercise: ExerciseData, order: Int): GeneratedExercise {
        val rampSteps = listOf(0.50 to 5, 0.60 to 3, 0.70 to 2, 0.80 to 1, 0.90 to 1, 0.95 to 1, 1.00 to 1)
        val wm = effectiveWorkingMax(exercise)
        val roundTo = if (exercise.isBarbell) 5.0 else 2.5
        val perSetWeights = wm?.let { wmVal -> rampSteps.map { (pct, _) -> (wmVal * pct).roundedToNearest(roundTo) } }
        val perSetReps = rampSteps.map { (_, r) -> r }
        return GeneratedExercise(
            exerciseId = exercise.id,
            exerciseName = exercise.name,
            role = ExerciseRole.MAIN_LIFT,
            order = order,
            sets = rampSteps.size,
            reps = 1,
            targetRPE = 10.0,
            targetRIR = null,
            suggestedWeight = wm?.let { (it * 1.00).roundedToNearest(roundTo) },
            dropPercentage = null,
            perSetWeights = perSetWeights,
            perSetReps = perSetReps,
            prescribedWarmupSetCount = 4,
            intensityTierRaw = null,
        )
    }
}
