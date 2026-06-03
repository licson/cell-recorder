package com.cellrecorder.app.ui.analytics.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cellrecorder.app.domain.analytics.model.CorrelationBins
import com.cellrecorder.app.ui.detail.analytics.CorrelationChart

@Composable
fun ExpandableCorrelationSection(
    correlationBins: CorrelationBins,
    modifier: Modifier = Modifier
) {
    val hasData = correlationBins.rsrpPing.isNotEmpty() ||
            correlationBins.rsrpLoss.isNotEmpty() ||
            correlationBins.sinrPing.isNotEmpty() ||
            correlationBins.sinrLoss.isNotEmpty()
    if (!hasData) return

    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Correlations",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand"
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (correlationBins.rsrpPing.isNotEmpty()) {
                        CorrelationChart(
                            title = "Ping vs RSRP",
                            yAxisUnit = "ms",
                            bins = correlationBins.rsrpPing,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    if (correlationBins.rsrpLoss.isNotEmpty()) {
                        CorrelationChart(
                            title = "Loss vs RSRP",
                            yAxisUnit = "%",
                            bins = correlationBins.rsrpLoss
                        )
                    }
                    if (correlationBins.sinrPing.isNotEmpty()) {
                        CorrelationChart(
                            title = "Ping vs SINR",
                            yAxisUnit = "ms",
                            bins = correlationBins.sinrPing
                        )
                    }
                    if (correlationBins.sinrLoss.isNotEmpty()) {
                        CorrelationChart(
                            title = "Loss vs SINR",
                            yAxisUnit = "%",
                            bins = correlationBins.sinrLoss
                        )
                    }
                }
            }
        }
    }
}