package com.cellrecorder.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "speed_test_records",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("sessionId"),
        Index("timestamp")
    ]
)
data class SpeedTestRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionId: Long,
    val timestamp: Long,
    val downloadBps: Long?,
    val uploadBps: Long?,
    val serverName: String?,
    val serverHost: String?,
    val serverLocation: String?,
    val serverId: Long?,
    val dataSimSlotIndex: Int?,
    val ratAtTest: String?,
    val rsrpAtTest: Int?,
    val bandAtTest: Int?,
    val succeeded: Boolean,
    val errorMessage: String?,
    val networkType: String?
)