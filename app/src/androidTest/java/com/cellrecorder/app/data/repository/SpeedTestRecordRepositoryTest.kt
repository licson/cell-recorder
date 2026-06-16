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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@MediumTest
class SpeedTestRecordRepositoryTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var repository: SpeedTestRecordRepository

    @Inject
    lateinit var sessionRepository: SessionRepository

    @Inject
    lateinit var db: AppDatabase

    private var sessionId: Long = 0

    @Before
    fun setUp() {
        hiltRule.inject()
        sessionId = runBlocking {
            sessionRepository.create(name = "SpeedTest Test", recordingMode = "OUTDOOR")
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun insertAndGetBySessionId() = runBlocking {
        val id = repository.insert(TestDataFactory.speedTestRecord(sessionId = sessionId))
        assertTrue(id > 0)

        val records = repository.getBySessionId(sessionId).first()
        assertEquals(1, records.size)
    }

    @Test
    fun getBySessionIdOnce() = runBlocking {
        repository.insert(TestDataFactory.speedTestRecord(sessionId = sessionId))
        repository.insert(TestDataFactory.speedTestRecord(sessionId = sessionId))

        val records = repository.getBySessionIdOnce(sessionId)
        assertEquals(2, records.size)
    }

    @Test
    fun insertAll() = runBlocking {
        val records = listOf(
            TestDataFactory.speedTestRecord(sessionId = sessionId, timestamp = 1000L),
            TestDataFactory.speedTestRecord(sessionId = sessionId, timestamp = 2000L)
        )
        repository.insertAll(records)

        val loaded = repository.getBySessionIdOnce(sessionId)
        assertEquals(2, loaded.size)
    }

    @Test
    fun getAvgDownloadBps() = runBlocking {
        repository.insert(TestDataFactory.speedTestRecord(sessionId = sessionId, downloadBps = 100_000_000L))
        repository.insert(TestDataFactory.speedTestRecord(sessionId = sessionId, downloadBps = 200_000_000L))

        val avg = repository.getAvgDownloadBps().first()
        assertNotNull(avg)
        assertEquals(150_000_000.0, avg!!, 1.0)
    }

    @Test
    fun getAvgUploadBps() = runBlocking {
        repository.insert(TestDataFactory.speedTestRecord(sessionId = sessionId, uploadBps = 10_000_000L))
        repository.insert(TestDataFactory.speedTestRecord(sessionId = sessionId, uploadBps = 30_000_000L))

        val avg = repository.getAvgUploadBps().first()
        assertNotNull(avg)
        assertEquals(20_000_000.0, avg!!, 1.0)
    }

    @Test
    fun getSuccessRate() = runBlocking {
        repository.insert(TestDataFactory.speedTestRecord(sessionId = sessionId, succeeded = true))
        repository.insert(TestDataFactory.speedTestRecord(sessionId = sessionId, succeeded = false))

        val rate = repository.getSuccessRate().first()
        assertNotNull(rate)
        assertEquals(0.5, rate!!, 0.001)
    }

    @Test
    fun deleteBySessionId() = runBlocking {
        repository.insert(TestDataFactory.speedTestRecord(sessionId = sessionId))
        repository.deleteBySessionId(sessionId)

        val records = repository.getBySessionIdOnce(sessionId)
        assertTrue(records.isEmpty())
    }

    @Test
    fun getTotalCount() = runBlocking {
        repository.insert(TestDataFactory.speedTestRecord(sessionId = sessionId))
        repository.insert(TestDataFactory.speedTestRecord(sessionId = sessionId))

        val count = repository.getTotalCount().first()
        assertEquals(2, count)
    }
}