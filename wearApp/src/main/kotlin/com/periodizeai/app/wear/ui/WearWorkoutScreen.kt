package com.periodizeai.app.wear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.ScalingLazyColumn
import androidx.wear.compose.material.Text
import com.periodizeai.app.sync.WatchWorkoutPayload

data class SetEntry(
    val weight: Double,
    val reps: Int,
    val isCompleted: Boolean = false,
)

@Composable
fun WearWorkoutScreen(
    workout: WatchWorkoutPayload,
    onRestTimer: () -> Unit,
    onFinish: () -> Unit,
) {
    val setStates = remember {
        workout.exercises.associate { ex ->
            ex.id to mutableStateOf(
                (0 until ex.workingSets).map { SetEntry(weight = ex.suggestedWeight, reps = ex.targetReps) }
            )
        }
    }

    ScalingLazyColumn(
        modifier = Modifier.padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        workout.exercises.forEach { exercise ->
            item {
                Text(
                    text = exercise.name,
                    style = MaterialTheme.typography.caption1,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp),
                )
            }
            item {
                Text(
                    text = exercise.prescription,
                    style = MaterialTheme.typography.caption2,
                    color = MaterialTheme.colors.secondary,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            val state = setStates[exercise.id] ?: return@forEach
            state.value.forEachIndexed { idx, set ->
                item {
                    SetRow(
                        setNumber = idx + 1,
                        entry = set,
                        onWeightUp = {
                            val list = state.value.toMutableList()
                            list[idx] = list[idx].copy(weight = list[idx].weight + 2.5)
                            state.value = list
                        },
                        onWeightDown = {
                            val list = state.value.toMutableList()
                            list[idx] = list[idx].copy(weight = (list[idx].weight - 2.5).coerceAtLeast(0.0))
                            state.value = list
                        },
                        onRepsUp = {
                            val list = state.value.toMutableList()
                            list[idx] = list[idx].copy(reps = list[idx].reps + 1)
                            state.value = list
                        },
                        onRepsDown = {
                            val list = state.value.toMutableList()
                            list[idx] = list[idx].copy(reps = (list[idx].reps - 1).coerceAtLeast(1))
                            state.value = list
                        },
                        onComplete = {
                            val list = state.value.toMutableList()
                            list[idx] = list[idx].copy(isCompleted = true)
                            state.value = list
                            onRestTimer()
                        },
                    )
                }
            }
        }
        item { Spacer(modifier = Modifier.height(8.dp)) }
        item {
            Button(
                onClick = onFinish,
                modifier = Modifier.fillMaxWidth(0.8f),
                colors = ButtonDefaults.secondaryButtonColors(),
            ) {
                Text("Finish", style = MaterialTheme.typography.button)
            }
        }
    }
}

@Composable
private fun SetRow(
    setNumber: Int,
    entry: SetEntry,
    onWeightUp: () -> Unit,
    onWeightDown: () -> Unit,
    onRepsUp: () -> Unit,
    onRepsDown: () -> Unit,
    onComplete: () -> Unit,
) {
    if (entry.isCompleted) {
        Chip(
            modifier = Modifier.fillMaxWidth(),
            onClick = {},
            enabled = false,
            label = { Text("Set $setNumber  ✓  ${entry.weight.toInt()} × ${entry.reps}", style = MaterialTheme.typography.caption2) },
            colors = ChipDefaults.chipColors(backgroundColor = MaterialTheme.colors.surface),
        )
    } else {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Set $setNumber", style = MaterialTheme.typography.caption2, color = MaterialTheme.colors.secondary)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(onClick = onWeightDown, modifier = Modifier.size(32.dp), colors = ButtonDefaults.secondaryButtonColors()) {
                    Text("−", style = MaterialTheme.typography.button)
                }
                Text("${entry.weight.toInt()} kg", style = MaterialTheme.typography.caption1)
                Button(onClick = onWeightUp, modifier = Modifier.size(32.dp), colors = ButtonDefaults.secondaryButtonColors()) {
                    Text("+", style = MaterialTheme.typography.button)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(onClick = onRepsDown, modifier = Modifier.size(32.dp), colors = ButtonDefaults.secondaryButtonColors()) {
                    Text("−", style = MaterialTheme.typography.button)
                }
                Text("${entry.reps} reps", style = MaterialTheme.typography.caption1)
                Button(onClick = onRepsUp, modifier = Modifier.size(32.dp), colors = ButtonDefaults.secondaryButtonColors()) {
                    Text("+", style = MaterialTheme.typography.button)
                }
            }
            Button(
                onClick = onComplete,
                modifier = Modifier.fillMaxWidth(0.7f),
                colors = ButtonDefaults.primaryButtonColors(),
            ) {
                Text("Done ✓", style = MaterialTheme.typography.button)
            }
        }
    }
}
