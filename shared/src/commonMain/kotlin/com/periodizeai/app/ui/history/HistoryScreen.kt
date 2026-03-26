package com.periodizeai.app.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.periodizeai.app.repositories.CompletedSetData
import com.periodizeai.app.repositories.WorkoutSessionData
import com.periodizeai.app.viewmodels.HistoryViewModel
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(navController: NavController? = null) {
    val vm = koinViewModel<HistoryViewModel>()
    val state by vm.uiState.collectAsState()

    LaunchedEffect(Unit) { vm.load() }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("History") })
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Exercise filter dropdown
            if (state.uniqueExerciseNames.isNotEmpty()) {
                ExerciseFilterRow(
                    exercises = state.uniqueExerciseNames,
                    selected = state.filterExercise,
                    onSelected = { vm.setFilterExercise(it) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                HorizontalDivider()
            }

            when {
                state.isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                state.filteredSessions.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("No Workout History", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Complete workouts to see your history here",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                else -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        state.groupedByWeek.forEach { (weekStart, sessions) ->
                            item(key = "header_$weekStart") {
                                WeekHeader(weekStart)
                            }
                            items(sessions, key = { it.id }) { session ->
                                SessionCard(session = session)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExerciseFilterRow(
    exercises: List<String>,
    selected: String?,
    onSelected: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { expanded = true },
        ) {
            Text(selected ?: "All Exercises")
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text("All Exercises") },
                onClick = {
                    onSelected(null)
                    expanded = false
                },
            )
            exercises.forEach { name ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        onSelected(name)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun WeekHeader(weekStartMs: Long) {
    val dt = Instant.fromEpochMilliseconds(weekStartMs)
        .toLocalDateTime(TimeZone.currentSystemDefault())
    val endMs = weekStartMs + 6L * 86_400_000L
    val endDt = Instant.fromEpochMilliseconds(endMs)
        .toLocalDateTime(TimeZone.currentSystemDefault())

    Text(
        text = "Week of ${dt.date.month.name.take(3)} ${dt.date.dayOfMonth} - ${endDt.date.month.name.take(3)} ${endDt.date.dayOfMonth}",
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun SessionCard(session: WorkoutSessionData) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
            .clickable { expanded = !expanded }
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                val dt = Instant.fromEpochMilliseconds(session.date)
                    .toLocalDateTime(TimeZone.currentSystemDefault())
                Text(
                    "${dt.date.dayOfWeek.name.take(3)}, ${dt.date.month.name.take(3)} ${dt.date.dayOfMonth} ${dt.date.year}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                val setCount = session.workingSets.size
                val exerciseCount = session.exerciseCount
                Text(
                    "$exerciseCount exercises B7 $setCount sets",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                session.durationMs?.let { dur ->
                    val mins = dur / 60_000L
                    Text(
                        "${mins}m",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val vol = session.totalVolume.toLong()
                Text(
                    "${vol} lb",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
                Icon(
                    if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (expanded) {
            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            ExerciseSummaryList(sets = session.completedSets)
        }
    }
}

@Composable
private fun ExerciseSummaryList(sets: List<CompletedSetData>) {
    val grouped = sets.groupBy { it.exerciseId }
    grouped.forEach { (_, exSets) ->
        val name = exSets.firstOrNull()?.exercise?.name ?: return@forEach
        val workingSets = exSets.filter { !it.isWarmup }
        if (workingSets.isEmpty()) return@forEach

        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            Text(
                name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            workingSets.forEach { set ->
                Row(
                    modifier = Modifier.padding(start = 8.dp, top = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "Set ${set.setNumber}:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "${set.weight.toInt()} lb  ${set.reps}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    set.rpe?.let {
                        Text(
                            "@ RPE ${it}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
