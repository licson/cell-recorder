package com.cellrecorder.app.domain.analytics.model

enum class HandoffType {
    INTER_SITE,
    INTRA_SITE_PCI_CHANGE,
    RAT_CHANGE,
    BAND_CHANGE,
    NSA_ANCHOR_CHANGE,
    UNKNOWN_CELL_CHANGE
}

data class HandoffEvent(
    val timestamp: Long,
    val simSlot: Int,
    val fromEnbOrGnbId: Long?,
    val toEnbOrGnbId: Long?,
    val fromPci: Int?,
    val toPci: Int?,
    val latencyDeltaMs: Double?,
    val packetLossDeltaPct: Double?,
    val type: HandoffType = HandoffType.INTER_SITE,
    val rat: String = "",
    val fromRat: String? = null,
    val toRat: String? = null,
    val fromBand: Int? = null,
    val toBand: Int? = null,
    val fromCellId: Int? = null,
    val toCellId: Int? = null
)