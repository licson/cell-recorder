package com.cellrecorder.app.ui.analytics.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cellrecorder.app.domain.analytics.model.LatencyStats

@Composable
fun LatencyStatsCard(
    stats: LatencyStats?,
    modifier: Modifier = Modifier
) {
    if (stats == null) return

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            SectionHeader("Latency Summary")
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(label = "Mean", value = "${"%.0f".format(stats.mean)}ms")
                StatItem(label = "p50", value = "${"%.0f".format(stats.p50)}ms")
                StatItem(label = "p95", value = "${"%.0f".format(stats.p95)}ms")
                StatItem(label = "p99", value = "${"%.0f".format(stats.p99)}ms")
                StatItem(label = "Jitter", value = "${"%.0f".format(stats.jitterMs)}ms")
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Based on ${stats.sampleCount} samples",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun StatItem(
    label: String,
    value: String
) {
    Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}