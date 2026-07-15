package com.cellrecorder.app.viewmodel

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.cellrecorder.app.data.local.entity.AppConfigEntity
import com.cellrecorder.app.data.repository.CellRecordRepository
import com.cellrecorder.app.data.repository.ConfigRepository
import com.cellrecorder.app.data.repository.RecentMarkerLabelRepository
import com.cellrecorder.app.data.repository.SessionMarkerRepository
import com.cellrecorder.app.data.repository.SessionRepository
import com.cellrecorder.app.data.repository.SpeedTestRecordRepository
import com.cellrecorder.app.domain.analytics.model.SessionAnalytics
import com.cellrecorder.app.domain.usecase.BatchResplitUseCase
import com.cellrecorder.app.domain.usecase.ExportSessionUseCase
import com.cellrecorder.app.domain.usecase.ExportSpeedTestUseCase
import com.cellrecorder.app.domain.usecase.GetConfigUseCase
import com.cellrecorder.app.domain.usecase.GetSessionPointsUseCase
import com.cellrecorder.app.ui.detail.SessionDetailViewModel
import com.cellrecorder.app.util.MainDispatcherRule
import com.cellrecorder.app.util.TestDataFactory
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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
class SessionDetailViewModelTest {

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

    @Inject
    lateinit var configRepository: ConfigRepository

    @Inject
    lateinit var sessionMarkerRepository: SessionMarkerRepository

    @Inject
    lateinit var recentMarkerLabelRepository: RecentMarkerLabelRepository

    private lateinit var viewModel: SessionDetailViewModel

    @Before
    fun setUp() {
        hiltRule.inject()
        val getSessionPointsUseCase = GetSessionPointsUseCase(cellRecordRepository)
        val exportSessionUseCase = ExportSessionUseCase()
        val batchResplitUseCase = BatchResplitUseCase(cellRecordRepository)
        val getConfigUseCase = GetConfigUseCase(configRepository)
        val exportSpeedTestUseCase = ExportSpeedTestUseCase()
        viewModel = SessionDetailViewModel(
            sessionRepository,
            getSessionPointsUseCase,
            exportSessionUseCase,
            batchResplitUseCase,
            getConfigUseCase,
            speedTestRecordRepository,
            exportSpeedTestUseCase,
            sessionMarkerRepository,
            recentMarkerLabelRepository
        )
    }

    @Test
    fun sessionSummary_emitsLoadedSession() = runBlocking {
        val sessionId = sessionRepository.create(
            name = "Detail Session",
            createdAt = 1000L,
            recordingMode = "INDOOR"
        )
        cellRecordRepository.insertAll(
            listOf(
                TestDataFactory.cellRecord(sessionId = sessionId, timestamp = 2000L),
                TestDataFactory.cellRecord(sessionId = sessionId, timestamp = 3000L)
            )
        )
        sessionRepository.refreshPointCount(sessionId)
        sessionRepository.updateEndedAt(sessionId, 5000L)
        sessionRepository.updatePrimarySimSlot(sessionId, 1)

        viewModel.loadSession(sessionId)
        val session = viewModel.session.first { it != null && it.pointCount == 2 && it.endedAt == 5000L }
        assertEquals("Detail Session", session!!.name)
        assertEquals(1000L, session.createdAt)
        assertEquals(2, session.pointCount)
        assertEquals(5000L, session.endedAt)
        assertEquals(1, session.primarySimSlot)
        assertEquals("INDOOR", session.recordingMode)
    }

    @Test
    fun session_nonExistentId_loadsEmpty() = runBlocking {
        viewModel.loadSession(999999L)
        delay(500)
        assertNull(viewModel.session.value)
        assertTrue(viewModel.records.value.isEmpty())
        assertTrue(viewModel.speedTestRecords.value.isEmpty())
    }

    @Test
    fun session_empty_loadsWithoutRecords() = runBlocking {
        val sessionId = sessionRepository.create(name = "Empty", recordingMode = "OUTDOOR")
        viewModel.loadSession(sessionId)
        val session = viewModel.session.first { it != null }
        assertEquals("Empty", session!!.name)
        delay(500)
        assertTrue(viewModel.records.value.isEmpty())
        assertTrue(viewModel.speedTestRecords.value.isEmpty())
        assertEquals(SessionAnalytics(), viewModel.analytics.value)
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
    fun speedTestAnalytics_nullWhenNoRecords() = runBlocking {
        val sessionId = sessionRepository.create(name = "No Speedtest", recordingMode = "OUTDOOR")
        viewModel.loadSession(sessionId)
        viewModel.session.first { it != null }
        delay(500)
        assertNull(viewModel.speedTestAnalytics.value)
        assertTrue(viewModel.speedTestRecords.value.isEmpty())
    }

    @Test
    fun speedTestAnalytics_emitsForRecords() = runBlocking {
        val sessionId = sessionRepository.create(name = "Speedtest", recordingMode = "OUTDOOR")
        speedTestRecordRepository.insertAll(
            listOf(
                TestDataFactory.speedTestRecord(
                    sessionId = sessionId,
                    downloadBps = 100_000_000L,
                    uploadBps = 20_000_000L,
                    downloadSucceeded = true,
                    uploadSucceeded = true
                ),
                TestDataFactory.speedTestRecord(
                    sessionId = sessionId,
                    downloadBps = 200_000_000L,
                    uploadBps = 40_000_000L,
                    downloadSucceeded = true,
                    uploadSucceeded = true
                )
            )
        )

        viewModel.loadSession(sessionId)
        val analytics = viewModel.speedTestAnalytics.first { it != null }
        assertEquals(2, analytics!!.sampleCount)
        assertEquals(0, analytics.failureCount)
        assertEquals(1.0, analytics.successRate, 0.001)
        assertEquals(150_000_000L, analytics.avgDownloadBps)
        assertEquals(30_000_000L, analytics.avgUploadBps)
    }

    @Test
    fun analytics_generatesRatCoverage() = runBlocking {
        val sessionId = sessionRepository.create(name = "Analytics", recordingMode = "OUTDOOR")
        cellRecordRepository.insertAll(
            listOf(
                TestDataFactory.cellRecord(sessionId = sessionId, timestamp = 1000L, rat = "4G"),
                TestDataFactory.cellRecord(sessionId = sessionId, timestamp = 2000L, rat = "4G"),
                TestDataFactory.cellRecord(sessionId = sessionId, timestamp = 3000L, rat = "4G"),
                TestDataFactory.cellRecord(sessionId = sessionId, timestamp = 4000L, rat = "5G_SA"),
                TestDataFactory.cellRecord(sessionId = sessionId, timestamp = 5000L, rat = "5G_SA")
            )
        )

        viewModel.loadSession(sessionId)
        val analytics = viewModel.analytics.first { it.ratCoverage.isNotEmpty() }
        val rats = analytics.ratCoverage.map { it.rat }.toSet()
        assertTrue(rats.contains("4G"))
        assertTrue(rats.contains("5G_SA"))
        assertEquals(2, rats.size)
    }

    @Test
    fun analytics_generatesInsightCard_crossSiteHandoff() = runBlocking {
        val sessionId = sessionRepository.create(name = "Insight", recordingMode = "OUTDOOR")
        val records = listOf(
            TestDataFactory.cellRecord(
                sessionId = sessionId, timestamp = 1000L,
                enbOrGnbId = 1L, pci = 100, simSlotIndex = 0
            ).copy(avgLatencyMs = 10.0),
            TestDataFactory.cellRecord(
                sessionId = sessionId, timestamp = 2000L,
                enbOrGnbId = 2L, pci = 200, simSlotIndex = 0
            ).copy(avgLatencyMs = 20.0),
            TestDataFactory.cellRecord(
                sessionId = sessionId, timestamp = 3000L,
                enbOrGnbId = 1L, pci = 100, simSlotIndex = 0
            ).copy(avgLatencyMs = 30.0),
            TestDataFactory.cellRecord(
                sessionId = sessionId, timestamp = 4000L,
                enbOrGnbId = 2L, pci = 200, simSlotIndex = 0
            ).copy(avgLatencyMs = 40.0)
        )
        cellRecordRepository.insertAll(records)

        viewModel.loadSession(sessionId)
        val analytics = viewModel.analytics.first { it.insightCards.isNotEmpty() }
        assertTrue(analytics.insightCards.any { it.title == "Cross-Site Handoff Impact" })
    }

    @Test
    fun batchResplit_updates5gSaRecords() = runBlocking {
        val sessionId = sessionRepository.create(name = "Resplit", recordingMode = "OUTDOOR")
        val fullCellIdentity = 0x100000001L
        cellRecordRepository.insertAll(
            listOf(
                TestDataFactory.cellRecord(
                    sessionId = sessionId,
                    timestamp = 1000L,
                    rat = "5G_SA",
                    fullCellIdentity = fullCellIdentity,
                    enbOrGnbId = null,
                    lcid = null,
                    cellIdBitLength = null
                )
            )
        )

        viewModel.loadSession(sessionId)
        val initial = viewModel.records.first { it.size == 1 }
        assertNull(initial[0].record.enbOrGnbId)
        assertNull(initial[0].record.lcid)

        viewModel.batchResplit(sessionId)
        val updated = viewModel.records.first { it.isNotEmpty() && it[0].record.enbOrGnbId != null }
        val record = updated[0].record
        assertEquals(24, record.cellIdBitLength)
        assertEquals(fullCellIdentity shr 12, record.enbOrGnbId)
        assertEquals((fullCellIdentity and 0xFFFL).toInt(), record.lcid)
    }

    @Test
    fun batchResplit_usesConfigFromDb() = runBlocking {
        configRepository.update(AppConfigEntity(nrGnbBitLength = 28))
        val sessionId = sessionRepository.create(name = "Resplit Config", recordingMode = "OUTDOOR")
        val fullCellIdentity = 0x100000001L
        cellRecordRepository.insertAll(
            listOf(
                TestDataFactory.cellRecord(
                    sessionId = sessionId,
                    timestamp = 1000L,
                    rat = "5G_SA",
                    fullCellIdentity = fullCellIdentity,
                    enbOrGnbId = null,
                    lcid = null,
                    cellIdBitLength = null
                )
            )
        )

        viewModel.loadSession(sessionId)
        viewModel.records.first { it.size == 1 }
        delay(500)

        viewModel.batchResplit(sessionId)
        val updated = viewModel.records.first { it.isNotEmpty() && it[0].record.enbOrGnbId != null }
        val record = updated[0].record
        assertEquals(28, record.cellIdBitLength)
        assertEquals(fullCellIdentity shr 8, record.enbOrGnbId)
        assertEquals((fullCellIdentity and 0xFFL).toInt(), record.lcid)
    }

    @Test
    fun exportCsv_producesOutput() = runBlocking {
        val sessionId = sessionRepository.create(name = "Export Csv", recordingMode = "OUTDOOR")
        cellRecordRepository.insertAll(
            listOf(
                TestDataFactory.cellRecord(
                    sessionId = sessionId, timestamp = 1000L,
                    latitude = 1.0, longitude = 2.0, altitude = 3.0, accuracy = 5f
                )
            )
        )

        viewModel.loadSession(sessionId)
        viewModel.session.first { it != null }
        viewModel.records.first { it.isNotEmpty() }

        viewModel.exportCsv()
        val export = viewModel.exportData.first { it != null }
        assertNotNull(export)
        assertTrue(export!!.content.startsWith("timestamp,lat,lon,alt,accuracy"))
        assertTrue(export.content.contains("1000,1.0,2.0,3.0,5.0"))
        assertEquals("text/csv", export.mimeType)
        assertTrue(export.suggestedFilename.endsWith("_cell_records.csv"))
    }

    @Test
    fun exportGeoJson_producesOutput() = runBlocking {
        val sessionId = sessionRepository.create(name = "Export Geo", recordingMode = "OUTDOOR")
        cellRecordRepository.insertAll(
            listOf(
                TestDataFactory.cellRecord(
                    sessionId = sessionId, timestamp = 1000L,
                    latitude = 1.0, longitude = 2.0, altitude = 3.0, accuracy = 5f
                )
            )
        )

        viewModel.loadSession(sessionId)
        viewModel.session.first { it != null }
        viewModel.records.first { it.isNotEmpty() }

        viewModel.exportGeoJson()
        val export = viewModel.exportData.first { it != null }
        assertNotNull(export)
        assertTrue(export!!.content.contains("FeatureCollection"))
        assertEquals("application/geo+json", export.mimeType)
        assertTrue(export.suggestedFilename.endsWith("_cell_records.geojson"))
    }

    @Test
    fun simFilter_filtersRecords() = runBlocking {
        val sessionId = sessionRepository.create(name = "Sim Filter", recordingMode = "OUTDOOR")
        cellRecordRepository.insertAll(
            listOf(
                TestDataFactory.cellRecord(sessionId = sessionId, timestamp = 1000L, simSlotIndex = 0),
                TestDataFactory.cellRecord(sessionId = sessionId, timestamp = 2000L, simSlotIndex = 1),
                TestDataFactory.cellRecord(sessionId = sessionId, timestamp = 3000L, simSlotIndex = 0)
            )
        )

        viewModel.loadSession(sessionId)
        viewModel.records.first { it.size == 3 }
        viewModel.filteredRecords.first { it.size == 3 }

        viewModel.setSimFilter(1)
        val filtered = viewModel.filteredRecords.first { it.all { r -> r.record.simSlotIndex == 1 } }
        assertEquals(1, filtered.size)
        assertEquals(1, filtered[0].record.simSlotIndex)

        viewModel.setSimFilter(null)
        val all = viewModel.filteredRecords.first { it.size == 3 }
        assertEquals(3, all.size)
    }

    @Test
    fun deleteSession_removesSession() = runBlocking {
        val sessionId = sessionRepository.create(name = "Delete Me", recordingMode = "OUTDOOR")
        viewModel.loadSession(sessionId)
        viewModel.session.first { it != null }

        viewModel.deleteSession(sessionId)
        viewModel.session.first { it == null }
        assertNull(viewModel.session.value)
    }
}
