package com.cellrecorder.app.domain.analytics.model

data class RatCoverage(
    val rat: String,
    val percentage: Double,
    val durationMs: Long,
    val excellent: Int,
    val good: Int,
    val fair: Int,
    val poor: Int
)