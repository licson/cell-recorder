package com.cellrecorder.app.data.repository

import com.cellrecorder.app.data.local.dao.SessionDao
import com.cellrecorder.app.data.local.entity.SessionEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionRepository @Inject constructor(
    private val sessionDao: SessionDao
) {
    suspend fun create(name: String, createdAt: Long = System.currentTimeMillis()): Long {
        return sessionDao.insert(
            SessionEntity(name = name, createdAt = createdAt)
        )
    }

    fun getAll(): Flow<List<SessionEntity>> = sessionDao.getAll()

    fun getById(id: Long): Flow<SessionEntity?> = sessionDao.getById(id)

    suspend fun updateEndedAt(id: Long, endedAt: Long) =
        sessionDao.updateEndedAt(id, endedAt)

    suspend fun incrementPointCount(id: Long) =
        sessionDao.incrementPointCount(id)

    suspend fun updateName(id: Long, name: String) = sessionDao.updateName(id, name)

    suspend fun updatePrimarySimSlot(id: Long, simSlotIndex: Int?) =
        sessionDao.updatePrimarySimSlot(id, simSlotIndex)

    suspend fun deleteById(id: Long) = sessionDao.deleteById(id)

    fun getTotalDurationMs(): Flow<Long?> = sessionDao.getTotalDurationMs()

    fun getTotalSessionCount(): Flow<Int> = sessionDao.getTotalSessionCount()
}