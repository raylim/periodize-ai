package com.periodizeai.app.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.periodizeai.app.models.BlockPhase
import com.periodizeai.app.viewmodels.DashboardViewModel

private fun blockPhaseColor(phase: BlockPhase): Color = when (phase) {
    BlockPhase.HYPERTROPHY -> Color(0xFF1565C0)   // Blue
    BlockPhase.STRENGTH    -> Color(0xFFE65100)   // Orange
    BlockPhase.PEAKING     -> Color(0xFFB71C1C)   // Red
    BlockPhase.MEET_PREP   -> Color(0xFF6A1B9A)   // Purple
    BlockPhase.BRIDGE      -> Color(0xFF546E7A)   // Grey-blue
}

@Composable
fun TrainingProgressChart(
    bars: List<DashboardViewModel.WeekBarData>,
    modifier: Modifier = Modifier,
) {
    val currentWeekHighlight = MaterialTheme.colorScheme.primary
    val completedAlpha = 0.35f
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Training Plan",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
            ) {
                val totalBars = bars.size
                if (totalBars == 0) return@Canvas

                val gapFraction = 0.15f
                val totalGaps = totalBars - 1
                val barWidth = (size.width - totalGaps * (size.width * gapFraction / totalBars)) / totalBars
                val gap = barWidth * gapFraction

                for ((idx, bar) in bars.withIndex()) {
                    val x = idx * (barWidth + gap)
                    val barHeight = (bar.heightFraction * size.height).toFloat().coerceAtLeast(4f)
                    val y = size.height - barHeight

                    val baseColor = if (bar.isCurrent) currentWeekHighlight else blockPhaseColor(bar.phase)
                    val barColor = if (bar.isCompleted) baseColor.copy(alpha = completedAlpha) else baseColor

                    drawRect(
                        color = barColor,
                        topLeft = Offset(x, y),
                        size = Size(barWidth, barHeight),
                    )

                    // Mark current week with a top indicator
                    if (bar.isCurrent) {
                        drawRect(
                            color = currentWeekHighlight,
                            topLeft = Offset(x, y - 4f),
                            size = Size(barWidth, 4f),
                        )
                    }

                    // Phase change divider
                    if (bar.isNewBlock && idx > 0) {
                        val divX = x - gap / 2f
                        drawLine(
                            color = onSurfaceColor.copy(alpha = 0.2f),
                            start = Offset(divX, 0f),
                            end = Offset(divX, size.height),
                            strokeWidth = 1.dp.toPx(),
                        )
                    }
                }
            }

            // Legend
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                val phases = bars.map { it.phase }.distinct()
                phases.forEach { phase ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        Canvas(Modifier.size(8.dp)) {
                            drawRect(color = blockPhaseColor(phase))
                        }
                        Text(
                            text = phase.shortName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        )
                    }
                }
            }
        }
    }
}
