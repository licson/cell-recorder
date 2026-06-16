package com.cellrecorder.app.viewmodel

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.cellrecorder.app.data.repository.CellRecordRepository
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
            sessionRepository, cellRecordRepository, csvRecordParser, geoJsonRecordParser
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
}