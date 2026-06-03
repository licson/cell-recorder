package com.cellrecorder.app.ui.recording

import android.app.Activity
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.cellrecorder.app.service.RecordingService
import com.cellrecorder.app.service.RecordingState
import com.cellrecorder.app.service.SimLiveState
import com.cellrecorder.app.ui.detail.ratColor
import com.cellrecorder.app.ui.shared.PermissionDeniedDialog
import com.cellrecorder.app.ui.shared.PermissionHelper
import com.cellrecorder.app.ui.shared.PermissionRationaleDialog
import com.cellrecorder.app.ui.shared.PermissionUiState
import com.cellrecorder.app.ui.shared.TooltipIconButton
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

    var permissionState by remember { mutableStateOf<PermissionUiState?>(null) }
    var showStopConfirm by remember { mutableStateOf(false) }
    var isRequestingPermissions by remember { mutableStateOf(false) }
    var hasAttemptedOnce by remember { mutableStateOf(false) }
    val activity = LocalContext.current as Activity
    val mainHandler = remember { android.os.Handler(android.os.Looper.getMainLooper()) }

    fun handlePermissionResult() {
        PermissionHelper.logPermissionState(context, "recording-handle-result")
        permissionState = when {
            PermissionHelper.allGranted(context) -> {
                if (!isRecording) {
                    RecordingService.start(context, sessionId)
                }
                null
            }
            hasAttemptedOnce -> {
                PermissionUiState.ShowSettings
            }
            PermissionHelper.hasPermanentDenial(activity) -> {
                PermissionUiState.ShowSettings
            }
            else -> {
                hasAttemptedOnce = true
                PermissionUiState.ShowRationale
            }
        }
    }

    val backgroundPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        isRequestingPermissions = false
        PermissionHelper.logPermissionState(context, "recording-bg-result")
        handlePermissionResult()
    }

    val foregroundPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        isRequestingPermissions = false
        PermissionHelper.logPermissionState(context, "recording-fg-result")
        if (PermissionHelper.allForegroundGranted(context) && PermissionHelper.missingBackgroundPermissions(context).isNotEmpty()) {
            isRequestingPermissions = true
            mainHandler.postDelayed({
                val missingBg = PermissionHelper.missingBackgroundPermissions(context)
                android.util.Log.d("RecordingScreen", "Foreground done, launching background: ${missingBg.toList()}")
                backgroundPermissionLauncher.launch(missingBg)
            }, 200)
        } else {
            handlePermissionResult()
        }
    }

    fun requestNextPermissions() {
        val missingFg = PermissionHelper.missingForegroundPermissions(context)
        val missingBg = PermissionHelper.missingBackgroundPermissions(context)
        android.util.Log.d("RecordingScreen", "requestNext: missingFg=${missingFg.toList()} missingBg=${missingBg.toList()}")

        when {
            missingFg.isNotEmpty() -> {
                mainHandler.postDelayed({
                    android.util.Log.d("RecordingScreen", "Launching foreground permissions: ${missingFg.toList()}")
                    foregroundPermissionLauncher.launch(missingFg)
                }, 200)
            }
            missingBg.isNotEmpty() -> {
                mainHandler.postDelayed({
                    android.util.Log.d("RecordingScreen", "Launching background permissions: ${missingBg.toList()}")
                    backgroundPermissionLauncher.launch(missingBg)
                }, 200)
            }
            else -> {
                isRequestingPermissions = false
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
            modifier = Modifier.fillMaxSize().padding(
                start = padding.calculateStartPadding(LocalLayoutDirection.current),
                top = padding.calculateTopPadding(),
                end = padding.calculateEndPadding(LocalLayoutDirection.current)
            )
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                OsmMapView(
                    state = serviceState,
                    modifier = Modifier.weight(1f).fillMaxWidth()
                )

                LiveStatsBar(
                    simStates = liveSimStates,
                    state = serviceState
                )

                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Button(
                        onClick = {
                            if (isRecording) {
                                showStopConfirm = true
                            } else {
                                when {
                                    PermissionHelper.allGranted(context) -> {
                                        RecordingService.start(context, sessionId)
                                    }
                                    hasAttemptedOnce -> {
                                        permissionState = PermissionUiState.ShowSettings
                                    }
                                    PermissionHelper.hasPermanentDenial(activity) -> {
                                        permissionState = PermissionUiState.ShowSettings
                                    }
                                    else -> {
                                        hasAttemptedOnce = true
                                        isRequestingPermissions = true
                                        permissionState = null
                                        requestNextPermissions()
                                    }
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
                }
            }
        }

        when (permissionState) {
            PermissionUiState.ShowRationale -> {
                PermissionRationaleDialog(
                    onGrant = {
                        isRequestingPermissions = true
                        permissionState = null
                        requestNextPermissions()
                    }
                )
            }
            PermissionUiState.ShowSettings -> {
                PermissionDeniedDialog(
                    onOpenSettings = { PermissionHelper.openAppSettings(context) }
                )
            }
            else -> {}
        }

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
            modifier = Modifier.fillMaxSize().clipToBounds()
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
    state: RecordingState?
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
                val latStr = String.format("%.5f", state.currentLatitude)
                val lonStr = String.format("%.5f", state.currentLongitude)
                val altStr = if (state.currentAltitude != 0.0) String.format("%.1f m", state.currentAltitude) else "---"
                val gpsStr = if (state.gpsStatus == "OK") "GPS OK" else "GPS ${state.gpsStatus}"
                val gpsColor = if (state.isExtrapolatingGps == true) Color(0xFFFF9800) else MaterialTheme.colorScheme.primary
                Text(
                    text = "Ping: ${state.currentLatency} ms $dataSimLabel  |  $gpsStr  |  $latStr, $lonStr  |  Alt: $altStr",
                    style = MaterialTheme.typography.bodySmall,
                    color = gpsColor,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun SimCard(sim: SimLiveState) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        shape = RoundedCornerShape(4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                StatItem("#", "#${sim.simSlotIndex + 1}", weight = 0.6f, valueColor = ratColor(sim.rat))
                StatItem("PLMN", sim.plmn, weight = 1f)
                StatItem("TAC", sim.tac, weight = 0.7f)
                StatItem("RAT", sim.rat, weight = 0.7f, valueColor = ratColor(sim.rat))
                StatItem("Band", sim.bandNumber, weight = 0.6f)
                StatItem("ARFCN", sim.earfcn, weight = 0.8f)
            }
            Spacer(Modifier.height(2.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                Box(Modifier.weight(0.6f)) { }
                StatItem("Cell ID", sim.cellId, weight = 1.2f, valueFontFamily = FontFamily.Monospace)
                StatItem("PCI", sim.pci, weight = 0.6f)
                StatItem("RSRP", sim.rsrp, weight = 0.7f)
                StatItem("RSRQ", sim.rsrq, weight = 0.6f)
                StatItem("SINR", sim.sinr, weight = 0.6f)
            }
        }
    }
}

@Composable
private fun RowScope.StatItem(
    label: String,
    value: String,
    weight: Float = 1f,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    valueFontFamily: FontFamily = FontFamily.Default
) {
    Column(modifier = Modifier.weight(weight), horizontalAlignment = Alignment.Start) {
        Text(
            text = value.ifEmpty { "---" },
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = valueFontFamily),
            color = valueColor,
            maxLines = 1
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            maxLines = 1
        )
    }
}

private fun formatElapsed(ms: Long): String {
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return "%02d:%02d".format(min, sec)
}

