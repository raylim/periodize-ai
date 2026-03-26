package com.periodizeai.app.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

actual object WidgetRefreshService {
    internal var applicationContext: Context? = null

    fun init(context: Context) {
        applicationContext = context.applicationContext
    }

    actual fun refresh() {
        val ctx = applicationContext ?: return
        CoroutineScope(Dispatchers.IO).launch {
            GlanceAppWidgetManager(ctx).getGlanceIds(WorkoutWidget::class.java).forEach { id ->
                WorkoutWidget().update(ctx, id)
            }
        }
    }
}
