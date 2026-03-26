package com.periodizeai.app.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.periodizeai.app.database.PeriodizeAIDatabase

// ---------------------------------------------------------------------------
// Data container
// ---------------------------------------------------------------------------

data class WidgetEntry(
    val workoutName: String,
    val focusLabel: String,
    val exerciseNames: List<String>,
    val phaseBadge: String,
    val weekProgress: String,
    val workoutId: String?,
) {
    companion object {
        val empty = WidgetEntry(
            workoutName = "No Plan Active",
            focusLabel = "Open PeriodizeAI to get started",
            exerciseNames = emptyList(),
            phaseBadge = "--",
            weekProgress = "--",
            workoutId = null,
        )
    }
}

// ---------------------------------------------------------------------------
// DB fetch
// ---------------------------------------------------------------------------

private fun fetchWidgetEntry(context: Context): WidgetEntry {
    return try {
        val driver = AndroidSqliteDriver(
            schema = PeriodizeAIDatabase.Schema,
            context = context,
            name = "periodizeai.db",
        )
        val db = PeriodizeAIDatabase(driver)

        val plan = db.trainingPlanQueries.selectActive().executeAsOneOrNull()
            ?: return WidgetEntry.empty

        val blocks = db.trainingBlockQueries.selectByPlan(plan.id).executeAsList()
        val currentBlock = blocks.firstOrNull { it.isCompleted == 0L }
            ?: return WidgetEntry.empty

        val weeks = db.trainingWeekQueries.selectByBlock(currentBlock.id).executeAsList()
        val currentWeek = weeks.firstOrNull { it.isCompleted == 0L }
            ?: return WidgetEntry.empty

        val workouts = db.plannedWorkoutQueries.selectByWeek(currentWeek.id).executeAsList()
        val nextWorkout = workouts.firstOrNull { it.isCompleted == 0L && it.isSkipped == 0L }

        val allWeeks = blocks.flatMap { block ->
            db.trainingWeekQueries.selectByBlock(block.id).executeAsList()
        }
        val totalWeeks = allWeeks.size
        val completedWeeks = allWeeks.count { it.isCompleted == 1L }

        if (nextWorkout == null) {
            return WidgetEntry(
                workoutName = "Week Comp",
                focusLabel = "All workouts done this week",
                exerciseNames = emptyList(),
                phaseBadge = currentBlock.phaseRaw,
                weekProgress = "Week ${completedWeeks + 1} of $totalWeeks",
                workoutId = null,
            )
        }

        val exercises = db.plannedExerciseQueries.selectByWorkout(nextWorkout.id).executeAsList()
        val exerciseNames = exercises.take(4).mapNotNull { pe ->
            pe.exerciseId?.let { exId ->
                db.exerciseQueries.selectById(exId).executeAsOneOrNull()?.name
            }
        }

        WidgetEntry(
            workoutName = nextWorkout.dayLabel,
            focusLabel = nextWorkout.focusRaw,
            exerciseNames = exerciseNames,
            phaseBadge = currentBlock.phaseRaw,
            weekProgress = "Week ${completedWeeks + 1} of $totalWeeks",
            workoutId = nextWorkout.id,
        )
    } catch (_: Exception) {
        WidgetEntry.empty
    }
}

// ---------------------------------------------------------------------------
// Glance widget
// ---------------------------------------------------------------------------

class WorkoutWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entry = fetchWidgetEntry(context)
        provideContent {
            GlanceTheme {
                WorkoutWidgetContent(entry = entry, context = context)
            }
        }
    }
}

private val orangeColor = ColorProvider(
    day = Color(0xFFFF6D00),
    night = Color(0xFFFF8F00),
)

@Composable
private fun WorkoutWidgetContent(entry: WidgetEntry, context: Context) {
    val clickModifier: GlanceModifier = entry.workoutId?.let { wId ->
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("periodizeai://workout/$wId")).apply {
            setPackage(context.packageName)
        }
        GlanceModifier.clickable(actionStartActivity(intent))
    } ?: GlanceModifier

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.background)
            .then(clickModifier)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        contentAlignment = Alignment.TopStart,
    ) {
        Column(modifier = GlanceModifier.fillMaxSize()) {

            // Phase badge
            Text(
                text ${entry.phaseBadge}",
                style = TextStyle(color = orangeColor, fontWeight = FontWeight.Bold),
            )

            Spacer(modifier = GlanceModifier.height(4.dp))

            // Workout name
            Text(
                text = entry.workoutName,
                style = TextStyle(
                    color = GlanceTheme.colors.onBackground,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 2,
            )

            Spacer(modifier = GlanceModifier.height(2.dp))

            // Focus label
            Text(
                text = entry.focusLabel,
                style = TextStyle(color = GlanceTheme.colors.secondary),
                maxLines = 1,
            )

            Spacer(modifier = GlanceModifier.height(8.dp))

            // Exercises (up to 4)
            entry.exerciseNames.forEach { name ->
                Text(
                    text = "b" $name",
                    style = TextStyle(color = GlanceTheme.colors.onBackground),
                    maxLines = 1,
                )
            }

            Spacer(modifier = GlanceModifier.height(6.dp))

            // Week progress footer
            Text(
                text = entry.weekProgress,
                style = TextStyle(color = GlanceTheme.colors.secondary),
                maxLines = 1,
            )
        }
    }
}
