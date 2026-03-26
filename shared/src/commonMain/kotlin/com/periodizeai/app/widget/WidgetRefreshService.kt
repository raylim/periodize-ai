package com.periodizeai.app.widget

/**
 * Platform bridge: triggers a home screen widget refresh after workout completion.
 * Android actual: calls GlanceAppWidgetManager to re-render the Glance widget.
 * iOS actual: posts an NSNotification for the Swift layer to call WidgetCenter.reloadAllTimelines().
 */
expect object WidgetRefreshService {
    fun refresh()
}
