package com.cellrecorder.app.domain.analytics.model

enum class MobilityType {
    STATIONARY,
    WALKING,
    DRIVING,
    INDOOR,
    OUTDOOR,
    TUNNEL
}

data class MobilitySegment(
    val startTime: Long,
    val endTime: Long,
    val type: MobilityType
)