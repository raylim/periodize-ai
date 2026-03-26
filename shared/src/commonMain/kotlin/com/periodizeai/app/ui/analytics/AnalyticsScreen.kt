package com.periodizeai.app.ui.analytics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.periodizeai.app.viewmodels.AnalyticsViewModel
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.viewmodel.koinViewModel

private fun Double.fmt1f(): String {
    val rounded = kotlin.math.round(this * 10).toInt()
    return "${rounded / 10}.${kotlin.math.abs(rounded % 10)}"
}


private fun fmt1(d: Double): String {
    val rounded = kotlin.math.round(d * 10)
    val whole = rounded / 10
    val frac = kotlin.math.abs(rounded % 10)
    return "$whole.$frac"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(navController: NavController? = null) {
    val vm = koinViewModel<AnalyticsViewModel>()
    val state by vm.uiState.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Progress", "Volume", "Frequency", "Duration", "RPE", "Calendar")

    Scaffold(
        topBar = { TopAppBar(title = { Text("Analytics") }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Exercise picker + time range
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ExercisePicker(
                    exercises = state.availableExercises,
                    selected = state.selectedExercise,
                    onSelected = { vm.selectExercise(it) },
                )
            }

            // Time range chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AnalyticsViewModel.TimeRange.entries.forEach { range ->
                    FilterChip(
                        selected = state.selectedTimeRange == range,
                        onClick = { vm.selectTimeRange(range) },
                        label = { Text(range.label) },
                    )
                }
            }

            HorizontalDivider()

            ScrollableTabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) },
                    )
                }
            }

            if (state.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    when (selectedTab) {
                        0 -> {
                            if (state.e1RMData.isEmpty()) {
                                item { EmptyState("No e1RM data available for ${state.selectedExercise}") }
                            } else {
                                item { SectionTitle("Estimated 1RMb  ${state.selectedExercise}") }
                                item { E1RMLineChart(data = state.e1RMData) }
                                items(state.e1RMData.takeLast(10).reversed()) { point ->
                                    E1RMRow(point)
                                }
                            }
                        }
                        1 -> {
                            if (state.volumeData.isEmpty()) {
                                item { EmptyState("No volume data available") }
                            } else {
                                item { SectionTitle("Weekly Volume by Muscle Group") }
                                val grouped = state.volumeData.groupBy { it.muscleGroup }
                                items(grouped.entries.toList()) { (muscle, points) ->
                                    VolumeRow(muscleGroup = muscle, points = points)
                                }
                            }
                        }
                        2 -> {
                            if (state.frequencyData.isEmpty()) {
                                item { EmptyState("No frequency data available") }
                            } else {
                                item { SectionTitle("Sessions per Week") }
                                items(state.frequencyData.reversed()) { point ->
                                    FrequencyRow(point)
                                }
                            }
                        }
                        3 -> {
                            if (state.durationData.isEmpty()) {
                                item { EmptyState("No duration data available") }
                            } else {
                                item { SectionTitle("Session Duration") }
                                items(state.durationData.reversed()) { point ->
                                    DurationRow(point)
                                }
                            }
                        }
                        4 -> {
                            if (state.rpeData.isEmpty()) {
                                item { EmptyState("No RPE data available") }
                            } else {
                                item { SectionTitle("RPE Distribution") }
                                item { RPEBars(data = state.rpeData) }
                            }
                        }
                        5 -> {
                            item { SectionTitle("Workout Calendar") }
                            item { CalendarView(data = state.calendarData) }
                        }
                    }
                    item { Spacer(Modifier.height(32.dp)) }
                }
            }
        }
    }
}

//bb  Componentsbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb 

@Composable
private fun ExercisePicker(
    exercises: List<String>,
    selected: String,
    onSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Text(selected, maxLines = 1)
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
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
private fun SectionTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun EmptyState(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun E1RMLineChart(data: List<AnalyticsViewModel.E1RMDataPoint>) {
    if (data.size < 2) return

    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceColor = MaterialTheme.colorScheme.surfaceVariant

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .background(surfaceColor, RoundedCornerShape(12.dp))
            .padding(16.dp),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val minE1rm = data.minOf { it.e1rm }
            val maxE1rm = data.maxOf { it.e1rm }
            val range = (maxE1rm - minE1rm).coerceAtLeast(1.0)
            val minDate = data.minOf { it.date }
            val maxDate = data.maxOf { it.date }
            val dateRange = (maxDate - minDate).coerceAtLeast(1L)

            fun xForDate(date: Long): Float = ((date - minDate).toFloat() / dateRange) * size.width
            fun yForE1rm(e1rm: Double): Float = size.height - ((e1rm - minE1rm) / range).toFloat() * size.height

            val path = Path()
            data.forEachIndexed { i, point ->
                val x = xForDate(point.date)
                val y = yForE1rm(point.e1rm)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, color = primaryColor, style = Stroke(width = 3.dp.toPx()))

            data.forEach { point ->
                drawCircle(
                    color = primaryColor,
                    radius = 4.dp.toPx(),
                    center = Offset(xForDate(point.date), yForE1rm(point.e1rm)),
                )
            }
        }
    }
}

@Composable
private fun E1RMRow(point: AnalyticsViewModel.E1RMDataPoint) {
    val dt = Instant.fromEpochMilliseconds(point.date)
        .toLocalDateTime(TimeZone.currentSystemDefault())
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            "${dt.date.month.name.take(3)} ${dt.date.dayOfMonth}, ${dt.date.year}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "${point.e1rm.fmt1f()} lb",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    HorizontalDivider(thickness = 0.5.dp)
}

@Composable
private fun VolumeRow(
    muscleGroup: String,
    points: List<AnalyticsViewModel.VolumeDataPoint>,
) {
    val totalSets = points.sumOf { it.sets }
    val avgSetsPerWeek = if (points.isEmpty()) 0.0 else totalSets.toDouble() / points.size
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(muscleGroup, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(
            "${avgSetsPerWeek.fmt1f()} sets/wk",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
    }
    HorizontalDivider(thickness = 0.5.dp)
}

@Composable
private fun FrequencyRow(point: AnalyticsViewModel.FrequencyDataPoint) {
    val dt = Instant.fromEpochMilliseconds(point.date)
        .toLocalDateTime(TimeZone.currentSystemDefault())
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            "Week of ${dt.date.month.name.take(3)} ${dt.date.dayOfMonth}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "${point.sessionCount} session${if (point.sessionCount == 1) "" else "s"}",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
    HorizontalDivider(thickness = 0.5.dp)
}

@Composable
private fun DurationRow(point: AnalyticsViewModel.DurationDataPoint) {
    val dt = Instant.fromEpochMilliseconds(point.date)
        .toLocalDateTime(TimeZone.currentSystemDefault())
    val mins = point.durationMs / 60_000L
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            "${dt.date.month.name.take(3)} ${dt.date.dayOfMonth}, ${dt.date.year}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "${mins}m",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
    HorizontalDivider(thickness = 0.5.dp)
}

@Composable
private fun RPEBars(data: List<AnalyticsViewModel.RPEDataPoint>) {
    val maxCount = data.maxOfOrNull { it.count } ?: 1
    val primaryColor = MaterialTheme.colorScheme.primary

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        data.forEach { point ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "RPE ${point.rpe.fmt1f()}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.width(60.dp),
                )
                Box(
                    modifier = Modifier
                        .weight(point.count.toFloat() / maxCount)
                        .height(20.dp)
                        .background(primaryColor, RoundedCornerShape(4.dp)),
                )
                Spacer(modifier = Modifier.weight((1f - point.count.toFloat() / maxCount).coerceAtLeast(0.001f)))
                Text(
                    "${point.count}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CalendarView(data: List<AnalyticsViewModel.CalendarDataPoint>) {
    if (data.isEmpty()) {
        EmptyState("No workout data available")
        return
    }

    val workoutDays = data.associate { it.date to it.setCount }
    val minDate = data.minOf { it.date }
    val maxDate = data.maxOf { it.date }

    // Group into weeks
    val dayMs = 86_400_000L
    val weekMs = 7 * dayMs
    val startDay = (minDate / dayMs) * dayMs
    val endDay = (maxDate / dayMs) * dayMs

    val weeks = mutableListOf<List<Long>>()
    var weekStart = startDay
    while (weekStart <= endDay) {
        val week = (0..6).map { d -> weekStart + d * dayMs }
        weeks.add(week)
        weekStart += weekMs
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceColor = MaterialTheme.colorScheme.surfaceVariant

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // Day-of-week header
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach { day ->
                Text(day, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        weeks.takeLast(12).forEach { week ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                week.forEach { dayEpoch ->
                    val sets = workoutDays[dayEpoch]
                    val hasWorkout = sets != null && sets > 0
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(
                                if (hasWorkout) primaryColor else surfaceColor,
                                CircleShape,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        val dt = Instant.fromEpochMilliseconds(dayEpoch)
                            .toLocalDateTime(TimeZone.currentSystemDefault())
                        Text(
                            "${dt.date.dayOfMonth}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (hasWorkout) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
