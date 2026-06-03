package com.cellrecorder.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.cellrecorder.app.data.local.entity.CellRecordCaBandEntity
import com.cellrecorder.app.data.local.entity.CellRecordEntity
import com.cellrecorder.app.data.local.entity.CellRecordWithCaBands
import com.cellrecorder.app.domain.model.BandDistribution
import com.cellrecorder.app.domain.model.BandDistributionPerSim
import com.cellrecorder.app.domain.model.RatDistribution
import com.cellrecorder.app.domain.model.RatDistributionPerSim
import com.cellrecorder.app.domain.model.Sim5GTime
import com.cellrecorder.app.domain.model.SimSlotDistribution
import kotlinx.coroutines.flow.Flow

@Dao
interface CellRecordDao {

    @Insert
    suspend fun insert(record: CellRecordEntity): Long

    @Insert
    suspend fun insertAll(records: List<CellRecordEntity>): List<Long>

    @Insert
    suspend fun insertCaBand(caBand: CellRecordCaBandEntity)

    @Insert
    suspend fun insertCaBands(caBands: List<CellRecordCaBandEntity>)

    @Transaction
    @Query("SELECT * FROM cell_records WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getBySessionIdWithCaBands(sessionId: Long): Flow<List<CellRecordWithCaBands>>

    @Transaction
    @Query("SELECT * FROM cell_records WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getBySessionIdOnceWithCaBands(sessionId: Long): List<CellRecordWithCaBands>

    @Query("SELECT * FROM cell_records WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getBySessionId(sessionId: Long): Flow<List<CellRecordEntity>>

    @Query("SELECT * FROM cell_records WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getBySessionIdOnce(sessionId: Long): List<CellRecordEntity>

    @Query("DELETE FROM cell_records WHERE sessionId = :sessionId")
    suspend fun deleteBySessionId(sessionId: Long)

    @Query("""
        UPDATE cell_records 
        SET enbOrGnbId = :enbOrGnbId, lcid = :lcid, cellIdBitLength = :cellIdBitLength 
        WHERE id = :recordId
    """)
    suspend fun updateSplitForRecord(
        recordId: Long,
        enbOrGnbId: Long?,
        lcid: Int?,
        cellIdBitLength: Int?
    )

    @Query("SELECT COUNT(*) FROM cell_records")
    fun getTotalRecordCount(): Flow<Int>

    @Query("SELECT rat, COUNT(*) AS count FROM cell_records GROUP BY rat ORDER BY count DESC")
    fun getRatDistribution(): Flow<List<RatDistribution>>

    @Query("""
        SELECT bandNumber, COUNT(*) AS count FROM (
            SELECT bandNumber FROM cell_records WHERE bandNumber IS NOT NULL
            UNION ALL
            SELECT bandNumber FROM cell_record_ca_bands WHERE bandNumber IS NOT NULL
        ) GROUP BY bandNumber ORDER BY count DESC LIMIT :limit
    """)
    fun getBandDistribution(limit: Int = 8): Flow<List<BandDistribution>>

    @Query("SELECT simSlotIndex, COUNT(*) AS count FROM cell_records WHERE simSlotIndex IS NOT NULL GROUP BY simSlotIndex ORDER BY simSlotIndex ASC")
    fun getSimSlotDistribution(): Flow<List<SimSlotDistribution>>

    @Query("""
        SELECT simSlotIndex,
               SUM(CASE WHEN rat = '5G_SA' THEN 1 ELSE 0 END) AS saCount,
               SUM(CASE WHEN rat = '5G_NSA' THEN 1 ELSE 0 END) AS nsaCount
        FROM cell_records
        WHERE simSlotIndex IS NOT NULL
        GROUP BY simSlotIndex
        ORDER BY simSlotIndex ASC
    """)
    fun get5GTimePerSim(): Flow<List<Sim5GTime>>

    @Query("SELECT COUNT(*) FROM cell_records WHERE rat != 'UNKNOWN'")
    fun getOnNetworkCount(): Flow<Int>

    @Query("SELECT simSlotIndex, COUNT(*) AS count FROM cell_records WHERE simSlotIndex IS NOT NULL AND rat != 'UNKNOWN' GROUP BY simSlotIndex ORDER BY simSlotIndex ASC")
    fun getOnNetworkPerSim(): Flow<List<SimSlotDistribution>>

    @Query("SELECT simSlotIndex, rat, COUNT(*) AS count FROM cell_records WHERE simSlotIndex IS NOT NULL GROUP BY simSlotIndex, rat ORDER BY simSlotIndex ASC, count DESC")
    fun getRatDistributionPerSim(): Flow<List<RatDistributionPerSim>>

    @Query("""
        SELECT simSlotIndex, bandNumber, COUNT(*) AS count FROM (
            SELECT cr.simSlotIndex, cr.bandNumber 
            FROM cell_records cr 
            WHERE cr.simSlotIndex IS NOT NULL AND cr.bandNumber IS NOT NULL
            UNION ALL
            SELECT cr.simSlotIndex, cb.bandNumber 
            FROM cell_record_ca_bands cb 
            INNER JOIN cell_records cr ON cb.cellRecordId = cr.id 
            WHERE cr.simSlotIndex IS NOT NULL AND cb.bandNumber IS NOT NULL
        ) 
        GROUP BY simSlotIndex, bandNumber 
        ORDER BY simSlotIndex ASC, count DESC
    """)
    fun getBandDistributionPerSim(): Flow<List<BandDistributionPerSim>>
}