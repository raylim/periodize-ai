package com.periodizeai.app.sync

import kotlinx.serialization.Serializable

const val WEAR_DATA_PATH = "/current_workout"
const val WEAR_PAYLOAD_KEY = "currentWorkout"

@Serializable
data class WatchWorkoutPayload(
    val workoutId: String,
    val dayLabel: String,
    val focus: String,
    val phaseName: String,
    val exercises: List<WatchExercisePayload>,
)

@Serializable
data class WatchExercisePayload(
    val id: String,
    val name: String,
    val prescription: String,
    val suggestedWeight: Double,
    val targetReps: Int,
    val workingSets: Int,
    val isAMRAP: Boolean,
)
