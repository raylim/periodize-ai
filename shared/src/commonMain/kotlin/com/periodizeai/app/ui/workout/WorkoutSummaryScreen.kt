package com.periodizeai.app.ui.workout

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.periodizeai.app.viewmodels.WorkoutExecutionViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutSummaryScreen(
    state: WorkoutExecutionViewModel.UiState,
    onDone: () -> Unit,
) {
    val durationMs = state.startTimeMs.let { com.periodizeai.app.utils.nowEpochMs() - it }
    val durationMin = durationMs / 60_000L

    val totalVolume = state.exerciseEntries.sumOf { entry ->
        entry.sets.filter { it.isCompleted }.sumOf { set ->
            (set.completedWeight ?: set.targetWeight) * (set.completedReps ?: set.targetReps).toDouble()
        }
    }

    val completedSets = state.completedSetCount
    val totalSets = state.totalSetCount

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Workout Complete! 🎉") },
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Summary stats
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        SummaryStatItem(label = "Duration", value = "${durationMin}m")
                        SummaryStatItem(label = "Sets", value = "$completedSets/$totalSets")
                        SummaryStatItem(
                            label = "Volume",
                            value = "${totalVolume.toInt()} lb"
                        )
                    }
                }
            }

            // Exercise breakdown
            item {
                Text(
                    "Exercise Summary",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            items(state.exerciseEntries, key = { it.id }) { entry ->
                val completedExerciseSets = entry.sets.filter { it.isCompleted }
                if (completedExerciseSets.isEmpty()) return@items

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = entry.exercise.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )

                        completedExerciseSets.forEachIndexed { idx, set ->
                            val weight = set.completedWeight ?: set.targetWeight
                            val reps = set.completedReps ?: set.targetReps
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "Set ${idx + 1}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "${weight.toInt()} lb × $reps",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                set.completedRpe?.let { rpe ->
                                    Text(
                                        "RPE ${rpe.toInt()}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Done button
            item {
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onDone,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Done")
                }
            }
        }
    }
}

@Composable
private fun SummaryStatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
