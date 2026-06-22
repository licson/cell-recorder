package com.cellrecorder.app.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.cellrecorder.app.data.local.AppDatabase
import com.cellrecorder.app.util.TestDataFactory
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@MediumTest
class CellRecordRepositoryTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var repository: CellRecordRepository

    @Inject
    lateinit var sessionRepository: SessionRepository

    @Inject
    lateinit var db: AppDatabase

    private var sessionId: Long = 0

    @Before
    fun setUp() {
        hiltRule.inject()
        sessionId = runBlocking {
            sessionRepository.create(name = "CellRecord Test", recordingMode = "OUTDOOR")
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun insertAndGetBySessionId() = runBlocking {
        val id = repository.insert(TestDataFactory.cellRecord(sessionId = sessionId))
        assertTrue(id > 0)

        val records = repository.getBySessionIdOnce(sessionId)
        assertEquals(1, records.size)
    }

    @Test
    fun insertAll() = runBlocking {
        val records = listOf(
            TestDataFactory.cellRecord(sessionId = sessionId, timestamp = 1000L),
            TestDataFactory.cellRecord(sessionId = sessionId, timestamp = 2000L)
        )
        val ids = repository.insertAll(records)
        assertEquals(2, ids.size)
    }

    @Test
    fun insertCaBands() = runBlocking {
        val recordId = repository.insert(TestDataFactory.cellRecord(sessionId = sessionId))
        repository.insertCaBands(listOf(TestDataFactory.caBand(cellRecordId = recordId)))

        val withCa = repository.getBySessionIdOnceWithCaBands(sessionId)
        assertEquals(1, withCa[0].caBands.size)
    }

    @Test
    fun insertRecordBatch() = runBlocking {
        val records = listOf(
            TestDataFactory.cellRecord(sessionId = sessionId, timestamp = 1000L),
            TestDataFactory.cellRecord(sessionId = sessionId, timestamp = 2000L)
        )
        val caBands = listOf(
            listOf(TestDataFactory.caBand(bandNumber = 1)),
            listOf(TestDataFactory.caBand(bandNumber = 2))
        )
        val ids = repository.insertRecordBatch(records, caBands)
        assertEquals(2, ids.size)
    }

    @Test
    fun updateSplitForRecord() = runBlocking {
        val recordId = repository.insert(TestDataFactory.cellRecord(sessionId = sessionId))
        repository.updateSplitForRecord(recordId, 12345L, 7, 24)

        val records = repository.getBySessionIdOnce(sessionId)
        assertEquals(12345L, records[0].enbOrGnbId)
    }

    @Test
    fun deleteBySessionId() = runBlocking {
        repository.insert(TestDataFactory.cellRecord(sessionId = sessionId))
        repository.insert(TestDataFactory.cellRecord(sessionId = sessionId))

        repository.deleteBySessionId(sessionId)

        val records = repository.getBySessionIdOnce(sessionId)
        assertTrue(records.isEmpty())
    }

    @Test
    fun getTotalRecordCount() = runBlocking {
        repository.insert(TestDataFactory.cellRecord(sessionId = sessionId))
        repository.insert(TestDataFactory.cellRecord(sessionId = sessionId))

        val count = repository.getTotalRecordCount().first()
        assertEquals(2, count)
    }

    @Test
    fun getRatDistribution() = runBlocking {
        repository.insert(TestDataFactory.cellRecord(sessionId = sessionId, rat = "4G"))
        repository.insert(TestDataFactory.cellRecord(sessionId = sessionId, rat = "5G_SA"))

        val dist = repository.getRatDistribution().first()
        assertEquals(2, dist.size)
    }

    @Test
    fun batchResplit() = runBlocking {
        repository.insert(TestDataFactory.cellRecord(
            sessionId = sessionId,
            fullCellIdentity = 0x123456789L,
            rat = "4G"
        ))
        repository.batchResplit(sessionId, 24)

        val records = repository.getBySessionIdOnce(sessionId)
        assertNotNull(records.first().enbOrGnbId)
        assertNotNull(records.first().lcid)
    }

    @Test
    fun getBandDistribution_throughRepo() = runBlocking {
        repeat(3) { repository.insert(TestDataFactory.cellRecord(sessionId = sessionId, bandNumber = 1)) }
        repeat(2) { repository.insert(TestDataFactory.cellRecord(sessionId = sessionId, bandNumber = 3)) }
        repository.insert(TestDataFactory.cellRecord(sessionId = sessionId, bandNumber = 7))

        val dist = repository.getBandDistribution().first()
        val bandCounts = dist.associate { it.bandNumber to it.count }
        assertEquals(3, bandCounts.size)
        assertEquals(3, bandCounts[1])
        assertEquals(2, bandCounts[3])
        assertEquals(1, bandCounts[7])
    }

    @Test
    fun getSimSlotDistribution_throughRepo() = runBlocking {
        repeat(3) { repository.insert(TestDataFactory.cellRecord(sessionId = sessionId, simSlotIndex = 0)) }
        repeat(2) { repository.insert(TestDataFactory.cellRecord(sessionId = sessionId, simSlotIndex = 1)) }

        val dist = repository.getSimSlotDistribution().first()
        assertEquals(2, dist.size)
        val slotCounts = dist.associate { it.simSlotIndex to it.count }
        assertEquals(3, slotCounts[0])
        assertEquals(2, slotCounts[1])
    }

    @Test
    fun getOnNetworkCount_throughRepo() = runBlocking {
        repository.insert(TestDataFactory.cellRecord(sessionId = sessionId, rat = "4G"))
        repository.insert(TestDataFactory.cellRecord(sessionId = sessionId, rat = "5G_SA"))
        repository.insert(TestDataFactory.cellRecord(sessionId = sessionId, rat = "UNKNOWN"))

        // "On-network" = rows whose rat != 'UNKNOWN' -> 2 rows
        val count = repository.getOnNetworkCount().first()
        assertEquals(2, count)
    }

    @Test
    fun batchResplit_splitValuesMatchFormula() = runBlocking {
        // 4G: enb = fullId shr 8, cid = fullId and 0xFF
        repository.insert(TestDataFactory.cellRecord(
            sessionId = sessionId,
            fullCellIdentity = 0x12345L,
            rat = "4G"
        ))
        // 5G_SA: shift = 36 - nrBitLen (24) = 12; gnb = fullId shr 12, clId = fullId and 0xFFF
        repository.insert(TestDataFactory.cellRecord(
            sessionId = sessionId,
            fullCellIdentity = 0x12345L,
            rat = "5G_SA"
        ))
        // 5G_NSA: same split as 5G_SA plus anchorEnb = fullId shr 8, anchorCid = fullId and 0xFF
        repository.insert(TestDataFactory.cellRecord(
            sessionId = sessionId,
            fullCellIdentity = 0xABCDEL,
            rat = "5G_NSA"
        ))

        repository.batchResplit(sessionId, 24)

        val records = repository.getBySessionIdOnce(sessionId)
        assertEquals(3, records.size)

        val record4G = records.first { it.rat == "4G" }
        assertEquals(0x123L, record4G.enbOrGnbId)
        assertEquals(0x45, record4G.lcid)
        assertNull(record4G.cellIdBitLength)

        val record5GSa = records.first { it.rat == "5G_SA" }
        assertEquals(0x12L, record5GSa.enbOrGnbId)
        assertEquals(0x345, record5GSa.lcid)
        assertEquals(24, record5GSa.cellIdBitLength)

        val record5GNsa = records.first { it.rat == "5G_NSA" }
        assertEquals(0xABL, record5GNsa.enbOrGnbId)
        assertEquals(0xCDE, record5GNsa.lcid)
        assertEquals(24, record5GNsa.cellIdBitLength)
        assertEquals(0xABCL, record5GNsa.anchorEnbOrGnbId)
        assertEquals(0xDE, record5GNsa.anchorLcid)
    }
}