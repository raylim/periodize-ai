package com.periodizeai.app.models

// ── Weight & Units ────────────────────────────────────────────────────────

enum class WeightUnit(val raw: String) {
    LB("lb"), KG("kg");
    companion object { fun from(raw: String) = entries.first { it.raw == raw } ?: LB }
}

// ── User Profile enums ────────────────────────────────────────────────────

enum class TrainingGoal(val raw: String) {
    STRENGTH("Strength"),
    HYPERTROPHY("Hypertrophy"),
    POWERLIFTING("Powerlifting"),
    GENERAL_FITNESS("General Fitness");
    companion object { fun from(raw: String) = entries.first { it.raw == raw } ?: POWERLIFTING }
}

enum class Sex(val raw: String) {
    MALE("Male"), FEMALE("Female");
    companion object { fun from(raw: String) = entries.first { it.raw == raw } ?: MALE }
}

enum class StrengthLevel(val raw: String) {
    BEGINNER("Beginner"),
    INTERMEDIATE("Intermediate"),
    ADVANCED("Advanced"),
    ELITE("Elite");

    val shortDescription get() = when (this) {
        BEGINNER     -> "< 2 years of consistent training"
        INTERMEDIATE -> "2–5 years of consistent training"
        ADVANCED     -> "5–10 years of serious training"
        ELITE        -> "10+ years or competitive powerlifting"
    }

    val benchmarkDescription get() = when (this) {
        BEGINNER     -> "Squat < 1.5× BW · Bench < 1.0× BW · Deadlift < 2.0× BW"
        INTERMEDIATE -> "Squat 1.5–2.0× BW · Bench 1.0–1.5× BW · Deadlift 2.0–2.5× BW"
        ADVANCED     -> "Squat 2.0–2.5× BW · Bench 1.5–2.0× BW · Deadlift 2.5–3.0× BW"
        ELITE        -> "Squat > 2.5× BW · Bench > 2.0× BW · Deadlift > 3.0× BW"
    }

    companion object {
        fun from(raw: String) = entries.first { it.raw == raw } ?: INTERMEDIATE

        fun compute(squatE1RM: Double?, benchE1RM: Double?, deadliftE1RM: Double?, bodyWeight: Double): StrengthLevel {
            if (bodyWeight <= 0) return INTERMEDIATE
            val scores = mutableListOf<Int>()
            squatE1RM?.takeIf { it > 0 }?.let { scores += scoreRatio(it / bodyWeight, listOf(1.5, 2.0, 2.5)) }
            benchE1RM?.takeIf { it > 0 }?.let { scores += scoreRatio(it / bodyWeight, listOf(1.0, 1.5, 2.0)) }
            deadliftE1RM?.takeIf { it > 0 }?.let { scores += scoreRatio(it / bodyWeight, listOf(2.0, 2.5, 3.0)) }
            if (scores.isEmpty()) return INTERMEDIATE
            return when (scores.average()) {
                in Double.MIN_VALUE..<0.5 -> BEGINNER
                in 0.5..<1.5  -> INTERMEDIATE
                in 1.5..<2.5  -> ADVANCED
                else           -> ELITE
            }
        }

        private fun scoreRatio(ratio: Double, thresholds: List<Double>) = when {
            ratio >= thresholds[2] -> 3
            ratio >= thresholds[1] -> 2
            ratio >= thresholds[0] -> 1
            else -> 0
        }
    }
}

enum class DietStatus(val raw: String) {
    DEFICIT("Caloric Deficit"),
    MAINTENANCE("Maintenance"),
    SURPLUS("Caloric Surplus");
    companion object { fun from(raw: String) = entries.first { it.raw == raw } ?: MAINTENANCE }
}

enum class RecoveryRating(val raw: String) {
    POOR("Poor"),
    BELOW_AVERAGE("Below Average"),
    AVERAGE("Average"),
    GOOD("Good"),
    EXCELLENT("Excellent");
    companion object { fun from(raw: String) = entries.first { it.raw == raw } ?: AVERAGE }
}

enum class DeadliftStance(val raw: String) {
    CONVENTIONAL("Conventional"),
    SUMO("Sumo");
    val compEquivalentName get() = when (this) {
        CONVENTIONAL -> "Conventional Deadlift"
        SUMO         -> "Sumo Deadlift"
    }
    companion object { fun from(raw: String) = entries.first { it.raw == raw } ?: CONVENTIONAL }
}

// ── Exercise enums ────────────────────────────────────────────────────────

enum class MuscleGroup(val raw: String) {
    QUADS("Quads"), HAMSTRINGS("Hamstrings"), GLUTES("Glutes"),
    CHEST("Chest"), UPPER_BACK("Upper Back"), LATS("Lats"),
    SHOULDERS("Shoulders"), REAR_DELTS("Rear Delts"),
    BICEPS("Biceps"), TRICEPS("Triceps"), CORE("Core"),
    CALVES("Calves"), FOREARMS("Forearms"),
    HIP_FLEXORS("Hip Flexors"), ADDUCTORS("Adductors");

    val volumeCategory get() = when (this) {
        QUADS, HAMSTRINGS, GLUTES, CHEST, UPPER_BACK, LATS -> VolumeCategory.MAJOR
        SHOULDERS, BICEPS, TRICEPS, CORE                   -> VolumeCategory.MODERATE
        else                                                -> VolumeCategory.MINOR
    }

    companion object { fun from(raw: String) = entries.firstOrNull { it.raw == raw } }
}

enum class VolumeCategory(val raw: String) {
    MAJOR("major"), MODERATE("moderate"), MINOR("minor");
    companion object { fun from(raw: String) = entries.first { it.raw == raw } ?: MAJOR }
}

enum class MovementPattern(val raw: String) {
    SQUAT("Squat"), HINGE("Hinge"),
    HORIZONTAL_PUSH("Horizontal Push"), HORIZONTAL_PULL("Horizontal Pull"),
    VERTICAL_PUSH("Vertical Push"), VERTICAL_PULL("Vertical Pull"),
    ISOLATION_ARMS("Isolation Arms"), ISOLATION_LEGS("Isolation Legs"),
    CORE("Core");
    companion object { fun from(raw: String) = entries.firstOrNull { it.raw == raw } ?: SQUAT }
}

enum class ExerciseClassification(val raw: String) {
    COMPOUND("compound"), ISOLATION("isolation");
    companion object { fun from(raw: String) = entries.first { it.raw == raw } ?: COMPOUND }
}

enum class EquipmentType(val raw: String) {
    BARBELL("Barbell"), DUMBBELL("Dumbbell"), CABLE("Cable"),
    MACHINE("Machine"), BODYWEIGHT("Bodyweight"), KETTLEBELL("Kettlebell"),
    EZ_BAR("EZ Bar"), BAND("Band"), TRAP_BAR("Trap Bar"),
    SAFETY_BAR("Safety Bar"), SMITH_MACHINE("Smith Machine"),
    AB_WHEEL("Ab Wheel"), OTHER("Other");
    companion object { fun from(raw: String) = entries.firstOrNull { it.raw == raw } ?: BARBELL }
}

enum class ExerciseRole(val raw: String) {
    MAIN_LIFT("mainLift"),
    COMPOUND_ACCESSORY("compoundAccessory"),
    ISOLATION_ACCESSORY("isolationAccessory");
    companion object { fun from(raw: String) = entries.first { it.raw == raw } ?: MAIN_LIFT }
}

// ── Periodization enums ───────────────────────────────────────────────────

enum class BlockPhase(val raw: String) {
    HYPERTROPHY("Hypertrophy"),
    STRENGTH("Strength"),
    PEAKING("Peaking"),
    MEET_PREP("Meet Prep"),
    BRIDGE("Bridge");

    val targetReps get() = when (this) {
        HYPERTROPHY -> 8; STRENGTH -> 5; PEAKING -> 3; MEET_PREP -> 1; BRIDGE -> 10
    }
    val shortName get() = when (this) {
        HYPERTROPHY -> "Hyp"; STRENGTH -> "Str"; PEAKING -> "Peak"; MEET_PREP -> "Meet"; BRIDGE -> "Brdg"
    }
    val focus get() = when (this) {
        HYPERTROPHY -> "Muscle Growth & Work Capacity"
        STRENGTH    -> "Maximal Strength Development"
        PEAKING     -> "Competition Specificity & Peaking"
        MEET_PREP   -> "Competition Peaking"
        BRIDGE      -> "Variation & Work Capacity Recovery"
    }

    companion object {
        val trainingPhases = listOf(HYPERTROPHY, STRENGTH, PEAKING)
        fun from(raw: String): BlockPhase = when (raw) {
            "10s Wave", "8s Wave" -> HYPERTROPHY
            "5s Wave"             -> STRENGTH
            "3s Wave"             -> PEAKING
            else -> entries.firstOrNull { it.raw == raw } ?: HYPERTROPHY
        }
    }
}

enum class SubPhase(val raw: String) {
    TRAINING("Training"),
    DELOAD("Deload"),
    OPENERS("Openers"),
    TAPER("Taper"),
    TRANSITIONAL("Transitional");

    val isSpecialWeek get() = this != TRAINING

    companion object {
        fun from(raw: String): SubPhase = when (raw) {
            "Accumulation", "Intensification", "Realization" -> TRAINING
            "Deload"      -> DELOAD
            "Transitional" -> TRANSITIONAL
            else -> entries.firstOrNull { it.raw == raw } ?: TRAINING
        }
    }
}

// ── Workout enums ─────────────────────────────────────────────────────────

enum class WorkoutFocus(val raw: String) {
    SQUAT_FOCUS("Squat Focus"), HINGE_FOCUS("Hinge Focus"),
    UPPER_PUSH("Upper Push"), UPPER_PULL("Upper Pull"),
    FULL_BODY("Full Body"), LOWER_BODY("Lower Body"),
    UPPER_BODY("Upper Body"), PUSH_DAY("Push"),
    PULL_DAY("Pull"), LEG_DAY("Legs");
    companion object { fun from(raw: String) = entries.firstOrNull { it.raw == raw } ?: FULL_BODY }
}

enum class IntensityTier(val raw: String) {
    HEAVY("Heavy"), MEDIUM("Medium"), LIGHT("Light");
    val rpeOffset get() = when (this) { HEAVY -> 0.5; MEDIUM -> 0.0; LIGHT -> -0.5 }
    val volumeMultiplier get() = when (this) { HEAVY -> 1.0; MEDIUM -> 0.75; LIGHT -> 0.5 }
    companion object { fun from(raw: String) = entries.firstOrNull { it.raw == raw } }
}

enum class BandType(val raw: String) {
    MICRO("Micro"), MINI("Mini"), LIGHT("Light"),
    MEDIUM("Medium"), HEAVY("Heavy"), STRONG("Strong");
    val resistance get() = when (this) {
        MICRO -> 15.0; MINI -> 30.0; LIGHT -> 50.0
        MEDIUM -> 75.0; HEAVY -> 120.0; STRONG -> 160.0
    }
    companion object { fun from(raw: String) = entries.firstOrNull { it.raw == raw } }
}

// ── Sticking points ───────────────────────────────────────────────────────

enum class LiftCategory(val raw: String) {
    SQUAT("Squat"), BENCH("Bench"), DEADLIFT("Deadlift");
    companion object { fun from(raw: String) = entries.firstOrNull { it.raw == raw } ?: SQUAT }
}

enum class StickingPoint(val raw: String) {
    // Squat
    SQUAT_OUT_OF_HOLE("Out of the Hole"),
    SQUAT_ABOVE_PARALLEL_WEAK_LEGS("Above Parallel – Weak Legs"),
    SQUAT_ABOVE_PARALLEL_WEAK_BACK("Above Parallel – Weak Back"),
    SQUAT_UPPER_BACK_ROUNDING("Upper Back Rounding"),
    SQUAT_MID_RANGE("Squat Mid-Range"),
    SQUAT_LOCKOUT("Squat Lockout"),
    // Bench
    BENCH_OFF_CHEST("Off the Chest"),
    BENCH_MID_RANGE("Bench Mid-Range"),
    BENCH_LOCKOUT("Bench Lockout"),
    // Deadlift
    DEADLIFT_OFF_FLOOR("Off the Floor"),
    DEADLIFT_BELOW_KNEE("Below the Knee"),
    DEADLIFT_LOCKOUT("Deadlift Lockout");

    val liftCategory get() = when (this) {
        SQUAT_OUT_OF_HOLE, SQUAT_ABOVE_PARALLEL_WEAK_LEGS, SQUAT_ABOVE_PARALLEL_WEAK_BACK,
        SQUAT_UPPER_BACK_ROUNDING, SQUAT_MID_RANGE, SQUAT_LOCKOUT -> LiftCategory.SQUAT
        BENCH_OFF_CHEST, BENCH_MID_RANGE, BENCH_LOCKOUT            -> LiftCategory.BENCH
        DEADLIFT_OFF_FLOOR, DEADLIFT_BELOW_KNEE, DEADLIFT_LOCKOUT  -> LiftCategory.DEADLIFT
    }

    companion object { fun from(raw: String) = entries.firstOrNull { it.raw == raw } }
}

// Aliases so repositories use descriptive names that map to Swift originals
typealias UserSex = Sex
typealias SleepQuality = RecoveryRating
typealias StressLevel = RecoveryRating
