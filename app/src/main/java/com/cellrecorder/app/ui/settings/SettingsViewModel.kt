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
        value.toLongOrNull()?.let { _config.value = _config.value.copy(pingIntervalMs = it) }
        debouncedSave()
    }

    fun updatePingTimeout(value: String) {
        value.toLongOrNull()?.let { _config.value = _config.value.copy(pingTimeoutMs = it) }
        debouncedSave()
    }

    fun updateRecordingInterval(value: String) {
        value.toLongOrNull()?.let { _config.value = _config.value.copy(recordingIntervalMs = it) }
        debouncedSave()
    }

    fun updateLocationChangeThreshold(value: String) {
        value.toFloatOrNull()?.let { _config.value = _config.value.copy(locationChangeThresholdM = it) }
        debouncedSave()
    }

    fun updateGpsAccuracyThreshold(value: String) {
        value.toFloatOrNull()?.let { _config.value = _config.value.copy(gpsAccuracyThresholdM = it) }
        debouncedSave()
    }

    fun updateMaxRecordingDuration(value: String) {
        value.toIntOrNull()?.let { _config.value = _config.value.copy(maxRecordingDurationMin = it) }
        debouncedSave()
    }

    fun updateNrGnbBitLength(value: String) {
        value.toIntOrNull()?.let { _config.value = _config.value.copy(nrGnbBitLength = it) }
        debouncedSave()
    }

    fun updateCellInfoRefreshInterval(value: String) {
        value.toIntOrNull()?.let { _config.value = _config.value.copy(cellInfoRefreshIntervalSec = it) }
        debouncedSave()
    }

    fun updateMaxGpsLossExtrapolation(value: String) {
        value.toIntOrNull()?.let { _config.value = _config.value.copy(maxGpsLossExtrapolationSec = it) }
        debouncedSave()
    }

    fun updateHandoffTimeWindow(value: String) {
        value.toLongOrNull()?.let { _config.value = _config.value.copy(handoffTimeWindowMs = it) }
        debouncedSave()
    }

    fun updateRsrpDropThreshold(value: String) {
        value.toIntOrNull()?.let { _config.value = _config.value.copy(rsrpDropThresholdDbm = it) }
        debouncedSave()
    }

    fun updateRsrpDropTimeWindow(value: String) {
        value.toLongOrNull()?.let { _config.value = _config.value.copy(rsrpDropTimeWindowMs = it) }
        debouncedSave()
    }

    fun updateLatencySpikeSigma(value: String) {
        value.toDoubleOrNull()?.let { _config.value = _config.value.copy(latencySpikeSigma = it) }
        debouncedSave()
    }

    fun updatePciFlapWindow(value: String) {
        value.toLongOrNull()?.let { _config.value = _config.value.copy(pciFlapWindowMs = it) }
        debouncedSave()
    }

    fun updatePciFlapCountThreshold(value: String) {
        value.toIntOrNull()?.let { _config.value = _config.value.copy(pciFlapCountThreshold = it) }
        debouncedSave()
    }

    fun updateCoverageGapThreshold(value: String) {
        value.toLongOrNull()?.let { _config.value = _config.value.copy(coverageGapThresholdMs = it) }
        debouncedSave()
    }

    fun updateMobilityStationary(value: String) {
        value.toFloatOrNull()?.let { _config.value = _config.value.copy(mobilityStationaryKmh = it) }
        debouncedSave()
    }

    fun updateMobilityWalking(value: String) {
        value.toFloatOrNull()?.let { _config.value = _config.value.copy(mobilityWalkingKmh = it) }
        debouncedSave()
    }

    fun updateIndoorAccuracyThreshold(value: String) {
        value.toFloatOrNull()?.let { _config.value = _config.value.copy(indoorAccuracyThresholdM = it) }
        debouncedSave()
    }

    fun updateTunnelSignalLossThreshold(value: String) {
        value.toLongOrNull()?.let { _config.value = _config.value.copy(tunnelSignalLossThresholdMs = it) }
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