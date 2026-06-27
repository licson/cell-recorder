package com.cellrecorder.app.ui.recording

import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.cellrecorder.app.BuildConfig
import com.cellrecorder.app.service.RecordingService
import com.cellrecorder.app.service.RecordingState
import com.cellrecorder.app.service.SimLiveState
import com.cellrecorder.app.ui.shared.CellInfoPanel
import com.cellrecorder.app.ui.shared.IndoorPathCanvas
import com.cellrecorder.app.ui.shared.IndoorPathLegend
import com.cellrecorder.app.ui.shared.PermissionFlowDialogs
import com.cellrecorder.app.ui.shared.PermissionFlowState
import com.cellrecorder.app.ui.shared.TrackingConfidenceIndicator
import com.cellrecorder.app.ui.shared.toCellInfoData
import java.util.Locale
import com.cellrecorder.app.ui.shared.PermissionHelper
import com.cellrecorder.app.ui.shared.TooltipIconButton
import com.cellrecorder.app.ui.shared.rememberPermissionFlowState
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingScreen(
    sessionId: Long,
    onNavigateBack: () -> Unit,
    viewModel: RecordingViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val session by viewModel.session.collectAsStateWithLifecycle()
    val serviceState by viewModel.serviceState.collectAsStateWithLifecycle()
    val liveSimStates by viewModel.liveSimStates.collectAsStateWithLifecycle()
    val isRecording = serviceState?.isRecording == true && serviceState?.sessionId == sessionId
    val snackbarHostState = remember { SnackbarHostState() }
    val recordingMode = session?.recordingMode ?: "OUTDOOR"
    val isIndoor = recordingMode == "INDOOR"

    LaunchedEffect(sessionId) {
        viewModel.loadSession(sessionId)
    }

    LaunchedEffect(serviceState?.errorMessage) {
        serviceState?.errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    val wasExtrapolating = remember { mutableStateOf(false) }

    LaunchedEffect(serviceState?.isExtrapolatingGps) {
        if (serviceState?.isExtrapolatingGps == true && !wasExtrapolating.value) {
            snackbarHostState.showSnackbar("GPS fix lost. Hold your phone in a stable position for inertial geolocation.")
        }
        wasExtrapolating.value = serviceState?.isExtrapolatingGps == true
    }

    var showStopConfirm by remember { mutableStateOf(false) }
    val activity = LocalActivity.current
    val mainHandler = remember { android.os.Handler(android.os.Looper.getMainLooper()) }

    val flowState = rememberPermissionFlowState(
        permissions = PermissionHelper.missingAllForMode(recordingMode, context).toList(),
        onAllGranted = {
            if (!isRecording) {
                RecordingService.start(context, sessionId, recordingMode)
            }
        },
        autoRequestOnLaunch = false,
    )

    fun handlePermissionResult() {
        PermissionHelper.logPermissionState(context, "recording-handle-result")
        val missing = PermissionHelper.missingAllForMode(recordingMode, context)
        flowState.handleResult(missing, activity) {
            if (!isRecording) {
                RecordingService.start(context, sessionId, recordingMode)
            }
        }
    }

    val backgroundPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        flowState.isRequestingPermissions = false
        PermissionHelper.logPermissionState(context, "recording-bg-result")
        handlePermissionResult()
    }

    val foregroundPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        flowState.isRequestingPermissions = false
        PermissionHelper.logPermissionState(context, "recording-fg-result")
        if (PermissionHelper.allForegroundGranted(context) && PermissionHelper.allIndoorGranted(context) && PermissionHelper.missingBackgroundPermissions(context).isNotEmpty()) {
            flowState.isRequestingPermissions = true
            mainHandler.postDelayed({
                val missingBg = PermissionHelper.missingBackgroundPermissions(context)
                if (BuildConfig.DEBUG) android.util.Log.d("RecordingScreen", "Foreground done, launching background: ${missingBg.toList()}")
                backgroundPermissionLauncher.launch(missingBg)
            }, 200)
        } else {
            handlePermissionResult()
        }
    }

    fun requestNextPermissions() {
        val missingFg = PermissionHelper.missingPermissionsForMode(recordingMode, context)
        val missingBg = PermissionHelper.missingBackgroundPermissions(context)
        if (BuildConfig.DEBUG) android.util.Log.d("RecordingScreen", "requestNext: missingFg=${missingFg.toList()} missingBg=${missingBg.toList()}")

        when {
            missingFg.isNotEmpty() -> {
                mainHandler.postDelayed({
                    if (BuildConfig.DEBUG) android.util.Log.d("RecordingScreen", "Launching foreground permissions: ${missingFg.toList()}")
                    foregroundPermissionLauncher.launch(missingFg)
                }, 200)
            }
            missingBg.isNotEmpty() -> {
                mainHandler.postDelayed({
                    if (BuildConfig.DEBUG) android.util.Log.d("RecordingScreen", "Launching background permissions: ${missingBg.toList()}")
                    backgroundPermissionLauncher.launch(missingBg)
                }, 200)
            }
            else -> {
                flowState.isRequestingPermissions = false
                handlePermissionResult()
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(session?.name ?: "Recording")
                },
                navigationIcon = {
                    if (!isRecording) {
                        TextButton(onClick = onNavigateBack) {
                            Text("Back")
                        }
                    }
                },
                actions = {
                    if (isRecording) {
                        Text(
                            text = formatElapsed(serviceState?.elapsedMs ?: 0),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(end = 16.dp)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (isIndoor) {
                    IndoorPathCanvas(
                        pathPoints = serviceState?.recordedPath ?: emptyList(),
                        currentPosition = if (serviceState?.isRecording == true)
                            Pair(serviceState?.currentRelativeX ?: 0.0, serviceState?.currentRelativeY ?: 0.0)
                        else null,
                        originPosition = Pair(0.0, 0.0),
                        driftRadiusM = serviceState?.estimatedDriftM ?: 0.0,
                        discontinuityIndices = serviceState?.recordedDiscontinuities ?: emptySet(),
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    )
                    if (isRecording) {
                        TrackingConfidenceIndicator(
                            trackingConfidence = viewModel.trackingConfidenceText(
                                serviceState?.estimatedDriftM ?: 0.0,
                                serviceState?.noStepWarning ?: false
                            ),
                            timeSinceResetMs = serviceState?.timeSinceOriginResetMs,
                            stepCount = serviceState?.currentStepCount,
                            driftM = serviceState?.estimatedDriftM
                        )
                        if (serviceState?.noStepWarning == true) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .semantics { contentDescription = "No steps" },
                                shape = RoundedCornerShape(0.dp),
                                color = Color(0xFFFF9800).copy(alpha = 0.12f)
                            ) {
                                Text(
                                    text = "No steps detected. Try moving the phone to your pocket.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFFF9800),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                    IndoorPathLegend()
                } else {
                    OsmMapView(
                        state = serviceState,
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    )
                }

                LiveStatsBar(
                    simStates = liveSimStates,
                    state = serviceState,
                    isIndoor = isIndoor
                )

                Box(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (isIndoor && isRecording) {
                            OutlinedButton(
                                onClick = { viewModel.resetOrigin() }
                            ) {
                                Text("Reset Origin", maxLines = 1)
                            }
                        }
                        Button(
                            onClick = {
                            if (isRecording) {
                                showStopConfirm = true
                            } else {
                                if (PermissionHelper.allGrantedForMode(recordingMode, context)) {
                                    RecordingService.start(context, sessionId, recordingMode)
                                } else {
                                    val missing = PermissionHelper.missingAllForMode(recordingMode, context)
                                    flowState.permissionState = PermissionHelper.decidePermissionState(
                                        flowState.hasAttemptedOnce, missing, activity
                                    )
                                }
                            }
                        },
                        modifier = Modifier.size(72.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isRecording) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.PlayArrow,
contentDescription = if (isRecording) "Stop" else "Start",
                            modifier = Modifier.size(32.dp)
                        )
                    }
                } // Row
            }
            }
        }

        PermissionFlowDialogs(
            state = flowState,
            recordingMode = recordingMode,
            onRationaleGrant = {
                flowState.prepareForRequest()
                requestNextPermissions()
            },
            onOpenSettings = { PermissionHelper.openAppSettings(context) },
        )

        if (showStopConfirm) {
            AlertDialog(
                onDismissRequest = { showStopConfirm = false },
                title = { Text("Stop recording?") },
                text = { Text("Are you sure you want to stop the current recording session?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            RecordingService.stop(context)
                            showStopConfirm = false
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Stop")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showStopConfirm = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
private fun OsmMapView(
    state: RecordingState?,
    modifier: Modifier = Modifier
) {
    val ctx = LocalContext.current

    LaunchedEffect(Unit) {
        Configuration.getInstance().let { config ->
            config.userAgentValue = ctx.packageName
            config.osmdroidBasePath = ctx.cacheDir
        }
    }

    val mapView = remember {
        MapView(ctx).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            setBuiltInZoomControls(false)
            controller.setZoom(16.0)
            val start = GeoPoint(0.0, 0.0)
            controller.setCenter(start)
        }
    }

    DisposableEffect(Unit) {
        mapView.onResume()
        onDispose { mapView.onPause() }
    }

    val marker = remember {
        Marker(mapView).apply {
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            title = "Current"
        }
    }

    val polyline = remember {
        Polyline().apply {
            outlinePaint.color = 0xFF2196F3.toInt()
            outlinePaint.strokeWidth = 4f
        }
    }

    LaunchedEffect(state?.currentLatitude, state?.currentLongitude) {
        val lat = state?.currentLatitude ?: 0.0
        val lon = state?.currentLongitude ?: 0.0
        if (lat != 0.0 || lon != 0.0) {
            val pt = GeoPoint(lat, lon)
            marker.position = pt
            marker.setVisible(true)
            mapView.controller.animateTo(pt)
            if (!mapView.overlays.contains(marker)) {
                mapView.overlays.add(marker)
            }
        } else {
            marker.setVisible(false)
        }
        mapView.invalidate()
    }

    LaunchedEffect(state?.recordedPath) {
        val path = state?.recordedPath ?: emptyList()
        mapView.overlays.remove(polyline)
        if (path.isNotEmpty()) {
            val pts = path.map { GeoPoint(it.first, it.second) }
            polyline.setPoints(pts)
            mapView.overlays.add(polyline)
        }
        mapView.invalidate()
    }

    Box(modifier = modifier) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize().clipToBounds(),
            onRelease = { it.onDetach() }
        )

        if ((state?.currentLatitude ?: 0.0) == 0.0 && (state?.currentLongitude ?: 0.0) == 0.0) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Black.copy(alpha = 0.55f)
                ) {
                    Text(
                        text = "Waiting for GPS...",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun LiveStatsBar(
    simStates: List<SimLiveState>,
    state: RecordingState?,
    isIndoor: Boolean = false
) {
    Surface(
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            simStates.forEach { sim ->
                SimCard(sim = sim)
            }

            if (state != null) {
                val dataSimLabel = (state.dataSubId.takeIf { it >= 0 }?.let { id ->
                    simStates.find { it.subscriptionId == id }?.simSlotIndex?.let { it + 1 }
                }?.let { "(via SIM $it)" } ?: "")
                if (isIndoor) {
                    Text(
                        text = "Ping: ${state.currentLatency} ms $dataSimLabel",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1
                    )
                } else {
                    val latStr = String.format(Locale.US, "%.5f", state.currentLatitude)
                    val lonStr = String.format(Locale.US, "%.5f", state.currentLongitude)
                    val altStr = if (state.currentAltitude != 0.0) String.format(Locale.US, "%.1f m", state.currentAltitude) else "---"
                    val gpsStr = if (state.gpsStatus == "OK") "GPS OK" else "GPS ${state.gpsStatus}"
                    val gpsColor = if (state.isExtrapolatingGps == true) Color(0xFFFF9800) else MaterialTheme.colorScheme.primary
                    Text(
                        text = "Ping: ${state.currentLatency} ms $dataSimLabel  |  $gpsStr  |  $latStr, $lonStr  |  Alt: $altStr",
                        style = MaterialTheme.typography.bodySmall,
                        color = gpsColor,
                        maxLines = 1
                    )
                }
                val speedText = when (state.speedTestStatus) {
                    "Discovering" -> "Speed: Selecting server..."
                    "Downloading" -> "Speed: Testing ↓..."
                    "Uploading" -> "Speed: Testing ↑..."
                    "Completed" -> {
                        val dl = state.lastSpeedTestDownloadBps?.let {
                            String.format(Locale.US, "%.1f", it / 1_000_000.0)
                        } ?: "?"
                        val ul = state.lastSpeedTestUploadBps?.let {
                            String.format(Locale.US, "%.1f", it / 1_000_000.0)
                        } ?: "?"
                        "Speed: ↓$dl ↑$ul Mbps"
                    }
                    "Failed" -> "Speed: Failed"
                    "SkippedWiFi" -> "Speed: (WiFi)"
                    else -> "Speed: ---"
                }
                Text(
                    text = speedText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun SimCard(sim: SimLiveState) {
    val data = sim.toCellInfoData()
    val hasExpandableData = data.anchorCell != null || data.caBands.isNotEmpty()
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clickable { if (hasExpandableData) expanded = !expanded },
        shape = RoundedCornerShape(4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            CellInfoPanel(
                data = data,
                isExpandable = true,
                expanded = expanded,
                onExpandToggle = { if (hasExpandableData) expanded = !expanded },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private fun formatElapsed(ms: Long): String {
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return "%02d:%02d".format(min, sec)
}

