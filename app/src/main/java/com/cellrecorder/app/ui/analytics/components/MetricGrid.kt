package com.cellrecorder.app.ui.analytics.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cellrecorder.app.domain.analytics.model.SessionAnalytics

@Composable
fun MetricGrid(
    analytics: SessionAnalytics,
    totalRecords: Int,
    modifier: Modifier = Modifier
) {
    val onNetwork = totalRecords - (analytics.ratCoverage.find { it.rat == "UNKNOWN" }?.let {
        (it.percentage / 100.0 * totalRecords).toInt()
    } ?: 0)

    val intraSiteCount = analytics.handoffEvents.count { it.type == com.cellrecorder.app.domain.analytics.model.HandoffType.INTRA_SITE_PCI_CHANGE }

    val metricPairs = listOf<Pair<String, String>>(
        Pair("Avg RSRP", avgRsrpLabel(analytics)),
        Pair("On Network", "${onNetwork * 100 / maxOf(totalRecords, 1)}%"),
        Pair("Handoffs", analytics.handoffEvents.size.toString()),
        Pair("p95 Latency", analytics.latencyStats?.let { "${"%.0f".format(it.p95)}ms" } ?: "---"),
        Pair("Intra-site", intraSiteCount.toString()),
        Pair("Duration", formatDurationShort(analytics))
    )

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader("Session KPIs")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            metricPairs.take(3).forEach { (label, value) ->
                MetricCard(label = label, value = value, modifier = Modifier.weight(1f))
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            metricPairs.drop(3).forEach { (label, value) ->
                MetricCard(label = label, value = value, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MetricCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
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
}

private fun avgRsrpLabel(analytics: SessionAnalytics): String {
    // approximate from histogram midpoints
    val total = analytics.rsrpHistogram.sumOf { it.count }
    if (total == 0) return "---"
    val weighted = analytics.rsrpHistogram.sumOf { bin ->
        val mid = when {
            bin.label.startsWith(">-") -> (-80)
            bin.label.startsWith("-8") -> (-85)
            bin.label.startsWith("-9") -> (-95)
            else -> (-105)
        }
        bin.count * mid
    }
    return "${weighted / total} dBm"
}

private fun formatDurationShort(analytics: SessionAnalytics): String {
    val maxMs = analytics.timelineSegments.maxOfOrNull { it.endTime } ?: return "---"
    val minMs = analytics.timelineSegments.minOfOrNull { it.startTime } ?: return "---"
    val totalSec = (maxMs - minMs) / 1000
    if (totalSec < 60) return "${totalSec}s"
    val min = totalSec / 60
    val sec = totalSec % 60
    if (min < 60) return "${min}m ${sec}s"
    return "${min / 60}h ${min % 60}m"
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 4.dp)
    )
}