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
}