package com.periodizeai.app.ui.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.periodizeai.app.viewmodels.DashboardViewModel

@Composable
fun MevMrvCard(data: List<DashboardViewModel.MevMrvLiftData>, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Volume Status",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
            HorizontalDivider()
            // Header row
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Lift", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.weight(1.5f))
                Text("MEV–MRV", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.weight(2f))
                Text("Sets", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.width(36.dp))
                Text("Status", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.width(56.dp))
            }
            data.forEach { lift ->
                MevMrvRow(lift)
            }
        }
    }
}

@Composable
private fun MevMrvRow(lift: DashboardViewModel.MevMrvLiftData) {
    val statusColor = when (lift.status) {
        "under"   -> Color(0xFFFFC107)  // Yellow
        "optimal" -> Color(0xFF4CAF50)  // Green
        "over"    -> Color(0xFFF44336)  // Red
        else      -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    }
    val statusLabel = when (lift.status) {
        "under"   -> "Under"
        "optimal" -> "✓ OK"
        "over"    -> "Over"
        else      -> "–"
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = lift.liftName,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1.5f),
        )
        Text(
            text = "${lift.mev}–${lift.mrv} sets",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.weight(2f),
        )
        Text(
            text = "${lift.currentSets}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(36.dp),
        )
        Text(
            text = statusLabel,
            style = MaterialTheme.typography.labelSmall,
            color = statusColor,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(56.dp),
        )
    }
}
