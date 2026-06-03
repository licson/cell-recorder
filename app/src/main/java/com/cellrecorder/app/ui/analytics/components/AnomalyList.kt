package com.cellrecorder.app.ui.analytics.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
    modifier: Modifier = Modifier
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

    var expanded by remember { mutableStateOf(false) }
    val maxVisible = 5
    val visible = if (expanded) anomalies else anomalies.take(maxVisible)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Anomalies (${anomalies.size})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            visible.forEach { anomaly ->
                AnomalyRow(anomaly = anomaly)
                Spacer(Modifier.height(4.dp))
            }

            if (anomalies.size > maxVisible) {
                TextButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text(if (expanded) "Show less" else "Show all (${anomalies.size})")
                }
            }
        }
    }
}

@Composable
private fun AnomalyRow(anomaly: AnomalyFlag) {
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

    Row(
        modifier = Modifier
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
                text = "SIM${anomaly.simSlot + 1} · ${dateFormat.format(Date(anomaly.timestamp))}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}