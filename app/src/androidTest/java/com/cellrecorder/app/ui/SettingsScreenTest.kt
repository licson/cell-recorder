package com.cellrecorder.app.ui

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.cellrecorder.app.data.local.AppDatabase
import com.cellrecorder.app.data.local.entity.AppConfigEntity
import com.cellrecorder.app.data.repository.ConfigRepository
import com.cellrecorder.app.domain.usecase.GetConfigUseCase
import com.cellrecorder.app.domain.usecase.UpdateConfigUseCase
import com.cellrecorder.app.ui.settings.SettingsScreen
import com.cellrecorder.app.ui.settings.SettingsViewModel
import com.cellrecorder.app.ui.theme.CellRecorderTheme
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@Ignore("Requires test runner without process isolation")
@RunWith(AndroidJUnit4::class)
@MediumTest
class SettingsScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val configRepository = ConfigRepository(db.configDao())
        runBlocking { configRepository.update(AppConfigEntity()) }
        val getConfigUseCase = GetConfigUseCase(configRepository)
        val updateConfigUseCase = UpdateConfigUseCase(configRepository)
        viewModel = SettingsViewModel(getConfigUseCase, updateConfigUseCase, app)
    }

    @Test
    fun settingsTitle_isDisplayed() {
        composeTestRule.setContent {
            CellRecorderTheme {
                SettingsScreen(
                    onNavigateBack = {},
                    viewModel = viewModel
                )
            }
        }

        composeTestRule.onNodeWithText("Settings").assertIsDisplayed()
    }

    @Test
    fun pingSection_isDisplayed() {
        composeTestRule.setContent {
            CellRecorderTheme {
                SettingsScreen(
                    onNavigateBack = {},
                    viewModel = viewModel
                )
            }
        }

        composeTestRule.onNodeWithText("Ping").assertIsDisplayed()
    }

    @Test
    fun recordingSection_isDisplayed() {
        composeTestRule.setContent {
            CellRecorderTheme {
                SettingsScreen(
                    onNavigateBack = {},
                    viewModel = viewModel
                )
            }
        }

        composeTestRule.onNodeWithText("Recording").assertIsDisplayed()
    }

    @Test
    fun cellIdSection_isDisplayed() {
        composeTestRule.setContent {
            CellRecorderTheme {
                SettingsScreen(
                    onNavigateBack = {},
                    viewModel = viewModel
                )
            }
        }

        composeTestRule.onNodeWithText("Cell ID").assertIsDisplayed()
    }

    @Test
    fun speedTestSection_isDisplayed() {
        composeTestRule.setContent {
            CellRecorderTheme {
                SettingsScreen(
                    onNavigateBack = {},
                    viewModel = viewModel
                )
            }
        }

        composeTestRule.onNodeWithText("Speed Test").assertIsDisplayed()
    }

    @Test
    fun gpsLossFallbackSection_isDisplayed() {
        composeTestRule.setContent {
            CellRecorderTheme {
                SettingsScreen(
                    onNavigateBack = {},
                    viewModel = viewModel
                )
            }
        }

        composeTestRule.onNodeWithText("GPS Loss Fallback").assertIsDisplayed()
    }

    @Test
    fun analyticsThresholdsSection_isDisplayed() {
        composeTestRule.setContent {
            CellRecorderTheme {
                SettingsScreen(
                    onNavigateBack = {},
                    viewModel = viewModel
                )
            }
        }

        composeTestRule.onNodeWithText("Analytics Thresholds").assertIsDisplayed()
    }

    @Test
    fun aboutSection_isDisplayed() {
        composeTestRule.setContent {
            CellRecorderTheme {
                SettingsScreen(
                    onNavigateBack = {},
                    viewModel = viewModel
                )
            }
        }

        composeTestRule.onNodeWithText("About").assertIsDisplayed()
    }

    @Test
    fun version_isDisplayed() {
        composeTestRule.setContent {
            CellRecorderTheme {
                SettingsScreen(
                    onNavigateBack = {},
                    viewModel = viewModel
                )
            }
        }

        composeTestRule.onNodeWithText("Version", substring = true).assertIsDisplayed()
    }
}