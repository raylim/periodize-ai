package com.periodizeai.app.wear.data

import android.content.Context
import com.periodizeai.app.sync.WatchWorkoutPayload
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

object WearDataStore {
    private const val PREFS_KEY = "watchCurrentWorkoutPayload"
    private const val PREFS_FILE = "wear_prefs"

    private val _currentWorkout = MutableStateFlow<WatchWorkoutPayload?>(null)
    val currentWorkout: StateFlow<WatchWorkoutPayload?> = _currentWorkout.asStateFlow()

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
        val json = prefs.getString(PREFS_KEY, null) ?: return
        _currentWorkout.value = try {
            Json.decodeFromString(WatchWorkoutPayload.serializer(), json)
        } catch (_: Exception) { null }
    }

    fun update(context: Context, payload: WatchWorkoutPayload?) {
        _currentWorkout.value = payload
        val prefs = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
        if (payload != null) {
            prefs.edit().putString(PREFS_KEY, Json.encodeToString(WatchWorkoutPayload.serializer(), payload)).apply()
        } else {
            prefs.edit().remove(PREFS_KEY).apply()
        }
    }
}
