package com.periodizeai.app.utils

/** Equivalent of Swift's HapticService — platform-specific implementations via expect/actual */
expect object HapticService {
    fun lightImpact()
    fun mediumImpact()
    fun heavyImpact()
    /** Played when a set is logged as complete */
    fun setComplete()
    /** Played at the end of a full workout */
    fun workoutFinished()
    /** Success notification pattern */
    fun success()
}
