package com.periodizeai.app.ui.workout

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.periodizeai.app.models.ExerciseRole
import com.periodizeai.app.viewmodels.WorkoutExecutionViewModel
import com.periodizeai.app.utils.nowEpochMs
import org.koin.core.parameter.parametersOf
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutExecutionScreen(
    workoutId: String,
    navController: NavController? = null,
) {
    val vm = koinViewModel<WorkoutExecutionViewModel> { parametersOf(workoutId) }
    val state by vm.uiState.collectAsState()

    // Local flag: has the user tapped "Start Workout"?
    var isWorkoutStarted by remember { mutableStateOf(false) }
    var showReadinessDialog by remember { mutableStateOf(false) }

    // When workout finishes, show summary
    if (state.isFinished) {
        WorkoutSummaryScreen(state = state, onDone = { navController?.popBackStack() })
        return
    }

    if (state.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    state.errorMessage?.let { error ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(error, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(16.dp))
                Button(onClick = { navController?.popBackStack() }) { Text("Back") }
            }
        }
        return
    }

    if (showReadinessDialog) {
        ReadinessCheckDialog(
            readinessState = state.readinessState,
            onUpdate = { vm.updateReadiness(it) },
            onSkip = {
                showReadinessDialog = false
                isWorkoutStarted = true
            },
            onStart = {
                showReadinessDialog = false
                isWorkoutStarted = true
            }
        )
    }

    val workout = state.plannedWorkout

    if (!isWorkoutStarted) {
        // Preview mode
        PreviewScreen(
            title = workout?.dayLabel?.ifBlank { "Workout" } ?: "Workout",
            state = state,
            onStartWorkout = { showReadinessDialog = true },
            onBack = { navController?.popBackStack() }
        )
    } else {
        // Active workout mode
        ActiveWorkoutScreen(
            title = workout?.dayLabel?.ifBlank { "Workout" } ?: "Workout",
            state = state,
            vm = vm,
            onFinish = { vm.finishWorkout() },
            onBack = { navController?.popBackStack() }
        )
    }
}

// ── Preview Screen ────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PreviewScreen(
    title: String,
    state: WorkoutExecutionViewModel.UiState,
    onStartWorkout: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Back") }
                }
            )
        },
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                Button(
                    onClick = onStartWorkout,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Start Workout", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = "Exercises",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            itemsIndexed(state.exerciseEntries, key = { _, e -> e.id }) { _, entry ->
                PreviewExerciseCard(entry)
            }
        }
    }
}

@Composable
private fun PreviewExerciseCard(entry: WorkoutExecutionViewModel.ExerciseEntry) {
    val planned = entry.plannedExercise
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = entry.exercise.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        RoleBadge(planned.role)
                        Text(
                            text = "${planned.sets}×${planned.reps}" +
                                    planned.suggestedWeight?.let { " @ ${it.toInt()} lb" }.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (expanded) "Collapse" else "Expand"
                    )
                }
            }

            if (expanded) {
                HorizontalDivider()
                // Header row
                Row(Modifier.fillMaxWidth()) {
                    Text("Set", Modifier.width(40.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Weight", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Reps", Modifier.width(44.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                entry.sets.forEachIndexed { idx, set ->
                    val isWarmup = set.isWarmup
                    Row(
                        Modifier.fillMaxWidth().alpha(if (isWarmup) 0.5f else 1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isWarmup) "W${idx + 1}" else "${idx - entry.sets.count { it.isWarmup } + 1}",
                            modifier = Modifier.width(40.dp),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "${set.targetWeight.toInt()} lb",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = set.targetReps.toString(),
                            modifier = Modifier.width(44.dp),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

// ── Active Workout Screen ─────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActiveWorkoutScreen(
    title: String,
    state: WorkoutExecutionViewModel.UiState,
    vm: WorkoutExecutionViewModel,
    onFinish: () -> Unit,
    onBack: () -> Unit,
) {
    val startMs = remember { nowEpochMs() }
    var elapsedSeconds by remember { mutableStateOf(0L) }

    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1000L)
            elapsedSeconds = (nowEpochMs() - startMs) / 1000L
        }
    }

    val elapsedFormatted = run {
        val min = elapsedSeconds / 60
        val sec = elapsedSeconds % 60
        "${min}:${sec.toString().padStart(2, '0')}"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(title)
                        Text(
                            elapsedFormatted,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    if (state.restTimerActive) {
                        val remaining = state.restTimerRemainingMs / 1000L
                        val min = remaining / 60
                        val sec = remaining % 60
                        TextButton(onClick = { vm.cancelRestTimer() }) {
                            Icon(Icons.Default.Timer, contentDescription = "Rest timer", tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(4.dp))
                            Text("${min}:${sec.toString().padStart(2, '0')}", color = MaterialTheme.colorScheme.primary)
                        }
                    } else {
                        IconButton(onClick = { vm.startRestTimer(120_000L) }) {
                            Icon(Icons.Default.Timer, contentDescription = "Start 2m rest")
                        }
                    }
                }
            )
        },
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                Column {
                    // Progress indicator
                    LinearProgressIndicator(
                        progress = { state.progressFraction.toFloat() },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = onFinish,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary
                        )
                    ) {
                        Text("Finish Workout (${state.completedSetCount}/${state.totalSetCount} sets)")
                    }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(state.exerciseEntries, key = { _, e -> e.id }) { _, entry ->
                    ActiveExerciseCard(
                        entry = entry,
                        onCompleteSet = { setId, reps, weight, rpe ->
                            vm.completeSet(entry.id, setId, reps, weight, rpe)
                        },
                        onUncompleteSet = { setId ->
                            vm.uncompleteSet(entry.id, setId)
                        },
                        onStartRestTimer = { vm.startRestTimer(120_000L) },
                        onToggleExpand = { vm.toggleExerciseExpanded(entry.id) }
                    )
                }
            }

            // Rest timer overlay at bottom
            AnimatedVisibility(
                visible = state.restTimerActive,
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            ) {
                RestTimerOverlay(
                    remaining = state.restTimerRemainingMs,
                    total = state.restTimerDurationMs,
                    onDismiss = { vm.cancelRestTimer() },
                    onExtend = { vm.startRestTimer(state.restTimerRemainingMs + 30_000L) }
                )
            }
        }
    }
}

// ── Active Exercise Card ──────────────────────────────────────────────────

@Composable
private fun ActiveExerciseCard(
    entry: WorkoutExecutionViewModel.ExerciseEntry,
    onCompleteSet: (setId: String, reps: Int, weight: Double, rpe: Double?) -> Unit,
    onUncompleteSet: (setId: String) -> Unit,
    onStartRestTimer: () -> Unit,
    onToggleExpand: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Exercise header
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = entry.exercise.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    RoleBadge(entry.plannedExercise.role)
                }

                val completedCount = entry.sets.count { it.isCompleted }
                val totalCount = entry.sets.size
                Text(
                    text = "$completedCount/$totalCount",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (completedCount == totalCount && totalCount > 0)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )

                IconButton(onClick = onToggleExpand) {
                    Icon(
                        if (entry.isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null
                    )
                }
            }

            if (entry.isExpanded) {
                HorizontalDivider()

                // Column headers
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Set", Modifier.width(36.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Weight", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Reps", Modifier.width(60.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("RPE", Modifier.width(48.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(40.dp))
                }

                entry.sets.forEachIndexed { idx, set ->
                    SetRow(
                        setIndex = idx,
                        set = set,
                        isAmrap = entry.plannedExercise.isAMRAP && idx == entry.plannedExercise.amrapSetIndex,
                        onComplete = { reps, weight, rpe ->
                            onCompleteSet(set.id, reps, weight, rpe)
                            onStartRestTimer()
                        },
                        onUncomplete = { onUncompleteSet(set.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SetRow(
    setIndex: Int,
    set: WorkoutExecutionViewModel.SetEntry,
    isAmrap: Boolean,
    onComplete: (reps: Int, weight: Double, rpe: Double?) -> Unit,
    onUncomplete: () -> Unit,
) {
    val isWarmup = set.isWarmup
    val warmupIndex = setIndex + 1

    var weightText by remember(set.id) {
        mutableStateOf(
            (set.completedWeight ?: set.targetWeight).let {
                if (it == 0.0) "" else it.toInt().toString()
            }
        )
    }
    var repsText by remember(set.id) {
        mutableStateOf(
            (set.completedReps ?: set.targetReps).toString()
        )
    }
    var rpeText by remember(set.id) {
        mutableStateOf(
            set.completedRpe?.toInt()?.toString()
                ?: set.targetRpe?.toInt()?.toString()
                ?: ""
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (isWarmup) 0.6f else 1f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Set label
        Text(
            text = if (isWarmup) "W$warmupIndex" else "${setIndex + 1}",
            modifier = Modifier.width(36.dp),
            style = MaterialTheme.typography.bodySmall,
            color = if (isWarmup) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface
        )

        // Weight field
        OutlinedTextField(
            value = weightText,
            onValueChange = { weightText = it },
            modifier = Modifier.weight(1f),
            enabled = !set.isCompleted,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )

        // Reps field
        OutlinedTextField(
            value = repsText,
            onValueChange = { repsText = it },
            modifier = Modifier.width(60.dp),
            enabled = !set.isCompleted,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            suffix = if (isAmrap) ({ Text("+") }) else null,
        )

        // RPE field
        OutlinedTextField(
            value = rpeText,
            onValueChange = { rpeText = it },
            modifier = Modifier.width(48.dp),
            enabled = !set.isCompleted,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )

        // Check / done button
        if (set.isCompleted) {
            FilledIconButton(
                onClick = onUncomplete,
                modifier = Modifier.size(32.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(Icons.Default.Check, contentDescription = "Completed", modifier = Modifier.size(16.dp))
            }
        } else {
            OutlinedIconButton(
                onClick = {
                    val reps = repsText.toIntOrNull() ?: set.targetReps
                    val weight = weightText.toDoubleOrNull() ?: set.targetWeight
                    val rpe = rpeText.toDoubleOrNull() ?: set.targetRpe
                    onComplete(reps, weight, rpe)
                },
                modifier = Modifier.size(32.dp),
            ) {
                Icon(Icons.Default.Check, contentDescription = "Complete set", modifier = Modifier.size(16.dp))
            }
        }
    }
}

// ── Role Badge ────────────────────────────────────────────────────────────

@Composable
private fun RoleBadge(role: ExerciseRole) {
    val (label, containerColor) = when (role) {
        ExerciseRole.MAIN_LIFT -> "Main Lift" to MaterialTheme.colorScheme.primaryContainer
        ExerciseRole.COMPOUND_ACCESSORY -> "Compound" to MaterialTheme.colorScheme.secondaryContainer
        ExerciseRole.ISOLATION_ACCESSORY -> "Isolation" to MaterialTheme.colorScheme.tertiaryContainer
    }
    val textColor = when (role) {
        ExerciseRole.MAIN_LIFT -> MaterialTheme.colorScheme.onPrimaryContainer
        ExerciseRole.COMPOUND_ACCESSORY -> MaterialTheme.colorScheme.onSecondaryContainer
        ExerciseRole.ISOLATION_ACCESSORY -> MaterialTheme.colorScheme.onTertiaryContainer
    }

    Surface(
        shape = RoundedCornerShape(4.dp),
        color = containerColor
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}
