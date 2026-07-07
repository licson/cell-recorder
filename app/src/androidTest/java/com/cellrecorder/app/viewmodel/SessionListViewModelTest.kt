package com.cellrecorder.app.viewmodel

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.cellrecorder.app.data.local.entity.CellRecordEntity
import com.cellrecorder.app.data.local.entity.SessionEntity
import com.cellrecorder.app.data.repository.CellRecordRepository
import com.cellrecorder.app.data.repository.RecentMarkerLabelRepository
import com.cellrecorder.app.data.repository.SessionMarkerRepository
import com.cellrecorder.app.data.repository.SessionRepository
import com.cellrecorder.app.domain.usecase.CreateSessionUseCase
import com.cellrecorder.app.domain.usecase.ExportSessionUseCase
import com.cellrecorder.app.domain.usecase.GetSessionPointsUseCase
import com.cellrecorder.app.domain.usecase.GetSessionsUseCase
import com.cellrecorder.app.domain.usecase.import_.CsvRecordParser
import com.cellrecorder.app.domain.usecase.import_.GeoJsonRecordParser
import com.cellrecorder.app.domain.usecase.import_.ImportSessionUseCase
import com.cellrecorder.app.ui.sessionlist.SessionListViewModel
import com.cellrecorder.app.util.MainDispatcherRule
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
class SessionListViewModelTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Inject
    lateinit var sessionRepository: SessionRepository

    @Inject
    lateinit var cellRecordRepository: CellRecordRepository

    @Inject
    lateinit var csvRecordParser: CsvRecordParser

    @Inject
    lateinit var geoJsonRecordParser: GeoJsonRecordParser

    @Inject
    lateinit var sessionMarkerRepository: SessionMarkerRepository

    @Inject
    lateinit var recentMarkerLabelRepository: RecentMarkerLabelRepository

    @Inject
    @ApplicationContext
    lateinit var context: Context

    private lateinit var viewModel: SessionListViewModel

    @Before
    fun setUp() {
        hiltRule.inject()
        val getSessionsUseCase = GetSessionsUseCase(sessionRepository)
        val createSessionUseCase = CreateSessionUseCase(sessionRepository)
        val exportSessionUseCase = ExportSessionUseCase()
        val getSessionPointsUseCase = GetSessionPointsUseCase(cellRecordRepository)
        val importSessionUseCase = ImportSessionUseCase(
            sessionRepository, cellRecordRepository, sessionMarkerRepository, csvRecordParser, geoJsonRecordParser
        )
        viewModel = SessionListViewModel(
            getSessionsUseCase,
            createSessionUseCase,
            sessionRepository,
            exportSessionUseCase,
            getSessionPointsUseCase,
            importSessionUseCase,
            context
        )
    }

    @Test
    fun sessionsEmitsEmptyInitially() = runBlocking {
        val sessions = viewModel.sessions.first()
        assertTrue(sessions.isEmpty())
    }

    @Test
    fun sessionsReflectsRepositoryData() = runBlocking {
        sessionRepository.create(name = "Seeded", recordingMode = "OUTDOOR")

        val sessions = viewModel.sessions.first { it.isNotEmpty() }
        assertEquals(1, sessions.size)
        assertEquals("Seeded", sessions[0].name)
    }

    @Test
    fun toggleSelection_selectsAndDeselects() = runBlocking {
        val id = sessionRepository.create(name = "Test", recordingMode = "OUTDOOR")
        viewModel.sessions.first { it.isNotEmpty() }

        viewModel.toggleSelection(id)
        var selected = viewModel.selectedIds.first()
        assertTrue(selected.contains(id))

        viewModel.toggleSelection(id)
        selected = viewModel.selectedIds.first()
        assertFalse(selected.contains(id))
    }

    @Test
    fun clearSelection_removesAll() = runBlocking {
        val id = sessionRepository.create(name = "Test", recordingMode = "OUTDOOR")
        viewModel.sessions.first { it.isNotEmpty() }
        viewModel.toggleSelection(id)
        viewModel.clearSelection()

        val selected = viewModel.selectedIds.first()
        assertTrue(selected.isEmpty())
    }

    @Test
    fun renameSession_updatesName() = runBlocking {
        val id = sessionRepository.create(name = "Old Name", recordingMode = "OUTDOOR")
        viewModel.sessions.first { it.isNotEmpty() }

        viewModel.renameSession(id, "New Name")

        val updated = viewModel.sessions.first { it[0].name == "New Name" }
        assertEquals("New Name", updated[0].name)
    }

    @Test
    fun deleteSession_removesFromList() = runBlocking {
        val id = sessionRepository.create(name = "Delete me", recordingMode = "OUTDOOR")
        viewModel.sessions.first { it.isNotEmpty() }

        viewModel.deleteSession(id)
        val sessions = viewModel.sessions.first { it.isEmpty() }
        assertTrue(sessions.isEmpty())
    }

    @Test
    fun multipleSessions_orderedByCreatedAtDesc() = runBlocking {
        sessionRepository.create(name = "Old", recordingMode = "OUTDOOR", createdAt = 1000L)
        sessionRepository.create(name = "New", recordingMode = "OUTDOOR", createdAt = 2000L)

        val sessions = viewModel.sessions.first { it.size == 2 }
        assertEquals(2, sessions.size)
        assertEquals("New", sessions[0].name)
        assertEquals("Old", sessions[1].name)
    }

    @Test
    fun selectionMode_trueWhenSelected() = runBlocking {
        val id = sessionRepository.create(name = "Test", recordingMode = "OUTDOOR")
        viewModel.sessions.first { it.isNotEmpty() }

        var selectionMode = viewModel.selectionMode.first()
        assertFalse(selectionMode)

        viewModel.toggleSelection(id)
        selectionMode = viewModel.selectionMode.first()
        assertTrue(selectionMode)
    }

    @Test
    fun createSession_createsInRepository() = runBlocking {
        viewModel.createSession("New Session", "OUTDOOR")

        val createdId = viewModel.createdSessionId.first { it != null }
        assertNotNull(createdId)
        assertTrue(createdId!! > 0)
    }

    @Test
    fun exportFlow_producesExpectedCsv() = runBlocking {
        val sessionId = sessionRepository.create(name = "Export Test", recordingMode = "OUTDOOR")
        cellRecordRepository.insertAll(
            listOf(
                CellRecordEntity(
                    sessionId = sessionId,
                    timestamp = 1000L,
                    latitude = 1.0,
                    longitude = 2.0,
                    altitude = 3.0,
                    accuracy = 5f,
                    rat = "4G",
                    pci = 100,
                    rsrp = -90,
                    rsrq = -10,
                    sinr = 5
                )
            )
        )

        val sessions = viewModel.sessions.first { it.isNotEmpty() }
        val summary = sessions[0]
        assertEquals(sessionId, summary.id)

        val records = cellRecordRepository.getBySessionIdOnceWithCaBands(sessionId)
        assertEquals(1, records.size)

        val exportSessionUseCase = ExportSessionUseCase()
        val sessionEntity = SessionEntity(
            id = summary.id,
            name = summary.name,
            createdAt = summary.createdAt,
            endedAt = summary.endedAt,
            pointCount = summary.pointCount,
            recordingMode = "OUTDOOR"
        )
        val export = exportSessionUseCase.exportCsv(sessionEntity, records)

        assertTrue(export.content.startsWith("timestamp,lat,lon,alt,accuracy"))
        assertTrue(export.content.contains("1000,1.0,2.0,3.0,5.0"))
        assertEquals("text/csv", export.mimeType)
        assertTrue(export.suggestedFilename.contains("Export_Test"))
    }

    @Test
    fun importFile_csv_createsSessionAndEmitsSummary() = runBlocking {
        val csv = "timestamp,lat,lon,alt,accuracy,rat\n" +
            "1000,1.0,2.0,3.0,5.0,4G\n" +
            "2000,1.5,2.5,3.0,5.0,4G\n"

        viewModel.importFile(csv, "test_import.csv")

        val summary = viewModel.importSummary.first { it != null }
        assertNotNull(summary)
        assertEquals(2, summary!!.importedCount)
        assertEquals(0, summary.errorCount)
        assertEquals("test import", summary.sessionName)

        val sessions = viewModel.sessions.first { it.isNotEmpty() }
        assertEquals(1, sessions.size)
        assertEquals("test import", sessions[0].name)
    }

    @Test
    fun deleteSelected_removesSelectedSessionsAndClearsSelection() = runBlocking {
        val id1 = sessionRepository.create(name = "S1", recordingMode = "OUTDOOR")
        val id2 = sessionRepository.create(name = "S2", recordingMode = "OUTDOOR")
        val id3 = sessionRepository.create(name = "S3", recordingMode = "OUTDOOR")
        viewModel.sessions.first { it.size == 3 }

        viewModel.toggleSelection(id1)
        viewModel.toggleSelection(id3)

        val selected = viewModel.selectedIds.first()
        assertEquals(setOf(id1, id3), selected)

        val selectionMode = viewModel.selectionMode.first()
        assertTrue(selectionMode)

        viewModel.deleteSelected()

        val remaining = viewModel.sessions.first { it.size == 1 }
        assertEquals(1, remaining.size)
        assertEquals(id2, remaining[0].id)

        val selectedAfter = viewModel.selectedIds.first()
        assertTrue(selectedAfter.isEmpty())
    }
}