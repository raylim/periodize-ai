package com.periodizeai.app.utils

object Constants {
    object Progression {
        const val squatDeadliftIncrement: Double = 5.0
        const val benchOHPIncrement: Double = 2.5
        const val maxExtraReps: Int = 10
        const val barbellAccessoryIncrement: Double = 5.0
        const val dumbbellAccessoryIncrement: Double = 2.5
        const val e1RMOldWeight: Double = 0.7
        const val e1RMNewWeight: Double = 0.3
    }

    object Volume {
        const val mev: Int = 6
        const val mavLow: Int = 10
        const val mavHigh: Int = 16
        const val mrv: Int = 22
    }

    object RestTimer {
        const val mainLift: Int = 180
        const val compoundAccessory: Int = 120
        const val isolation: Int = 90
    }

    object WarmupRamp {
        const val barWeight: Double = 45.0
        const val barWeightKg: Double = 20.0
    }
}
