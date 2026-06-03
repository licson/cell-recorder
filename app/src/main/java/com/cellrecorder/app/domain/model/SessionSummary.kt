package com.cellrecorder.app.domain.model

data class SessionSummary(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val endedAt: Long?,
    val pointCount: Int,
    val primarySimSlot: Int? = null
)