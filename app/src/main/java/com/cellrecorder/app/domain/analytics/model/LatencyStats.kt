package com.cellrecorder.app.domain.analytics.model

data class LatencyStats(
    val mean: Double,
    val p50: Double,
    val p95: Double,
    val p99: Double,
    val jitterMs: Double,
    val sampleCount: Int
)