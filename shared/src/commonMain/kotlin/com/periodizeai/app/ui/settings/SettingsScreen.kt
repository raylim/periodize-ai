package com.periodizeai.app.ui.settings

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.periodizeai.app.models.*
import com.periodizeai.app.navigation.Screen
import com.periodizeai.app.repositories.TrainingBlockData
import com.periodizeai.app.repositories.TrainingWeekData
import com.periodizeai.app.viewmodels.SettingsViewModel
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)

@Composable
fun SettingsScreen(navController: NavController? = null) {
    val vm = koinViewModel<SettingsViewModel>()
    val state by vm.uiState.collectAsState()
    LaunchedEffect(Unit) { vm.load() }

    var showResetConfirmation by remember { mutableStateOf(false) }
    var showRegenerateConfirmation by remember { mutableStateOf(false) }
    var showWeekPicker by remember { mutableStateOf(false) }
    var snackbarMessage by remember { mutableStateOf<String?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            snackbarMessage = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Settings", fontWeight = FontWeight.Bold) })
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ── Section 1: Training ────────────────────────────────────────────
            item {
                SettingsSectionCard(title = "Training") {
                    // Training days stepper
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Training Days / Week", style = MaterialTheme.typography.bodyMedium)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { vm.setSelectedTrainingDays((state.selectedTrainingDaysPerWeek - 1).coerceAtLeast(2)) },
                                enabled = state.selectedTrainingDaysPerWeek > 2,
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = "Decrease")
                            }
                            Text(
                                text = "${state.selectedTrainingDaysPerWeek}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(24.dp),
                            )
                            IconButton(
                                onClick = { vm.setSelectedTrainingDays((state.selectedTrainingDaysPerWeek + 1).coerceAtMost(6)) },
                                enabled = state.selectedTrainingDaysPerWeek < 6,
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Increase")
                            }
                        }
                    }

                    if (state.hasPendingDaysChange) {
                        Spacer(Modifier.height(4.dp))
                        Button(
                            onClick = { vm.applyTrainingDaysChange() },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        ) {
                            Text("Apply to Remaining Weeks")
                        }
                    }

                    HorizontalDivider(Modifier.padding(vertical = 8.dp))

                    // Weight unit
                    val profile = state.profile
                    if (profile != null) {
                        Text("Weight Unit", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        Spacer(Modifier.height(4.dp))
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            WeightUnit.entries.forEachIndexed { idx, unit ->
                                SegmentedButton(
                                    selected = profile.weightUnit == unit,
                                    onClick = { /* read-only display — profile update not in VM */ },
                                    shape = SegmentedButtonDefaults.itemShape(idx, WeightUnit.entries.size),
                                ) {
                                    Text(unit.raw)
                                }
                            }
                        }

                        HorizontalDivider(Modifier.padding(vertical = 8.dp))

                        // Goal picker
                        Text("Goal", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        Spacer(Modifier.height(4.dp))
                        Column {
                            TrainingGoal.entries.forEach { goal ->
                                Row(
                                    Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    RadioButton(
                                        selected = profile.goal == goal,
                                        onClick = { /* read-only display */ },
                                    )
                                    Text(goal.raw, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }

                        HorizontalDivider(Modifier.padding(vertical = 8.dp))

                        // Deadlift stance
                        Text("Deadlift Stance", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        Spacer(Modifier.height(4.dp))
                        Row {
                            DeadliftStance.entries.forEach { stance ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(
                                        selected = profile.deadliftStance == stance,
                                        onClick = { /* read-only display */ },
                                    )
                                    Text(stance.raw, style = MaterialTheme.typography.bodyMedium)
                                    Spacer(Modifier.width(8.dp))
                                }
                            }
                        }

                        // Navigate to Plates Editor
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        OutlinedButton(
                            onClick = { navController?.navigate(Screen.PlatesEditor.route) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Edit Available Plates")
                        }
                    }
                }
            }

            // ── Section 2: Competition ─────────────────────────────────────────
            item {
                val profile = state.profile
                SettingsSectionCard(title = "Competition") {
                    val hasMeetDate = profile?.meetDateMs != null
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Target a Meet Date", style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = hasMeetDate,
                            onCheckedChange = { /* read-only display — no VM method */ },
                        )
                    }
                    if (hasMeetDate && profile?.meetDateMs != null) {
                        Spacer(Modifier.height(4.dp))
                        val meetMs = profile.meetDateMs
                        val meetStr = remember(meetMs) {
                            val local = Instant.fromEpochMilliseconds(meetMs)
                                .toLocalDateTime(TimeZone.currentSystemDefault())
                            "${local.month.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)} ${local.dayOfMonth}, ${local.year}"
                        }
                        Text(
                            text = "Meet Date: $meetStr",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        )
                    }
                }
            }

            // ── Section 3: Plan ────────────────────────────────────────────────
            item {
                SettingsSectionCard(title = "Plan") {
                    OutlinedButton(
                        onClick = { showWeekPicker = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = state.weekEntries.isNotEmpty(),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Set Current Week")
                            if (state.weekEntries.isNotEmpty()) {
                                val currentEntry = state.weekEntries.getOrNull(state.currentWeekIndex)
                                if (currentEntry != null) {
                                    val (block, week) = currentEntry
                                    Text(
                                        text = "${block.phase.shortName} - Week ${week.weekNumber}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    Button(
                        onClick = { showRegenerateConfirmation = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    ) {
                        Text("Regenerate Entire Plan")
                    }
                }
            }

            // ── Section 4: Data ────────────────────────────────────────────────
            item {
                SettingsSectionCard(title = "Data") {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                val csv = vm.exportCsvAsync()
                                snackbarMessage = if (csv.lines().size > 1)
                                    "Exported ${csv.lines().size - 1} rows"
                                else
                                    "No data to export"
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Export Workout History (CSV)")
                    }

                    Spacer(Modifier.height(8.dp))

                    Button(
                        onClick = { showResetConfirmation = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    ) {
                        Text("Reset All Data")
                    }

                    if (state.errorMessage != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = state.errorMessage!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    // Reset confirmation dialog
    if (showResetConfirmation) {
        AlertDialog(
            onDismissRequest = { showResetConfirmation = false },
            title = { Text("Reset All Data") },
            text = { Text("This will permanently delete all workout sessions and your training plan. This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        vm.resetAllData()
                        showResetConfirmation = false
                        snackbarMessage = "All data reset"
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) {
                    Text("Reset")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmation = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    // Regenerate plan confirmation dialog
    if (showRegenerateConfirmation) {
        AlertDialog(
            onDismissRequest = { showRegenerateConfirmation = false },
            title = { Text("Regenerate Entire Plan") },
            text = { Text("This will delete your current plan and create a new one based on your profile. All progress will be lost.") },
            confirmButton = {
                Button(
                    onClick = {
                        vm.regenerateEntirePlan()
                        showRegenerateConfirmation = false
                        snackbarMessage = "Plan regenerated"
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) {
                    Text("Regenerate")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRegenerateConfirmation = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    // Week picker dialog
    if (showWeekPicker && state.weekEntries.isNotEmpty()) {
        WeekPickerDialog(
            entries = state.weekEntries,
            currentIndex = state.currentWeekIndex,
            onSelect = { weekId ->
                vm.setCurrentWeek(weekId)
                showWeekPicker = false
                snackbarMessage = "Current week updated"
            },
            onDismiss = { showWeekPicker = false },
        )
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                letterSpacing = androidx.compose.ui.unit.TextUnit(1.5f, androidx.compose.ui.unit.TextUnitType.Sp),
            )
            Spacer(Modifier.height(4.dp))
            content()
        }
    }
}

@Composable
private fun WeekPickerDialog(
    entries: List<Pair<TrainingBlockData, TrainingWeekData>>,
    currentIndex: Int,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedIdx by remember { mutableIntStateOf(currentIndex) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set Current Week") },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 300.dp),
            ) {
                itemsIndexed(entries) { idx, entry ->
                    val block = entry.first
                    val week = entry.second
                    val label = "${block.phase.shortName} – Week ${week.weekNumber}" +
                        if (week.subPhase.isSpecialWeek) " (${week.subPhase.raw})" else ""
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = idx == selectedIdx,
                            onClick = { selectedIdx = idx },
                        )
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                entries.getOrNull(selectedIdx)?.second?.id?.let { onSelect(it) }
            }) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
