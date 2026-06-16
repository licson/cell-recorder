package com.cellrecorder.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cellrecorder.app.data.local.dao.CellRecordDao
import com.cellrecorder.app.data.local.entity.CellRecordCaBandEntity
import com.cellrecorder.app.data.local.entity.CellRecordEntity
import com.cellrecorder.app.data.local.entity.SessionEntity
import com.cellrecorder.app.util.TestDataFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class CellRecordDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: CellRecordDao
    private lateinit var sessionDao: com.cellrecorder.app.data.local.dao.SessionDao
    private var sessionId: Long = 0

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.cellRecordDao()
        sessionDao = db.sessionDao()
        sessionId = runBlocking {
            sessionDao.insert(SessionEntity(name = "CellRecord Test", createdAt = 1000L))
        }
    }

    @After
    @Throws(IOException::class)
    fun tearDown() {
        db.close()
    }

    @Test
    fun insertAndReadRecord() = runBlocking {
        val record = TestDataFactory.cellRecord(sessionId = sessionId)
        val id = dao.insert(record)
        assertNotNull(id)

        val records = dao.getBySessionIdOnce(sessionId)
        assertEquals(1, records.size)
        assertEquals(37.7749, records[0].latitude, 0.0001)
        assertEquals("4G", records[0].rat)
    }

    @Test
    fun insertAll() = runBlocking {
        val records = listOf(
            TestDataFactory.cellRecord(sessionId = sessionId, timestamp = 1000L),
            TestDataFactory.cellRecord(sessionId = sessionId, timestamp = 2000L)
        )
        val ids = dao.insertAll(records)
        assertEquals(2, ids.size)

        val loaded = dao.getBySessionIdOnce(sessionId)
        assertEquals(2, loaded.size)
    }

    @Test
    fun insertCaBand() = runBlocking {
        val recordId = dao.insert(TestDataFactory.cellRecord(sessionId = sessionId))
        val caBand = TestDataFactory.caBand(cellRecordId = recordId)
        dao.insertCaBand(caBand)

        val withCa = dao.getBySessionIdOnceWithCaBands(sessionId)
        assertEquals(1, withCa.size)
        assertEquals(1, withCa[0].caBands.size)
        assertEquals(3, withCa[0].caBands[0].bandNumber)
    }

    @Test
    fun insertRecordBatch() = runBlocking {
        val records = listOf(
            TestDataFactory.cellRecord(sessionId = sessionId, timestamp = 1000L),
            TestDataFactory.cellRecord(sessionId = sessionId, timestamp = 2000L)
        )
        val caBands = listOf(
            listOf(TestDataFactory.caBand(bandNumber = 1)),
            listOf(TestDataFactory.caBand(bandNumber = 2), TestDataFactory.caBand(bandNumber = 3))
        )
        val ids = dao.insertRecordBatch(records, caBands)
        assertEquals(2, ids.size)

        val withCa = dao.getBySessionIdOnceWithCaBands(sessionId)
        assertEquals(2, withCa.size)
        assertEquals(1, withCa[0].caBands.size)
        assertEquals(2, withCa[1].caBands.size)
    }

    @Test
    fun getBySessionIdWithCaBands_returnsRecordsWithBands() = runBlocking {
        val recordId = dao.insert(TestDataFactory.cellRecord(sessionId = sessionId))
        dao.insertCaBand(TestDataFactory.caBand(cellRecordId = recordId, bandNumber = 3))
        dao.insertCaBand(TestDataFactory.caBand(cellRecordId = recordId, bandNumber = 5))

        val flowResult = dao.getBySessionIdWithCaBands(sessionId).first()
        assertEquals(1, flowResult.size)
        assertEquals(2, flowResult[0].caBands.size)
        val bandNumbers = flowResult[0].caBands.map { it.bandNumber }
        assertTrue(bandNumbers.containsAll(listOf(3, 5)))
    }

    @Test
    fun updateSplitForRecord() = runBlocking {
        val recordId = dao.insert(TestDataFactory.cellRecord(sessionId = sessionId))
        dao.updateSplitForRecord(recordId, 12345L, 7, 24)

        val records = dao.getBySessionIdOnce(sessionId)
        assertEquals(12345L, records[0].enbOrGnbId)
        assertEquals(7, records[0].lcid)
        assertEquals(24, records[0].cellIdBitLength)
    }

    @Test
    fun deleteBySessionId() = runBlocking {
        dao.insert(TestDataFactory.cellRecord(sessionId = sessionId))
        dao.insert(TestDataFactory.cellRecord(sessionId = sessionId))

        dao.deleteBySessionId(sessionId)

        val records = dao.getBySessionIdOnce(sessionId)
        assertTrue(records.isEmpty())
    }

    @Test
    fun getTotalRecordCount() = runBlocking {
        dao.insert(TestDataFactory.cellRecord(sessionId = sessionId))
        dao.insert(TestDataFactory.cellRecord(sessionId = sessionId))

        val count = dao.getTotalRecordCount().first()
        assertEquals(2, count)
    }

    @Test
    fun getRatDistribution() = runBlocking {
        dao.insert(TestDataFactory.cellRecord(sessionId = sessionId, rat = "4G"))
        dao.insert(TestDataFactory.cellRecord(sessionId = sessionId, rat = "4G"))
        dao.insert(TestDataFactory.cellRecord(sessionId = sessionId, rat = "5G_SA"))

        val dist = dao.getRatDistribution().first()
        assertEquals(2, dist.size)
        assertEquals("4G", dist[0].rat)
        assertEquals(2, dist[0].count)
        assertEquals("5G_SA", dist[1].rat)
        assertEquals(1, dist[1].count)
    }

    @Test
    fun getBandDistribution_includesCaBands() = runBlocking {
        val recordId = dao.insert(TestDataFactory.cellRecord(sessionId = sessionId, bandNumber = 3))
        dao.insertCaBand(TestDataFactory.caBand(cellRecordId = recordId, bandNumber = 5))

        val dist = dao.getBandDistribution(5).first()
        val bandCounts = dist.associate { it.bandNumber to it.count }
        assertTrue(bandCounts.containsKey(3))
        assertTrue(bandCounts.containsKey(5))
    }

    @Test
    fun getSimSlotDistribution() = runBlocking {
        dao.insert(TestDataFactory.cellRecord(sessionId = sessionId, simSlotIndex = 0))
        dao.insert(TestDataFactory.cellRecord(sessionId = sessionId, simSlotIndex = 0))
        dao.insert(TestDataFactory.cellRecord(sessionId = sessionId, simSlotIndex = 1))

        val dist = dao.getSimSlotDistribution().first()
        assertEquals(2, dist.size)
        val slotCounts = dist.associate { it.simSlotIndex to it.count }
        assertEquals(2, slotCounts[0])
        assertEquals(1, slotCounts[1])
    }

    @Test
    fun getOnNetworkCount() = runBlocking {
        dao.insert(TestDataFactory.cellRecord(sessionId = sessionId, rat = "4G"))
        dao.insert(TestDataFactory.cellRecord(sessionId = sessionId, rat = "5G_SA"))
        dao.insert(TestDataFactory.cellRecord(sessionId = sessionId, rat = "UNKNOWN"))

        val count = dao.getOnNetworkCount().first()
        assertEquals(2, count)
    }
}