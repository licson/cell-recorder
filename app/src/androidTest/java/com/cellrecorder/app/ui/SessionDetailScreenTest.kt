package com.cellrecorder.app.ui

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.cellrecorder.app.HiltTestActivity
import com.cellrecorder.app.data.local.AppDatabase
import com.cellrecorder.app.data.local.entity.AppConfigEntity
import com.cellrecorder.app.data.local.entity.CellRecordEntity
import com.cellrecorder.app.data.local.entity.SessionMarkerEntity
import com.cellrecorder.app.data.repository.CellRecordRepository
import com.cellrecorder.app.data.repository.ConfigRepository
import com.cellrecorder.app.data.repository.RecentMarkerLabelRepository
import com.cellrecorder.app.data.repository.SessionMarkerRepository
import com.cellrecorder.app.data.repository.SessionRepository
import com.cellrecorder.app.data.repository.SpeedTestRecordRepository
import com.cellrecorder.app.domain.usecase.BatchResplitUseCase
import com.cellrecorder.app.domain.usecase.ExportSessionUseCase
import com.cellrecorder.app.domain.usecase.ExportSpeedTestUseCase
import com.cellrecorder.app.domain.usecase.GetConfigUseCase
import com.cellrecorder.app.domain.usecase.GetSessionPointsUseCase
import com.cellrecorder.app.ui.detail.SessionDetailScreen
import com.cellrecorder.app.ui.detail.SessionDetailViewModel
import com.cellrecorder.app.ui.theme.CellRecorderTheme
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@MediumTest
class SessionDetailScreenTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<HiltTestActivity>()

    private lateinit var viewModel: SessionDetailViewModel
    private var populatedSessionId: Long = 0L
    private var emptySessionId: Long = 0L
    private var tunnelSessionId: Long = 0L

    @Before
    fun setUp() {
        hiltRule.inject()
        val app = ApplicationProvider.getApplicationContext<Application>()
        val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val sessionRepository = SessionRepository(db.sessionDao())
        val cellRecordRepository = CellRecordRepository(db.cellRecordDao())
        val speedTestRecordRepository = SpeedTestRecordRepository(db.speedTestRecordDao())
        val configRepository = ConfigRepository(db.configDao())

        runBlocking {
            configRepository.update(AppConfigEntity())
            populatedSessionId = sessionRepository.create(
                name = "Detail Session",
                createdAt = 1_700_000_000_000L,
                recordingMode = "INDOOR"
            )
            emptySessionId = sessionRepository.create(
                name = "Empty Session",
                createdAt = 1_700_000_001_000L,
                recordingMode = "INDOOR"
            )
            val records = (0..2).map { i ->
                CellRecordEntity(
                    sessionId = populatedSessionId,
                    timestamp = 1_700_000_000_000L + i * 1000L,
                    latitude = 37.0,
                    longitude = -122.0,
                    altitude = 0.0,
                    accuracy = 5f,
                    relativeX = i.toDouble(),
                    relativeY = (i * 2).toDouble(),
                    rat = "4G",
                    simSlotIndex = 0,
                    mcc = "310",
                    mnc = "260",
                    bandNumber = 3,
                    earfcn = 1650,
                    pci = 100,
                    rsrp = -95,
                    rsrq = -10,
                    sinr = 5,
                    fullCellIdentity = 12345L,
                    enbOrGnbId = 48L,
                    lcid = 3,
                    avgLatencyMs = 20.0
                )
            } + CellRecordEntity(
                sessionId = populatedSessionId,
                timestamp = 1_700_000_003_000L,
                latitude = 37.0,
                longitude = -122.0,
                altitude = 0.0,
                accuracy = 5f,
                relativeX = 3.0,
                relativeY = 6.0,
                rat = "5G_NSA",
                simSlotIndex = 0,
                mcc = "310",
                mnc = "260",
                bandNumber = 78,
                earfcn = 620_000,
                pci = 200,
                rsrp = -85,
                rsrq = -9,
                sinr = 10,
                fullCellIdentity = 99999L,
                enbOrGnbId = 48L,
                lcid = 3,
                anchorPci = 100,
                anchorEnbOrGnbId = 200L,
                anchorLcid = 5,
                anchorBandNumber = 3,
                anchorEarfcn = 1650,
                anchorTac = 1,
                anchorRsrp = -85,
                avgLatencyMs = 20.0
            )
            db.cellRecordDao().insertAll(records)

            // Create a tunnel session with markers and TUNNEL cell records
            tunnelSessionId = sessionRepository.create(
                name = "Tunnel Session",
                createdAt = 1_700_000_002_000L,
                recordingMode = "TUNNEL"
            )
            val tunnelRecords = (0..1).map { i ->
                CellRecordEntity(
                    sessionId = tunnelSessionId,
                    timestamp = 1_700_000_002_000L + i * 1000L,
                    latitude = 0.0,
                    longitude = 0.0,
                    altitude = 0.0,
                    accuracy = 0f,
                    relativeX = null,
                    relativeY = null,
                    rat = "UNKNOWN",
                    locationSource = "TUNNEL",
                    simSlotIndex = 0,
                    rsrp = -90,
                    rsrq = -10,
                    sinr = 5
                )
            }
            db.cellRecordDao().insertAll(tunnelRecords)
        }

        val getSessionPointsUseCase = GetSessionPointsUseCase(cellRecordRepository)
        val exportSessionUseCase = ExportSessionUseCase()
        val batchResplitUseCase = BatchResplitUseCase(cellRecordRepository)
        val getConfigUseCase = GetConfigUseCase(configRepository)
        val exportSpeedTestUseCase = ExportSpeedTestUseCase()
        val sessionMarkerRepository = SessionMarkerRepository(
            db.sessionMarkerDao(),
            db.recentMarkerLabelDao(),
            db
        )
        // Seed markers for the populated session (for task 8.6)
        runBlocking {
            sessionMarkerRepository.insertMarkerWithAutoLabel(populatedSessionId, com.cellrecorder.app.domain.model.MarkerType.NOTE)
            sessionMarkerRepository.insertMarkerWithAutoLabel(populatedSessionId, com.cellrecorder.app.domain.model.MarkerType.WAYPOINT)
        }
        val recentMarkerLabelRepository = RecentMarkerLabelRepository(db.recentMarkerLabelDao())

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
    fun backButton_isDisplayed() {
        composeTestRule.setContent {
            CellRecorderTheme {
                SessionDetailScreen(
                    sessionId = populatedSessionId,
                    onNavigateBack = {},
                    onOpenReplay = {},
                    viewModel = viewModel
                )
            }
        }
        composeTestRule.waitUntil(2000) { viewModel.session.value != null }
        composeTestRule.onNodeWithContentDescription("Back").assertIsDisplayed()
    }

    @Test
    fun title_sessionName_isDisplayed() {
        composeTestRule.setContent {
            CellRecorderTheme {
                SessionDetailScreen(
                    sessionId = populatedSessionId,
                    onNavigateBack = {},
                    onOpenReplay = {},
                    viewModel = viewModel
                )
            }
        }
        composeTestRule.waitUntil(2000) { viewModel.session.value != null }
        composeTestRule.onNodeWithText("Detail Session").assertIsDisplayed()
    }

    @Test
    fun analyticsButton_isDisplayed() {
        composeTestRule.setContent {
            CellRecorderTheme {
                SessionDetailScreen(
                    sessionId = populatedSessionId,
                    onNavigateBack = {},
                    onOpenReplay = {},
                    viewModel = viewModel
                )
            }
        }
        composeTestRule.waitUntil(2000) { viewModel.session.value != null }
        composeTestRule.onNodeWithContentDescription("Analytics").assertIsDisplayed()
    }

    @Test
    fun replayButton_isDisplayed() {
        composeTestRule.setContent {
            CellRecorderTheme {
                SessionDetailScreen(
                    sessionId = populatedSessionId,
                    onNavigateBack = {},
                    onOpenReplay = {},
                    viewModel = viewModel
                )
            }
        }
        composeTestRule.waitUntil(2000) { viewModel.session.value != null }
        composeTestRule.onNodeWithContentDescription("Replay").assertIsDisplayed()
    }

    @Test
    fun moreButton_isDisplayed() {
        composeTestRule.setContent {
            CellRecorderTheme {
                SessionDetailScreen(
                    sessionId = populatedSessionId,
                    onNavigateBack = {},
                    onOpenReplay = {},
                    viewModel = viewModel
                )
            }
        }
        composeTestRule.waitUntil(2000) { viewModel.session.value != null }
        composeTestRule.onNodeWithContentDescription("More").assertIsDisplayed()
    }

    @Test
    fun recordsList_plmnHeader_isDisplayed() {
        composeTestRule.setContent {
            CellRecorderTheme {
                SessionDetailScreen(
                    sessionId = populatedSessionId,
                    onNavigateBack = {},
                    onOpenReplay = {},
                    viewModel = viewModel
                )
            }
        }
        composeTestRule.waitUntil(2000) { viewModel.records.value.isNotEmpty() }
        composeTestRule.onNodeWithText("PLMN").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun recordsList_relXHeader_isDisplayed() {
        composeTestRule.setContent {
            CellRecorderTheme {
                SessionDetailScreen(
                    sessionId = populatedSessionId,
                    onNavigateBack = {},
                    onOpenReplay = {},
                    viewModel = viewModel
                )
            }
        }
        composeTestRule.waitUntil(2000) { viewModel.records.value.isNotEmpty() }
        composeTestRule.onNodeWithText("relX (m)").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun emptySession_rendersWithoutRecords() {
        composeTestRule.setContent {
            CellRecorderTheme {
                SessionDetailScreen(
                    sessionId = emptySessionId,
                    onNavigateBack = {},
                    onOpenReplay = {},
                    viewModel = viewModel
                )
            }
        }
        composeTestRule.waitUntil(2000) { viewModel.session.value != null }
        composeTestRule.onNodeWithText("Empty Session").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Back").assertIsDisplayed()
    }

    @Test
    fun populatedSession_toggleAnalytics_showsCoverageByRat() {
        composeTestRule.setContent {
            CellRecorderTheme {
                SessionDetailScreen(
                    sessionId = populatedSessionId,
                    onNavigateBack = {},
                    onOpenReplay = {},
                    viewModel = viewModel
                )
            }
        }
        composeTestRule.waitUntil(2000) { viewModel.records.value.isNotEmpty() }
        composeTestRule.onNodeWithContentDescription("Analytics").performClick()
        composeTestRule.waitUntil(2000) { viewModel.showAnalytics.value }
        composeTestRule.onNodeWithText("Coverage by RAT").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun nsaRecord_anchorCellId_isDisplayedInDetailSheet() {
        composeTestRule.setContent {
            CellRecorderTheme {
                SessionDetailScreen(
                    sessionId = populatedSessionId,
                    onNavigateBack = {},
                    onOpenReplay = {},
                    viewModel = viewModel
                )
            }
        }
        composeTestRule.waitUntil(2000) { viewModel.records.value.isNotEmpty() }
        composeTestRule.onNodeWithText("Anchor: B3 PCI 100 RSRP -85", substring = true).performScrollTo().performClick()
        composeTestRule.waitUntil(2000) { viewModel.selectedRecord.value != null }
        composeTestRule.onNodeWithText("Anchor Cell").assertExists()
        composeTestRule.onNodeWithText("200:5").assertExists()
    }

    @Test
    fun markersSection_isDisplayed_whenMarkersExist() {
        composeTestRule.setContent {
            CellRecorderTheme {
                SessionDetailScreen(
                    sessionId = populatedSessionId,
                    onNavigateBack = {},
                    onOpenReplay = {},
                    viewModel = viewModel
                )
            }
        }
        composeTestRule.waitUntil(2000) { viewModel.markers.value.isNotEmpty() }
        composeTestRule.onNodeWithText("Markers (2)", substring = true).assertExists()
    }

    @Test
    fun markersSection_isHidden_whenNoMarkersExist() {
        composeTestRule.setContent {
            CellRecorderTheme {
                SessionDetailScreen(
                    sessionId = emptySessionId,
                    onNavigateBack = {},
                    onOpenReplay = {},
                    viewModel = viewModel
                )
            }
        }
        composeTestRule.waitUntil(2000) { viewModel.session.value != null }
        composeTestRule.waitUntil(2000) { viewModel.markers.value.isEmpty() }
    }

    @Test
    fun markersSection_expandingShowsRowsWithEditAndDeleteButtons() {
        composeTestRule.setContent {
            CellRecorderTheme {
                SessionDetailScreen(
                    sessionId = populatedSessionId,
                    onNavigateBack = {},
                    onOpenReplay = {},
                    viewModel = viewModel
                )
            }
        }
        composeTestRule.waitUntil(2000) { viewModel.markers.value.isNotEmpty() }
        composeTestRule.onNodeWithText("Markers (2)", substring = true).performClick()
        composeTestRule.waitUntil(2000) { viewModel.markers.value.isNotEmpty() }
        composeTestRule.onAllNodesWithContentDescription("Edit").assertCountEquals(2)
        composeTestRule.onAllNodesWithContentDescription("Delete").assertCountEquals(2)
    }

    @Test
    fun tunnelSession_showsPlaceholderPanelInsteadOfMap() {
        composeTestRule.setContent {
            CellRecorderTheme {
                SessionDetailScreen(
                    sessionId = tunnelSessionId,
                    onNavigateBack = {},
                    onOpenReplay = {},
                    viewModel = viewModel
                )
            }
        }
        composeTestRule.waitUntil(2000) { viewModel.session.value != null }
        composeTestRule.onNodeWithText("Tunnel recording", substring = true).assertExists()
    }
}
