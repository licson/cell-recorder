package com.cellrecorder.app.ui

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.cellrecorder.app.data.local.AppDatabase
import com.cellrecorder.app.data.repository.CellRecordRepository
import com.cellrecorder.app.data.repository.SessionRepository
import com.cellrecorder.app.domain.usecase.CreateSessionUseCase
import com.cellrecorder.app.domain.usecase.ExportSessionUseCase
import com.cellrecorder.app.domain.usecase.GetSessionPointsUseCase
import com.cellrecorder.app.domain.usecase.GetSessionsUseCase
import com.cellrecorder.app.domain.usecase.import_.CsvRecordParser
import com.cellrecorder.app.domain.usecase.import_.GeoJsonRecordParser
import com.cellrecorder.app.domain.usecase.import_.ImportSessionUseCase
import com.cellrecorder.app.ui.sessionlist.SessionListScreen
import com.cellrecorder.app.ui.sessionlist.SessionListViewModel
import com.cellrecorder.app.ui.theme.CellRecorderTheme
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@Ignore("Requires test runner without process isolation")
@RunWith(AndroidJUnit4::class)
@MediumTest
class SessionListScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private lateinit var viewModel: SessionListViewModel

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val sessionRepository = SessionRepository(db.sessionDao())
        val cellRecordRepository = CellRecordRepository(db.cellRecordDao())
        val getSessionsUseCase = GetSessionsUseCase(sessionRepository)
        val createSessionUseCase = CreateSessionUseCase(sessionRepository)
        val exportSessionUseCase = ExportSessionUseCase()
        val getSessionPointsUseCase = GetSessionPointsUseCase(cellRecordRepository)
        val csvRecordParser = CsvRecordParser()
        val geoJsonRecordParser = GeoJsonRecordParser()
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
    fun emptyState_showsNoSessionsMessage() {
        composeTestRule.setContent {
            CellRecorderTheme {
                SessionListScreen(
                    onStartRecording = {},
                    onOpenSession = {},
                    onOpenSettings = {},
                    viewModel = viewModel
                )
            }
        }

        composeTestRule.onNodeWithText("No sessions yet.").assertIsDisplayed()
    }

    @Test
    fun floatingActionButton_isDisplayed() {
        composeTestRule.setContent {
            CellRecorderTheme {
                SessionListScreen(
                    onStartRecording = {},
                    onOpenSession = {},
                    onOpenSettings = {},
                    viewModel = viewModel
                )
            }
        }

        composeTestRule.onNodeWithText("New Session").assertIsDisplayed()
    }

    @Test
    fun settingsButton_isDisplayed() {
        composeTestRule.setContent {
            CellRecorderTheme {
                SessionListScreen(
                    onStartRecording = {},
                    onOpenSession = {},
                    onOpenSettings = {},
                    viewModel = viewModel
                )
            }
        }

        composeTestRule.onNodeWithText("Settings").assertIsDisplayed()
    }

    @Test
    fun importButton_isDisplayed() {
        composeTestRule.setContent {
            CellRecorderTheme {
                SessionListScreen(
                    onStartRecording = {},
                    onOpenSession = {},
                    onOpenSettings = {},
                    viewModel = viewModel
                )
            }
        }

        composeTestRule.onNodeWithText("Import recording").assertIsDisplayed()
    }

    @Test
    fun selectButton_isDisplayed() {
        composeTestRule.setContent {
            CellRecorderTheme {
                SessionListScreen(
                    onStartRecording = {},
                    onOpenSession = {},
                    onOpenSettings = {},
                    viewModel = viewModel
                )
            }
        }

        composeTestRule.onNodeWithText("Select sessions").assertIsDisplayed()
    }
}