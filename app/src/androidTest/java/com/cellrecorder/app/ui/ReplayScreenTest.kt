package com.cellrecorder.app.ui

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.cellrecorder.app.HiltTestActivity
import com.cellrecorder.app.data.local.AppDatabase
import com.cellrecorder.app.data.local.entity.CellRecordEntity
import com.cellrecorder.app.data.repository.CellRecordRepository
import com.cellrecorder.app.data.repository.SessionRepository
import com.cellrecorder.app.data.repository.SpeedTestRecordRepository
import com.cellrecorder.app.domain.usecase.GetSessionPointsUseCase
import com.cellrecorder.app.ui.detail.replay.ReplayScreen
import com.cellrecorder.app.ui.detail.replay.ReplayViewModel
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
class ReplayScreenTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<HiltTestActivity>()

    private lateinit var viewModel: ReplayViewModel
    private var populatedSessionId: Long = 0L
    private var emptySessionId: Long = 0L

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

        runBlocking {
            populatedSessionId = sessionRepository.create(
                name = "Replay Session",
                createdAt = 1_700_000_000_000L,
                recordingMode = "INDOOR"
            )
            emptySessionId = sessionRepository.create(
                name = "Replay Empty",
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
            }
            db.cellRecordDao().insertAll(records)
        }

        val getSessionPointsUseCase = GetSessionPointsUseCase(cellRecordRepository)
        viewModel = ReplayViewModel(
            getSessionPointsUseCase,
            speedTestRecordRepository,
            sessionRepository
        )
    }

    @Test
    fun title_isDisplayed() {
        composeTestRule.setContent {
            CellRecorderTheme {
                ReplayScreen(
                    sessionId = populatedSessionId,
                    onNavigateBack = {},
                    viewModel = viewModel
                )
            }
        }
        composeTestRule.onNodeWithText("Replay").assertIsDisplayed()
    }

    @Test
    fun backButton_isDisplayed() {
        composeTestRule.setContent {
            CellRecorderTheme {
                ReplayScreen(
                    sessionId = populatedSessionId,
                    onNavigateBack = {},
                    viewModel = viewModel
                )
            }
        }
        composeTestRule.onNodeWithContentDescription("Back").assertIsDisplayed()
    }

    @Test
    fun emptyState_showsNoRecordSelected() {
        composeTestRule.setContent {
            CellRecorderTheme {
                ReplayScreen(
                    sessionId = emptySessionId,
                    onNavigateBack = {},
                    viewModel = viewModel
                )
            }
        }
        composeTestRule.onNodeWithText("No record selected").assertIsDisplayed()
    }

    @Test
    fun populatedStatsPanel_cellIdLabel_isDisplayed() {
        composeTestRule.setContent {
            CellRecorderTheme {
                ReplayScreen(
                    sessionId = populatedSessionId,
                    onNavigateBack = {},
                    viewModel = viewModel
                )
            }
        }
        composeTestRule.waitUntil(2000) { viewModel.filteredRecords.value.isNotEmpty() }
        composeTestRule.onNodeWithText("Cell ID").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun populatedStatsPanel_plmnLabel_isDisplayed() {
        composeTestRule.setContent {
            CellRecorderTheme {
                ReplayScreen(
                    sessionId = populatedSessionId,
                    onNavigateBack = {},
                    viewModel = viewModel
                )
            }
        }
        composeTestRule.waitUntil(2000) { viewModel.filteredRecords.value.isNotEmpty() }
        composeTestRule.onNodeWithText("PLMN").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun populated_ratTimeline_isDisplayed() {
        composeTestRule.setContent {
            CellRecorderTheme {
                ReplayScreen(
                    sessionId = populatedSessionId,
                    onNavigateBack = {},
                    viewModel = viewModel
                )
            }
        }
        composeTestRule.waitUntil(2000) { viewModel.filteredRecords.value.isNotEmpty() }
        composeTestRule.onNodeWithText("RAT Timeline").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun populated_playButton_isDisplayed() {
        composeTestRule.setContent {
            CellRecorderTheme {
                ReplayScreen(
                    sessionId = populatedSessionId,
                    onNavigateBack = {},
                    viewModel = viewModel
                )
            }
        }
        composeTestRule.waitUntil(2000) { viewModel.filteredRecords.value.isNotEmpty() }
        composeTestRule.onNodeWithText("Replay").assertIsDisplayed()
    }
}
