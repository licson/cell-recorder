package com.cellrecorder.app.viewmodel

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.cellrecorder.app.data.local.entity.AppConfigEntity
import com.cellrecorder.app.data.repository.ConfigRepository
import com.cellrecorder.app.domain.usecase.GetConfigUseCase
import com.cellrecorder.app.domain.usecase.UpdateConfigUseCase
import com.cellrecorder.app.ui.settings.SettingsViewModel
import com.cellrecorder.app.util.MainDispatcherRule
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@MediumTest
class SettingsViewModelTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Inject
    lateinit var configRepository: ConfigRepository

    @Inject
    lateinit var app: Application

    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        hiltRule.inject()
        runBlocking {
            configRepository.update(AppConfigEntity())
        }
        val getConfigUseCase = GetConfigUseCase(configRepository)
        val updateConfigUseCase = UpdateConfigUseCase(configRepository)
        viewModel = SettingsViewModel(getConfigUseCase, updateConfigUseCase, app)
    }

    @Test
    fun configEmitsDefaults() = runBlocking {
        val config = viewModel.config.first { it.pingDestination == "8.8.8.8" }
        assertEquals("8.8.8.8", config.pingDestination)
        assertEquals(1000L, config.pingIntervalMs)
        assertEquals(5000L, config.recordingIntervalMs)
    }

    @Test
    fun updatePingDestination() = runBlocking {
        viewModel.updatePingDestination("1.1.1.1")
        val config = viewModel.config.first { it.pingDestination == "1.1.1.1" }
        assertEquals("1.1.1.1", config.pingDestination)
    }

    @Test
    fun updatePingInterval_valid() = runBlocking {
        viewModel.updatePingInterval("2000")
        val config = viewModel.config.first { it.pingIntervalMs == 2000L }
        assertEquals(2000L, config.pingIntervalMs)
    }

    @Test
    fun updatePingInterval_invalidIgnored() = runBlocking {
        viewModel.updatePingInterval("-1")
        val config = viewModel.config.first { it.pingIntervalMs == 1000L }
        assertEquals(1000L, config.pingIntervalMs)
    }

    @Test
    fun updateRecordingInterval() = runBlocking {
        viewModel.updateRecordingInterval("10000")
        val config = viewModel.config.first { it.recordingIntervalMs == 10000L }
        assertEquals(10000L, config.recordingIntervalMs)
    }

    @Test
    fun updateLocationChangeThreshold() = runBlocking {
        viewModel.updateLocationChangeThreshold("25.5")
        val config = viewModel.config.first { it.locationChangeThresholdM == 25.5f }
        assertEquals(25.5f, config.locationChangeThresholdM, 0.01f)
    }

    @Test
    fun updateNrGnbBitLength() = runBlocking {
        viewModel.updateNrGnbBitLength("32")
        val config = viewModel.config.first { it.nrGnbBitLength == 32 }
        assertEquals(32, config.nrGnbBitLength)
    }

    @Test
    fun toggleSpeedTest_enabled() = runBlocking {
        viewModel.toggleSpeedTest(true)
        val config = viewModel.config.first { it.speedTestEnabled }
        assertEquals(true, config.speedTestEnabled)
    }

    @Test
    fun toggleSpeedTest_disabled() = runBlocking {
        viewModel.toggleSpeedTest(false)
        val config = viewModel.config.first { !it.speedTestEnabled }
        assertEquals(false, config.speedTestEnabled)
    }

    @Test
    fun getVersionDisplay_returnsVersionString() {
        val version = viewModel.getVersionDisplay()
        assertNotNull(version)
        assert(version.contains("1.2.0"))
    }

    @Test
    fun getDeviceInfoString_containsDeviceInfo() {
        val info = viewModel.getDeviceInfoString()
        assert(info.contains("App Version:"))
        assert(info.contains("Android:"))
    }

    @Test
    fun getLatestCrashLog_returnsNullWhenNoCrash() = runBlocking {
        val log = viewModel.getLatestCrashLog()
        assertNull(log)
    }
}