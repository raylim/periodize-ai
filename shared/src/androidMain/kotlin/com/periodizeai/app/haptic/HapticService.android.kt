package com.periodizeai.app.utils

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

actual object HapticService {

    private var vibrator: Vibrator? = null

    fun init(context: Context) {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager)
                .defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    actual fun lightImpact()  = vibrate(VibrationEffect.EFFECT_TICK)
    actual fun mediumImpact() = vibrate(VibrationEffect.EFFECT_CLICK)
    actual fun heavyImpact()  = vibrate(VibrationEffect.EFFECT_HEAVY_CLICK)
    actual fun setComplete()  = vibrate(VibrationEffect.EFFECT_CLICK)
    actual fun workoutFinished() {
        val pattern = longArrayOf(0, 80, 60, 80, 60, 120)
        vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
    }
    actual fun success() = vibrate(VibrationEffect.EFFECT_DOUBLE_CLICK)

    private fun vibrate(effectId: Int) {
        vibrator?.vibrate(VibrationEffect.createPredefined(effectId))
    }
}
