package com.cellrecorder.app.ui.detail.analytics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cellrecorder.app.domain.analytics.model.CorrelationBin
import com.cellrecorder.app.domain.analytics.model.SessionAnalytics
import com.cellrecorder.app.domain.analytics.model.SpeedTestSessionAnalytics
import com.cellrecorder.app.data.local.entity.SpeedTestRecordEntity
import com.cellrecorder.app.domain.model.BandResolver
import com.cellrecorder.app.ui.analytics.components.AnomalyInspectorSheet
import com.cellrecorder.app.ui.analytics.components.AnomalyList
import com.cellrecorder.app.ui.analytics.components.CoverageBar
import com.cellrecorder.app.ui.analytics.components.CoverageGapList
import com.cellrecorder.app.ui.analytics.components.ExpandableCorrelationSection
import com.cellrecorder.app.ui.analytics.components.HandoffTimeline
import com.cellrecorder.app.ui.analytics.components.InsightCard
import com.cellrecorder.app.ui.analytics.components.LatencyStatsCard
import com.cellrecorder.app.ui.analytics.components.MetricGrid
import com.cellrecorder.app.ui.analytics.components.MobilityBadge
import com.cellrecorder.app.ui.detail.ratColor

@Composable
fun AnalyticsPanel(
    analytics: SessionAnalytics,
    modifier: Modifier = Modifier,
    speedTestAnalytics: SpeedTestSessionAnalytics? = null,
    speedTestRecords: List<SpeedTestRecordEntity> = emptyList()
) {
    val totalRecords = analytics.timelineSegments.sumOf { it.recordCount }
    val hasAnyData = analytics.ratCoverage.isNotEmpty() ||
            analytics.bandDistributionPerSim.isNotEmpty() ||
            analytics.rsrpHistogram.isNotEmpty()

    var showAnomalySheet by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Section 1: KPIs
        if (analytics.ratCoverage.isNotEmpty() || totalRecords > 0) {
            item(key = "kpi") {
                MetricGrid(analytics = analytics, totalRecords = totalRecords)
            }
        }

        item(key = "coverage") {
            CoverageBar(coverage = analytics.ratCoverage)
        }

        if (analytics.mobilitySegments.isNotEmpty()) {
            item(key = "mobility") {
                MobilityBadge(segments = analytics.mobilitySegments)
            }
        }

        // Section 2: Temporal
        item(key = "timeline") {
            HandoffTimeline(
                segments = analytics.timelineSegments,
                handoffs = analytics.handoffEvents,
                anomalies = analytics.anomalyFlags,
                gaps = analytics.coverageGaps
            )
        }

        // Section 3: Issues
        item(key = "anomalies") {
            AnomalyList(
                anomalies = analytics.anomalyFlags,
                onViewAllClick = { showAnomalySheet = true }
            )
        }

        item(key = "gaps") {
            CoverageGapList(gaps = analytics.coverageGaps)
        }

        // Section 4: Performance
        if (analytics.latencyStats != null) {
            item(key = "latency") {
                LatencyStatsCard(stats = analytics.latencyStats)
            }
        }

        if (analytics.pingHistogram.isNotEmpty()) {
            item(key = "ping_histo") {
                SignalHistogram(
                    title = "Ping Distribution",
                    bins = analytics.pingHistogram
                )
            }
        }

        item(key = "correlations") {
            ExpandableCorrelationSection(correlationBins = analytics.correlationBins)
        }

        // Section 5: Signal Quality
        if (analytics.rsrpHistogram.isNotEmpty()) {
            item(key = "rsrp_histo") {
                SignalHistogram(
                    title = "RSRP Distribution",
                    bins = analytics.rsrpHistogram
                )
            }
        }

        if (analytics.sinrHistogram.isNotEmpty()) {
            item(key = "sinr_histo") {
                SignalHistogram(
                    title = "SINR Distribution",
                    bins = analytics.sinrHistogram
                )
            }
        }

        // Section 6: Network Composition
        if (analytics.ratCoverage.isNotEmpty()) {
            item(key = "rat_header") {
                SectionHeader("RAT Distribution per SIM")
            }
            analytics.ratCoverage.forEachIndexed { index, coverage ->
                if (coverage.percentage > 0) {
                    item(key = "rat_$index") {
                        SimBarCard(
                            simLabel = coverage.rat,
                            items = listOf(
                                StackedItem("Excellent", coverage.excellent, Color(0xFF4CAF50)),
                                StackedItem("Good", coverage.good, Color(0xFF00BCD4)),
                                StackedItem("Fair", coverage.fair, Color(0xFFFF9800)),
                                StackedItem("Poor", coverage.poor, Color(0xFFF44336))
                            ).filter { it.count > 0 },
                            total = coverage.excellent + coverage.good + coverage.fair + coverage.poor
                        )
                    }
                }
            }
        }

        if (analytics.bandDistributionPerSim.isNotEmpty()) {
            item(key = "band_header") {
                SectionHeader("Band Distribution")
            }
            analytics.bandDistributionPerSim.forEach { (simSlot, items) ->
                if (items.isNotEmpty()) {
                    item(key = "band_sim_$simSlot") {
                        val groupedItems = items.groupBy { it.rat.startsWith("5G") }.toSortedMap(compareByDescending { it })
                        val stackedItems = groupedItems.flatMap { (isNr, group) ->
                            group.mapIndexed { index, item ->
                                val color = if (isNr) {
                                    nrColors[index % nrColors.size]
                                } else {
                                    lteColors[index % lteColors.size]
                                }
                                StackedItem(
                                    BandResolver.formatBand(item.bandNumber, earfcn = null, rat = item.rat),
                                    item.count,
                                    color
                                )
                            }
                        }
                        SimBarCard(
                            simLabel = "SIM ${simSlot + 1}",
                            items = stackedItems,
                            total = items.sumOf { it.count }
                        )
                    }
                }
            }
        }

        // Section 7: AI Insights
        item(key = "insight") {
            InsightCard(insights = analytics.insightCards)
        }

        // Section 8: Speed Test (if data exists)
        val st = speedTestAnalytics
        if (st != null && st.sampleCount > 0) {
            item(key = "st_header") {
                SectionHeader("Speed Tests")
            }
            item(key = "st_summary") {
                SpeedTestSummary(analytics = st)
            }
            if (speedTestRecords.isNotEmpty()) {
                item(key = "st_entries_header") {
                    SectionHeader("Speed Test Entries")
                }
                items(speedTestRecords.take(50)) { record ->
                    SpeedTestEntryRow(record)
                }
            }
            if (st.downloadHistogram.isNotEmpty()) {
                item(key = "st_histo") {
                    SignalHistogram(
                        title = "Download Speed Distribution",
                        bins = st.downloadHistogram
                    )
                }
            }
            if (st.downloadByRsrp.isNotEmpty()) {
                item(key = "st_rsrp_corr") {
                    CorrelationChart(
                        title = "Download Speed vs RSRP",
                        yAxisUnit = "Mbps",
                        bins = st.downloadByRsrp
                    )
                }
            }
            if (st.downloadByRat.isNotEmpty()) {
                item(key = "st_rat_corr") {
                    CorrelationChart(
                        title = "Download Speed per RAT",
                        yAxisUnit = "Mbps",
                        bins = st.downloadByRat
                    )
                }
            }
            if (st.downloadBySim.isNotEmpty()) {
                item(key = "st_sim_corr") {
                    CorrelationChart(
                        title = "Download Speed per SIM",
                        yAxisUnit = "Mbps",
                        bins = st.downloadBySim
                    )
                }
            }
            if (!st.uploadByRsrp.isNullOrEmpty()) {
                item(key = "st_upload_corr") {
                    CorrelationChart(
                        title = "Upload Speed vs RSRP",
                        yAxisUnit = "Mbps",
                        bins = st.uploadByRsrp
                    )
                }
            }
        }

        // Empty state
        if (!hasAnyData) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No data available for this session.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showAnomalySheet) {
        AnomalyInspectorSheet(
            anomalies = analytics.anomalyFlags,
            onDismiss = { showAnomalySheet = false }
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
private fun SpeedTestSummary(analytics: SpeedTestSessionAnalytics) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                MetricItem("Tests", "${analytics.sampleCount}")
                MetricItem("Failed", "${analytics.failureCount}")
                MetricItem("Rate", "${"%.0f".format(analytics.successRate * 100)}%")
            }
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                MetricItem("Avg DL", analytics.avgDownloadBps?.let { formatBps(it) } ?: "---")
                MetricItem("P95 DL", analytics.p95DownloadBps?.let { formatBps(it) } ?: "---")
                MetricItem("Avg UL", analytics.avgUploadBps?.let { formatBps(it) } ?: "---")
                MetricItem("P95 UL", analytics.p95UploadBps?.let { formatBps(it) } ?: "---")
            }
            if (analytics.avgDurationMs != null) {
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    MetricItem("Avg Duration", formatDurationBadge(analytics.avgDurationMs!!))
                }
            }
            if (analytics.serverName != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Server: ${analytics.serverName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun MetricItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SpeedTestEntryRow(record: SpeedTestRecordEntity) {
    val hasDuration = record.finishedAt > 0 && record.finishedAt > record.timestamp
    val durationMs = if (hasDuration) record.finishedAt - record.timestamp else null
    val timeStr = remember(record.timestamp) {
        java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date(record.timestamp))
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = timeStr,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            modifier = Modifier.weight(0.3f)
        )
        Text(
            text = if (record.downloadSucceeded) "↓${record.downloadBps?.let { formatBps(it) } ?: "?"}" else "Failed",
            style = MaterialTheme.typography.bodySmall,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            color = if (record.downloadSucceeded) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(0.4f)
        )
        if (hasDuration && durationMs != null) {
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.weight(0.3f)
            ) {
                Text(
                    text = formatDurationBadge(durationMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        } else {
            Spacer(Modifier.weight(0.3f))
        }
    }
}

private fun formatBps(bps: Long): String = when {
    bps >= 1_000_000_000 -> "${"%.1f".format(bps / 1_000_000_000.0)}G"
    bps >= 1_000_000 -> "${"%.1f".format(bps / 1_000_000.0)}M"
    bps >= 1_000 -> "${bps / 1_000}k"
    else -> "${bps}b"
}

private fun formatDurationBadge(ms: Long): String {
    if (ms <= 0) return "0s"
    val seconds = ms / 1000.0
    return when {
        seconds < 10 -> String.format(java.util.Locale.US, "%.1fs", seconds)
        seconds < 60 -> String.format(java.util.Locale.US, "%.0fs", seconds)
        else -> {
            val m = (seconds / 60).toInt()
            val s = (seconds % 60).toInt()
            "${m}m ${s}s"
        }
    }
}

@Composable
private fun SimBarCard(
    simLabel: String,
    items: List<StackedItem>,
    total: Int
) {
    Column {
        Text(
            text = simLabel,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        StackedDistributionBar(items = items, total = total)
    }
}

private data class StackedItem(
    val label: String,
    val count: Int,
    val color: Color
)

private val bandColors = listOf(
    Color(0xFF1565C0), Color(0xFF7B1FA2), Color(0xFFC62828), Color(0xFF2E7D32),
    Color(0xFFE65100), Color(0xFF00838F), Color(0xFF4E342E), Color(0xFF37474F)
)

private val nrColors = listOf(
    Color(0xFF00695C), Color(0xFF00838F), Color(0xFF0097A7), Color(0xFF00BCD4)
)

private val lteColors = listOf(
    Color(0xFF1565C0), Color(0xFF283593), Color(0xFF3F51B5), Color(0xFF5C6BC0)
)

@Composable
private fun StackedDistributionBar(
    items: List<StackedItem>,
    total: Int,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty() || total == 0) return

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val barWidth = size.width
                    var left = 0f
                    items.forEach { item ->
                        val fraction = item.count.toFloat() / total
                        val segWidth = barWidth * fraction
                        drawRect(
                            color = item.color,
                            topLeft = Offset(left, 0f),
                            size = Size(segWidth, size.height)
                        )
                        left += segWidth
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            val legendItems = items.sortedByDescending { it.count }
            val chunked = legendItems.chunked(3)
            chunked.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    row.forEach { item ->
                        val pct = item.count.toFloat() / total * 100f
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(item.color)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "${item.label} ${"%.1f".format(pct)}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (row.size < 3) {
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}