package com.cellrecorder.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cellrecorder.app.data.local.dao.SpeedTestRecordDao
import com.cellrecorder.app.data.local.entity.SessionEntity
import com.cellrecorder.app.data.local.entity.SpeedTestRecordEntity
import com.cellrecorder.app.util.TestDataFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class SpeedTestRecordDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: SpeedTestRecordDao
    private lateinit var sessionDao: com.cellrecorder.app.data.local.dao.SessionDao
    private var sessionId: Long = 0

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.speedTestRecordDao()
        sessionDao = db.sessionDao()
        sessionId = runBlocking {
            sessionDao.insert(SessionEntity(name = "SpeedTest Session", createdAt = 1000L))
        }
    }

    @After
    @Throws(IOException::class)
    fun tearDown() {
        db.close()
    }

    @Test
    fun insertAndGetBySessionId() = runBlocking {
        val id = dao.insert(TestDataFactory.speedTestRecord(sessionId = sessionId))
        assertTrue(id > 0)

        val records = dao.getBySessionId(sessionId).first()
        assertEquals(1, records.size)
    }

    @Test
    fun getBySessionIdOnce() = runBlocking {
        dao.insert(TestDataFactory.speedTestRecord(sessionId = sessionId))
        dao.insert(TestDataFactory.speedTestRecord(sessionId = sessionId, timestamp = 4000L))

        val records = dao.getBySessionIdOnce(sessionId)
        assertEquals(2, records.size)
    }

    @Test
    fun insertAll() = runBlocking {
        val records = listOf(
            TestDataFactory.speedTestRecord(sessionId = sessionId, timestamp = 1000L),
            TestDataFactory.speedTestRecord(sessionId = sessionId, timestamp = 2000L)
        )
        dao.insertAll(records)

        val loaded = dao.getBySessionIdOnce(sessionId)
        assertEquals(2, loaded.size)
    }

    @Test
    fun getBySessionIdAndTimestampRange() = runBlocking {
        dao.insert(TestDataFactory.speedTestRecord(sessionId = sessionId, timestamp = 1000L))
        dao.insert(TestDataFactory.speedTestRecord(sessionId = sessionId, timestamp = 2000L))
        dao.insert(TestDataFactory.speedTestRecord(sessionId = sessionId, timestamp = 3000L))

        val filtered = dao.getBySessionIdAndTimestampRange(sessionId, 1500L, 2500L).first()
        assertEquals(1, filtered.size)
        assertEquals(2000L, filtered[0].timestamp)
    }

    @Test
    fun getTotalCount() = runBlocking {
        dao.insert(TestDataFactory.speedTestRecord(sessionId = sessionId))
        dao.insert(TestDataFactory.speedTestRecord(sessionId = sessionId))

        val count = dao.getTotalCount().first()
        assertEquals(2, count)
    }

    @Test
    fun getAvgDownloadBps() = runBlocking {
        dao.insert(TestDataFactory.speedTestRecord(sessionId = sessionId, downloadBps = 100_000_000L))
        dao.insert(TestDataFactory.speedTestRecord(sessionId = sessionId, downloadBps = 200_000_000L))

        val avg = dao.getAvgDownloadBps().first()
        assertNotNull(avg)
        assertEquals(150_000_000.0, avg!!, 1.0)
    }

    @Test
    fun getAvgUploadBps() = runBlocking {
        dao.insert(TestDataFactory.speedTestRecord(sessionId = sessionId, uploadBps = 10_000_000L))
        dao.insert(TestDataFactory.speedTestRecord(sessionId = sessionId, uploadBps = 30_000_000L))

        val avg = dao.getAvgUploadBps().first()
        assertNotNull(avg)
        assertEquals(20_000_000.0, avg!!, 1.0)
    }

    @Test
    fun getSuccessRate() = runBlocking {
        dao.insert(TestDataFactory.speedTestRecord(sessionId = sessionId, succeeded = true))
        dao.insert(TestDataFactory.speedTestRecord(sessionId = sessionId, succeeded = true))
        dao.insert(TestDataFactory.speedTestRecord(sessionId = sessionId, succeeded = false))

        val rate = dao.getSuccessRate().first()
        assertNotNull(rate)
        assertEquals(2.0 / 3.0, rate!!, 0.001)
    }

    @Test
    fun deleteBySessionId() = runBlocking {
        dao.insert(TestDataFactory.speedTestRecord(sessionId = sessionId))
        dao.insert(TestDataFactory.speedTestRecord(sessionId = sessionId))

        dao.deleteBySessionId(sessionId)

        val records = dao.getBySessionIdOnce(sessionId)
        assertTrue(records.isEmpty())
    }

    @Test
    fun getCountBySessionId() = runBlocking {
        dao.insert(TestDataFactory.speedTestRecord(sessionId = sessionId))
        dao.insert(TestDataFactory.speedTestRecord(sessionId = sessionId))

        val count = dao.getCountBySessionId(sessionId).first()
        assertEquals(2, count)
    }

    @Test
    fun cascadeDeleteOnSessionDelete() = runBlocking {
        dao.insert(TestDataFactory.speedTestRecord(sessionId = sessionId))
        dao.insert(TestDataFactory.speedTestRecord(sessionId = sessionId))

        sessionDao.deleteById(sessionId)

        val records = dao.getBySessionIdOnce(sessionId)
        assertTrue(records.isEmpty())
    }

    @Test
    fun getAvgBps_handlesNullRows() = runBlocking {
        // Only null rows -> AVG returns null (WHERE downloadBps IS NOT NULL filters them all out)
        dao.insert(TestDataFactory.speedTestRecord(sessionId = sessionId, downloadBps = null, uploadBps = null, succeeded = true))
        assertNull(dao.getAvgDownloadBps().first())
        assertNull(dao.getAvgUploadBps().first())

        // Add a non-null row -> AVG should be computed only over the non-null row
        dao.insert(TestDataFactory.speedTestRecord(sessionId = sessionId, downloadBps = 100_000_000L, uploadBps = 20_000_000L, succeeded = true))

        val avgDownload = dao.getAvgDownloadBps().first()
        val avgUpload = dao.getAvgUploadBps().first()
        assertNotNull(avgDownload)
        assertNotNull(avgUpload)
        assertEquals(100_000_000.0, avgDownload!!, 1.0)
        assertEquals(20_000_000.0, avgUpload!!, 1.0)
    }

    @Test
    fun getSuccessRate_allFailed_returnsZero() = runBlocking {
        dao.insert(TestDataFactory.speedTestRecord(sessionId = sessionId, succeeded = false))
        dao.insert(TestDataFactory.speedTestRecord(sessionId = sessionId, succeeded = false))
        dao.insert(TestDataFactory.speedTestRecord(sessionId = sessionId, succeeded = false))

        val rate = dao.getSuccessRate().first()
        assertNotNull(rate)
        assertEquals(0.0, rate!!, 0.001)
    }

    @Test
    fun getSuccessRate_allSucceeded_returnsOne() = runBlocking {
        dao.insert(TestDataFactory.speedTestRecord(sessionId = sessionId, succeeded = true))
        dao.insert(TestDataFactory.speedTestRecord(sessionId = sessionId, succeeded = true))
        dao.insert(TestDataFactory.speedTestRecord(sessionId = sessionId, succeeded = true))

        val rate = dao.getSuccessRate().first()
        assertNotNull(rate)
        assertEquals(1.0, rate!!, 0.001)
    }

    @Test
    fun getBySessionIdAndTimestampRange_inclusiveBoundaries() = runBlocking {
        dao.insert(TestDataFactory.speedTestRecord(sessionId = sessionId, timestamp = 1000L))
        dao.insert(TestDataFactory.speedTestRecord(sessionId = sessionId, timestamp = 2000L))
        dao.insert(TestDataFactory.speedTestRecord(sessionId = sessionId, timestamp = 3000L))

        // BETWEEN is inclusive on both ends -> all 3 records match
        val filtered = dao.getBySessionIdAndTimestampRange(sessionId, 1000L, 3000L).first()
        assertEquals(3, filtered.size)
    }

    @Test
    fun getBySessionIdAndTimestampRange_justOutsideBoundaries() = runBlocking {
        dao.insert(TestDataFactory.speedTestRecord(sessionId = sessionId, timestamp = 1000L))
        dao.insert(TestDataFactory.speedTestRecord(sessionId = sessionId, timestamp = 2000L))
        dao.insert(TestDataFactory.speedTestRecord(sessionId = sessionId, timestamp = 3000L))

        // 1000 < 1001 and 3000 > 2999 -> only the 2000 record falls in range
        val filtered = dao.getBySessionIdAndTimestampRange(sessionId, 1001L, 2999L).first()
        assertEquals(1, filtered.size)
        assertEquals(2000L, filtered[0].timestamp)
    }

    @Test
    fun insertWithExplicitFinishedAt_roundTrips() = runBlocking {
        dao.insert(TestDataFactory.speedTestRecord(
            sessionId = sessionId,
            timestamp = 1000L,
            finishedAt = 4500L,
            succeeded = true
        ))

        val loaded = dao.getBySessionIdOnce(sessionId)
        assertEquals(1, loaded.size)
        assertEquals(1000L, loaded[0].timestamp)
        assertEquals(4500L, loaded[0].finishedAt)
    }

    @Test
    fun legacyRowWithDefaultFinishedAtZero_roundTrips() = runBlocking {
        // finishedAt defaults to 0 in TestDataFactory when not specified (legacy shape)
        dao.insert(TestDataFactory.speedTestRecord(sessionId = sessionId, timestamp = 1000L))

        val loaded = dao.getBySessionIdOnce(sessionId)
        assertEquals(1, loaded.size)
        assertEquals(0L, loaded[0].finishedAt)
    }

    @Test
    fun instantBailOutFinishedAtEqualsTimestamp_persists() = runBlocking {
        dao.insert(TestDataFactory.speedTestRecord(
            sessionId = sessionId,
            timestamp = 7000L,
            finishedAt = 7000L, // instant bail-out: finishedAt = timestamp
            succeeded = false
        ))

        val loaded = dao.getBySessionIdOnce(sessionId)
        assertEquals(1, loaded.size)
        assertEquals(7000L, loaded[0].timestamp)
        assertEquals(7000L, loaded[0].finishedAt)
        assertEquals(false, loaded[0].succeeded)
    }
}