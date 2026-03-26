package com.periodizeai.app.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.periodizeai.app.navigation.Screen
import com.periodizeai.app.repositories.WorkoutSessionData
import com.periodizeai.app.repositories.PlannedWorkoutData
import com.periodizeai.app.viewmodels.DashboardViewModel
import kotlinx.datetime.*
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun DashboardScreen(navController: NavController? = null) {
    val vm = koinViewModel<DashboardViewModel>()
    val state by vm.uiState.collectAsState()
    LaunchedEffect(Unit) { vm.load() }

    var showWeekPreview by remember { mutableStateOf(false) }

    if (state.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 1. Phase card
        item {
            PhaseCard(
                phaseBadge = state.phaseBadge,
                progressFraction = state.progressFraction,
                progressText = state.progressText,
                canSelectPrevious = state.canSelectPrevious,
                canSelectNext = state.canSelectNext,
                isShowingCurrentWeek = state.isShowingCurrentWeek,
                onPrevious = { vm.selectPreviousWeek() },
                onNext = { vm.selectNextWeek() },
                onReset = { vm.resetToCurrentWeek() },
                onTapWeek = { showWeekPreview = true },
            )
        }

        // 2. Today's workout card
        val todaysWorkout = state.todaysWorkout()
        item {
            TodaysWorkoutCard(
                workout = todaysWorkout,
                onClick = {
                    todaysWorkout?.let { navController?.navigate(Screen.WorkoutDetail.createRoute(it.id)) }
                },
            )
        }

        // 3. Recent sessions
        if (state.recentSessions.isNotEmpty()) {
            item {
                SectionTitle("Recent Sessions")
            }
            items(state.recentSessions.take(3)) { session ->
                RecentSessionRow(session)
            }
        }

        // 4. Main lift e1RMs
        if (state.mainLiftE1RMs.isNotEmpty()) {
            item {
                SectionTitle("Estimated 1-Rep Max")
                Spacer(Modifier.height(4.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.mainLiftE1RMs) { (name, e1rm) ->
                        E1RMChip(name = name, e1rm = e1rm)
                    }
                }
            }
        }

        // 5. MEV/MRV card
        if (state.mevMrvData.isNotEmpty()) {
            item {
                MevMrvCard(data = state.mevMrvData)
            }
        }

        // 6. Progress chart
        item {
            val bars = remember(state.activePlan) { vm.progressBars() }
            if (bars.isNotEmpty()) {
                TrainingProgressChart(
                    bars = bars,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        item { Spacer(Modifier.height(80.dp)) }
    }

    if (showWeekPreview) {
        val week = state.displayedWeek
        val block = state.displayedBlock
        if (week != null && block != null) {
            WeekPreviewSheet(
                week = week,
                phaseBadge = state.phaseBadge,
                onDismiss = { showWeekPreview = false },
            )
        } else {
            showWeekPreview = false
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
    )
}

@Composable
private fun PhaseCard(
    phaseBadge: String,
    progressFraction: Double,
    progressText: String,
    canSelectPrevious: Boolean,
    canSelectNext: Boolean,
    isShowingCurrentWeek: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onReset: () -> Unit,
    onTapWeek: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = phaseBadge,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (!isShowingCurrentWeek) {
                    IconButton(onClick = onReset, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reset to current week", modifier = Modifier.size(18.dp))
                    }
                }
            }

            LinearProgressIndicator(
                progress = { progressFraction.toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
            )

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onPrevious, enabled = canSelectPrevious) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous week")
                }
                Text(
                    text = progressText,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.clickable(onClick = onTapWeek),
                )
                IconButton(onClick = onNext, enabled = canSelectNext) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next week")
                }
            }
        }
    }
}

@Composable
private fun TodaysWorkoutCard(
    workout: PlannedWorkoutData?,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(enabled = workout != null, onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (workout != null)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surface,
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                Icons.Default.FitnessCenter,
                contentDescription = null,
                tint = if (workout != null) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                modifier = Modifier.size(32.dp),
            )
            Column {
                Text(
                    text = "Today's Workout",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
                if (workout != null) {
                    Text(
                        text = workout.dayLabel.ifBlank { workout.focus.raw },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "${workout.exercises.size} exercises",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                } else {
                    Text(
                        text = "No workout scheduled",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentSessionRow(session: WorkoutSessionData) {
    val dateStr = remember(session.date) {
        val local = Instant.fromEpochMilliseconds(session.date)
            .toLocalDateTime(TimeZone.currentSystemDefault())
        "${local.month.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)} ${local.dayOfMonth}"
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(dateStr, style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                Text(
                    "${session.exerciseCount} exercises · ${session.workingSets.size} sets",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            val durationMin = session.durationMs?.let { it / 60_000 }
            if (durationMin != null) {
                Text("${durationMin}m", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            }
        }
    }
}

@Composable
private fun E1RMChip(name: String, e1rm: Double) {
    val shortName = when {
        name.contains("Squat", ignoreCase = true) -> "Squat"
        name.contains("Bench", ignoreCase = true) -> "Bench"
        name.contains("Deadlift", ignoreCase = true) -> "Deadlift"
        name.contains("Military", ignoreCase = true) || name.contains("Press", ignoreCase = true) -> "OHP"
        else -> name.take(8)
    }
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(shortName, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            Text(
                text = "${e1rm.toInt()} lb",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
