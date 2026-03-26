package com.periodizeai.app.ui.planeditor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.periodizeai.app.repositories.PlannedWorkoutData
import com.periodizeai.app.repositories.TrainingBlockData
import com.periodizeai.app.repositories.TrainingPlanData
import com.periodizeai.app.repositories.TrainingPlanRepository
import com.periodizeai.app.repositories.TrainingWeekData
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanEditorScreen(navController: NavController? = null) {
    val planRepo = koinInject<TrainingPlanRepository>()
    var plan by remember { mutableStateOf<TrainingPlanData?>(null) }

    LaunchedEffect(Unit) {
        plan = planRepo.getActivePlan()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Training Plan") },
                navigationIcon = {
                    IconButton(onClick = { navController?.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        }
    ) { innerPadding ->
        val currentPlan = plan
        if (currentPlan == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            PlanContent(plan = currentPlan, modifier = Modifier.padding(innerPadding))
        }
    }
}

@Composable
private fun PlanContent(plan: TrainingPlanData, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column {
                Text(plan.name, style = MaterialTheme.typography.headlineSmall)
                Text(
                    "${plan.totalWeeks} weeks · ${plan.trainingDaysPerWeek} days/week",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { if (plan.totalWeeks > 0) plan.completedWeeks.toFloat() / plan.totalWeeks else 0f },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "${plan.completedWeeks} / ${plan.totalWeeks} weeks completed",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        items(plan.blocks.sortedBy { it.blockOrder }, key = { it.id }) { block ->
            BlockCard(block = block)
        }
    }
}

@Composable
private fun BlockCard(block: TrainingBlockData) {
    var expanded by remember { mutableStateOf(!block.isCompleted) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = block.phase.raw,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "${block.weeks.size} weeks",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (block.isCompleted) {
                    Badge { Text("Done") }
                    Spacer(modifier = Modifier.width(8.dp))
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(bottom = 8.dp)) {
                    HorizontalDivider()
                    block.weeks.sortedBy { it.weekNumber }.forEach { week ->
                        WeekRow(week = week)
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekRow(week: TrainingWeekData) {
    var expanded by remember { mutableStateOf(!week.isCompleted) }

    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Week ${week.weekNumber} · ${week.subPhase.raw}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "${week.completedWorkouts}/${week.workouts.size} workouts done",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (week.isCompleted) {
                Text(
                    "✓",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            IconButton(onClick = { expanded = !expanded }) {
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                )
            }
        }

        AnimatedVisibility(visible = expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                week.sortedWorkouts.forEach { workout ->
                    WorkoutRow(workout = workout)
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
    }
}

@Composable
private fun WorkoutRow(workout: PlannedWorkoutData) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 4.dp, bottom = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = workout.dayLabel.ifBlank { "Day ${workout.dayNumber}" },
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(modifier = Modifier.weight(1f))
            if (workout.isCompleted) {
                Text("✓", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            } else if (workout.isSkipped) {
                Text("Skipped", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (workout.exercises.isNotEmpty()) {
            Text(
                text = workout.exercises
                    .sortedBy { it.order }
                    .mapNotNull { it.exercise?.name }
                    .joinToString(", "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
