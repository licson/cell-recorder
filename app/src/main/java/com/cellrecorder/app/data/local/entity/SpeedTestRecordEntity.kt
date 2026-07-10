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
    /**
     * Wall-clock millisecond timestamp captured at the start of the speedtest
     * (engine entry). For instant bail-outs this is also the effective finish
     * time (see [finishedAt]).
     */
    val timestamp: Long,
    /**
     * Wall-clock millisecond timestamp captured when the speedtest finished.
     * Always non-null. For instant bail-outs (SKIPPED_WIFI, config/selection
     * failure, exception) this equals [timestamp]. For rows inserted before
     * the `finishedAt` column existed (legacy rows), this is `0` and consumers
     * treat `0` as "unknown finish time" — do not compute a duration.
     */
    val finishedAt: Long = 0L,
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