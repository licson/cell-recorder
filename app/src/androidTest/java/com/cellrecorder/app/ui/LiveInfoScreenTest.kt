package com.cellrecorder.app.ui

import android.app.Application
import android.content.Context
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
import com.cellrecorder.app.data.local.entity.AppConfigEntity
import com.cellrecorder.app.data.repository.ConfigRepository
import com.cellrecorder.app.domain.model.CellRecordSnapshot
import com.cellrecorder.app.domain.ping.PingEngine
import com.cellrecorder.app.service.CellInfoCollector
import com.cellrecorder.app.ui.liveinfo.LiveInfoScreen
import com.cellrecorder.app.ui.liveinfo.LiveInfoViewModel
import com.cellrecorder.app.ui.theme.CellRecorderTheme
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@MediumTest
class LiveInfoScreenTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<HiltTestActivity>()

    private lateinit var viewModel: LiveInfoViewModel
    private lateinit var emptyViewModel: LiveInfoViewModel
    private lateinit var nsaViewModel: LiveInfoViewModel

    @Before
    fun setUp() {
        hiltRule.inject()
        val app = ApplicationProvider.getApplicationContext<Application>()
        val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val configRepository = ConfigRepository(db.configDao())
        runBlocking { configRepository.update(AppConfigEntity()) }

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

        val pingEngine = mockk<PingEngine>(relaxed = true)
        every { pingEngine.pingFlow(any(), any(), any()) } returns emptyFlow()
        val appContext = mockk<Context>(relaxed = true)

        val populatedCollector = mockk<CellInfoCollector>(relaxed = true)
        every { populatedCollector.snapshots(any()) } returns listOf(snapshot)
        viewModel = LiveInfoViewModel(populatedCollector, configRepository, pingEngine, appContext)

        val emptyCollector = mockk<CellInfoCollector>(relaxed = true)
        every { emptyCollector.snapshots(any()) } returns emptyList()
        emptyViewModel = LiveInfoViewModel(emptyCollector, configRepository, pingEngine, appContext)

        val nsaSnapshot = CellRecordSnapshot(
            subscriptionId = 1,
            rat = "5G_NSA",
            mcc = "310",
            mnc = "260",
            bandNumber = 78,
            earfcn = 620_000,
            pci = 200,
            rsrp = -85,
            rsrq = -9,
            sinr = 10,
            anchorPci = 100,
            anchorEnbOrGnbId = 200L,
            anchorLcid = 5,
            anchorBandNumber = 3,
            anchorEarfcn = 1650,
            anchorRsrp = -85
        )
        val nsaCollector = mockk<CellInfoCollector>(relaxed = true)
        every { nsaCollector.snapshots(any()) } returns listOf(nsaSnapshot)
        nsaViewModel = LiveInfoViewModel(nsaCollector, configRepository, pingEngine, appContext)
    }

    @Test
    fun title_isDisplayed() {
        composeTestRule.setContent {
            CellRecorderTheme {
                LiveInfoScreen(viewModel = viewModel)
            }
        }
        composeTestRule.onNodeWithText("Live Cell Info").assertIsDisplayed()
    }

    @Test
    fun emptyState_showsNoCellDataMessage() {
        composeTestRule.setContent {
            CellRecorderTheme {
                LiveInfoScreen(viewModel = emptyViewModel)
            }
        }
        composeTestRule.onNodeWithText("No cell data available.", substring = true).assertIsDisplayed()
    }

    @Test
    fun simCard_label_isDisplayed() {
        composeTestRule.setContent {
            CellRecorderTheme {
                LiveInfoScreen(viewModel = viewModel)
            }
        }
        composeTestRule.waitUntil(2000) { viewModel.liveSimStates.value.isNotEmpty() }
        composeTestRule.onNodeWithText("SIM 1").assertIsDisplayed()
    }

    @Test
    fun cellInfo_plmnLabel_isDisplayed() {
        composeTestRule.setContent {
            CellRecorderTheme {
                LiveInfoScreen(viewModel = viewModel)
            }
        }
        composeTestRule.waitUntil(2000) { viewModel.liveSimStates.value.isNotEmpty() }
        composeTestRule.onNodeWithText("PLMN").assertIsDisplayed()
    }

    @Test
    fun cellInfo_cellIdLabel_isDisplayed() {
        composeTestRule.setContent {
            CellRecorderTheme {
                LiveInfoScreen(viewModel = viewModel)
            }
        }
        composeTestRule.waitUntil(2000) { viewModel.liveSimStates.value.isNotEmpty() }
        composeTestRule.onNodeWithText("Cell ID").assertIsDisplayed()
    }

    @Test
    fun cellInfo_arfcnLabel_isDisplayed() {
        composeTestRule.setContent {
            CellRecorderTheme {
                LiveInfoScreen(viewModel = viewModel)
            }
        }
        composeTestRule.waitUntil(2000) { viewModel.liveSimStates.value.isNotEmpty() }
        composeTestRule.onNodeWithText("ARFCN").assertIsDisplayed()
    }

    @Test
    fun pingCard_title_isDisplayed() {
        composeTestRule.setContent {
            CellRecorderTheme {
                LiveInfoScreen(viewModel = viewModel)
            }
        }
        composeTestRule.waitUntil(2000) { viewModel.liveSimStates.value.isNotEmpty() }
        composeTestRule.onNodeWithText("Ping").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun pingCard_latencyLabel_isDisplayed() {
        composeTestRule.setContent {
            CellRecorderTheme {
                LiveInfoScreen(viewModel = viewModel)
            }
        }
        composeTestRule.waitUntil(2000) { viewModel.liveSimStates.value.isNotEmpty() }
        composeTestRule.onNodeWithText("Latency").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun nsaSim_anchorCellId_isDisplayed() {
        composeTestRule.setContent {
            CellRecorderTheme {
                LiveInfoScreen(viewModel = nsaViewModel)
            }
        }
        composeTestRule.waitUntil(2000) { nsaViewModel.liveSimStates.value.isNotEmpty() }
        composeTestRule.onNodeWithText("200:5").performScrollTo().assertIsDisplayed()
    }
}
