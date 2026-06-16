package com.cellrecorder.app.domain.analytics.model

enum class GapType {
    NO_RAT,
    NO_SERVING_CELL,
    NO_SIGNAL_METRIC,
    WEAK_SIGNAL
}

data class CoverageGap(
    val startTime: Long,
    val endTime: Long,
    val durationMs: Long,
    val lastKnownLat: Double?,
    val lastKnownLng: Double?,
    val type: GapType = GapType.NO_RAT
)