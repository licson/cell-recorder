package com.cellrecorder.app.ui.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Report
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val config by viewModel.config.collectAsStateWithLifecycle()
    val context = LocalContext.current

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

            SettingsCard(title = "Cell ID") {
                SettingsRow("NR gNB Bit-Length", config.nrGnbBitLength.toString(), viewModel::updateNrGnbBitLength, keyboardType = KeyboardType.Number)
                SettingsRow("Cell Info Refresh Interval (s)", config.cellInfoRefreshIntervalSec.toString(), viewModel::updateCellInfoRefreshInterval, keyboardType = KeyboardType.Number)
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
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/licson/cell-recorder"))
                        context.startActivity(intent)
                    }
                )
                HorizontalDivider()
                ClickableAboutRow(
                    icon = Icons.Default.Report,
                    label = "Report Issue",
                    onClick = {
                        val body = viewModel.getDeviceInfoString() + "\n\n**Description:**\n"
                        val uri = Uri.parse("https://github.com/licson/cell-recorder/issues/new?body=${Uri.encode(body)}")
                        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                    }
                )
                HorizontalDivider()
                val scope = rememberCoroutineScope()
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