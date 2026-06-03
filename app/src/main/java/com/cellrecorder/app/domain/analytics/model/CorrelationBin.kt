package com.cellrecorder.app.domain.analytics.model

data class CorrelationBin(
    val label: String,
    val values: List<SimValue>
)

data class SimValue(
    val simSlotIndex: Int,
    val value: Double?
)