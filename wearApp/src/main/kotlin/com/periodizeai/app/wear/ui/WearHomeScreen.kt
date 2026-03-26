package com.periodizeai.app.wear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.ScalingLazyColumn
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.items
import com.periodizeai.app.sync.WatchWorkoutPayload

@Composable
fun WearHomeScreen(
    workout: WatchWorkoutPayload?,
    onStartWorkout: () -> Unit,
) {
    if (workout == null) {
        NotSyncedScreen()
    } else {
        WorkoutPreviewScreen(workout = workout, onStart = onStartWorkout)
    }
}

@Composable
private fun WorkoutPreviewScreen(
    workout: WatchWorkoutPayload,
    onStart: () -> Unit,
) {
    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            Text(
                text = workout.phaseName,
                style = MaterialTheme.typography.caption2,
                color = MaterialTheme.colors.secondary,
            )
        }
        item {
            Text(
                text = workout.dayLabel,
                style = MaterialTheme.typography.title3,
                textAlign = TextAlign.Center,
            )
        }
        item {
            Text(
                text = workout.focus,
                style = MaterialTheme.typography.caption1,
                color = MaterialTheme.colors.secondary,
            )
        }
        item { Spacer(modifier = Modifier.height(4.dp)) }
        items(workout.exercises.take(6)) { exercise ->
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                Text(
                    text = exercise.name,
                    style = MaterialTheme.typography.caption2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = exercise.prescription,
                    style = MaterialTheme.typography.caption2,
                    color = MaterialTheme.colors.secondary,
                )
            }
        }
        item { Spacer(modifier = Modifier.height(8.dp)) }
        item {
            Button(
                onClick = onStart,
                modifier = Modifier.fillMaxWidth(0.8f),
                colors = ButtonDefaults.primaryButtonColors(),
            ) {
                Text("Start", style = MaterialTheme.typography.button)
            }
        }
    }
}

@Composable
private fun NotSyncedScreen() {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Not Synced",
            style = MaterialTheme.typography.title3,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Open PeriodizeAI on your phone to sync.",
            style = MaterialTheme.typography.caption2,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colors.secondary,
        )
    }
}
