package com.periodizeai.app.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.periodizeai.app.models.DeadliftStance
import com.periodizeai.app.models.StickingPoint
import com.periodizeai.app.models.StrengthLevel
import com.periodizeai.app.models.TrainingGoal
import com.periodizeai.app.models.UserSex
import com.periodizeai.app.models.WeightUnit
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    val vm = koinViewModel<com.periodizeai.app.viewmodels.OnboardingViewModel>()
    val state by vm.uiState.collectAsState()

    val stepCount = com.periodizeai.app.viewmodels.OnboardingViewModel.OnboardingStep.entries.size
    val currentIndex = state.currentStep.ordinal
    val progress = (currentIndex + 1).toFloat() / stepCount

    Scaffold { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Progress bar (not on WELCOME or COMPLETE)
            if (state.currentStep != com.periodizeai.app.viewmodels.OnboardingViewModel.OnboardingStep.WELCOME &&
                state.currentStep != com.periodizeai.app.viewmodels.OnboardingViewModel.OnboardingStep.COMPLETE
            ) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            AnimatedContent(
                targetState = state.currentStep,
                transitionSpec = {
                    (slideInHorizontally { it } + fadeIn()) togetherWith
                        (slideOutHorizontally { -it } + fadeOut())
                },
                modifier = Modifier.weight(1f),
            ) { step ->
                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    when (step) {
                        com.periodizeai.app.viewmodels.OnboardingViewModel.OnboardingStep.WELCOME ->
                            WelcomeStep(onGetStarted = { vm.advance() })

                        com.periodizeai.app.viewmodels.OnboardingViewModel.OnboardingStep.CSV_IMPORT ->
                            CsvImportStep(
                                isImporting = state.isImporting,
                                importResult = state.importResult,
                                importError = state.importError,
                                onSkip = {
                                    vm.skipCsvImport()
                                    vm.advance()
                                },
                                onImport = {
                                    vm.skipCsvImport()
                                    vm.advance()
                                },
                            )

                        com.periodizeai.app.viewmodels.OnboardingViewModel.OnboardingStep.TRAINING_DAYS ->
                            TrainingDaysStep(
                                trainingDays = state.trainingDays,
                                planWeeksDescription = state.planWeeksDescription,
                                onDaysChanged = { vm.setTrainingDays(it) },
                            )

                        com.periodizeai.app.viewmodels.OnboardingViewModel.OnboardingStep.GOAL ->
                            GoalStep(
                                selectedGoal = state.selectedGoal,
                                onGoalSelected = { vm.setGoal(it) },
                            )

                        com.periodizeai.app.viewmodels.OnboardingViewModel.OnboardingStep.BODY_WEIGHT ->
                            BodyWeightStep(
                                bodyWeight = state.bodyWeight,
                                unit = state.selectedUnit,
                                onBodyWeightChanged = { vm.setBodyWeight(it) },
                                onUnitChanged = { vm.setUnit(it) },
                            )

                        com.periodizeai.app.viewmodels.OnboardingViewModel.OnboardingStep.ABOUT_YOU ->
                            AboutYouStep(
                                sex = state.selectedSex,
                                trainingAgeYears = state.trainingAgeYears,
                                userHeight = state.userHeight,
                                deadliftStance = state.deadliftStance,
                                onSexChanged = { vm.setSex(it) },
                                onTrainingAgeChanged = { vm.setTrainingAgeYears(it) },
                                onHeightChanged = { vm.setUserHeight(it) },
                                onDeadliftStanceChanged = { vm.setDeadliftStance(it) },
                            )

                        com.periodizeai.app.viewmodels.OnboardingViewModel.OnboardingStep.STRENGTH_LEVEL ->
                            StrengthLevelStep(
                                selected = state.selectedStrengthLevel,
                                suggested = state.suggestedStrengthLevel,
                                onSelected = { vm.setStrengthLevel(it) },
                            )

                        com.periodizeai.app.viewmodels.OnboardingViewModel.OnboardingStep.STICKING_POINTS ->
                            StickingPointsStep(
                                squatPoint = state.squatStickingPoint,
                                benchPoint = state.benchStickingPoint,
                                deadliftPoint = state.deadliftStickingPoint,
                                onSquatChanged = { vm.setSquatStickingPoint(it) },
                                onBenchChanged = { vm.setBenchStickingPoint(it) },
                                onDeadliftChanged = { vm.setDeadliftStickingPoint(it) },
                            )

                        com.periodizeai.app.viewmodels.OnboardingViewModel.OnboardingStep.GENERATING ->
                            GeneratingStep()

                        com.periodizeai.app.viewmodels.OnboardingViewModel.OnboardingStep.COMPLETE ->
                            CompleteStep(onLetsGo = {
                                vm.completeOnboarding()
                                onComplete()
                            })
                    }
                }
            }

            // Bottom navigation
            val isTerminal = state.currentStep == com.periodizeai.app.viewmodels.OnboardingViewModel.OnboardingStep.WELCOME ||
                state.currentStep == com.periodizeai.app.viewmodels.OnboardingViewModel.OnboardingStep.GENERATING ||
                state.currentStep == com.periodizeai.app.viewmodels.OnboardingViewModel.OnboardingStep.COMPLETE

            if (!isTerminal) {
                HorizontalDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    val canGoBack = currentIndex > 1 // Not WELCOME or CSV_IMPORT as first nav step
                    TextButton(
                        onClick = {
                            // go back by manipulating step manually via going to previous
                            val prev = com.periodizeai.app.viewmodels.OnboardingViewModel.OnboardingStep.entries.getOrNull(currentIndex - 1)
                        },
                        enabled = canGoBack,
                    ) {
                        Text("Back")
                    }

                    Button(
                        onClick = {
                            when (state.currentStep) {
                                com.periodizeai.app.viewmodels.OnboardingViewModel.OnboardingStep.STICKING_POINTS ->
                                    vm.generatePlanAndFinish()
                                else -> vm.advance()
                            }
                        },
                        enabled = !state.isGeneratingPlan,
                    ) {
                        val label = when (state.currentStep) {
                            com.periodizeai.app.viewmodels.OnboardingViewModel.OnboardingStep.STICKING_POINTS -> "Generate Plan"
                            else -> "Next"
                        }
                        Text(label)
                    }
                }
            }
        }
    }
}

//bb  Stepsbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb 

@Composable
private fun WelcomeStep(onGetStarted: () -> Unit) {
    Spacer(Modifier.height(60.dp))
    Text(
        text = "JUGGERNAUT",
        fontSize = 42.sp,
        fontWeight = FontWeight.Black,
        color = MaterialTheme.colorScheme.primary,
        letterSpacing = 4.sp,
    )
    Spacer(Modifier.height(16.dp))
    Text(
        text = "Science-based strength programming\nbased on the Juggernaut Method",
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(60.dp))
    Button(
        onClick = onGetStarted,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Get Started", fontSize = 18.sp)
    }
}

@Composable
private fun CsvImportStep(
    isImporting: Boolean,
    importResult: Any?,
    importError: String?,
    onSkip: () -> Unit,
    onImport: () -> Unit,
) {
    StepHeader(
        title = "Import History",
        subtitle = "Import your workout history from a CSV file to get personalized strength level suggestions.",
    )
    Spacer(Modifier.height(32.dp))

    if (isImporting) {
        CircularProgressIndicator()
    } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                .padding(16.dp),
        ) {
            Text(
                "CSV format: date, exercise, weight, reps, rpe (optional)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onImport,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Import CSV")
        }
        Spacer(Modifier.height(12.dp))
        importError?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
        }
        OutlinedButton(
            onClick = onSkip,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Skip for Now")
        }
    }
}

@Composable
private fun TrainingDaysStep(
    trainingDays: Int,
    planWeeksDescription: String,
    onDaysChanged: (Int) -> Unit,
) {
    StepHeader(
        title = "Training Days",
        subtitle = "How many days per week can you train?",
    )
    Spacer(Modifier.height(32.dp))

    Text(
        text = "$trainingDays days / week",
        style = MaterialTheme.typography.displaySmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
    )
    Spacer(Modifier.height(8.dp))
    Slider(
        value = trainingDays.toFloat(),
        onValueChange = { onDaysChanged(it.toInt()) },
        valueRange = 2f..6f,
        steps = 3,
        modifier = Modifier.fillMaxWidth(),
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("2", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("6", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (planWeeksDescription.isNotEmpty()) {
        Spacer(Modifier.height(16.dp))
        Text(
            "Estimated plan length: $planWeeksDescription",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun GoalStep(
    selectedGoal: TrainingGoal,
    onGoalSelected: (TrainingGoal) -> Unit,
) {
    StepHeader(
        title = "Training Goal",
        subtitle = "What is your primary training goal?",
    )
    Spacer(Modifier.height(24.dp))

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TrainingGoal.entries.forEach { goal ->
            val isSelected = goal == selectedGoal
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        shape = RoundedCornerShape(12.dp),
                    )
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        RoundedCornerShape(12.dp),
                    )
                    .clickable { onGoalSelected(goal) }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    goal.raw,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.weight(1f),
                )
                if (isSelected) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun BodyWeightStep(
    bodyWeight: Double,
    unit: WeightUnit,
    onBodyWeightChanged: (Double) -> Unit,
    onUnitChanged: (WeightUnit) -> Unit,
) {
    StepHeader(
        title = "Body Weight",
        subtitle = "Enter your current body weight.",
    )
    Spacer(Modifier.height(32.dp))

    // Unit toggle
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        WeightUnit.entries.forEach { u ->
            FilterChip(
                selected = unit == u,
                onClick = { onUnitChanged(u) },
                label = { Text(u.raw.uppercase()) },
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
    Spacer(Modifier.height(24.dp))

    var bodyWeightText by remember(bodyWeight) { mutableStateOf(bodyWeight.toInt().toString()) }
    OutlinedTextField(
        value = bodyWeightText,
        onValueChange = { text ->
            bodyWeightText = text
            text.toDoubleOrNull()?.let { onBodyWeightChanged(it) }
        },
        label = { Text("Body Weight (${unit.raw})") },
        suffix = { Text(unit.raw) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
}

@Composable
private fun AboutYouStep(
    sex: UserSex,
    trainingAgeYears: Int,
    userHeight: Double,
    deadliftStance: DeadliftStance,
    onSexChanged: (UserSex) -> Unit,
    onTrainingAgeChanged: (Int) -> Unit,
    onHeightChanged: (Double) -> Unit,
    onDeadliftStanceChanged: (DeadliftStance) -> Unit,
) {
    StepHeader(
        title = "About You",
        subtitle = "Help us tailor your program.",
    )
    Spacer(Modifier.height(24.dp))

    // Sex
    Text("Sex", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        UserSex.entries.forEach { s ->
            FilterChip(
                selected = sex == s,
                onClick = { onSexChanged(s) },
                label = { Text(s.raw) },
            )
        }
    }
    Spacer(Modifier.height(24.dp))

    // Height
    var heightText by remember(userHeight) { mutableStateOf(userHeight.toInt().toString()) }
    OutlinedTextField(
        value = heightText,
        onValueChange = { text ->
            heightText = text
            text.toDoubleOrNull()?.let { onHeightChanged(it) }
        },
        label = { Text("Height (inches)") },
        suffix = { Text("in") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
    Spacer(Modifier.height(24.dp))

    // Training age
    Text(
        "Training Age: $trainingAgeYears year${if (trainingAgeYears == 1) "" else "s"}",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Slider(
        value = trainingAgeYears.toFloat(),
        onValueChange = { onTrainingAgeChanged(it.toInt()) },
        valueRange = 0f..20f,
        steps = 19,
        modifier = Modifier.fillMaxWidth(),
    )
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("0", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("20", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Spacer(Modifier.height(24.dp))

    // Deadlift stance
    Text("Deadlift Stance", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DeadliftStance.entries.forEach { stance ->
            FilterChip(
                selected = deadliftStance == stance,
                onClick = { onDeadliftStanceChanged(stance) },
                label = { Text(stance.raw) },
            )
        }
    }
}

@Composable
private fun StrengthLevelStep(
    selected: StrengthLevel,
    suggested: StrengthLevel?,
    onSelected: (StrengthLevel) -> Unit,
) {
    StepHeader(
        title = "Strength Level",
        subtitle = "Select your current strength level.",
    )
    suggested?.let {
        Spacer(Modifier.height(12.dp))
        Text(
            "Suggested based on your history: ${it.raw}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
    }
    Spacer(Modifier.height(24.dp))

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        StrengthLevel.entries.forEach { level ->
            val isSelected = level == selected
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        shape = RoundedCornerShape(12.dp),
                    )
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        RoundedCornerShape(12.dp),
                    )
                    .clickable { onSelected(level) }
                    .padding(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        level.raw,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    if (isSelected) Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    if (level == suggested) {
                        Spacer(Modifier.width(4.dp))
                        Text("b&", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                    }
                }
                Text(
                    level.shortDescription,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    level.benchmarkDescription,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StickingPointsStep(
    squatPoint: StickingPoint?,
    benchPoint: StickingPoint?,
    deadliftPoint: StickingPoint?,
    onSquatChanged: (StickingPoint?) -> Unit,
    onBenchChanged: (StickingPoint?) -> Unit,
    onDeadliftChanged: (StickingPoint?) -> Unit,
) {
    StepHeader(
        title = "Sticking Points",
        subtitle = "Where do you struggle most? (Optionalb  select one per lift)",
    )
    Spacer(Modifier.height(24.dp))

    val squatPoints = StickingPoint.entries.filter { it.liftCategory == com.periodizeai.app.models.LiftCategory.SQUAT }
    val benchPoints = StickingPoint.entries.filter { it.liftCategory == com.periodizeai.app.models.LiftCategory.BENCH }
    val deadliftPoints = StickingPoint.entries.filter { it.liftCategory == com.periodizeai.app.models.LiftCategory.DEADLIFT }

    StickingPointSection(
        title = "Squat",
        points = squatPoints,
        selected = squatPoint,
        onSelected = { onSquatChanged(if (it == squatPoint) null else it) },
    )
    Spacer(Modifier.height(16.dp))
    StickingPointSection(
        title = "Bench",
        points = benchPoints,
        selected = benchPoint,
        onSelected = { onBenchChanged(if (it == benchPoint) null else it) },
    )
    Spacer(Modifier.height(16.dp))
    StickingPointSection(
        title = "Deadlift",
        points = deadliftPoints,
        selected = deadliftPoint,
        onSelected = { onDeadliftChanged(if (it == deadliftPoint) null else it) },
    )
}

@Composable
private fun StickingPointSection(
    title: String,
    points: List<StickingPoint>,
    selected: StickingPoint?,
    onSelected: (StickingPoint) -> Unit,
) {
    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(8.dp))
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        points.forEach { sp ->
            FilterChip(
                selected = sp == selected,
                onClick = { onSelected(sp) },
                label = { Text(sp.raw, style = MaterialTheme.typography.bodySmall) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun GeneratingStep() {
    Spacer(Modifier.height(80.dp))
    CircularProgressIndicator(modifier = Modifier.size(64.dp))
    Spacer(Modifier.height(24.dp))
    Text(
        "Generating your planb&",
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(12.dp))
    Text(
        "Calculating volumes, intensities, and progressive overload based on the Juggernaut Method.",
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun CompleteStep(onLetsGo: () -> Unit) {
    Spacer(Modifier.height(60.dp))
    Icon(
        Icons.Default.CheckCircle,
        contentDescription = null,
        modifier = Modifier.size(80.dp),
        tint = MaterialTheme.colorScheme.primary,
    )
    Spacer(Modifier.height(24.dp))
    Text(
        "You're All Set!",
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(12.dp))
    Text(
        "Your personalized Juggernaut Method program is ready.",
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(48.dp))
    Button(
        onClick = onLetsGo,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Let's Go!", fontSize = 18.sp)
    }
}

//bb  Shared helpersbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb 

@Composable
private fun StepHeader(title: String, subtitle: String) {
    Spacer(Modifier.height(16.dp))
    Text(
        title,
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
    )
    Spacer(Modifier.height(8.dp))
    Text(
        subtitle,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
