package com.cellrecorder.app.ui.statistics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cellrecorder.app.domain.model.BandDistribution
import com.cellrecorder.app.domain.model.RatDistribution
import com.cellrecorder.app.ui.detail.ratColor

private val bandColors = listOf(
    Color(0xFF1565C0), Color(0xFF7B1FA2), Color(0xFFC62828), Color(0xFF2E7D32),
    Color(0xFFE65100), Color(0xFF00838F), Color(0xFF4E342E), Color(0xFF37474F)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    viewModel: StatisticsViewModel = hiltViewModel()
) {
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val ratDistributionPerSim by viewModel.ratDistributionPerSim.collectAsStateWithLifecycle()
    val bandDistributionPerSim by viewModel.bandDistributionPerSim.collectAsStateWithLifecycle()
    val simSlotDist by viewModel.simSlotDistribution.collectAsStateWithLifecycle()
    val fiveGPercent by viewModel.fiveGPercentPerSim.collectAsStateWithLifecycle()
    val onNetworkPerSim by viewModel.onNetworkPerSim.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Statistics") }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SummaryCards(stats = stats)
            }

            if (ratDistributionPerSim.isNotEmpty()) {
                item(key = "rat_header") {
                    SectionHeader("RAT Distribution")
                }
                ratDistributionPerSim.forEach { (simSlotIndex, distributions) ->
                    if (distributions.isNotEmpty()) {
                        item(key = "rat_sim_$simSlotIndex") {
                            SimBarCard(
                                simLabel = "SIM ${simSlotIndex + 1}",
                                items = distributions.map { StackedItem(it.rat, it.count, ratColor(it.rat)) },
                                total = distributions.sumOf { it.count }
                            )
                        }
                    }
                }
            }

            if (bandDistributionPerSim.isNotEmpty()) {
                item(key = "band_header") {
                    SectionHeader("Band Distribution")
                }
                bandDistributionPerSim.forEach { (simSlotIndex, distributions) ->
                    if (distributions.isNotEmpty()) {
                        item(key = "band_sim_$simSlotIndex") {
                            SimBarCard(
                                simLabel = "SIM ${simSlotIndex + 1}",
                                items = distributions.mapIndexed { i, it ->
                                    StackedItem("Band ${it.bandNumber}", it.count, bandColors[i % bandColors.size])
                                },
                                total = distributions.sumOf { it.count }
                            )
                        }
                    }
                }
            }

            if (simSlotDist.isNotEmpty()) {
                item {
                    SectionHeader("Records per SIM")
                }
                items(simSlotDist) { item ->
                    DistributionBar(
                        label = "SIM ${item.simSlotIndex + 1}",
                        count = item.count,
                        total = simSlotDist.sumOf { it.count }
                    )
                }
            }

            if (onNetworkPerSim.isNotEmpty()) {
                item {
                    SectionHeader("Time on Network per SIM")
                }
                items(onNetworkPerSim) { item ->
                    DistributionBar(
                        label = "SIM ${item.simSlotIndex + 1}",
                        count = item.onNetworkCount,
                        total = item.totalRecords,
                        color = Color(0xFF2E7D32)
                    )
                }
            }

            if (fiveGPercent.isNotEmpty()) {
                item {
                    SectionHeader("5G Time per SIM")
                }
                items(fiveGPercent) { item ->
                    FiveGPercentCard(item = item)
                }
            }

            if (stats.totalPoints == 0 && ratDistributionPerSim.isEmpty() && bandDistributionPerSim.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No recording data yet.\nComplete a recording session to see statistics.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryCards(stats: GlobalStats) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SummaryCard(
                label = "Sessions",
                value = stats.totalSessions.toString(),
                modifier = Modifier.weight(1f)
            )
            SummaryCard(
                label = "Total Points",
                value = stats.totalPoints.toString(),
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SummaryCard(
                label = "Total Duration",
                value = formatDuration(stats.totalDurationMs),
                modifier = Modifier.weight(1f)
            )
            SummaryCard(
                label = "On Network",
                value = "${stats.onNetworkPct.toInt()}%",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SummaryCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 8.dp)
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
                        repeat(3 - row.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DistributionBar(
    label: String,
    count: Int,
    total: Int,
    color: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier
) {
    val fraction = if (total > 0) count.toFloat() / total else 0f

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(6.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "$count (${(fraction * 100).toInt()}%)",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(4.dp))
                        .background(color)
                )
            }
        }
    }
}

@Composable
private fun FiveGPercentCard(item: Sim5GPercent) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(6.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "SIM ${item.simSlotIndex + 1}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${"%.1f".format(item.fiveGPct)}% on 5G",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = Color(0xFF00BCD4)
                )
            }
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
            ) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant))
                Row(
                    modifier = Modifier
                        .fillMaxWidth(item.fiveGPct / 100f)
                        .fillMaxHeight()
                ) {
                    if (item.saCount > 0) {
                        Box(
                            Modifier
                                .weight(item.saCount.toFloat())
                                .fillMaxHeight()
                                .background(Color(0xFF00BCD4))
                        )
                    }
                    if (item.nsaCount > 0) {
                        Box(
                            Modifier
                                .weight(item.nsaCount.toFloat())
                                .fillMaxHeight()
                                .background(Color(0xFF4DD0E1))
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "SA: ${"%.1f".format(item.saPct)}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF00BCD4),
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "NSA: ${"%.1f".format(item.nsaPct)}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF4DD0E1),
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "Other: ${"%.1f".format((100f - item.fiveGPct).coerceAtLeast(0f))}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
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
    val hours = min / 60
    val mins = min % 60
    return "${hours}h ${mins}m"
}

