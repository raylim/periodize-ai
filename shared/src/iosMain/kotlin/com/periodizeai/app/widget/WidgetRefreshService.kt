package com.periodizeai.app.widget

import platform.Foundation.NSNotificationCenter

actual object WidgetRefreshService {
    actual fun refresh() {
        // Post a notification for the Swift layer to observe and call
        // WidgetCenter.shared.reloadAllTimelines() — WidgetKit is not
        // accessible via ObjC headers so we bridge via NSNotificationCenter.
        NSNotificationCenter.defaultCenter.postNotificationName(
            aName = "PeriodizeAIRefreshWidget",
            `object` = null,
        )
    }
}
