package com.cellrecorder.app.ui.settings

import android.app.Application
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cellrecorder.app.BuildConfig
import com.cellrecorder.app.data.local.entity.AppConfigEntity
import com.cellrecorder.app.domain.usecase.GetConfigUseCase
import com.cellrecorder.app.domain.usecase.UpdateConfigUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val getConfigUseCase: GetConfigUseCase,
    private val updateConfigUseCase: UpdateConfigUseCase,
    private val app: Application
) : ViewModel() {

    private val _config = MutableStateFlow(AppConfigEntity())
    val config: StateFlow<AppConfigEntity> = _config

    private var saveJob: Job? = null

    private fun debouncedSave() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(500)
            updateConfigUseCase(_config.value)
        }
    }

    init {
        viewModelScope.launch {
            getConfigUseCase().collect { _config.value = it }
        }
    }

    fun updatePingDestination(value: String) {
        _config.value = _config.value.copy(pingDestination = value)
        debouncedSave()
    }

    fun updatePingInterval(value: String) {
        val parsed = value.toLongOrNull()
        if (parsed != null && parsed in 1..60_000) {
            _config.value = _config.value.copy(pingIntervalMs = parsed)
            debouncedSave()
        }
    }

    fun updatePingTimeout(value: String) {
        val parsed = value.toLongOrNull()
        if (parsed != null && parsed in 1..300_000) {
            _config.value = _config.value.copy(pingTimeoutMs = parsed)
            debouncedSave()
        }
    }

    fun updateRecordingInterval(value: String) {
        val parsed = value.toLongOrNull()
        if (parsed != null && parsed in 1_000..60_000) {
            _config.value = _config.value.copy(recordingIntervalMs = parsed)
            debouncedSave()
        }
    }

    fun updateLocationChangeThreshold(value: String) {
        val parsed = value.toFloatOrNull()
        if (parsed != null && parsed >= 0f && parsed <= 1_000f) {
            _config.value = _config.value.copy(locationChangeThresholdM = parsed)
            debouncedSave()
        }
    }

    fun updateGpsAccuracyThreshold(value: String) {
        val parsed = value.toFloatOrNull()
        if (parsed != null && parsed in 1f..500f) {
            _config.value = _config.value.copy(gpsAccuracyThresholdM = parsed)
            debouncedSave()
        }
    }

    fun updateMaxRecordingDuration(value: String) {
        val parsed = value.toIntOrNull()
        if (parsed != null && parsed in 1..1_440) {
            _config.value = _config.value.copy(maxRecordingDurationMin = parsed)
            debouncedSave()
        }
    }

    fun updateNrGnbBitLength(value: String) {
        val parsed = value.toIntOrNull()
        if (parsed != null && parsed in 1..35) {
            _config.value = _config.value.copy(nrGnbBitLength = parsed)
            debouncedSave()
        }
    }

    fun updateCellInfoRefreshInterval(value: String) {
        val parsed = value.toIntOrNull()
        if (parsed != null && parsed in 1..60) {
            _config.value = _config.value.copy(cellInfoRefreshIntervalSec = parsed)
            debouncedSave()
        }
    }

    fun updateMaxGpsLossExtrapolation(value: String) {
        val parsed = value.toIntOrNull()
        if (parsed != null && parsed in 0..600) {
            _config.value = _config.value.copy(maxGpsLossExtrapolationSec = parsed)
            debouncedSave()
        }
    }

    fun updateHandoffTimeWindow(value: String) {
        val parsed = value.toLongOrNull()
        if (parsed != null && parsed in 1_000..60_000) {
            _config.value = _config.value.copy(handoffTimeWindowMs = parsed)
            debouncedSave()
        }
    }

    fun updateRsrpDropThreshold(value: String) {
        val parsed = value.toIntOrNull()
        if (parsed != null && parsed in 1..100) {
            _config.value = _config.value.copy(rsrpDropThresholdDbm = parsed)
            debouncedSave()
        }
    }

    fun updateRsrpDropTimeWindow(value: String) {
        val parsed = value.toLongOrNull()
        if (parsed != null && parsed in 1_000..60_000) {
            _config.value = _config.value.copy(rsrpDropTimeWindowMs = parsed)
            debouncedSave()
        }
    }

    fun updateLatencySpikeSigma(value: String) {
        val parsed = value.toDoubleOrNull()
        if (parsed != null && parsed in 0.1..10.0) {
            _config.value = _config.value.copy(latencySpikeSigma = parsed)
            debouncedSave()
        }
    }

    fun updatePciFlapWindow(value: String) {
        val parsed = value.toLongOrNull()
        if (parsed != null && parsed in 1_000..120_000) {
            _config.value = _config.value.copy(pciFlapWindowMs = parsed)
            debouncedSave()
        }
    }

    fun updatePciFlapCountThreshold(value: String) {
        val parsed = value.toIntOrNull()
        if (parsed != null && parsed in 2..20) {
            _config.value = _config.value.copy(pciFlapCountThreshold = parsed)
            debouncedSave()
        }
    }

    fun updateCoverageGapThreshold(value: String) {
        val parsed = value.toLongOrNull()
        if (parsed != null && parsed in 1_000..300_000) {
            _config.value = _config.value.copy(coverageGapThresholdMs = parsed)
            debouncedSave()
        }
    }

    fun updateMobilityStationary(value: String) {
        val parsed = value.toFloatOrNull()
        if (parsed != null && parsed in 0f..50f) {
            _config.value = _config.value.copy(mobilityStationaryKmh = parsed)
            debouncedSave()
        }
    }

    fun updateMobilityWalking(value: String) {
        val parsed = value.toFloatOrNull()
        if (parsed != null && parsed in 0f..200f) {
            _config.value = _config.value.copy(mobilityWalkingKmh = parsed)
            debouncedSave()
        }
    }

    fun updateIndoorAccuracyThreshold(value: String) {
        val parsed = value.toFloatOrNull()
        if (parsed != null && parsed in 1f..100f) {
            _config.value = _config.value.copy(indoorAccuracyThresholdM = parsed)
            debouncedSave()
        }
    }

    fun updateIndoorStepLength(value: String) {
        val parsed = value.toFloatOrNull()
        if (parsed != null && parsed in 0.1f..2.0f) {
            _config.value = _config.value.copy(indoorStepLengthM = parsed)
            debouncedSave()
        }
    }

    fun updateIndoorRecordingInterval(value: String) {
        val parsed = value.toLongOrNull()
        if (parsed != null && parsed in 1_000..60_000) {
            _config.value = _config.value.copy(indoorRecordingIntervalMs = parsed)
            debouncedSave()
        }
    }

    fun updateTunnelSignalLossThreshold(value: String) {
        val parsed = value.toLongOrNull()
        if (parsed != null && parsed in 1_000..60_000) {
            _config.value = _config.value.copy(tunnelSignalLossThresholdMs = parsed)
            debouncedSave()
        }
    }

    fun toggleSpeedTest(enabled: Boolean) {
        _config.value = _config.value.copy(speedTestEnabled = enabled)
        debouncedSave()
    }

    fun toggleSpeedTestUpload(enabled: Boolean) {
        _config.value = _config.value.copy(speedTestUploadEnabled = enabled)
        debouncedSave()
    }

    fun updateSpeedTestInterval(value: String) {
        val parsed = value.toLongOrNull()
        if (parsed != null && parsed in 10_000..300_000) {
            _config.value = _config.value.copy(speedTestIntervalMs = parsed)
            debouncedSave()
        }
    }

    fun updateSpeedTestServerId(value: String) {
        val sanitized = value.filter { it.isDigit() }
        _config.value = _config.value.copy(speedTestServerId = sanitized.ifBlank { null })
        debouncedSave()
    }

    fun getVersionDisplay(): String {
        return "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}, ${BuildConfig.GIT_HASH})"
    }

    fun getDeviceInfoString(): String {
        return buildString {
            appendLine("**App Version:** ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("**Git Hash:** ${BuildConfig.GIT_HASH}")
            appendLine("**Android:** ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("**Device:** ${Build.MANUFACTURER} ${Build.MODEL}")
        }
    }

    suspend fun getLatestCrashLog(): String? {
        return withContext(Dispatchers.IO) {
            val logDir = File(app.filesDir, "crash_logs")
            logDir.listFiles()
                ?.sortedByDescending { it.lastModified() }
                ?.firstOrNull()
                ?.readText()
        }
    }
}