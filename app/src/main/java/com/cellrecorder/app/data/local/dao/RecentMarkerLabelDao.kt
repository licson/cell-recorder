package com.cellrecorder.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.cellrecorder.app.data.local.entity.RecentMarkerLabelEntity

@Dao
interface RecentMarkerLabelDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: RecentMarkerLabelEntity)

    @Query("SELECT * FROM recent_marker_labels WHERE type = :type ORDER BY lastUsed DESC")
    suspend fun getByTypeOrdered(type: String): List<RecentMarkerLabelEntity>

    @Query("SELECT * FROM recent_marker_labels WHERE type = :type AND label = :label")
    suspend fun getByTypeAndLabel(type: String, label: String): RecentMarkerLabelEntity?

    @Query("DELETE FROM recent_marker_labels")
    suspend fun deleteAll()
}
