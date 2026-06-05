package com.cellrecorder.app.ui.liveinfo

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cellrecorder.app.service.SimLiveState
import com.cellrecorder.app.ui.detail.ratColor
import com.cellrecorder.app.ui.detail.replay.MetricChart

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveInfoScreen(
    viewModel: LiveInfoViewModel = hiltViewModel()
) {
    val liveSimStates by viewModel.liveSimStates.collectAsStateWithLifecycle()
    val rsrpHistory by viewModel.rsrpHistory.collectAsStateWithLifecycle()
    val sinrHistory by viewModel.sinrHistory.collectAsStateWithLifecycle()
    val pingLatencyHistory by viewModel.pingLatencyHistory.collectAsStateWithLifecycle()
    val packetLossHistory by viewModel.packetLossHistory.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Live Cell Info") }
            )
        }
    ) { padding ->
        if (liveSimStates.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No cell data available.\nEnsure cellular radios are active.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(liveSimStates, key = { it.subscriptionId }) { sim ->
                    LiveSimCard(
                        sim = sim,
                        rsrpHistory = rsrpHistory[sim.subscriptionId] ?: emptyList(),
                        sinrHistory = sinrHistory[sim.subscriptionId] ?: emptyList()
                    )
                }
                item {
                    PingCard(
                        pingLatencyHistory = pingLatencyHistory,
                        packetLossHistory = packetLossHistory
                    )
                }
            }
        }
    }
}

@Composable
private fun LiveSimCard(
    sim: SimLiveState,
    rsrpHistory: List<Int?>,
    sinrHistory: List<Int?>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "SIM ${sim.simSlotIndex + 1}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                LiveStatItem("PLMN", sim.plmn, Modifier.weight(1f))
                LiveStatItem("RAT", sim.rat, Modifier.weight(1f), valueColor = ratColor(sim.rat))
                LiveStatItem("Band", sim.bandNumber, Modifier.weight(1f))
                LiveStatItem("ARFCN", sim.earfcn, Modifier.weight(1f))
            }
            Spacer(Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                LiveStatItem("Cell ID", sim.cellId, Modifier.weight(1f), valueFontFamily = FontFamily.Monospace)
                LiveStatItem("PCI", sim.pci, Modifier.weight(1f))
                LiveStatItem("TAC", sim.tac, Modifier.weight(1f))
            }
            Spacer(Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                LiveStatItem("RSRP", sim.rsrp, Modifier.weight(1f))
                LiveStatItem("RSRQ", sim.rsrq, Modifier.weight(1f))
                LiveStatItem("SINR", sim.sinr, Modifier.weight(1f))
            }

            if (sim.caBands.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                LiveStatItem("CA Bands", sim.caBands.joinToString(", "), Modifier.fillMaxWidth())
            }

            if (rsrpHistory.isNotEmpty() || sinrHistory.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MetricChart(
                        label = "RSRP",
                        values = rsrpHistory.map { it?.toFloat() },
                        unit = "dBm",
                        currentIndex = rsrpHistory.size - 1,
                        color = Color(0xFF2196F3),
                        modifier = Modifier.weight(1f)
                    )
                    MetricChart(
                        label = "SINR",
                        values = sinrHistory.map { it?.toFloat() },
                        unit = "dB",
                        currentIndex = sinrHistory.size - 1,
                        color = Color(0xFF00BCD4),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun LiveStatItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    valueFontFamily: FontFamily = FontFamily.Default
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.Start) {
        Text(
            text = value.ifEmpty { "---" },
            style = MaterialTheme.typography.bodyLarge.copy(
                fontFamily = valueFontFamily,
                fontWeight = FontWeight.SemiBold
            ),
            color = valueColor,
            maxLines = 1
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            maxLines = 1
        )
    }
}

@Composable
private fun PingCard(
    pingLatencyHistory: List<Float?>,
    packetLossHistory: List<Float>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Ping",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricChart(
                    label = "Latency",
                    values = pingLatencyHistory,
                    unit = "ms",
                    currentIndex = pingLatencyHistory.size - 1,
                    color = Color(0xFFFF9800),
                    fixedMin = 0f,
                    modifier = Modifier.weight(1f)
                )
                MetricChart(
                    label = "Packet Loss",
                    values = packetLossHistory,
                    unit = "%",
                    currentIndex = packetLossHistory.size - 1,
                    color = Color(0xFFF44336),
                    fixedMin = 0f,
                    fixedMax = 100f,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}