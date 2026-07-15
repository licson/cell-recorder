package com.cellrecorder.app.ui

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.cellrecorder.app.HiltTestActivity
import com.cellrecorder.app.data.local.AppDatabase
import com.cellrecorder.app.data.local.entity.CellRecordEntity
import com.cellrecorder.app.data.local.entity.SpeedTestRecordEntity
import com.cellrecorder.app.data.repository.CellRecordRepository
import com.cellrecorder.app.data.repository.SessionRepository
import com.cellrecorder.app.data.repository.SpeedTestRecordRepository
import com.cellrecorder.app.ui.statistics.StatisticsScreen
import com.cellrecorder.app.ui.statistics.StatisticsViewModel
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
class StatisticsScreenTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<HiltTestActivity>()

    private lateinit var viewModel: StatisticsViewModel
    private lateinit var sessionRepository: SessionRepository
    private lateinit var cellRecordRepository: CellRecordRepository
    private lateinit var speedTestRecordRepository: SpeedTestRecordRepository

    @Before
    fun setUp() {
        hiltRule.inject()
        val app = ApplicationProvider.getApplicationContext<Application>()
        val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        sessionRepository = SessionRepository(db.sessionDao())
        cellRecordRepository = CellRecordRepository(db.cellRecordDao())
        speedTestRecordRepository = SpeedTestRecordRepository(db.speedTestRecordDao())
        viewModel = StatisticsViewModel(
            sessionRepository,
            cellRecordRepository,
            speedTestRecordRepository
        )
    }

    @Test
    fun title_isDisplayed() {
        composeTestRule.setContent {
            CellRecorderTheme {
                StatisticsScreen(viewModel = viewModel)
            }
        }
        composeTestRule.onNodeWithText("Statistics").assertIsDisplayed()
    }

    @Test
    fun emptyState_showsNoDataMessage() {
        composeTestRule.setContent {
            CellRecorderTheme {
                StatisticsScreen(viewModel = viewModel)
            }
        }
        composeTestRule.onNodeWithText("No recording data yet.", substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun summaryCards_sessionsLabel_isDisplayed() {
        composeTestRule.setContent {
            CellRecorderTheme {
                StatisticsScreen(viewModel = viewModel)
            }
        }
        composeTestRule.onNodeWithText("Sessions").assertIsDisplayed()
    }

    @Test
    fun summaryCards_totalPointsLabel_isDisplayed() {
        composeTestRule.setContent {
            CellRecorderTheme {
                StatisticsScreen(viewModel = viewModel)
            }
        }
        composeTestRule.onNodeWithText("Total Points").assertIsDisplayed()
    }

    @Test
    fun summaryCards_totalDurationLabel_isDisplayed() {
        composeTestRule.setContent {
            CellRecorderTheme {
                StatisticsScreen(viewModel = viewModel)
            }
        }
        composeTestRule.onNodeWithText("Total Duration").assertIsDisplayed()
    }

    @Test
    fun populated_ratDistributionSection_isDisplayed() {
        runBlocking {
            val sessionId = sessionRepository.create(
                name = "Stats Session",
                createdAt = 1_700_000_000_000L,
                recordingMode = "OUTDOOR"
            )
            cellRecordRepository.insert(sampleRecord(sessionId, simSlotIndex = 0, rat = "4G"))
            cellRecordRepository.insert(sampleRecord(sessionId, simSlotIndex = 0, rat = "4G"))
        }
        composeTestRule.setContent {
            CellRecorderTheme {
                StatisticsScreen(viewModel = viewModel)
            }
        }
        composeTestRule.waitUntil(3000) {
            viewModel.stats.value.totalPoints >= 2 &&
                viewModel.ratDistributionPerSim.value.isNotEmpty()
        }
        composeTestRule.onNodeWithText("RAT Distribution").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun populated_speedTestSection_isDisplayed() {
        runBlocking {
            val sessionId = sessionRepository.create(
                name = "Speed Session",
                createdAt = 1_700_000_000_000L,
                recordingMode = "OUTDOOR"
            )
            cellRecordRepository.insert(sampleRecord(sessionId, simSlotIndex = 0, rat = "4G"))
            speedTestRecordRepository.insert(
                SpeedTestRecordEntity(
                    sessionId = sessionId,
                    timestamp = 1_700_000_000_000L,
                    downloadBps = 10_000_000L,
                    uploadBps = 5_000_000L,
                    serverName = "Test Server",
                    serverHost = "host",
                    serverLocation = "loc",
                    serverId = 1L,
                    dataSimSlotIndex = 0,
                    ratAtTest = "4G",
                    rsrpAtTest = -95,
                    bandAtTest = 3,
                    downloadSucceeded = true,
                    uploadSucceeded = true,
                    errorMessage = null,
                    networkType = "LTE"
                )
            )
        }
        composeTestRule.setContent {
            CellRecorderTheme {
                StatisticsScreen(viewModel = viewModel)
            }
        }
        composeTestRule.waitUntil(3000) { viewModel.speedTestGlobalStats.value != null }
        composeTestRule.onNodeWithText("Speed Test Overview").performScrollTo().assertIsDisplayed()
    }

    private fun sampleRecord(
        sessionId: Long,
        simSlotIndex: Int = 0,
        rat: String = "4G"
    ): CellRecordEntity = CellRecordEntity(
        sessionId = sessionId,
        timestamp = System.currentTimeMillis(),
        latitude = 37.0,
        longitude = -122.0,
        altitude = 0.0,
        accuracy = 5f,
        rat = rat,
        simSlotIndex = simSlotIndex,
        mcc = "310",
        mnc = "260",
        bandNumber = 3,
        earfcn = 1650,
        pci = 100,
        rsrp = -95,
        rsrq = -10,
        sinr = 5
    )
}
