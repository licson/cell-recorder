package com.cellrecorder.app.ui.shared

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cellrecorder.app.data.local.entity.CellRecordWithCaBands
import com.cellrecorder.app.domain.model.BandResolver
import com.cellrecorder.app.service.SimLiveState

/**
 * Structured data for a single CA band, used by [CellInfoPanel].
 */
data class CaBandInfo(
    val bandNumber: String,
    val pci: String,
    val earfcn: String,
    val rat: String,
    val rsrp: String,
    val rsrq: String,
    val sinr: String
)

/**
 * Structured data for a 5G NSA anchor cell, used by [CellInfoPanel].
 */
data class AnchorCellInfo(
    val cellId: String,
    val bandNumber: String,
    val earfcn: String,
    val pci: String,
    val tac: String,
    val rat: String,
    val rsrp: String,
    val rsrq: String,
    val sinr: String
)

/**
 * Unified data model for a single SIM's cell info, used by [CellInfoPanel].
 * Carries pre-formatted strings so the panel is presentation-only.
 */
data class CellInfoData(
    val simSlotIndex: Int,
    val plmn: String,
    val rat: String,
    val tac: String,
    val bandNumber: String,
    val earfcn: String,
    val cellId: String,
    val pci: String,
    val rsrp: String,
    val rsrq: String,
    val sinr: String,
    val caBands: List<CaBandInfo>,
    val anchorCell: AnchorCellInfo?
)

/**
 * Convert a [SimLiveState] (live data) into [CellInfoData].
 */
fun SimLiveState.toCellInfoData(): CellInfoData {
    val bandText = if (caBandDetails.isNotEmpty()) {
        "$bandNumber+${caBandDetails.size}"
    } else bandNumber

    return CellInfoData(
        simSlotIndex = simSlotIndex,
        plmn = plmn,
        rat = rat,
        tac = tac,
        bandNumber = bandText,
        earfcn = earfcn,
        cellId = cellId,
        pci = pci,
        rsrp = rsrp,
        rsrq = rsrq,
        sinr = sinr,
        caBands = caBandDetails.map { ca ->
            CaBandInfo(
                bandNumber = BandResolver.formatBand(ca.band.toIntOrNull(), ca.earfcn, "4G"),
                pci = ca.pci,
                earfcn = ca.earfcn?.toString() ?: "---",
                rat = "4G",
                rsrp = ca.rsrp,
                rsrq = ca.rsrq,
                sinr = ca.sinr
            )
        },
        anchorCell = if (rat.startsWith("5G_NSA") && anchorInfo.isNotEmpty()) {
            AnchorCellInfo(
                cellId = anchorCellId,
                bandNumber = BandResolver.formatBand(anchorBand.toIntOrNull(), anchorArfcn.toIntOrNull(), "4G"),
                earfcn = anchorArfcn,
                pci = anchorPci,
                tac = anchorTac,
                rat = "4G",
                rsrp = anchorRsrp,
                rsrq = anchorRsrq,
                sinr = anchorSinr
            )
        } else null
    )
}

/**
 * Convert a recorded [CellRecordWithCaBands] into [CellInfoData].
 */
fun CellRecordWithCaBands.toCellInfoData(): CellInfoData {
    val record = this.record
    val primaryBand = BandResolver.formatBand(record.bandNumber, record.earfcn, record.rat)
    val bandText = if (caBands.isNotEmpty()) {
        "$primaryBand+${caBands.size}"
    } else primaryBand

    return CellInfoData(
        simSlotIndex = record.simSlotIndex ?: 0,
        plmn = formatPlmn(record.mcc, record.mnc),
        rat = record.rat,
        tac = record.tac?.toString() ?: "---",
        bandNumber = bandText,
        earfcn = record.earfcn?.toString() ?: "---",
        cellId = formatCellId(record),
        pci = record.pci?.toString() ?: "---",
        rsrp = record.rsrp?.toString() ?: "---",
        rsrq = record.rsrq?.toString() ?: "---",
        sinr = record.sinr?.toString() ?: "---",
        caBands = caBands.map { ca ->
            CaBandInfo(
                bandNumber = BandResolver.formatBand(ca.bandNumber, ca.earfcn, "4G"),
                pci = ca.pci?.toString() ?: "---",
                earfcn = ca.earfcn?.toString() ?: "---",
                rat = "4G",
                rsrp = ca.rsrp?.toString() ?: "---",
                rsrq = ca.rsrq?.toString() ?: "---",
                sinr = ca.sinr?.toString() ?: "---"
            )
        },
        anchorCell = if (record.rat.startsWith("5G_NSA") && record.anchorPci != null) {
            AnchorCellInfo(
                cellId = if (record.anchorEnbOrGnbId != null && record.anchorLcid != null) {
                    "${record.anchorEnbOrGnbId}:${record.anchorLcid}"
                } else "---",
                bandNumber = BandResolver.formatBand(record.anchorBandNumber, record.anchorEarfcn, "4G"),
                earfcn = record.anchorEarfcn?.toString() ?: "---",
                pci = record.anchorPci?.toString() ?: "---",
                tac = record.anchorTac?.toString() ?: "---",
                rat = "4G",
                rsrp = record.anchorRsrp?.toString() ?: "---",
                rsrq = record.anchorRsrq?.toString() ?: "---",
                sinr = record.anchorSinr?.toString() ?: "---"
            )
        } else null
    )
}

/**
 * Shared cell info composable used by [RecordingScreen], [ReplayScreen], and [LiveInfoScreen].
 *
 * @param isExpandable When true the panel shows a chevron and collapses anchor/CA details
 *   into a compact state; when false everything is always visible.
 * @param expanded Current expansion state (only meaningful when [isExpandable] is true).
 * @param onExpandToggle Callback invoked when the user taps the expand/collapse area.
 */
@Composable
fun CellInfoPanel(
    data: CellInfoData,
    isExpandable: Boolean = true,
    expanded: Boolean = false,
    onExpandToggle: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val hasExpandableData = data.anchorCell != null || data.caBands.isNotEmpty()
    val showExpanded = if (isExpandable) expanded else true

    Column(modifier = modifier) {
        // Row 1: # (if expandable), PLMN, TAC, RAT, Band, ARFCN, chevron (if expandable)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
            if (isExpandable) {
                StatItem("#", "#${data.simSlotIndex + 1}", weight = 0.5f, valueColor = ratColor(data.rat))
            }
            StatItem("PLMN", data.plmn, weight = 1f)
            StatItem("TAC", data.tac, weight = 0.6f)
            StatItem("RAT", data.rat, weight = 0.8f, valueColor = ratColor(data.rat))
            StatItem("Band", data.bandNumber, weight = 0.6f)
            StatItem("ARFCN", data.earfcn, weight = 0.8f)
            if (isExpandable && hasExpandableData) {
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    modifier = Modifier
                        .size(18.dp)
                        .clickable { onExpandToggle?.invoke() },
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(2.dp))

        // Row 2: Cell ID, PCI, RSRP, RSRQ, SINR
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
            if (isExpandable) {
                Box(Modifier.weight(0.5f)) { }
            }
            StatItem("Cell ID", data.cellId, weight = 1.2f, valueFontFamily = FontFamily.Monospace)
            StatItem("PCI", data.pci, weight = 0.6f)
            val rsrpInt = data.rsrp.toIntOrNull()
            StatItem("RSRP", data.rsrp, weight = 0.7f, valueColor = rsrpColor(rsrpInt))
            val rsrqInt = data.rsrq.toIntOrNull()
            StatItem("RSRQ", data.rsrq, weight = 0.6f, valueColor = rsrqColor(rsrqInt))
            val sinrInt = data.sinr.toIntOrNull()
            StatItem("SINR", data.sinr, weight = 0.6f, valueColor = sinrColor(sinrInt))
        }

        // Compact anchor row (only when collapsed and expandable)
        if (isExpandable && !expanded && data.anchorCell != null) {
            Spacer(Modifier.height(2.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                Box(Modifier.weight(0.5f)) { }
                val aRsrp = data.anchorCell.rsrp.toIntOrNull()
                StatItem(
                    "Anchor",
                    "LTE: ${data.anchorCell.bandNumber} PCI ${data.anchorCell.pci} RSRP ${data.anchorCell.rsrp}",
                    weight = 2.5f,
                    valueColor = rsrpColor(aRsrp)
                )
            }
        }

        // Expanded sections
        if (showExpanded && hasExpandableData) {
            // Anchor cell
            data.anchorCell?.let { anchor ->
                Spacer(Modifier.height(4.dp))
                HorizontalDivider()
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Anchor Cell",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(2.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                    if (isExpandable) Box(Modifier.weight(0.5f)) { }
                    StatItem("Cell ID", anchor.cellId, weight = 1.2f, valueFontFamily = FontFamily.Monospace)
                }
                Spacer(Modifier.height(2.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                    if (isExpandable) Box(Modifier.weight(0.5f)) { }
                    StatItem("Band", anchor.bandNumber, weight = 0.6f)
                    StatItem("ARFCN", anchor.earfcn, weight = 0.8f)
                    StatItem("PCI", anchor.pci, weight = 0.6f)
                    StatItem("TAC", anchor.tac, weight = 0.7f)
                }
                Spacer(Modifier.height(2.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                    if (isExpandable) Box(Modifier.weight(0.5f)) { }
                    val aRsrp = anchor.rsrp.toIntOrNull()
                    val aRsrq = anchor.rsrq.toIntOrNull()
                    val aSinr = anchor.sinr.toIntOrNull()
                    StatItem("RSRP", anchor.rsrp, weight = 0.7f, valueColor = rsrpColor(aRsrp))
                    StatItem("RSRQ", anchor.rsrq, weight = 0.6f, valueColor = rsrqColor(aRsrq))
                    StatItem("SINR", anchor.sinr, weight = 0.6f, valueColor = sinrColor(aSinr))
                }
            }

            // CA bands
            if (data.caBands.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                HorizontalDivider()
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "CA Bands",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(2.dp))
                data.caBands.forEach { ca ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                        if (isExpandable) Box(Modifier.weight(0.5f)) { }
                        StatItem("Band", ca.bandNumber, weight = 0.6f)
                        StatItem("PCI", ca.pci, weight = 0.6f)
                        StatItem("EARFCN", ca.earfcn, weight = 0.8f)
                    }
                    Spacer(Modifier.height(2.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                        if (isExpandable) Box(Modifier.weight(0.5f)) { }
                        val caRsrp = ca.rsrp.toIntOrNull()
                        val caRsrq = ca.rsrq.toIntOrNull()
                        val caSinr = ca.sinr.toIntOrNull()
                        StatItem("RSRP", ca.rsrp, weight = 0.7f, valueColor = rsrpColor(caRsrp))
                        StatItem("RSRQ", ca.rsrq, weight = 0.6f, valueColor = rsrqColor(caRsrq))
                        StatItem("SINR", ca.sinr, weight = 0.6f, valueColor = sinrColor(caSinr))
                    }
                    Spacer(Modifier.height(2.dp))
                }
            }
        }
    }
}

@Composable
private fun RowScope.StatItem(
    label: String,
    value: String,
    weight: Float = 1f,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    valueFontFamily: FontFamily = FontFamily.Default
) {
    Column(modifier = Modifier.weight(weight), horizontalAlignment = Alignment.Start) {
        Text(
            text = value.ifEmpty { "---" },
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = valueFontFamily),
            color = valueColor,
            maxLines = 1
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            maxLines = 1
        )
    }
}
