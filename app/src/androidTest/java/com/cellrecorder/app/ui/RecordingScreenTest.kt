package com.cellrecorder.app.ui

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.cellrecorder.app.HiltTestActivity
import com.cellrecorder.app.data.local.AppDatabase
import com.cellrecorder.app.data.local.entity.AppConfigEntity
import com.cellrecorder.app.data.repository.ConfigRepository
import com.cellrecorder.app.data.repository.RecentMarkerLabelRepository
import com.cellrecorder.app.data.repository.SessionMarkerRepository
import com.cellrecorder.app.data.repository.SessionRepository
import com.cellrecorder.app.domain.model.CellRecordSnapshot
import com.cellrecorder.app.service.CellInfoCollector
import com.cellrecorder.app.service.IndoorPositionCollector
import com.cellrecorder.app.service.RecordingMutex
import com.cellrecorder.app.service.RecordingState
import com.cellrecorder.app.service.RecordingStateManager
import com.cellrecorder.app.ui.recording.RecordingScreen
import com.cellrecorder.app.ui.recording.RecordingViewModel
import com.cellrecorder.app.ui.theme.CellRecorderTheme
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@MediumTest
class RecordingScreenTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<HiltTestActivity>()

    private lateinit var viewModel: RecordingViewModel
    private lateinit var stateManager: RecordingStateManager
    private var sessionId: Long = 0L
    private var tunnelSessionId: Long = 0L

    @Before
    fun setUp() {
        hiltRule.inject()
        val app = ApplicationProvider.getApplicationContext<Application>()
        val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val sessionRepository = SessionRepository(db.sessionDao())
        val configRepository = ConfigRepository(db.configDao())
        runBlocking {
            configRepository.update(AppConfigEntity())
            sessionId = sessionRepository.create(
                name = "Rec Session",
                createdAt = 1_700_000_000_000L,
                recordingMode = "OUTDOOR"
            )
            tunnelSessionId = sessionRepository.create(
                name = "Tunnel Rec Session",
                createdAt = 1_700_000_000_000L,
                recordingMode = "TUNNEL"
            )
        }

        val snapshot = CellRecordSnapshot(
            subscriptionId = 1,
            rat = "4G",
            mcc = "310",
            mnc = "260",
            bandNumber = 3,
            earfcn = 1650,
            pci = 100,
            tac = 1,
            rsrp = -95,
            rsrq = -10,
            sinr = 5
        )
        val cellInfoCollector = mockk<CellInfoCollector>(relaxed = true)
        every { cellInfoCollector.snapshots(any()) } returns listOf(snapshot)
        val indoorPositionCollector = mockk<IndoorPositionCollector>(relaxed = true)
        val appContext = mockk<Context>(relaxed = true)
        stateManager = RecordingStateManager()
        val sessionMarkerRepository = SessionMarkerRepository(
            db.sessionMarkerDao(),
            db.recentMarkerLabelDao(),
            db
        )
        val recentMarkerLabelRepository = RecentMarkerLabelRepository(db.recentMarkerLabelDao())
        val recordingMutex = RecordingMutex()

        viewModel = RecordingViewModel(
            sessionRepository,
            sessionMarkerRepository,
            recentMarkerLabelRepository,
            recordingMutex,
            cellInfoCollector,
            configRepository,
            stateManager,
            indoorPositionCollector,
            appContext
        )
    }

    @Test
    fun title_sessionName_isDisplayed() {
        composeTestRule.setContent {
            CellRecorderTheme {
                RecordingScreen(
                    sessionId = sessionId,
                    onNavigateBack = {},
                    viewModel = viewModel
                )
            }
        }
        composeTestRule.waitUntil(2000) { viewModel.session.value != null }
        composeTestRule.onNodeWithText("Rec Session").assertIsDisplayed()
    }

    @Test
    fun backButton_isDisplayed() {
        composeTestRule.setContent {
            CellRecorderTheme {
                RecordingScreen(
                    sessionId = sessionId,
                    onNavigateBack = {},
                    viewModel = viewModel
                )
            }
        }
        composeTestRule.waitUntil(2000) { viewModel.session.value != null }
        composeTestRule.onNodeWithText("Back").assertIsDisplayed()
    }

    @Test
    fun startButton_isDisplayed() {
        composeTestRule.setContent {
            CellRecorderTheme {
                RecordingScreen(
                    sessionId = sessionId,
                    onNavigateBack = {},
                    viewModel = viewModel
                )
            }
        }
        composeTestRule.waitUntil(2000) { viewModel.session.value != null }
        composeTestRule.onNodeWithContentDescription("Start").assertIsDisplayed()
    }

    @Test
    fun gpsWaitingIndicator_isDisplayed() {
        composeTestRule.setContent {
            CellRecorderTheme {
                RecordingScreen(
                    sessionId = sessionId,
                    onNavigateBack = {},
                    viewModel = viewModel
                )
            }
        }
        composeTestRule.waitUntil(2000) { viewModel.session.value != null }
        composeTestRule.onNodeWithText("Waiting for GPS...").assertIsDisplayed()
    }

    @Test
    fun liveCellInfo_plmnLabel_isDisplayed() {
        composeTestRule.setContent {
            CellRecorderTheme {
                RecordingScreen(
                    sessionId = sessionId,
                    onNavigateBack = {},
                    viewModel = viewModel
                )
            }
        }
        composeTestRule.waitUntil(2000) { viewModel.liveSimStates.value.isNotEmpty() }
        composeTestRule.onNodeWithText("PLMN").assertIsDisplayed()
    }

    @Test
    fun liveCellInfo_cellIdLabel_isDisplayed() {
        composeTestRule.setContent {
            CellRecorderTheme {
                RecordingScreen(
                    sessionId = sessionId,
                    onNavigateBack = {},
                    viewModel = viewModel
                )
            }
        }
        composeTestRule.waitUntil(2000) { viewModel.liveSimStates.value.isNotEmpty() }
        composeTestRule.onNodeWithText("Cell ID").assertIsDisplayed()
    }

    @Test
    fun serviceStatePresent_showsGpsStatus() {
        stateManager.currentState = RecordingState(
            sessionId = sessionId,
            isRecording = false,
            recordingMode = "OUTDOOR",
            gpsStatus = "OK",
            currentLatency = "--",
            currentLatitude = 1.0,
            currentLongitude = 1.0
        )
        composeTestRule.setContent {
            CellRecorderTheme {
                RecordingScreen(
                    sessionId = sessionId,
                    onNavigateBack = {},
                    viewModel = viewModel
                )
            }
        }
        composeTestRule.waitUntil(2000) { viewModel.session.value != null }
        composeTestRule.onNodeWithText("GPS OK", substring = true).assertIsDisplayed()
    }

    @Test
    fun recordingState_showsStopButton() {
        stateManager.currentState = RecordingState(
            sessionId = sessionId,
            isRecording = true,
            recordingMode = "OUTDOOR",
            currentLatitude = 1.0,
            currentLongitude = 1.0
        )
        composeTestRule.setContent {
            CellRecorderTheme {
                RecordingScreen(
                    sessionId = sessionId,
                    onNavigateBack = {},
                    viewModel = viewModel
                )
            }
        }
        composeTestRule.waitUntil(2000) { viewModel.session.value != null }
        composeTestRule.onNodeWithContentDescription("Stop").assertIsDisplayed()
    }

    @Test
    fun markButton_isDisplayed_whenRecordingActive_inOutdoorMode() {
        stateManager.currentState = RecordingState(
            sessionId = sessionId,
            isRecording = true,
            recordingMode = "OUTDOOR",
            currentLatitude = 1.0,
            currentLongitude = 1.0
        )
        composeTestRule.setContent {
            CellRecorderTheme {
                RecordingScreen(
                    sessionId = sessionId,
                    onNavigateBack = {},
                    viewModel = viewModel
                )
            }
        }
        composeTestRule.waitUntil(2000) { viewModel.session.value != null }
        composeTestRule.onNodeWithText("Mark").assertIsDisplayed()
    }

    @Test
    fun markButton_isDisplayed_whenRecordingActive_inIndoorMode() {
        stateManager.currentState = RecordingState(
            sessionId = sessionId,
            isRecording = true,
            recordingMode = "INDOOR",
            currentRelativeX = 0.0,
            currentRelativeY = 0.0
        )
        composeTestRule.setContent {
            CellRecorderTheme {
                RecordingScreen(
                    sessionId = sessionId,
                    onNavigateBack = {},
                    viewModel = viewModel
                )
            }
        }
        composeTestRule.waitUntil(2000) { viewModel.session.value != null }
        composeTestRule.onNodeWithText("Mark").assertIsDisplayed()
    }

    @Test
    fun markButton_isDisplayed_whenRecordingActive_inTunnelMode() {
        stateManager.currentState = RecordingState(
            sessionId = sessionId,
            isRecording = true,
            recordingMode = "TUNNEL"
        )
        composeTestRule.setContent {
            CellRecorderTheme {
                RecordingScreen(
                    sessionId = sessionId,
                    onNavigateBack = {},
                    viewModel = viewModel
                )
            }
        }
        composeTestRule.waitUntil(2000) { viewModel.session.value != null }
        composeTestRule.onNodeWithText("Mark").assertIsDisplayed()
    }

    @Test
    fun markButton_isNotDisplayed_whenRecordingInactive() {
        stateManager.currentState = RecordingState(
            sessionId = sessionId,
            isRecording = false,
            recordingMode = "OUTDOOR"
        )
        composeTestRule.setContent {
            CellRecorderTheme {
                RecordingScreen(
                    sessionId = sessionId,
                    onNavigateBack = {},
                    viewModel = viewModel
                )
            }
        }
        composeTestRule.waitUntil(2000) { viewModel.session.value != null }
        composeTestRule.onNodeWithContentDescription("Start").assertIsDisplayed()
    }

    @Test
    fun tunnelPlaceholderPanel_isDisplayed_inTunnelMode() {
        stateManager.currentState = RecordingState(
            sessionId = tunnelSessionId,
            isRecording = true,
            recordingMode = "TUNNEL"
        )
        composeTestRule.setContent {
            CellRecorderTheme {
                RecordingScreen(
                    sessionId = tunnelSessionId,
                    onNavigateBack = {},
                    viewModel = viewModel
                )
            }
        }
        composeTestRule.waitUntil(2000) { viewModel.session.value != null }
        composeTestRule.onNodeWithText("Tunnel recording in progress", substring = true).assertIsDisplayed()
    }
}
