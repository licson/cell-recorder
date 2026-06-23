package com.cellrecorder.app.ui.detail

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cellrecorder.app.data.local.entity.CellRecordEntity
import com.cellrecorder.app.data.local.entity.CellRecordWithCaBands
import com.cellrecorder.app.domain.model.BandResolver
import com.cellrecorder.app.ui.shared.formatPlmn
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordDetailSheet(
    wrapper: CellRecordWithCaBands,
    onDismiss: () -> Unit
) {
    val record = wrapper.record
    ModalBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "Record Detail",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(16.dp))

            // Primary Cell
            SectionTitle("Primary Cell")
            DetailRow("RAT", record.rat)
            DetailRow("PLMN", formatPlmn(record.mcc, record.mnc))
            DetailRow("Cell ID", com.cellrecorder.app.ui.detail.formatCellId(record))
            DetailRow("PCI", record.pci?.toString() ?: "---")
            DetailRow("TAC", record.tac?.toString() ?: "---")
            DetailRow("Band", BandResolver.formatBand(record.bandNumber, record.earfcn, record.rat))
            DetailRow("ARFCN", record.earfcn?.toString() ?: "---")
            DetailRow("BW", record.bandwidthKhz?.let { "${it} kHz" } ?: "---")
            val rsrp = record.rsrp
            DetailRow("RSRP", rsrp?.toString() ?: "---", valueColor = rsrpColor(rsrp))
            val rsrq = record.rsrq
            DetailRow("RSRQ", rsrq?.toString() ?: "---", valueColor = rsrpColor(rsrq))
            val sinr = record.sinr
            DetailRow("SINR", sinr?.toString() ?: "---", valueColor = rsrpColor(sinr))
            DetailRow("RSSI", record.rssi?.toString() ?: "---")
            DetailRow("CQI", record.cqi?.toString() ?: "---")
            DetailRow("TA", record.timingAdvance?.toString() ?: "---")

            // CA Bands
            if (wrapper.caBands.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                SectionTitle("CA Bands (${wrapper.caBands.size})")
                wrapper.caBands.forEach { ca ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        shape = RoundedCornerShape(4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            DetailRow("Band", "B${ca.bandNumber ?: "?"}")
                            DetailRow("EARFCN", ca.earfcn?.toString() ?: "---")
                            DetailRow("PCI", ca.pci?.toString() ?: "---")
                            val caRsrp = ca.rsrp
                            DetailRow("RSRP", caRsrp?.toString() ?: "---", valueColor = rsrpColor(caRsrp))
                            val caRsrq = ca.rsrq
                            DetailRow("RSRQ", caRsrq?.toString() ?: "---", valueColor = rsrpColor(caRsrq))
                            val caSinr = ca.sinr
                            DetailRow("SINR", caSinr?.toString() ?: "---", valueColor = rsrpColor(caSinr))
                        }
                    }
                }
            }

            // Anchor Cell
            if (record.rat.startsWith("5G_NSA") && record.anchorPci != null) {
                Spacer(Modifier.height(16.dp))
                SectionTitle("Anchor Cell")
                DetailRow("Band", "B${record.anchorBandNumber ?: "?"}")
                DetailRow("EARFCN", record.anchorEarfcn?.toString() ?: "---")
                DetailRow("PCI", record.anchorPci?.toString() ?: "---")
                DetailRow("TAC", record.anchorTac?.toString() ?: "---")
                val aRsrp = record.anchorRsrp
                DetailRow("RSRP", aRsrp?.toString() ?: "---", valueColor = rsrpColor(aRsrp))
                val aRsrq = record.anchorRsrq
                DetailRow("RSRQ", aRsrq?.toString() ?: "---", valueColor = rsrpColor(aRsrq))
                val aSinr = record.anchorSinr
                DetailRow("SINR", aSinr?.toString() ?: "---", valueColor = rsrpColor(aSinr))
            }

            // Location
            Spacer(Modifier.height(16.dp))
            SectionTitle("Location")
            if (record.relativeX != null && record.relativeY != null) {
                DetailRow("relX", String.format(Locale.US, "%.2f m", record.relativeX))
                DetailRow("relY", String.format(Locale.US, "%.2f m", record.relativeY))
            } else {
                DetailRow("Latitude", String.format(Locale.US, "%.6f", record.latitude))
                DetailRow("Longitude", String.format(Locale.US, "%.6f", record.longitude))
                DetailRow("Altitude", String.format(Locale.US, "%.1f m", record.altitude))
                DetailRow("Accuracy", "${record.accuracy} m")
                DetailRow("Source", record.locationSource)
            }

            // Connectivity
            Spacer(Modifier.height(16.dp))
            SectionTitle("Connectivity")
            DetailRow("Latency", record.avgLatencyMs?.let { String.format(Locale.US, "%.1f ms", it) } ?: "---")
            DetailRow("Packet Loss", record.packetLossPct?.let { String.format(Locale.US, "%.1f%%", it) } ?: "---")

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 4.dp)
    )
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold
            ),
            color = valueColor
        )
    }
}
