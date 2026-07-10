package com.cellrecorder.app.ui.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import com.cellrecorder.app.domain.speedtest.SpeedTestDebugEvent
import com.cellrecorder.app.ui.settings.ManualLaunchUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val config by viewModel.config.collectAsStateWithLifecycle()
    val debugEvents by viewModel.debugEvents.collectAsStateWithLifecycle()
    val manualLaunchState by viewModel.manualLaunchState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showSpeedTestEula by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            SettingsCard(title = "Ping") {
                SettingsRow("Destination", config.pingDestination, viewModel::updatePingDestination)
                SettingsRow("Interval (ms)", config.pingIntervalMs.toString(), viewModel::updatePingInterval, keyboardType = KeyboardType.Number)
                SettingsRow("Timeout (ms)", config.pingTimeoutMs.toString(), viewModel::updatePingTimeout, keyboardType = KeyboardType.Number)
            }

            SettingsCard(title = "Recording") {
                SettingsRow("Interval (ms)", config.recordingIntervalMs.toString(), viewModel::updateRecordingInterval, keyboardType = KeyboardType.Number)
                SettingsRow("Location Change Threshold (m)", config.locationChangeThresholdM.toString(), viewModel::updateLocationChangeThreshold, keyboardType = KeyboardType.Decimal)
                SettingsRow("GPS Accuracy Threshold (m)", config.gpsAccuracyThresholdM.toString(), viewModel::updateGpsAccuracyThreshold, keyboardType = KeyboardType.Decimal)
                SettingsRow("Max Duration (min)", config.maxRecordingDurationMin.toString(), viewModel::updateMaxRecordingDuration, keyboardType = KeyboardType.Number)
            }

            SettingsCard(title = "Indoor Recording") {
                SettingsRow("Step Length (m)", config.indoorStepLengthM.toString(), viewModel::updateIndoorStepLength, keyboardType = KeyboardType.Decimal)
                SettingsRow("Indoor Interval (ms)", config.indoorRecordingIntervalMs.toString(), viewModel::updateIndoorRecordingInterval, keyboardType = KeyboardType.Number)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Indoor sessions under 5 minutes give best accuracy.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }

            SettingsCard(title = "Cell ID") {
                SettingsRow("NR gNB Bit-Length", config.nrGnbBitLength.toString(), viewModel::updateNrGnbBitLength, keyboardType = KeyboardType.Number)
                SettingsRow("Cell Info Refresh Interval (s)", config.cellInfoRefreshIntervalSec.toString(), viewModel::updateCellInfoRefreshInterval, keyboardType = KeyboardType.Number)
            }

            SettingsCard(title = "Speed Test") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Enable Speed Test", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Switch(
                        checked = config.speedTestEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                showSpeedTestEula = true
                            } else {
                                viewModel.toggleSpeedTest(false)
                            }
                        }
                    )
                }
                if (config.speedTestEnabled) {
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Upload Test", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Switch(
                            checked = config.speedTestUploadEnabled,
                            onCheckedChange = { viewModel.toggleSpeedTestUpload(it) }
                        )
                    }
                    SettingsRow("Interval (ms)", config.speedTestIntervalMs.toString(), viewModel::updateSpeedTestInterval, keyboardType = KeyboardType.Number)
                    SettingsRow("Server ID (optional)", config.speedTestServerId ?: "", viewModel::updateSpeedTestServerId, keyboardType = KeyboardType.Number)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Leave blank for auto-select. Each test uses ~5-30 MB of data.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.launchTest() },
                            enabled = manualLaunchState !is ManualLaunchUiState.Running
                        ) {
                            Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Launch Test")
                        }
                        if (manualLaunchState is ManualLaunchUiState.Running) {
                            Spacer(Modifier.width(12.dp))
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Running…", style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    val isRunning = manualLaunchState is ManualLaunchUiState.Running
                    val isFinished = manualLaunchState is ManualLaunchUiState.Finished
                    AnimatedVisibility(
                        visible = isRunning || isFinished,
                        enter = expandVertically(),
                        exit = shrinkVertically()
                    ) {
                        SpeedTestDebugCard(
                            events = debugEvents,
                            manualLaunchState = manualLaunchState,
                            onShareLog = {
                                if (debugEvents.isEmpty()) {
                                    Toast.makeText(context, "No debug events", Toast.LENGTH_SHORT).show()
                                } else {
                                    viewModel.shareDebugLog()
                                }
                            }
                        )
                    }
                }
            }

            if (showSpeedTestEula) {
                SpeedTestEulaDialog(
                    onAccept = {
                        showSpeedTestEula = false
                        viewModel.toggleSpeedTest(true)
                    },
                    onDecline = {
                        showSpeedTestEula = false
                    }
                )
            }

            SettingsCard(title = "GPS Loss Fallback") {
                SettingsRow("Max Extrapolation Time (s)", config.maxGpsLossExtrapolationSec.toString(), viewModel::updateMaxGpsLossExtrapolation, keyboardType = KeyboardType.Number)
            }

            SettingsCard(title = "Analytics Thresholds") {
                SettingsRow("Handoff Time Window (ms)", config.handoffTimeWindowMs.toString(), viewModel::updateHandoffTimeWindow, keyboardType = KeyboardType.Number)
                SettingsRow("RSRP Drop Threshold (dBm)", config.rsrpDropThresholdDbm.toString(), viewModel::updateRsrpDropThreshold, keyboardType = KeyboardType.Number)
                SettingsRow("RSRP Drop Time Window (ms)", config.rsrpDropTimeWindowMs.toString(), viewModel::updateRsrpDropTimeWindow, keyboardType = KeyboardType.Number)
                SettingsRow("Latency Spike Sigma", config.latencySpikeSigma.toString(), viewModel::updateLatencySpikeSigma, keyboardType = KeyboardType.Decimal)
                SettingsRow("PCI Flap Window (ms)", config.pciFlapWindowMs.toString(), viewModel::updatePciFlapWindow, keyboardType = KeyboardType.Number)
                SettingsRow("PCI Flap Count", config.pciFlapCountThreshold.toString(), viewModel::updatePciFlapCountThreshold, keyboardType = KeyboardType.Number)
                SettingsRow("Coverage Gap Threshold (ms)", config.coverageGapThresholdMs.toString(), viewModel::updateCoverageGapThreshold, keyboardType = KeyboardType.Number)
                SettingsRow("Mobility Stationary (km/h)", config.mobilityStationaryKmh.toString(), viewModel::updateMobilityStationary, keyboardType = KeyboardType.Decimal)
                SettingsRow("Mobility Walking (km/h)", config.mobilityWalkingKmh.toString(), viewModel::updateMobilityWalking, keyboardType = KeyboardType.Decimal)
                SettingsRow("Indoor Accuracy (m)", config.indoorAccuracyThresholdM.toString(), viewModel::updateIndoorAccuracyThreshold, keyboardType = KeyboardType.Decimal)
                SettingsRow("Tunnel Signal Loss (ms)", config.tunnelSignalLossThresholdMs.toString(), viewModel::updateTunnelSignalLossThreshold, keyboardType = KeyboardType.Number)
            }

            SettingsCard(title = "About") {
                AboutRow(icon = Icons.Default.Info, label = "Version", value = viewModel.getVersionDisplay())
                HorizontalDivider()
                ClickableAboutRow(
                    icon = Icons.Default.Code,
                    label = "View Source",
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, "https://github.com/licson/cell-recorder".toUri())
                        context.startActivity(intent)
                    }
                )
                HorizontalDivider()
                ClickableAboutRow(
                    icon = Icons.Default.Report,
                    label = "Report Issue",
                    onClick = {
                        val body = viewModel.getDeviceInfoString() + "\n\n**Description:**\n"
                        val uri = "https://github.com/licson/cell-recorder/issues/new?body=${Uri.encode(body)}".toUri()
                        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                    }
                )
                HorizontalDivider()
                ClickableAboutRow(
                    icon = Icons.Default.BugReport,
                    label = "Share Crash Log",
                    onClick = {
                        scope.launch {
                            val log = viewModel.getLatestCrashLog()
                            if (log != null) {
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, "Cell Recorder Crash Log")
                                    putExtra(Intent.EXTRA_TEXT, log)
                                }
                                context.startActivity(Intent.createChooser(intent, "Share Crash Log"))
                            } else {
                                Toast.makeText(context, "No crash logs available", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )
            }

            Spacer(Modifier.height(80.dp))
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(4.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun SettingsRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.width(130.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            textStyle = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun AboutRow(icon: ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ClickableAboutRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Icon(
            Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SpeedTestDebugCard(
    events: List<SpeedTestDebugEvent>,
    manualLaunchState: ManualLaunchUiState,
    onShareLog: () -> Unit
) {
    val listState = rememberLazyListState()
    LaunchedEffect(events.size) {
        if (events.isNotEmpty()) {
            listState.animateScrollToItem(events.lastIndex)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Debug Log",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onShareLog) {
                    Icon(Icons.Default.Share, contentDescription = "Share Debug Log")
                }
            }
            HorizontalDivider()
            Spacer(Modifier.height(4.dp))
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp)
            ) {
                items(events) { event ->
                    SpeedTestDebugEventRow(event)
                }
                if (events.isEmpty()) {
                    item {
                        Text(
                            text = "Waiting for events…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }
            }
            val finished = manualLaunchState as? ManualLaunchUiState.Finished
            if (finished != null) {
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                SpeedTestResultSummary(finished)
            }
        }
    }
}

@Composable
private fun SpeedTestDebugEventRow(event: SpeedTestDebugEvent) {
    val timeStr = remember(event.timestampMs) {
        val sdf = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault())
        sdf.format(java.util.Date(event.timestampMs))
    }
    val color = when (event.status) {
        SpeedTestDebugEvent.Status.OK -> Color(0xFF4CAF50)
        SpeedTestDebugEvent.Status.INFO -> Color(0xFF2196F3)
        SpeedTestDebugEvent.Status.WARN -> Color(0xFFFF9800)
        SpeedTestDebugEvent.Status.FAIL -> Color(0xFFF44336)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = timeStr,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f)
        )
        Text(
            text = "[${event.phase}]",
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = color,
            modifier = Modifier.weight(0.3f)
        )
        Text(
            text = event.message,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SpeedTestResultSummary(finished: ManualLaunchUiState.Finished) {
    val durationStr = formatDuration(finished.durationMs)
    val startTimeStr = remember(finished.startedAt) {
        val sdf = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
        sdf.format(java.util.Date(finished.startedAt))
    }
    val finishTimeStr = remember(finished.finishedAt) {
        val sdf = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
        sdf.format(java.util.Date(finished.finishedAt))
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = "Result: ${if (finished.succeeded) "Success" else "Failed"}",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = if (finished.succeeded) Color(0xFF4CAF50) else Color(0xFFF44336)
        )
        Text(
            text = "Start: $startTimeStr  Finish: $finishTimeStr",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "Duration: $durationStr",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (finished.downloadBps != null) {
            Text(
                text = "Download: ${formatBpsManual(finished.downloadBps)}",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace
            )
        }
        if (finished.uploadBps != null) {
            Text(
                text = "Upload: ${formatBpsManual(finished.uploadBps)}",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace
            )
        }
        if (finished.serverName != null || finished.serverHost != null) {
            Text(
                text = "Server: ${finished.serverName ?: "?"} (${finished.serverHost ?: "?"})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (finished.errorMessage != null) {
            Text(
                text = "Error: ${finished.errorMessage}",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFF44336)
            )
        }
    }
}

private fun formatDuration(ms: Long): String {
    if (ms <= 0) return "0s"
    val seconds = ms / 1000.0
    return when {
        seconds < 10 -> String.format(java.util.Locale.US, "%.1fs", seconds)
        seconds < 60 -> String.format(java.util.Locale.US, "%.0fs", seconds)
        else -> {
            val m = (seconds / 60).toInt()
            val s = (seconds % 60).toInt()
            "${m}m ${s}s"
        }
    }
}

private fun formatBpsManual(bps: Long): String = when {
    bps >= 1_000_000_000 -> String.format(java.util.Locale.US, "%.1fGbps", bps / 1_000_000_000.0)
    bps >= 1_000_000 -> String.format(java.util.Locale.US, "%.1fMbps", bps / 1_000_000.0)
    bps >= 1_000 -> String.format(java.util.Locale.US, "%.0fkbps", bps / 1_000.0)
    else -> "${bps}bps"
}