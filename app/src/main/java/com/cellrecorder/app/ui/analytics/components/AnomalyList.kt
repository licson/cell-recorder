package com.cellrecorder.app.ui.analytics.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cellrecorder.app.domain.analytics.model.AnomalyFlag
import com.cellrecorder.app.domain.analytics.model.AnomalyType
import com.cellrecorder.app.domain.analytics.model.Severity
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun AnomalyList(
    anomalies: List<AnomalyFlag>,
    modifier: Modifier = Modifier,
    onViewAllClick: () -> Unit = {}
) {
    if (anomalies.isEmpty()) {
        Text(
            text = "No anomalies detected",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.padding(vertical = 4.dp)
        )
        return
    }

    val topFive = remember(anomalies) {
        anomalies
            .sortedWith(
                compareByDescending<AnomalyFlag> { it.severity.ordinal }
                    .thenBy { it.timestamp }
            )
            .take(5)
    }

    val severityCounts = remember(anomalies) {
        anomalies.groupingBy { it.severity }.eachCount()
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Anomalies (${anomalies.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = buildString {
                        append(summaryLabel(severityCounts, Severity.CRITICAL))
                        append(" · ")
                        append(summaryLabel(severityCounts, Severity.WARNING))
                        append(" · ")
                        append(summaryLabel(severityCounts, Severity.INFO))
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(8.dp))

            topFive.forEach { anomaly ->
                AnomalyRow(anomaly = anomaly)
                Spacer(Modifier.height(4.dp))
            }

            if (anomalies.size > 5) {
                TextButton(
                    onClick = onViewAllClick,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text("View all (${anomalies.size})")
                }
            }
        }
    }
}

private fun summaryLabel(counts: Map<Severity, Int>, severity: Severity): String {
    val n = counts[severity] ?: 0
    val label = severity.name.lowercase()
    return if (n == 1) "$n $label" else "$n ${label}s"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnomalyInspectorSheet(
    anomalies: List<AnomalyFlag>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier
    ) {
        AnomalyInspectorContent(anomalies = anomalies)
    }
}

@Composable
private fun AnomalyInspectorContent(
    anomalies: List<AnomalyFlag>
) {
    var selectedTypes by remember { mutableStateOf(AnomalyType.entries.toSet()) }

    val filteredAndSorted = remember(anomalies, selectedTypes) {
        anomalies
            .filter { anomaly -> anomaly.type in selectedTypes }
            .sortedWith(
                compareByDescending<AnomalyFlag> { it.severity.ordinal }
                    .thenBy { it.timestamp }
            )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 200.dp)
            .padding(top = 8.dp)
    ) {
        Text(
            text = "Anomalies",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(Modifier.height(4.dp))

        Text(
            text = "${filteredAndSorted.size} of ${anomalies.size} anomalies",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(Modifier.height(12.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(AnomalyType.entries.toList(), key = { it }) { type ->
                val count = anomalies.count { it.type == type }
                FilterChip(
                    selected = type in selectedTypes,
                    onClick = {
                        selectedTypes = if (type in selectedTypes) {
                            selectedTypes - type
                        } else {
                            selectedTypes + type
                        }
                    },
                    label = { Text("${typeLabel(type)} ($count)") }
                )
            }
            if (selectedTypes.size < AnomalyType.entries.size) {
                item(key = "clear") {
                    TextButton(onClick = { selectedTypes = AnomalyType.entries.toSet() }) {
                        Text("Clear", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        if (filteredAndSorted.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "No anomalies match the selected filters",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { selectedTypes = AnomalyType.entries.toSet() }) {
                        Text("Clear filters")
                    }
                }
            }
        } else {
            VirtualizedAnomalyList(
                anomalies = filteredAndSorted,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(16.dp))
    }
}

private fun typeLabel(type: AnomalyType): String = when (type) {
    AnomalyType.RSRP_DROP -> "RSRP Drop"
    AnomalyType.LATENCY_SPIKE -> "Latency Spike"
    AnomalyType.PCI_FLAP -> "PCI Flap"
    AnomalyType.MISSING_PING_CLUSTER -> "Missing Ping"
}

@Composable
private fun VirtualizedAnomalyList(
    anomalies: List<AnomalyFlag>,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val measuredHeights = remember { mutableStateMapOf<Int, Int>() }
    val density = LocalDensity.current

    val visibleWindow by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val first = info.visibleItemsInfo.firstOrNull()?.index ?: 0
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            first..last
        }
    }

    LazyColumn(
        modifier = modifier,
        state = listState,
        contentPadding = PaddingValues(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(
            count = anomalies.size,
            key = { index -> "${anomalies[index].timestamp}-${anomalies[index].type}-$index" }
        ) { index ->
            if (index in visibleWindow) {
                AnomalyRow(
                    anomaly = anomalies[index],
                    modifier = Modifier.onGloballyPositioned { coordinates ->
                        measuredHeights[index] = coordinates.size.height
                    }
                )
            } else {
                val heightPx = measuredHeights[index]
                val placeholderHeight = if (heightPx != null) {
                    with(density) { heightPx.toDp() }
                } else {
                    56.dp
                }
                Box(modifier = Modifier.fillMaxWidth().height(placeholderHeight))
            }
        }
    }
}

@Composable
private fun AnomalyRow(
    anomaly: AnomalyFlag,
    modifier: Modifier = Modifier
) {
    val severityColor = when (anomaly.severity) {
        Severity.INFO -> Color(0xFF42A5F5)
        Severity.WARNING -> Color(0xFFFFA726)
        Severity.CRITICAL -> Color(0xFFEF5350)
    }

    val typeIcon = when (anomaly.type) {
        AnomalyType.RSRP_DROP -> "\u2B07"
        AnomalyType.LATENCY_SPIKE -> "\u26A1"
        AnomalyType.PCI_FLAP -> "\uD83D\uDD04"
        AnomalyType.MISSING_PING_CLUSTER -> "\u2753"
    }

    val dateFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    val timeText = if (anomaly.endTimestamp > anomaly.timestamp) {
        "${dateFormat.format(Date(anomaly.timestamp))} – ${dateFormat.format(Date(anomaly.endTimestamp))} (${formatDuration(anomaly.timestamp, anomaly.endTimestamp)})"
    } else {
        dateFormat.format(Date(anomaly.timestamp))
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(severityColor.copy(alpha = 0.1f))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(24.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(severityColor)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = typeIcon,
            style = MaterialTheme.typography.labelLarge
        )
        Spacer(Modifier.width(6.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = anomaly.description,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2
            )
            Text(
                text = "SIM${anomaly.simSlot + 1} · $timeText",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatDuration(fromMs: Long, toMs: Long): String {
    val seconds = (toMs - fromMs) / 1000
    if (seconds < 60) return "${seconds}s"
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return if (remainingSeconds > 0) "${minutes}m ${remainingSeconds}s" else "${minutes}m"
}