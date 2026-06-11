package com.cellrecorder.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val createdAt: Long,
    val endedAt: Long? = null,
    val pointCount: Int = 0,
    val primarySimSlot: Int? = null,
    val recordingMode: String = "OUTDOOR"
)