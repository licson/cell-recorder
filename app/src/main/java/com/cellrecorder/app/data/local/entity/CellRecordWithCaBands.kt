package com.cellrecorder.app.data.local.entity

import androidx.room.Embedded
import androidx.room.Relation

data class CellRecordWithCaBands(
    @Embedded
    val record: CellRecordEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "cellRecordId"
    )
    val caBands: List<CellRecordCaBandEntity> = emptyList()
)