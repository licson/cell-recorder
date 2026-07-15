package com.cellrecorder.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.cellrecorder.app.data.local.entity.SpeedTestRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SpeedTestRecordDao {

    @Insert
    suspend fun insert(record: SpeedTestRecordEntity): Long

    @Insert
    suspend fun insertAll(records: List<SpeedTestRecordEntity>)

    @Query("SELECT * FROM speed_test_records WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getBySessionId(sessionId: Long): Flow<List<SpeedTestRecordEntity>>

    @Query("SELECT * FROM speed_test_records WHERE sessionId = :sessionId AND timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp ASC")
    fun getBySessionIdAndTimestampRange(sessionId: Long, startTime: Long, endTime: Long): Flow<List<SpeedTestRecordEntity>>

    @Query("SELECT * FROM speed_test_records ORDER BY timestamp DESC")
    fun getAll(): Flow<List<SpeedTestRecordEntity>>

    @Query("SELECT COUNT(*) FROM speed_test_records")
    fun getTotalCount(): Flow<Int>

    @Query("SELECT AVG(downloadBps) FROM speed_test_records WHERE downloadSucceeded = 1 AND downloadBps IS NOT NULL")
    fun getAvgDownloadBps(): Flow<Double?>

    @Query("SELECT AVG(uploadBps) FROM speed_test_records WHERE uploadSucceeded = 1 AND uploadBps IS NOT NULL")
    fun getAvgUploadBps(): Flow<Double?>

    @Query("SELECT CAST(SUM(CASE WHEN downloadSucceeded = 1 THEN 1 ELSE 0 END) AS REAL) / CAST(COUNT(*) AS REAL) FROM speed_test_records WHERE errorMessage != 'SKIPPED_WIFI' OR errorMessage IS NULL")
    fun getSuccessRate(): Flow<Double?>

    @Query("SELECT COUNT(*) FROM speed_test_records WHERE sessionId = :sessionId")
    fun getCountBySessionId(sessionId: Long): Flow<Int>

    @Query("SELECT * FROM speed_test_records WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getBySessionIdOnce(sessionId: Long): List<SpeedTestRecordEntity>

    @Query("DELETE FROM speed_test_records WHERE sessionId = :sessionId")
    suspend fun deleteBySessionId(sessionId: Long)
}