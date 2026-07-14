package com.cellrecorder.app.viewmodel

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.cellrecorder.app.BuildConfig
import com.cellrecorder.app.data.local.entity.AppConfigEntity
import com.cellrecorder.app.data.repository.ConfigRepository
import com.cellrecorder.app.domain.speedtest.SpeedTestDebugRingBuffer
import com.cellrecorder.app.domain.speedtest.SpeedTestEngine
import com.cellrecorder.app.domain.speedtest.SpeedTestHttpClient
import com.cellrecorder.app.domain.usecase.GetConfigUseCase
import com.cellrecorder.app.domain.usecase.UpdateConfigUseCase
import com.cellrecorder.app.ui.settings.SettingsViewModel
import com.cellrecorder.app.util.MainDispatcherRule
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
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

    @Inject
    lateinit var speedTestEngine: SpeedTestEngine

    @Inject
    lateinit var debugRingBuffer: SpeedTestDebugRingBuffer

    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        hiltRule.inject()
        runBlocking {
            configRepository.update(AppConfigEntity())
        }
        val crashDir = File(app.filesDir, "crash_logs")
        if (crashDir.exists()) crashDir.listFiles()?.forEach { it.delete() }
        val logsDir = File(app.filesDir, "app_logs")
        if (logsDir.exists()) logsDir.listFiles()?.forEach { it.delete() }
        val getConfigUseCase = GetConfigUseCase(configRepository)
        val updateConfigUseCase = UpdateConfigUseCase(configRepository)
        viewModel = SettingsViewModel(getConfigUseCase, updateConfigUseCase, speedTestEngine, debugRingBuffer, app)
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
        assert(version.contains(BuildConfig.VERSION_NAME))
    }

    @Test
    fun getDeviceInfoString_containsDeviceInfo() {
        val info = viewModel.getDeviceInfoString()
        assert(info.contains("App Version:"))
        assert(info.contains("Android:"))
    }

    @Test
    fun getLogsForShare_returnsNullWhenNoLogs() = runBlocking {
        val log = viewModel.getLogsForShare()
        assertNull(log)
    }

    @Test
    fun updateRecordingInterval_invalidIgnored() = runBlocking {
        viewModel.updateRecordingInterval("0")
        viewModel.updateRecordingInterval("-1")
        viewModel.updateRecordingInterval("100")
        val config = viewModel.config.first { it.recordingIntervalMs == 5000L }
        assertEquals(5000L, config.recordingIntervalMs)
    }

    @Test
    fun updateLocationChangeThreshold_invalidIgnored() = runBlocking {
        viewModel.updateLocationChangeThreshold("-1")
        viewModel.updateLocationChangeThreshold("2000")
        val config = viewModel.config.first { it.locationChangeThresholdM == 10f }
        assertEquals(10f, config.locationChangeThresholdM, 0.01f)
    }

    @Test
    fun updateNrGnbBitLength_invalidIgnored() = runBlocking {
        viewModel.updateNrGnbBitLength("0")
        viewModel.updateNrGnbBitLength("-1")
        viewModel.updateNrGnbBitLength("36")
        viewModel.updateNrGnbBitLength("100")
        val config = viewModel.config.first { it.nrGnbBitLength == 24 }
        assertEquals(24, config.nrGnbBitLength)
    }

    @Test
    fun getLogsForShare_returnsCrashAndRollingContent() = runBlocking {
        val crashDir = File(app.filesDir, "crash_logs").apply { mkdirs() }
        val logsDir = File(app.filesDir, "app_logs").apply { mkdirs() }
        val crashContent = "java.lang.RuntimeException: test crash\n\tat com.example.Test.test(Test.java:10)"
        val rollingContent = "2026-07-13 12:00:00.000 E recording started\n2026-07-13 12:05:00.000 E point recorded\n"
        val crashFile = File(crashDir, "crash_test.txt")
        val rollingFile = File(logsDir, "runtime.log")
        try {
            crashFile.writeText(crashContent)
            rollingFile.writeText(rollingContent)

            val result = viewModel.getLogsForShare()
            assertNotNull(result)
            assertTrue("result should contain crash content", result!!.contains(crashContent))
            assertTrue("result should contain rolling content", result.contains(rollingContent))
            assertTrue("crash section should come before rolling section",
                result.indexOf(crashContent) < result.indexOf(rollingContent))
            assertTrue("result should contain Crash Log header", result.contains("=== Crash Log ==="))
            assertTrue("result should contain Runtime Log header", result.contains("=== Runtime Log ==="))
        } finally {
            crashFile.delete()
            rollingFile.delete()
        }
    }

    @Test
    fun getLogsForShare_returnsRollingOnlyWhenNoCrash() = runBlocking {
        val logsDir = File(app.filesDir, "app_logs").apply { mkdirs() }
        val rollingContent = "2026-07-13 12:00:00.000 E recording started\n"
        val rollingFile = File(logsDir, "runtime.log")
        try {
            rollingFile.writeText(rollingContent)

            val result = viewModel.getLogsForShare()
            assertNotNull(result)
            assertTrue(result!!.contains(rollingContent))
            assertFalse("should NOT contain Crash Log header when no crash",
                result.contains("=== Crash Log ==="))
            assertTrue("should contain Runtime Log header", result.contains("=== Runtime Log ==="))
        } finally {
            rollingFile.delete()
        }
    }

    @Test
    fun getLogsForShare_returnsCrashOnlyWhenNoRolling() = runBlocking {
        val crashDir = File(app.filesDir, "crash_logs").apply { mkdirs() }
        val crashContent = "java.lang.RuntimeException: test crash\n"
        val crashFile = File(crashDir, "crash_test.txt")
        try {
            crashFile.writeText(crashContent)

            val result = viewModel.getLogsForShare()
            assertNotNull(result)
            assertTrue(result!!.contains(crashContent))
            assertTrue("should contain Crash Log header", result.contains("=== Crash Log ==="))
            assertFalse("should NOT contain Runtime Log header when no rolling log",
                result.contains("=== Runtime Log ==="))
        } finally {
            crashFile.delete()
        }
    }
}