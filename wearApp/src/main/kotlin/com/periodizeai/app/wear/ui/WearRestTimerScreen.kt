package com.periodizeai.app.wear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import kotlinx.coroutines.delay

@Composable
fun WearRestTimerScreen(
    totalSeconds: Int,
    onFinished: () -> Unit,
) {
    var remaining by remember { mutableIntStateOf(totalSeconds) }

    LaunchedEffect(Unit) {
        while (remaining > 0) {
            delay(1_000L)
            remaining -= 1
        }
        onFinished()
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Rest",
            style = MaterialTheme.typography.caption2,
            color = MaterialTheme.colors.secondary,
        )

        Spacer(modifier = Modifier.height(8.dp))

        val progress = if (totalSeconds > 0) remaining.toFloat() / totalSeconds else 0f
        val minutes = remaining / 60
        val seconds = remaining % 60
        val timeText = "%d:%02d".format(minutes, seconds)

        CircularProgressIndicator(
            progress = progress,
            modifier = Modifier.size(80.dp),
            strokeWidth = 6.dp,
            indicatorColor = MaterialTheme.colors.primary,
            trackColor = MaterialTheme.colors.onSurface.copy(alpha = 0.2f),
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = timeText,
            style = MaterialTheme.typography.title2,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = { remaining = (remaining + 30).coerceAtMost(remaining + 60) },
                modifier = Modifier.size(40.dp),
                colors = ButtonDefaults.secondaryButtonColors(),
            ) {
                Text("+30s", style = MaterialTheme.typography.caption2)
            }
            Button(
                onClick = onFinished,
                modifier = Modifier.size(40.dp),
                colors = ButtonDefaults.secondaryButtonColors(),
            ) {
                Text("Skip", style = MaterialTheme.typography.caption2)
            }
        }
    }
}
