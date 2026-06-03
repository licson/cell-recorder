package com.cellrecorder.app.ui.analytics.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cellrecorder.app.domain.analytics.model.MobilitySegment
import com.cellrecorder.app.domain.analytics.model.MobilityType

@Composable
fun MobilityBadge(
    segments: List<MobilitySegment>,
    modifier: Modifier = Modifier
) {
    if (segments.isEmpty()) return

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Mobility",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            val grouped = segments.groupBy { it.type }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                grouped.entries.forEach { (type, segs) ->
                    val totalMs = segs.sumOf { it.endTime - it.startTime }
                    val icon = when (type) {
                        MobilityType.STATIONARY -> "\uD83D\uDCCD"
                        MobilityType.WALKING -> "\uD83D\uDEB6"
                        MobilityType.DRIVING -> "\uD83D\uDE97"
                        MobilityType.INDOOR -> "\uD83C\uDFE0"
                        MobilityType.OUTDOOR -> "\u2601\uFE0F"
                        MobilityType.TUNNEL -> "\uD83D\uDE84"
                    }
                    AssistChip(
                        onClick = {},
                        label = {
                            Text(
                                "$icon ${type.name.lowercase().replaceFirstChar { it.uppercase() }} ${formatDuration(totalMs)}",
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSec = ms / 1000
    if (totalSec < 60) return "${totalSec}s"
    val min = totalSec / 60
    val sec = totalSec % 60
    if (min < 60) return "${min}m ${sec}s"
    return "${min / 60}h ${min % 60}m"
}