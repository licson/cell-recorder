package com.cellrecorder.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.cellrecorder.app.data.local.entity.SessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {

    @Insert
    suspend fun insert(session: SessionEntity): Long

    @Query("SELECT * FROM sessions ORDER BY createdAt DESC")
    fun getAll(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE id = :id")
    fun getById(id: Long): Flow<SessionEntity?>

    @Query("UPDATE sessions SET name = :name WHERE id = :id")
    suspend fun updateName(id: Long, name: String)

    @Query("UPDATE sessions SET endedAt = :endedAt WHERE id = :id")
    suspend fun updateEndedAt(id: Long, endedAt: Long)

    @Query("UPDATE sessions SET pointCount = pointCount + 1 WHERE id = :id")
    suspend fun incrementPointCount(id: Long)

    @Query("UPDATE sessions SET pointCount = (SELECT COUNT(*) FROM cell_records WHERE sessionId = :sessionId) WHERE id = :sessionId")
    suspend fun refreshPointCount(sessionId: Long)

    @Query("UPDATE sessions SET primarySimSlot = :simSlotIndex WHERE id = :sessionId")
    suspend fun updatePrimarySimSlot(sessionId: Long, simSlotIndex: Int?)

    @Delete
    suspend fun delete(session: SessionEntity)

    @Transaction
    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT SUM(endedAt - createdAt) FROM sessions WHERE endedAt IS NOT NULL")
    fun getTotalDurationMs(): Flow<Long?>

    @Query("SELECT COUNT(*) FROM sessions")
    fun getTotalSessionCount(): Flow<Int>
}