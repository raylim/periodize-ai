package com.periodizeai.app.ui.workout

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.periodizeai.app.viewmodels.WorkoutExecutionViewModel

@Composable
fun ReadinessCheckDialog(
    readinessState: WorkoutExecutionViewModel.ReadinessState,
    onUpdate: (WorkoutExecutionViewModel.ReadinessState.() -> WorkoutExecutionViewModel.ReadinessState) -> Unit,
    onSkip: () -> Unit,
    onStart: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onSkip,
        title = { Text("How are you feeling?", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Wellbeing section
                Text("Wellbeing", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)

                ReadinessSlider(
                    label = "Sleep Quality",
                    value = readinessState.sleep,
                    onValueChange = { v -> onUpdate { copy(sleep = v) } }
                )
                ReadinessSlider(
                    label = "Nutrition",
                    value = readinessState.nutrition,
                    onValueChange = { v -> onUpdate { copy(nutrition = v) } }
                )
                ReadinessSlider(
                    label = "Stress (lower = less stress)",
                    value = readinessState.stress,
                    onValueChange = { v -> onUpdate { copy(stress = v) } }
                )
                ReadinessSlider(
                    label = "Energy",
                    value = readinessState.energy,
                    onValueChange = { v -> onUpdate { copy(energy = v) } }
                )

                HorizontalDivider()

                // Soreness section
                Text("Soreness (1 = none, 10 = severe)", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)

                ReadinessSlider(
                    label = "Chest / Pecs",
                    value = readinessState.sorenessPecs,
                    onValueChange = { v -> onUpdate { copy(sorenessPecs = v) } }
                )
                ReadinessSlider(
                    label = "Back / Lats",
                    value = readinessState.sorenessLats,
                    onValueChange = { v -> onUpdate { copy(sorenessLats = v) } }
                )
                ReadinessSlider(
                    label = "Lower Back",
                    value = readinessState.sorenessLowerBack,
                    onValueChange = { v -> onUpdate { copy(sorenessLowerBack = v) } }
                )
                ReadinessSlider(
                    label = "Glutes / Hamstrings",
                    value = readinessState.sorenessGlutesHams,
                    onValueChange = { v -> onUpdate { copy(sorenessGlutesHams = v) } }
                )
                ReadinessSlider(
                    label = "Quads",
                    value = readinessState.sorenessQuads,
                    onValueChange = { v -> onUpdate { copy(sorenessQuads = v) } }
                )
            }
        },
        confirmButton = {
            Button(onClick = onStart) { Text("Start") }
        },
        dismissButton = {
            TextButton(onClick = onSkip) { Text("Skip") }
        }
    )
}

@Composable
private fun ReadinessSlider(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = 1f..10f,
            steps = 8,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
