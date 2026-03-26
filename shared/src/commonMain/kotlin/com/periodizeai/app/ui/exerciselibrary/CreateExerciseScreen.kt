package com.periodizeai.app.ui.exerciselibrary

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.periodizeai.app.models.*
import com.periodizeai.app.repositories.ExerciseData
import com.periodizeai.app.repositories.ExerciseRepository
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateExerciseScreen(navController: NavController? = null) {
    val exerciseRepo = koinInject<ExerciseRepository>()
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf("") }
    var movementPattern by remember { mutableStateOf(MovementPattern.SQUAT) }
    var equipment by remember { mutableStateOf(EquipmentType.BARBELL) }
    var classification by remember { mutableStateOf(ExerciseClassification.COMPOUND) }
    var selectedPrimary by remember { mutableStateOf(setOf<MuscleGroup>()) }
    var selectedSecondary by remember { mutableStateOf(setOf<MuscleGroup>()) }
    var minReps by remember { mutableStateOf(6f) }
    var maxReps by remember { mutableStateOf(12f) }

    var nameError by remember { mutableStateOf(false) }

    var movementPatternExpanded by remember { mutableStateOf(false) }
    var equipmentExpanded by remember { mutableStateOf(false) }
    var classificationExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Create Exercise") },
                navigationIcon = {
                    IconButton(onClick = { navController?.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Name
            OutlinedTextField(
                value = name,
                onValueChange = { name = it; nameError = false },
                label = { Text("Exercise Name") },
                isError = nameError,
                supportingText = if (nameError) ({ Text("Name is required") }) else null,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            // Movement Pattern dropdown
            ExposedDropdownMenuBox(
                expanded = movementPatternExpanded,
                onExpandedChange = { movementPatternExpanded = it },
            ) {
                OutlinedTextField(
                    value = movementPattern.raw,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Movement Pattern") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = movementPatternExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                )
                ExposedDropdownMenu(
                    expanded = movementPatternExpanded,
                    onDismissRequest = { movementPatternExpanded = false },
                ) {
                    MovementPattern.entries.forEach { pattern ->
                        DropdownMenuItem(
                            text = { Text(pattern.raw) },
                            onClick = {
                                movementPattern = pattern
                                movementPatternExpanded = false
                            },
                        )
                    }
                }
            }

            // Equipment dropdown
            ExposedDropdownMenuBox(
                expanded = equipmentExpanded,
                onExpandedChange = { equipmentExpanded = it },
            ) {
                OutlinedTextField(
                    value = equipment.raw,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Equipment") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = equipmentExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                )
                ExposedDropdownMenu(
                    expanded = equipmentExpanded,
                    onDismissRequest = { equipmentExpanded = false },
                ) {
                    EquipmentType.entries.forEach { eq ->
                        DropdownMenuItem(
                            text = { Text(eq.raw) },
                            onClick = {
                                equipment = eq
                                equipmentExpanded = false
                            },
                        )
                    }
                }
            }

            // Classification dropdown
            ExposedDropdownMenuBox(
                expanded = classificationExpanded,
                onExpandedChange = { classificationExpanded = it },
            ) {
                OutlinedTextField(
                    value = classification.raw.replaceFirstChar { it.uppercase() },
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Classification") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = classificationExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                )
                ExposedDropdownMenu(
                    expanded = classificationExpanded,
                    onDismissRequest = { classificationExpanded = false },
                ) {
                    ExerciseClassification.entries.forEach { cls ->
                        DropdownMenuItem(
                            text = { Text(cls.raw.replaceFirstChar { it.uppercase() }) },
                            onClick = {
                                classification = cls
                                classificationExpanded = false
                            },
                        )
                    }
                }
            }

            // Primary muscles multi-select
            Text("Primary Muscles", style = MaterialTheme.typography.labelLarge)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(MuscleGroup.entries) { muscle ->
                    FilterChip(
                        selected = muscle in selectedPrimary,
                        onClick = {
                            selectedPrimary = if (muscle in selectedPrimary)
                                selectedPrimary - muscle
                            else
                                selectedPrimary + muscle
                        },
                        label = { Text(muscle.raw, style = MaterialTheme.typography.labelSmall) },
                    )
                }
            }

            // Secondary muscles multi-select
            Text("Secondary Muscles", style = MaterialTheme.typography.labelLarge)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(MuscleGroup.entries) { muscle ->
                    FilterChip(
                        selected = muscle in selectedSecondary,
                        onClick = {
                            selectedSecondary = if (muscle in selectedSecondary)
                                selectedSecondary - muscle
                            else
                                selectedSecondary + muscle
                        },
                        label = { Text(muscle.raw, style = MaterialTheme.typography.labelSmall) },
                    )
                }
            }

            // Rep range sliders
            Column {
                Text(
                    "Min Reps: ${minReps.toInt()}",
                    style = MaterialTheme.typography.labelLarge,
                )
                Slider(
                    value = minReps,
                    onValueChange = { minReps = it; if (it > maxReps) maxReps = it },
                    valueRange = 1f..30f,
                    steps = 28,
                )
            }
            Column {
                Text(
                    "Max Reps: ${maxReps.toInt()}",
                    style = MaterialTheme.typography.labelLarge,
                )
                Slider(
                    value = maxReps,
                    onValueChange = { maxReps = it; if (it < minReps) minReps = it },
                    valueRange = 1f..30f,
                    steps = 28,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Save button
            Button(
                onClick = {
                    if (name.isBlank()) {
                        nameError = true
                        return@Button
                    }
                    val id = "custom_" + buildString {
                        val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
                        repeat(12) { append(chars[kotlin.random.Random.nextInt(chars.length)]) }
                    }
                    val exercise = ExerciseData(
                        id = id,
                        name = name.trim(),
                        primaryMuscles = selectedPrimary.toList(),
                        secondaryMuscles = selectedSecondary.toList(),
                        movementPattern = movementPattern,
                        classification = classification,
                        equipment = equipment,
                        isCustom = true,
                        isBarbell = equipment == EquipmentType.BARBELL,
                        minReps = minReps.toInt(),
                        maxReps = maxReps.toInt(),
                    )
                    scope.launch {
                        exerciseRepo.save(exercise)
                        navController?.popBackStack()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Save Exercise")
            }

            OutlinedButton(
                onClick = { navController?.popBackStack() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Cancel")
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
