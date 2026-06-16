package com.cellrecorder.app.ui.analytics.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cellrecorder.app.domain.analytics.model.CoverageGap
import com.cellrecorder.app.domain.analytics.model.GapType
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun CoverageGapList(
    gaps: List<CoverageGap>,
    modifier: Modifier = Modifier
) {
    if (gaps.isEmpty()) {
        Text(
            text = "No coverage gaps detected",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.padding(vertical = 4.dp)
        )
        return
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Coverage Gaps (${gaps.size})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            gaps.forEach { gap ->
                CoverageGapRow(gap = gap)
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun CoverageGapRow(gap: CoverageGap) {
    val dateFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val durationSec = gap.durationMs / 1000
    val durationLabel = if (durationSec < 60) "${durationSec}s" else "${durationSec / 60}m ${durationSec % 60}s"

    val typeLabel = when (gap.type) {
        GapType.NO_RAT -> "No network coverage"
        GapType.NO_SERVING_CELL -> "No serving cell"
        GapType.NO_SIGNAL_METRIC -> "No signal metrics"
        GapType.WEAK_SIGNAL -> "Weak signal"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "\uD83D\uDEE1\uFE0F",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "$typeLabel for $durationLabel",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "at ${dateFormat.format(Date(gap.startTime))}" +
                        (gap.lastKnownLat?.let { " · ${"%.4f".format(it)}, ${"%.4f".format(gap.lastKnownLng)}" } ?: ""),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}