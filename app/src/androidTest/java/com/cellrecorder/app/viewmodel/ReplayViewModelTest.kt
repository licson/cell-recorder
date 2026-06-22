package com.cellrecorder.app.viewmodel

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.cellrecorder.app.data.repository.CellRecordRepository
import com.cellrecorder.app.data.repository.SessionRepository
import com.cellrecorder.app.data.repository.SpeedTestRecordRepository
import com.cellrecorder.app.domain.usecase.GetSessionPointsUseCase
import com.cellrecorder.app.ui.detail.replay.ReplayViewModel
import com.cellrecorder.app.util.MainDispatcherRule
import com.cellrecorder.app.util.TestDataFactory
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
class ReplayViewModelTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Inject
    lateinit var sessionRepository: SessionRepository

    @Inject
    lateinit var cellRecordRepository: CellRecordRepository

    @Inject
    lateinit var speedTestRecordRepository: SpeedTestRecordRepository

    private lateinit var viewModel: ReplayViewModel

    @Before
    fun setUp() {
        hiltRule.inject()
        val getSessionPointsUseCase = GetSessionPointsUseCase(cellRecordRepository)
        viewModel = ReplayViewModel(
            getSessionPointsUseCase,
            speedTestRecordRepository,
            sessionRepository
        )
    }

    @Test
    fun session_loadsCorrectly() = runBlocking {
        val sessionId = sessionRepository.create(
            name = "Replay Session", createdAt = 1000L, recordingMode = "OUTDOOR"
        )
        sessionRepository.updateEndedAt(sessionId, 5000L)
        cellRecordRepository.insertAll(
            listOf(
                TestDataFactory.cellRecord(sessionId = sessionId, timestamp = 2000L)
            )
        )

        viewModel.loadSession(sessionId)
        val session = viewModel.session.first { it != null }
        assertEquals("Replay Session", session!!.name)
        assertEquals(1000L, session.createdAt)
        assertEquals(5000L, session.endedAt)
        assertEquals("OUTDOOR", session.recordingMode)
    }

    @Test
    fun records_loadInTimestampOrder() = runBlocking {
        val sessionId = sessionRepository.create(name = "Order", recordingMode = "OUTDOOR")
        cellRecordRepository.insertAll(
            listOf(
                TestDataFactory.cellRecord(sessionId = sessionId, timestamp = 3000L),
                TestDataFactory.cellRecord(sessionId = sessionId, timestamp = 1000L),
                TestDataFactory.cellRecord(sessionId = sessionId, timestamp = 2000L)
            )
        )

        viewModel.loadSession(sessionId)
        val records = viewModel.records.first { it.size == 3 }
        assertEquals(1000L, records[0].record.timestamp)
        assertEquals(2000L, records[1].record.timestamp)
        assertEquals(3000L, records[2].record.timestamp)
    }

    @Test
    fun speedTestRecords_load() = runBlocking {
        val sessionId = sessionRepository.create(name = "Speedtest", recordingMode = "OUTDOOR")
        speedTestRecordRepository.insertAll(
            listOf(
                TestDataFactory.speedTestRecord(sessionId = sessionId, timestamp = 1000L),
                TestDataFactory.speedTestRecord(sessionId = sessionId, timestamp = 2000L)
            )
        )

        viewModel.loadSession(sessionId)
        val records = viewModel.speedTestRecords.first { it.size == 2 }
        assertEquals(1000L, records[0].timestamp)
        assertEquals(2000L, records[1].timestamp)
    }

    @Test
    fun speedTestMarkers_emitForRecords() = runBlocking {
        val sessionId = sessionRepository.create(name = "Markers", recordingMode = "OUTDOOR")
        cellRecordRepository.insertAll(
            listOf(
                TestDataFactory.cellRecord(sessionId = sessionId, timestamp = 1000L, simSlotIndex = 0),
                TestDataFactory.cellRecord(sessionId = sessionId, timestamp = 2000L, simSlotIndex = 0),
                TestDataFactory.cellRecord(sessionId = sessionId, timestamp = 3000L, simSlotIndex = 0)
            )
        )
        speedTestRecordRepository.insertAll(
            listOf(
                TestDataFactory.speedTestRecord(sessionId = sessionId, timestamp = 1500L)
            )
        )

        viewModel.loadSession(sessionId)
        val markers = viewModel.speedTestMarkers.first { it.isNotEmpty() }
        assertEquals(1, markers.size)
        assertEquals(1500L, markers[0].record.timestamp)
        assertEquals(0, markers[0].timelineIndex)
    }

    @Test
    fun availableSimSlots_emitFromRecords() = runBlocking {
        val sessionId = sessionRepository.create(name = "Slots", recordingMode = "OUTDOOR")
        cellRecordRepository.insertAll(
            listOf(
                TestDataFactory.cellRecord(sessionId = sessionId, timestamp = 1000L, simSlotIndex = 1),
                TestDataFactory.cellRecord(sessionId = sessionId, timestamp = 2000L, simSlotIndex = 0)
            )
        )

        viewModel.loadSession(sessionId)
        val slots = viewModel.availableSimSlots.first { it.size == 2 }
        assertEquals(listOf(0, 1), slots)
    }

    @Test
    fun selectedSim_defaultsToFirstSlot() = runBlocking {
        val sessionId = sessionRepository.create(name = "Default Sim", recordingMode = "OUTDOOR")
        cellRecordRepository.insertAll(
            listOf(
                TestDataFactory.cellRecord(sessionId = sessionId, timestamp = 1000L, simSlotIndex = 1),
                TestDataFactory.cellRecord(sessionId = sessionId, timestamp = 2000L, simSlotIndex = 0)
            )
        )

        viewModel.loadSession(sessionId)
        val selectedSim = viewModel.selectedSim.first { it != null }
        assertEquals(0, selectedSim)
    }

    @Test
    fun setSimFilter_filtersRecords() = runBlocking {
        val sessionId = sessionRepository.create(name = "Filter", recordingMode = "OUTDOOR")
        cellRecordRepository.insertAll(
            listOf(
                TestDataFactory.cellRecord(sessionId = sessionId, timestamp = 1000L, simSlotIndex = 0),
                TestDataFactory.cellRecord(sessionId = sessionId, timestamp = 2000L, simSlotIndex = 1),
                TestDataFactory.cellRecord(sessionId = sessionId, timestamp = 3000L, simSlotIndex = 0)
            )
        )

        viewModel.loadSession(sessionId)
        viewModel.records.first { it.size == 3 }
        viewModel.filteredRecords.first { it.isNotEmpty() }

        viewModel.setSimFilter(null)
        val all = viewModel.filteredRecords.first { it.size == 3 }
        assertEquals(3, all.size)

        viewModel.setSimFilter(1)
        val filtered = viewModel.filteredRecords.first { it.size == 1 }
        assertEquals(1, filtered.size)
        assertEquals(1, filtered[0].record.simSlotIndex)
        assertEquals(0, viewModel.currentIndex.value)

        viewModel.setSimFilter(null)
        val allAgain = viewModel.filteredRecords.first { it.size == 3 }
        assertEquals(3, allAgain.size)
    }

    @Test
    fun togglePlayPause_togglesIsPlayingState() = runBlocking {
        val sessionId = sessionRepository.create(name = "Playback", recordingMode = "OUTDOOR")
        cellRecordRepository.insertAll(
            listOf(
                TestDataFactory.cellRecord(sessionId = sessionId, timestamp = 1000L, simSlotIndex = 0),
                TestDataFactory.cellRecord(sessionId = sessionId, timestamp = 2000L, simSlotIndex = 0)
            )
        )

        viewModel.loadSession(sessionId)
        viewModel.filteredRecords.first { it.size == 2 }

        assertFalse(viewModel.isPlaying.value)
        viewModel.togglePlayPause()
        assertTrue(viewModel.isPlaying.value)
        viewModel.togglePlayPause()
        assertFalse(viewModel.isPlaying.value)
    }

    @Test
    fun nonExistentSession_loadsEmptyState() = runBlocking {
        viewModel.loadSession(999999L)
        delay(500)
        assertNull(viewModel.session.value)
        assertTrue(viewModel.records.value.isEmpty())
        assertTrue(viewModel.filteredRecords.value.isEmpty())
        assertTrue(viewModel.speedTestRecords.value.isEmpty())
        assertTrue(viewModel.speedTestMarkers.value.isEmpty())
        assertTrue(viewModel.availableSimSlots.value.isEmpty())
    }

    @Test
    fun emptySession_loadsWithoutRecords() = runBlocking {
        val sessionId = sessionRepository.create(name = "Empty Replay", recordingMode = "OUTDOOR")
        viewModel.loadSession(sessionId)
        val session = viewModel.session.first { it != null }
        assertNotNull(session)
        assertEquals("Empty Replay", session!!.name)
        delay(500)
        assertTrue(viewModel.records.value.isEmpty())
        assertTrue(viewModel.filteredRecords.value.isEmpty())
        assertTrue(viewModel.speedTestRecords.value.isEmpty())
    }
}
