package com.cellrecorder.app.domain.analytics.model

data class CoverageGap(
    val startTime: Long,
    val endTime: Long,
    val durationMs: Long,
    val lastKnownLat: Double?,
    val lastKnownLng: Double?
)