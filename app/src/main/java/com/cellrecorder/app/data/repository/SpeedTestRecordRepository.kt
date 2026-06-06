package com.cellrecorder.app.data.repository

import com.cellrecorder.app.data.local.dao.SpeedTestRecordDao
import com.cellrecorder.app.data.local.entity.SpeedTestRecordEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpeedTestRecordRepository @Inject constructor(
    private val dao: SpeedTestRecordDao
) {
    suspend fun insert(record: SpeedTestRecordEntity): Long = dao.insert(record)

    suspend fun insertAll(records: List<SpeedTestRecordEntity>) = dao.insertAll(records)

    fun getBySessionId(sessionId: Long): Flow<List<SpeedTestRecordEntity>> = dao.getBySessionId(sessionId)

    fun getBySessionIdAndTimestampRange(sessionId: Long, startTime: Long, endTime: Long): Flow<List<SpeedTestRecordEntity>> =
        dao.getBySessionIdAndTimestampRange(sessionId, startTime, endTime)

    fun getAll(): Flow<List<SpeedTestRecordEntity>> = dao.getAll()

    fun getTotalCount(): Flow<Int> = dao.getTotalCount()

    fun getAvgDownloadBps(): Flow<Double?> = dao.getAvgDownloadBps()

    fun getAvgUploadBps(): Flow<Double?> = dao.getAvgUploadBps()

    fun getSuccessRate(): Flow<Double?> = dao.getSuccessRate()

    fun getCountBySessionId(sessionId: Long): Flow<Int> = dao.getCountBySessionId(sessionId)

    suspend fun getBySessionIdOnce(sessionId: Long): List<SpeedTestRecordEntity> = dao.getBySessionIdOnce(sessionId)

    suspend fun deleteBySessionId(sessionId: Long) = dao.deleteBySessionId(sessionId)
}