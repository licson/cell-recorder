package com.cellrecorder.app.viewmodel

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.cellrecorder.app.data.repository.CellRecordRepository
import com.cellrecorder.app.data.repository.SessionRepository
import com.cellrecorder.app.data.repository.SpeedTestRecordRepository
import com.cellrecorder.app.ui.statistics.StatisticsViewModel
import com.cellrecorder.app.util.MainDispatcherRule
import com.cellrecorder.app.util.TestDataFactory
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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
class StatisticsViewModelTest {

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

    private lateinit var viewModel: StatisticsViewModel

    @Before
    fun setUp() {
        hiltRule.inject()
        viewModel = StatisticsViewModel(
            sessionRepository,
            cellRecordRepository,
            speedTestRecordRepository
        )
    }

    @Test
    fun emptyState_allStatsZero() = runBlocking {
        val stats = viewModel.stats.first()
        assertEquals(0, stats.totalSessions)
        assertEquals(0, stats.totalPoints)
        assertEquals(0L, stats.totalDurationMs)
        assertEquals(0, stats.onNetworkCount)
        assertEquals(0f, stats.onNetworkPct, 0.001f)

        assertTrue(viewModel.ratDistributionPerSim.first().isEmpty())
        assertTrue(viewModel.bandDistributionPerSim.first().isEmpty())
        assertTrue(viewModel.simSlotDistribution.first().isEmpty())
        assertTrue(viewModel.fiveGTimePerSim.first().isEmpty())
        assertTrue(viewModel.onNetworkPerSim.first().isEmpty())
        assertTrue(viewModel.fiveGPercentPerSim.first().isEmpty())
        assertNull(viewModel.speedTestGlobalStats.first())
    }

    @Test
    fun stats_singleSession_aggregatesCorrectly() = runBlocking {
        val sessionId = sessionRepository.create(
            name = "S1", createdAt = 1000L, recordingMode = "OUTDOOR"
        )
        sessionRepository.updateEndedAt(sessionId, 5000L)
        cellRecordRepository.insertAll(
            listOf(
                TestDataFactory.cellRecord(
                    sessionId = sessionId, timestamp = 2000L, rat = "4G", simSlotIndex = 0
                ),
                TestDataFactory.cellRecord(
                    sessionId = sessionId, timestamp = 3000L, rat = "4G", simSlotIndex = 0
                )
            )
        )

        val stats = viewModel.stats.first { it.totalSessions == 1 && it.totalPoints == 2 }
        assertEquals(1, stats.totalSessions)
        assertEquals(2, stats.totalPoints)
        assertEquals(4000L, stats.totalDurationMs)
        assertEquals(2, stats.onNetworkCount)
        assertEquals(100f, stats.onNetworkPct, 0.001f)
    }

    @Test
    fun stats_multipleSessions_aggregatesAcrossSessions() = runBlocking {
        val s1 = sessionRepository.create(name = "A", createdAt = 1000L, recordingMode = "OUTDOOR")
        sessionRepository.updateEndedAt(s1, 5000L)
        val s2 = sessionRepository.create(name = "B", createdAt = 10000L, recordingMode = "OUTDOOR")
        sessionRepository.updateEndedAt(s2, 15000L)
        cellRecordRepository.insertAll(
            listOf(
                TestDataFactory.cellRecord(sessionId = s1, timestamp = 2000L, rat = "4G"),
                TestDataFactory.cellRecord(sessionId = s2, timestamp = 11000L, rat = "5G_SA"),
                TestDataFactory.cellRecord(sessionId = s2, timestamp = 12000L, rat = "5G_SA")
            )
        )

        val stats = viewModel.stats.first { it.totalSessions == 2 && it.totalPoints == 3 }
        assertEquals(2, stats.totalSessions)
        assertEquals(3, stats.totalPoints)
        assertEquals(9000L, stats.totalDurationMs)
        assertEquals(3, stats.onNetworkCount)
    }

    @Test
    fun ratDistributionPerSim_emitsDistributionAcrossSimsAndRats() = runBlocking {
        val sessionId = sessionRepository.create(name = "RAT Dist", recordingMode = "OUTDOOR")
        cellRecordRepository.insertAll(
            listOf(
                TestDataFactory.cellRecord(sessionId = sessionId, timestamp = 1000L, rat = "4G", simSlotIndex = 0),
                TestDataFactory.cellRecord(sessionId = sessionId, timestamp = 2000L, rat = "4G", simSlotIndex = 0),
                TestDataFactory.cellRecord(sessionId = sessionId, timestamp = 3000L, rat = "5G_SA", simSlotIndex = 0),
                TestDataFactory.cellRecord(sessionId = sessionId, timestamp = 4000L, rat = "4G", simSlotIndex = 1)
            )
        )

        val distribution = viewModel.ratDistributionPerSim
            .first { it.isNotEmpty() && it.keys.containsAll(setOf(0, 1)) }
        assertEquals(setOf(0, 1), distribution.keys)
        val sim0 = distribution[0]!!
        assertEquals(2, sim0.size)
        assertEquals(2, sim0.first { it.rat == "4G" }.count)
        assertEquals(1, sim0.first { it.rat == "5G_SA" }.count)
        val sim1 = distribution[1]!!
        assertEquals(1, sim1.size)
        assertEquals(1, sim1.first { it.rat == "4G" }.count)
    }

    @Test
    fun bandDistributionPerSim_emitsDistributionAcrossSims() = runBlocking {
        val sessionId = sessionRepository.create(name = "Band Dist", recordingMode = "OUTDOOR")
        cellRecordRepository.insertAll(
            listOf(
                TestDataFactory.cellRecord(sessionId = sessionId, timestamp = 1000L, bandNumber = 3, simSlotIndex = 0),
                TestDataFactory.cellRecord(sessionId = sessionId, timestamp = 2000L, bandNumber = 3, simSlotIndex = 0),
                TestDataFactory.cellRecord(sessionId = sessionId, timestamp = 3000L, bandNumber = 7, simSlotIndex = 1)
            )
        )

        val distribution = viewModel.bandDistributionPerSim
            .first { it.isNotEmpty() && it.keys.containsAll(setOf(0, 1)) }
        val sim0 = distribution[0]!!
        assertEquals(2, sim0.first { it.bandNumber == 3 }.count)
        val sim1 = distribution[1]!!
        assertEquals(1, sim1.first { it.bandNumber == 7 }.count)
    }

    @Test
    fun simSlotDistribution_emitsPerSimCounts() = runBlocking {
        val sessionId = sessionRepository.create(name = "SIM Dist", recordingMode = "OUTDOOR")
        cellRecordRepository.insertAll(
            listOf(
                TestDataFactory.cellRecord(sessionId = sessionId, timestamp = 1000L, simSlotIndex = 0),
                TestDataFactory.cellRecord(sessionId = sessionId, timestamp = 2000L, simSlotIndex = 0),
                TestDataFactory.cellRecord(sessionId = sessionId, timestamp = 3000L, simSlotIndex = 1)
            )
        )

        val distribution = viewModel.simSlotDistribution.first { it.size == 2 }
        assertEquals(0, distribution[0].simSlotIndex)
        assertEquals(2, distribution[0].count)
        assertEquals(1, distribution[1].simSlotIndex)
        assertEquals(1, distribution[1].count)
    }

    @Test
    fun fiveGTimePerSim_emitsSaAndNsaCounts() = runBlocking {
        val sessionId = sessionRepository.create(name = "5G Time", recordingMode = "OUTDOOR")
        cellRecordRepository.insertAll(
            listOf(
                TestDataFactory.cellRecord(sessionId = sessionId, timestamp = 1000L, rat = "5G_SA", simSlotIndex = 0),
                TestDataFactory.cellRecord(sessionId = sessionId, timestamp = 2000L, rat = "5G_SA", simSlotIndex = 0),
                TestDataFactory.cellRecord(sessionId = sessionId, timestamp = 3000L, rat = "5G_NSA", simSlotIndex = 0),
                TestDataFactory.cellRecord(sessionId = sessionId, timestamp = 4000L, rat = "5G_NSA", simSlotIndex = 1)
            )
        )

        val times = viewModel.fiveGTimePerSim.first { it.size == 2 }
        val sim0 = times.first { it.simSlotIndex == 0 }
        assertEquals(2, sim0.saCount)
        assertEquals(1, sim0.nsaCount)
        val sim1 = times.first { it.simSlotIndex == 1 }
        assertEquals(0, sim1.saCount)
        assertEquals(1, sim1.nsaCount)
    }

    @Test
    fun onNetworkPerSim_emitsCountsExcludingUnknownRat() = runBlocking {
        val sessionId = sessionRepository.create(name = "On Net", recordingMode = "OUTDOOR")
        cellRecordRepository.insertAll(
            listOf(
                TestDataFactory.cellRecord(sessionId = sessionId, timestamp = 1000L, rat = "4G", simSlotIndex = 0),
                TestDataFactory.cellRecord(sessionId = sessionId, timestamp = 2000L, rat = "UNKNOWN", simSlotIndex = 0),
                TestDataFactory.cellRecord(sessionId = sessionId, timestamp = 3000L, rat = "5G_SA", simSlotIndex = 1)
            )
        )

        val onNet = viewModel.onNetworkPerSim.first { it.size == 2 }
        val sim0 = onNet.first { it.simSlotIndex == 0 }
        assertEquals(1, sim0.onNetworkCount)
        val sim1 = onNet.first { it.simSlotIndex == 1 }
        assertEquals(1, sim1.onNetworkCount)
        assertEquals(1, sim1.totalRecords)
    }

    @Test
    fun speedTestGlobalStats_emptyWhenNoTests() = runBlocking {
        val stats = viewModel.speedTestGlobalStats.first()
        assertNull(stats)
    }

    @Test
    fun speedTestGlobalStats_averagesAndSuccessRate() = runBlocking {
        val sessionId = sessionRepository.create(name = "Speedtest Stats", recordingMode = "OUTDOOR")
        speedTestRecordRepository.insertAll(
            listOf(
                TestDataFactory.speedTestRecord(
                    sessionId = sessionId, downloadBps = 100_000_000L,
                    uploadBps = 20_000_000L, downloadSucceeded = true, uploadSucceeded = true
                ),
                TestDataFactory.speedTestRecord(
                    sessionId = sessionId, downloadBps = null,
                    uploadBps = null, downloadSucceeded = false, uploadSucceeded = null
                )
            )
        )

        val stats = viewModel.speedTestGlobalStats.first { it != null }
        assertEquals(2, stats!!.totalTests)
        assertEquals(100_000_000.0, stats.avgDownloadBps!!, 1.0)
        assertEquals(20_000_000.0, stats.avgUploadBps!!, 1.0)
        assertEquals(0.5, stats.successRate!!, 0.001)
    }

    @Test
    fun speedTestGlobalStats_allFailed_successRateZero() = runBlocking {
        val sessionId = sessionRepository.create(name = "All Failed", recordingMode = "OUTDOOR")
        speedTestRecordRepository.insertAll(
            listOf(
                TestDataFactory.speedTestRecord(
                    sessionId = sessionId, downloadBps = null,
                    uploadBps = null, downloadSucceeded = false, uploadSucceeded = null
                ),
                TestDataFactory.speedTestRecord(
                    sessionId = sessionId, downloadBps = null,
                    uploadBps = null, downloadSucceeded = false, uploadSucceeded = null
                )
            )
        )

        val stats = viewModel.speedTestGlobalStats.first { it != null }
        assertEquals(2, stats!!.totalTests)
        assertNull(stats.avgDownloadBps)
        assertNull(stats.avgUploadBps)
        assertEquals(0.0, stats.successRate!!, 0.001)
    }

    @Test
    fun speedTestGlobalStats_allSucceeded_successRateOne() = runBlocking {
        val sessionId = sessionRepository.create(name = "All OK", recordingMode = "OUTDOOR")
        speedTestRecordRepository.insertAll(
            listOf(
                TestDataFactory.speedTestRecord(
                    sessionId = sessionId, downloadBps = 50_000_000L,
                    uploadBps = 10_000_000L, downloadSucceeded = true, uploadSucceeded = true
                ),
                TestDataFactory.speedTestRecord(
                    sessionId = sessionId, downloadBps = 150_000_000L,
                    uploadBps = 30_000_000L, downloadSucceeded = true, uploadSucceeded = true
                )
            )
        )

        val stats = viewModel.speedTestGlobalStats.first { it != null }
        assertEquals(2, stats!!.totalTests)
        assertEquals(100_000_000.0, stats.avgDownloadBps!!, 1.0)
        assertEquals(20_000_000.0, stats.avgUploadBps!!, 1.0)
        assertEquals(1.0, stats.successRate!!, 0.001)
    }
}
