package com.cellrecorder.app.ui.detail

import androidx.compose.ui.graphics.Color
import com.cellrecorder.app.data.local.entity.CellRecordEntity

enum class MapDisplayMode(val label: String) {
    SIGNAL_TRAILS("Signal Trails"),
    PACKET_LOSS("Packet Loss"),
    CELL_ID("Cell ID"),
    RAT("RAT"),
    BAND("Band")
}

fun packetLossColorArgb(loss: Double?): Int = when {
    loss == null -> 0xFF9E9E9E.toInt()
    loss == 0.0 -> 0xFF4CAF50.toInt()
    loss <= 20.0 -> 0xFF00BCD4.toInt()
    loss <= 40.0 -> 0xFFFF9800.toInt()
    else -> 0xFFF44336.toInt()
}

fun rsrpColorArgb(rsrp: Int?): Int = when {
    rsrp == null -> 0xFF9E9E9E.toInt()
    rsrp > -80 -> 0xFF4CAF50.toInt()
    rsrp > -90 -> 0xFF00BCD4.toInt()
    rsrp > -100 -> 0xFFFF9800.toInt()
    else -> 0xFFF44336.toInt()
}

fun rsrpColor(rsrp: Int?): Color = when {
    rsrp == null -> Color.Gray
    rsrp > -80 -> Color(0xFF4CAF50)
    rsrp > -90 -> Color(0xFF00BCD4)
    rsrp > -100 -> Color(0xFFFF9800)
    else -> Color(0xFFF44336)
}

fun ratColorArgb(rat: String): Int = when {
    rat.startsWith("5G") -> 0xFF00BCD4.toInt()
    rat.startsWith("4G") -> 0xFF2196F3.toInt()
    rat == "3G" -> 0xFFFF9800.toInt()
    rat == "2G" -> 0xFFF44336.toInt()
    else -> 0xFF9E9E9E.toInt()
}

fun ratColor(rat: String): Color = when {
    rat.startsWith("5G") -> Color(0xFF00BCD4)
    rat.startsWith("4G") -> Color(0xFF2196F3)
    rat == "3G" -> Color(0xFFFF9800)
    rat == "2G" -> Color(0xFFF44336)
    else -> Color.Gray
}

fun formatCellId(record: CellRecordEntity): String {
    if (record.enbOrGnbId != null && record.lcid != null) {
        return "${record.enbOrGnbId}:${record.lcid}"
    }
    return record.fullCellIdentity?.toString() ?: "---"
}