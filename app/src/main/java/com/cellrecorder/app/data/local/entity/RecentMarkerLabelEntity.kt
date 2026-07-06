package com.cellrecorder.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(
    tableName = "recent_marker_labels",
    primaryKeys = ["type", "label"]
)
data class RecentMarkerLabelEntity(
    val type: String,
    val label: String,
    val useCount: Int = 1,
    val lastUsed: Long
)
