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
}