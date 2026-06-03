package com.cellrecorder.app.domain.analytics.model

data class TimelineSegment(
    val startTime: Long,
    val endTime: Long,
    val rat: String,
    val recordCount: Int
)