package com.cellrecorder.app.ui.detail

import androidx.compose.ui.graphics.Color
import com.cellrecorder.app.data.local.entity.CellRecordEntity
import com.cellrecorder.app.ui.shared.ratColor as sharedRatColor
import com.cellrecorder.app.ui.shared.ratColorArgb as sharedRatColorArgb
import com.cellrecorder.app.ui.shared.rsrpColor as sharedRsrpColor
import com.cellrecorder.app.ui.shared.rsrpColorArgb as sharedRsrpColorArgb
import com.cellrecorder.app.ui.shared.rsrqColor as sharedRsrqColor
import com.cellrecorder.app.ui.shared.sinrColor as sharedSinrColor
import com.cellrecorder.app.ui.shared.packetLossColorArgb as sharedPacketLossColorArgb
import com.cellrecorder.app.ui.shared.formatCellId as sharedFormatCellId

enum class MapDisplayMode(val label: String) {
    SIGNAL_TRAILS("Signal Trails"),
    PACKET_LOSS("Packet Loss"),
    CELL_ID("Cell ID"),
    RAT("RAT"),
    BAND("Band")
}

fun packetLossColorArgb(loss: Double?): Int = sharedPacketLossColorArgb(loss)

fun rsrpColorArgb(rsrp: Int?): Int = sharedRsrpColorArgb(rsrp)

fun rsrpColor(rsrp: Int?): Color = sharedRsrpColor(rsrp)

fun rsrqColor(rsrq: Int?): Color = sharedRsrqColor(rsrq)

fun sinrColor(sinr: Int?): Color = sharedSinrColor(sinr)

fun ratColorArgb(rat: String): Int = sharedRatColorArgb(rat)

fun ratColor(rat: String): Color = sharedRatColor(rat)

fun formatCellId(record: CellRecordEntity): String = sharedFormatCellId(record)