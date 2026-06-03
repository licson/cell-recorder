package com.cellrecorder.app.domain.analytics.model

data class HistogramBin(
    val label: String,
    val count: Int,
    val countLabel: String
)