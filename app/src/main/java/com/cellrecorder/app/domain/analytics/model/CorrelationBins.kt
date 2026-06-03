package com.cellrecorder.app.domain.analytics.model

data class CorrelationBins(
    val rsrpPing: List<CorrelationBin> = emptyList(),
    val rsrpLoss: List<CorrelationBin> = emptyList(),
    val sinrPing: List<CorrelationBin> = emptyList(),
    val sinrLoss: List<CorrelationBin> = emptyList()
)