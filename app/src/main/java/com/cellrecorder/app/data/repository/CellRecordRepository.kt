package com.cellrecorder.app.data.repository

import com.cellrecorder.app.data.local.dao.CellRecordDao
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
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CellRecordRepository @Inject constructor(
    private val cellRecordDao: CellRecordDao
) {
    suspend fun insert(record: CellRecordEntity): Long =
        cellRecordDao.insert(record)

    suspend fun insertAll(records: List<CellRecordEntity>): List<Long> =
        cellRecordDao.insertAll(records)

    suspend fun insertCaBands(caBands: List<CellRecordCaBandEntity>) =
        cellRecordDao.insertCaBands(caBands)

    suspend fun insertRecordBatch(
        records: List<CellRecordEntity>,
        caBandsByRecord: List<List<CellRecordCaBandEntity>>
    ): List<Long> = cellRecordDao.insertRecordBatch(records, caBandsByRecord)

    fun getBySessionIdWithCaBands(sessionId: Long): Flow<List<CellRecordWithCaBands>> =
        cellRecordDao.getBySessionIdWithCaBands(sessionId)

    suspend fun getBySessionIdOnceWithCaBands(sessionId: Long): List<CellRecordWithCaBands> =
        cellRecordDao.getBySessionIdOnceWithCaBands(sessionId)

    fun getBySessionId(sessionId: Long): Flow<List<CellRecordEntity>> =
        cellRecordDao.getBySessionId(sessionId)

    suspend fun getBySessionIdOnce(sessionId: Long): List<CellRecordEntity> =
        cellRecordDao.getBySessionIdOnce(sessionId)

    suspend fun deleteBySessionId(sessionId: Long) =
        cellRecordDao.deleteBySessionId(sessionId)

    suspend fun updateSplitForRecord(
        recordId: Long,
        enbOrGnbId: Long?,
        lcid: Int?,
        cellIdBitLength: Int?
    ) = cellRecordDao.updateSplitForRecord(recordId, enbOrGnbId, lcid, cellIdBitLength)

    fun getTotalRecordCount(): Flow<Int> = cellRecordDao.getTotalRecordCount()

    fun getRatDistribution(): Flow<List<RatDistribution>> = cellRecordDao.getRatDistribution()

    fun getBandDistribution(limit: Int = 8): Flow<List<BandDistribution>> = cellRecordDao.getBandDistribution(limit)

    fun getSimSlotDistribution(): Flow<List<SimSlotDistribution>> = cellRecordDao.getSimSlotDistribution()

    fun get5GTimePerSim(): Flow<List<Sim5GTime>> = cellRecordDao.get5GTimePerSim()

    fun getOnNetworkCount(): Flow<Int> = cellRecordDao.getOnNetworkCount()

    fun getOnNetworkPerSim(): Flow<List<SimSlotDistribution>> = cellRecordDao.getOnNetworkPerSim()

    fun getRatDistributionPerSim(): Flow<List<RatDistributionPerSim>> = cellRecordDao.getRatDistributionPerSim()

    fun getBandDistributionPerSim(): Flow<List<BandDistributionPerSim>> = cellRecordDao.getBandDistributionPerSim()

    suspend fun batchResplit(
        sessionId: Long,
        nrBitLen: Int
    ) {
        val records = cellRecordDao.getBySessionIdOnce(sessionId)
        for (record in records) {
            val fullId = record.fullCellIdentity ?: continue
            when (record.rat) {
                "4G", "4G_CA" -> {
                    val enb = fullId shr 8
                    val cid = fullId and 0xFF
                    cellRecordDao.updateSplitForRecord(
                        recordId = record.id,
                        enbOrGnbId = enb,
                        lcid = cid.toInt(),
                        cellIdBitLength = null
                    )
                }
                "5G_SA" -> {
                    val shift = 36 - nrBitLen
                    val gnb = fullId shr shift
                    val mask = (1L shl shift) - 1
                    val clId = fullId and mask
                    cellRecordDao.updateSplitForRecord(
                        recordId = record.id,
                        enbOrGnbId = gnb,
                        lcid = clId.toInt(),
                        cellIdBitLength = nrBitLen
                    )
                }
                "5G_NSA" -> {
                    val shift = 36 - nrBitLen
                    val gnb = fullId shr shift
                    val mask = (1L shl shift) - 1
                    val clId = fullId and mask
                    val anchorEnb = fullId shr 8
                    val anchorCid = fullId and 0xFF
                    cellRecordDao.updateSplitWithAnchorForRecord(
                        recordId = record.id,
                        enbOrGnbId = gnb,
                        lcid = clId.toInt(),
                        cellIdBitLength = nrBitLen,
                        anchorEnbOrGnbId = anchorEnb,
                        anchorLcid = anchorCid.toInt()
                    )
                }
            }
        }
    }
}