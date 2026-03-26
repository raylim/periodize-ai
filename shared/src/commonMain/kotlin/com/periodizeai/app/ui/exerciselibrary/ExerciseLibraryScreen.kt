package com.periodizeai.app.ui.exerciselibrary

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.periodizeai.app.models.MovementPattern
import com.periodizeai.app.navigation.Screen
import com.periodizeai.app.repositories.ExerciseData
import com.periodizeai.app.repositories.ExerciseRepository
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseLibraryScreen(navController: NavController? = null) {
    val exerciseRepo = koinInject<ExerciseRepository>()
    val scope = rememberCoroutineScope()

    var exercises by remember { mutableStateOf(emptyList<ExerciseData>()) }
    var searchQuery by remember { mutableStateOf("") }
    var filterPattern by remember { mutableStateOf<MovementPattern?>(null) }
    var selectedExercise by remember { mutableStateOf<ExerciseData?>(null) }

    LaunchedEffect(Unit) {
        exercises = exerciseRepo.getAll()
    }

    val filtered = remember(exercises, searchQuery, filterPattern) {
        exercises.filter { ex ->
            (filterPattern == null || ex.movementPattern == filterPattern) &&
            (searchQuery.isBlank() || ex.name.contains(searchQuery, ignoreCase = true))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Exercise Library") })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                navController?.navigate(Screen.CreateExercise.route)
            }) {
                Icon(Icons.Filled.Add, contentDescription = "Add exercise")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search exercises…") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
            )

            // Movement pattern filter chips
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 8.dp),
            ) {
                item {
                    FilterChip(
                        selected = filterPattern == null,
                        onClick = { filterPattern = null },
                        label = { Text("All") },
                    )
                }
                items(MovementPattern.entries) { pattern ->
                    FilterChip(
                        selected = filterPattern == pattern,
                        onClick = {
                            filterPattern = if (filterPattern == pattern) null else pattern
                        },
                        label = { Text(pattern.raw) },
                    )
                }
            }

            if (filtered.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (exercises.isEmpty()) "Loading…" else "No exercises found",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(filtered, key = { it.id }) { exercise ->
                        ExerciseCard(
                            exercise = exercise,
                            onClick = { selectedExercise = exercise },
                        )
                    }
                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            }
        }
    }

    selectedExercise?.let { exercise ->
        ExerciseDetailDialog(
            exercise = exercise,
            onDismiss = { selectedExercise = null },
        )
    }
}

@Composable
private fun ExerciseCard(
    exercise: ExerciseData,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = exercise.name,
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AssistChip(
                    onClick = {},
                    label = { Text(exercise.movementPattern.raw, style = MaterialTheme.typography.labelSmall) },
                )
                if (exercise.primaryMuscles.isNotEmpty()) {
                    AssistChip(
                        onClick = {},
                        label = {
                            Text(
                                exercise.primaryMuscles.take(2).joinToString { it.raw },
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                exercise.estimatedOneRepMax?.let { e1rm ->
                    Text(
                        text = "e1RM: ${e1rm.toInt()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun ExerciseDetailDialog(
    exercise: ExerciseData,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(exercise.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DetailRow("Pattern", exercise.movementPattern.raw)
                DetailRow("Equipment", exercise.equipment.raw)
                DetailRow("Classification", exercise.classification.raw.replaceFirstChar { it.uppercase() })
                if (exercise.primaryMuscles.isNotEmpty()) {
                    DetailRow("Primary", exercise.primaryMuscles.joinToString { it.raw })
                }
                if (exercise.secondaryMuscles.isNotEmpty()) {
                    DetailRow("Secondary", exercise.secondaryMuscles.joinToString { it.raw })
                }
                exercise.estimatedOneRepMax?.let { e1rm ->
                    DetailRow("e1RM", "${e1rm.toInt()} (working max: ${((e1rm * 0.9).toInt())})")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row {
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = value, style = MaterialTheme.typography.bodySmall)
    }
}
