package com.cellrecorder.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.cellrecorder.app.data.local.entity.SessionMarkerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionMarkerDao {

    @Insert
    suspend fun insert(marker: SessionMarkerEntity): Long

    @Insert
    suspend fun insertAll(markers: List<SessionMarkerEntity>): List<Long>

    @Update
    suspend fun update(marker: SessionMarkerEntity)

    @Query("SELECT * FROM session_markers WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getBySessionId(sessionId: Long): Flow<List<SessionMarkerEntity>>

    @Query("SELECT * FROM session_markers WHERE id = :id")
    suspend fun getById(id: Long): SessionMarkerEntity?

    @Query("DELETE FROM session_markers WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM session_markers WHERE sessionId = :sessionId")
    suspend fun countBySessionId(sessionId: Long): Int

    @Query("SELECT MAX(seq) FROM session_markers WHERE sessionId = :sessionId")
    suspend fun getMaxSeq(sessionId: Long): Int?
}
