package com.cellrecorder.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "cell_record_ca_bands",
    foreignKeys = [
        ForeignKey(
            entity = CellRecordEntity::class,
            parentColumns = ["id"],
            childColumns = ["cellRecordId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("cellRecordId"), Index("bandNumber")]
)
data class CellRecordCaBandEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val cellRecordId: Long,
    val bandNumber: Int? = null,
    val earfcn: Int? = null,
    val pci: Int? = null,
    val rsrp: Int? = null,
    val rsrq: Int? = null,
    val sinr: Int? = null,
    val rssi: Int? = null,
    val cqi: Int? = null,
    val timingAdvance: Int? = null,
    @ColumnInfo(name = "bandwidthKhz")
    val bandwidthKhz: Int? = null
)