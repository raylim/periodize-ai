package com.periodizeai.app.utils

import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle
import platform.UIKit.UINotificationFeedbackGenerator
import platform.UIKit.UINotificationFeedbackType

actual object HapticService {

    actual fun lightImpact() =
        UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleLight).impactOccurred()

    actual fun mediumImpact() =
        UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleMedium).impactOccurred()

    actual fun heavyImpact() =
        UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleHeavy).impactOccurred()

    actual fun setComplete()      = mediumImpact()
    actual fun workoutFinished()  = success()
    actual fun success()          =
        UINotificationFeedbackGenerator().notificationOccurred(UINotificationFeedbackType.UINotificationFeedbackTypeSuccess)
}
