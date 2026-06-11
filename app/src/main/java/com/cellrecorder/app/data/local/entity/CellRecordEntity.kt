package com.cellrecorder.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "cell_records",
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
        Index("timestamp"),
        Index("simSlotIndex", "rat"),
        Index("bandNumber")
    ]
)
data class CellRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionId: Long,
    val timestamp: Long,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val accuracy: Float,
    val relativeX: Double? = null,
    val relativeY: Double? = null,
    val rat: String,
    val networkTypeCode: Int? = null,
    val fullCellIdentity: Long? = null,
    val enbOrGnbId: Long? = null,
    val lcid: Int? = null,
    val cellIdBitLength: Int? = null,
    val pci: Int? = null,
    val tac: Int? = null,
    val bandNumber: Int? = null,
    val earfcn: Int? = null,
    val bandwidthKhz: Int? = null,
    val rsrp: Int? = null,
    val rsrq: Int? = null,
    val sinr: Int? = null,
    val rssi: Int? = null,
    val cqi: Int? = null,
    val timingAdvance: Int? = null,
    val mcc: String? = null,
    val mnc: String? = null,
    val subscriptionId: Int? = null,
    val simSlotIndex: Int? = null,
    val avgLatencyMs: Double? = null,
    val packetLossPct: Double? = null,
    val isLocationEstimated: Boolean = false,
    val locationSource: String = "GPS",
    val anchorEnbOrGnbId: Long? = null,
    val anchorLcid: Int? = null,
    val anchorPci: Int? = null,
    val anchorTac: Int? = null,
    val anchorBandNumber: Int? = null,
    val anchorEarfcn: Int? = null,
    val anchorBandwidthKhz: Int? = null,
    val anchorRsrp: Int? = null,
    val anchorRsrq: Int? = null,
    val anchorSinr: Int? = null,
    val anchorRssi: Int? = null,
    val anchorCqi: Int? = null,
    val anchorTimingAdvance: Int? = null
)