package com.cellrecorder.app.ui.detail.analytics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cellrecorder.app.domain.analytics.model.SessionAnalytics
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
    modifier: Modifier = Modifier
) {
    val totalRecords = analytics.timelineSegments.sumOf { it.recordCount }
    val hasAnyData = analytics.ratCoverage.isNotEmpty() ||
            analytics.bandDistributionPerSim.isNotEmpty() ||
            analytics.rsrpHistogram.isNotEmpty()

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
            AnomalyList(anomalies = analytics.anomalyFlags)
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
                        SimBarCard(
                            simLabel = "SIM ${simSlot + 1}",
                            items = items.mapIndexed { i, it ->
                                StackedItem("Band ${it.bandNumber}", it.count, bandColors[i % bandColors.size])
                            },
                            total = items.sumOf { it.count }
                        )
                    }
                }
            }
        }

        // Section 7: AI Insights
        item(key = "insight") {
            InsightCard()
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