package com.cellrecorder.app.ui.settings

import android.app.Application
import android.content.Intent
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cellrecorder.app.BuildConfig
import com.cellrecorder.app.data.local.entity.AppConfigEntity
import com.cellrecorder.app.domain.speedtest.SpeedTestDebugEvent
import com.cellrecorder.app.domain.speedtest.SpeedTestDebugRingBuffer
import com.cellrecorder.app.domain.speedtest.SpeedTestEngine
import com.cellrecorder.app.domain.speedtest.model.SpeedTestResult
import com.cellrecorder.app.domain.usecase.GetConfigUseCase
import com.cellrecorder.app.domain.usecase.UpdateConfigUseCase
import com.cellrecorder.app.logging.RollingFileTree
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * UI state for a manual speedtest launch.
 */
sealed interface ManualLaunchUiState {
    data object Idle : ManualLaunchUiState
    data class Running(val startedAt: Long) : ManualLaunchUiState
    data class Finished(
        val startedAt: Long,
        val finishedAt: Long,
        val durationMs: Long,
        val downloadBps: Long?,
        val uploadBps: Long?,
        val serverName: String?,
        val serverHost: String?,
        val downloadSucceeded: Boolean,
        val uploadSucceeded: Boolean?,
        val errorMessage: String?
    ) : ManualLaunchUiState
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val getConfigUseCase: GetConfigUseCase,
    private val updateConfigUseCase: UpdateConfigUseCase,
    private val speedTestEngine: SpeedTestEngine,
    private val debugRingBuffer: SpeedTestDebugRingBuffer,
    private val app: Application
) : ViewModel() {

    private val _config = MutableStateFlow(AppConfigEntity())
    val config: StateFlow<AppConfigEntity> = _config

    val debugEvents: StateFlow<List<SpeedTestDebugEvent>> = debugRingBuffer.events

    private val _manualLaunchState = MutableStateFlow<ManualLaunchUiState>(ManualLaunchUiState.Idle)
    val manualLaunchState: StateFlow<ManualLaunchUiState> = _manualLaunchState.asStateFlow()

    private var saveJob: Job? = null
    private var launchJob: Job? = null

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

    /**
     * Launches a manual speedtest outside of a recording session. Re-primes
     * server selection and gauge (keeps cached config), then runs the engine
     * with the configured `preferredServerId` and `speedTestUploadEnabled`.
     * The result is NOT persisted (the `sessionId` FK is non-null; no sentinel
     * session is created). On success, the engine's `primedSinceLastInvalidation`
     * flag is set to warm the next recording session's cache.
     */
    fun launchTest(): Job {
        val currentConfig = _config.value
        if (!currentConfig.speedTestEnabled) return viewModelScope.launch { }
        launchJob?.cancel()
        _manualLaunchState.value = ManualLaunchUiState.Running(startedAt = System.currentTimeMillis())
        return viewModelScope.launch {
            speedTestEngine.reprimeServerAndGauge()
            val result: SpeedTestResult = speedTestEngine.runTest(
                preferredServerId = currentConfig.speedTestServerId?.toIntOrNull(),
                uploadEnabled = currentConfig.speedTestUploadEnabled,
                primeOnSuccess = true
            )
            _manualLaunchState.value = ManualLaunchUiState.Finished(
                startedAt = result.startedAt,
                finishedAt = result.finishedAt,
                durationMs = result.finishedAt - result.startedAt,
                downloadBps = result.downloadBps,
                uploadBps = result.uploadBps,
                serverName = result.serverName,
                serverHost = result.serverHost,
                downloadSucceeded = result.downloadSucceeded,
                uploadSucceeded = result.uploadSucceeded,
                errorMessage = result.errorMessage
            )
        }
    }

    /**
     * Serializes the current ring buffer snapshot to plain text and launches an
     * `Intent.ACTION_SEND` chooser. Mirrors the "Share Crash Log" pattern. Shows
     * a toast "No debug events" (handled by the caller) if the buffer is empty.
     */
    fun shareDebugLog(): Job = viewModelScope.launch {
        val snapshot = debugRingBuffer.snapshot()
        if (snapshot.isEmpty()) return@launch
        val text = formatDebugLog(snapshot)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Cell Recorder Speedtest Debug Log")
            putExtra(Intent.EXTRA_TEXT, text)
        }
        Intent.createChooser(intent, "Share Debug Log").also {
            it.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }.let { app.startActivity(it) }
    }

    private fun formatDebugLog(events: List<SpeedTestDebugEvent>): String {
        val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
        return buildString {
            for (e in events) {
                append(sdf.format(Date(e.timestampMs)))
                append(" [")
                append(e.phase)
                append("] ")
                append(e.status)
                append(": ")
                append(e.message)
                if (e.serverHost != null) {
                    append(" (host=")
                    append(e.serverHost)
                    if (e.serverId != null) {
                        append(", id=")
                        append(e.serverId)
                    }
                    append(')')
                }
                append('\n')
            }
        }
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

    suspend fun getLogsForShare(): String? {
        return withContext(Dispatchers.IO) {
            // Flush any queued writes so the share payload reflects the latest entries.
            RollingFileTree.flushPlanted()
            val crashContent = readLatestCrashLog()
            val rollingContent = readRollingLog()
            if (crashContent == null && rollingContent == null) return@withContext null
            buildString {
                if (crashContent != null) {
                    append("=== Crash Log ===\n")
                    append(crashContent)
                    if (!crashContent.endsWith('\n')) append('\n')
                    if (rollingContent != null) {
                        append("\n=== Runtime Log ===\n")
                        append(rollingContent)
                    }
                } else {
                    append("=== Runtime Log ===\n")
                    append(rollingContent)
                }
            }
        }
    }

    private fun readLatestCrashLog(): String? {
        val logDir = File(app.filesDir, "crash_logs")
        val latest = logDir.listFiles()
            ?.sortedByDescending { it.lastModified() }
            ?.firstOrNull()
            ?: return null
        return try { latest.readText() } catch (_: Exception) { null }?.takeIf { it.isNotBlank() }
    }

    private fun readRollingLog(): String? {
        val logDir = File(app.filesDir, RollingFileTree.LOG_DIR_NAME)
        val current = File(logDir, RollingFileTree.CURRENT_FILE_NAME)
        val rotated = File(logDir, RollingFileTree.ROTATED_FILE_NAME)
        val currentText = readLogFile(current)
        val rotatedText = readLogFile(rotated)
        if (currentText == null && rotatedText == null) return null
        return buildString {
            if (rotatedText != null) {
                append(rotatedText)
                if (!rotatedText.endsWith('\n')) append('\n')
            }
            if (currentText != null) append(currentText)
        }
    }

    private fun readLogFile(file: File): String? {
        if (!file.exists()) return null
        return try { file.readText() } catch (_: Exception) { null }?.takeIf { it.isNotBlank() }
    }
}