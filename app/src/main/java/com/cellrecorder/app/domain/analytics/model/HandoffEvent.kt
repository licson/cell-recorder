package com.cellrecorder.app.domain.analytics.model

enum class HandoffType {
    INTER_SITE,
    INTRA_SITE_PCI_CHANGE
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
    val rat: String = ""
)