package com.cellrecorder.app.ui.detail.replay

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.cellrecorder.app.data.local.entity.CellRecordEntity
import com.cellrecorder.app.data.local.entity.CellRecordWithCaBands
import com.cellrecorder.app.data.local.entity.SpeedTestRecordEntity
import com.cellrecorder.app.domain.model.BandResolver
import com.cellrecorder.app.ui.detail.ratColor
import com.cellrecorder.app.ui.detail.rsrpColor
import com.cellrecorder.app.ui.shared.formatCellId
import com.cellrecorder.app.ui.shared.IndoorPathCanvas
import com.cellrecorder.app.ui.shared.IndoorPathLegend
import com.cellrecorder.app.ui.shared.TooltipIconButton
import java.util.Locale
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReplayScreen(
    sessionId: Long,
    onNavigateBack: () -> Unit,
    viewModel: ReplayViewModel = hiltViewModel()
) {
    val records by viewModel.records.collectAsStateWithLifecycle()
    val filteredRecords by viewModel.filteredRecords.collectAsStateWithLifecycle()
    val currentIndex by viewModel.currentIndex.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val speed by viewModel.speed.collectAsStateWithLifecycle()
    val selectedSim by viewModel.selectedSim.collectAsStateWithLifecycle()
    val availableSimSlots by viewModel.availableSimSlots.collectAsStateWithLifecycle()
    val speedTestMarkers by viewModel.speedTestMarkers.collectAsStateWithLifecycle()
    val selectedSpeedTestMarker by viewModel.selectedSpeedTestMarker.collectAsStateWithLifecycle()
    val session by viewModel.session.collectAsStateWithLifecycle()
    val isIndoor = session?.recordingMode == "INDOOR"

    LaunchedEffect(sessionId) {
        viewModel.loadSession(sessionId)
    }

    val currentRecord by remember(filteredRecords, currentIndex) {
        derivedStateOf { filteredRecords.getOrNull(currentIndex) }
    }

    val entities = remember(filteredRecords) { filteredRecords.map { it.record } }
    val currentWrapper = remember(currentRecord) { currentRecord }

    val positionSpeedTestMarker = remember(currentIndex, speedTestMarkers) {
        speedTestMarkers.lastOrNull { it.timelineIndex <= currentIndex }
    }

    val highlightedMarker = remember(selectedSpeedTestMarker, positionSpeedTestMarker, speedTestMarkers) {
        if (selectedSpeedTestMarker != null) {
            speedTestMarkers.firstOrNull { it.record == selectedSpeedTestMarker } ?: positionSpeedTestMarker
        } else {
            positionSpeedTestMarker
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Replay") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        val isLandscape = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

        if (isLandscape) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = padding.calculateTopPadding())
            ) {
                if (isIndoor) {
                    val indoorPath = entities.map { Pair(it.relativeX ?: 0.0, it.relativeY ?: 0.0) }
                    IndoorPathCanvas(
                        pathPoints = indoorPath,
                        currentPosition = entities.getOrNull(currentIndex)?.let {
                            Pair(it.relativeX ?: 0.0, it.relativeY ?: 0.0)
                        },
                        modifier = Modifier.fillMaxHeight().weight(0.5f)
                    )
                    IndoorPathLegend()
                } else {
                    ReplayMapView(
                        filteredRecords = entities,
                        currentIndex = currentIndex,
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(0.5f)
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(0.5f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                ) {
                    StatsPanel(record = currentWrapper)

                    RatTimelineBar(
                        records = entities,
                        currentIndex = currentIndex,
                        speedTestMarkers = speedTestMarkers,
                        onMarkerClick = { viewModel.selectSpeedTestMarker(it) }
                    )

                    if (speedTestMarkers.isNotEmpty()) {
                        SpeedTestSummaryCard(
                            markers = speedTestMarkers,
                            highlightedMarker = highlightedMarker,
                            isSelected = selectedSpeedTestMarker != null
                        )
                    }

                    ChartGrid(
                        records = entities,
                        currentIndex = currentIndex
                    )

                    if (availableSimSlots.isNotEmpty()) {
                        SimFilterRow(
                            availableSimSlots = availableSimSlots,
                            selectedSim = selectedSim,
                            onSelect = { viewModel.setSimFilter(it) }
                        )
                    }

                    Slider(
                        value = currentIndex.toFloat(),
                        onValueChange = { viewModel.seekTo(it.toInt()) },
                        valueRange = 0f..(filteredRecords.size - 1).coerceAtLeast(0).toFloat(),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TooltipIconButton(tooltip = "Previous point", onClick = { viewModel.seekTo((currentIndex - 1).coerceAtLeast(0)) }) {
                            Icon(Icons.Default.SkipPrevious, contentDescription = "Previous")
                        }
                        FilledIconButton(
                            onClick = { viewModel.togglePlayPause() },
                            modifier = Modifier.size(56.dp),
                            shape = CircleShape
                        ) {
                            Icon(
                                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        TooltipIconButton(tooltip = "Next point", onClick = { viewModel.seekTo((currentIndex + 1).coerceAtMost(filteredRecords.lastIndex)) }) {
                            Icon(Icons.Default.SkipNext, contentDescription = "Next")
                        }
                    }

                    SpeedSelector(
                        currentSpeed = speed,
                        onSpeedSelected = { viewModel.setSpeed(it) }
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = padding.calculateTopPadding())
            ) {
                if (isIndoor) {
                        val indoorPath = entities.map { Pair(it.relativeX ?: 0.0, it.relativeY ?: 0.0) }
                        IndoorPathCanvas(
                            pathPoints = indoorPath,
                            currentPosition = entities.getOrNull(currentIndex)?.let {
                                Pair(it.relativeX ?: 0.0, it.relativeY ?: 0.0)
                            },
                            modifier = Modifier.fillMaxWidth().height(350.dp)
                        )
                        IndoorPathLegend()
                    } else {
                        ReplayMapView(
                            filteredRecords = entities,
                            currentIndex = currentIndex,
                            modifier = Modifier.fillMaxWidth().height(350.dp)
                        )
                    }

                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth()
                ) {
                    item {
                        StatsPanel(record = currentWrapper)
                    }

                    item {
                        RatTimelineBar(
                            records = entities,
                            currentIndex = currentIndex,
                            speedTestMarkers = speedTestMarkers,
                            onMarkerClick = { viewModel.selectSpeedTestMarker(it) }
                        )
                    }

                    if (speedTestMarkers.isNotEmpty()) {
                        item {
                            SpeedTestSummaryCard(
                                markers = speedTestMarkers,
                                highlightedMarker = highlightedMarker,
                                isSelected = selectedSpeedTestMarker != null
                            )
                        }
                    }

                    item {
                        ChartGrid(
                            records = entities,
                            currentIndex = currentIndex
                        )
                    }

                    if (availableSimSlots.isNotEmpty()) {
                        item {
                            SimFilterRow(
                                availableSimSlots = availableSimSlots,
                                selectedSim = selectedSim,
                                onSelect = { viewModel.setSimFilter(it) }
                            )
                        }
                    }

                    item {
                        Slider(
                            value = currentIndex.toFloat(),
                            onValueChange = { viewModel.seekTo(it.toInt()) },
                            valueRange = 0f..(filteredRecords.size - 1).coerceAtLeast(0).toFloat(),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                        )
                    }

                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TooltipIconButton(tooltip = "Previous point", onClick = { viewModel.seekTo((currentIndex - 1).coerceAtLeast(0)) }) {
                                Icon(Icons.Default.SkipPrevious, contentDescription = "Previous")
                            }
                            FilledIconButton(
                                onClick = { viewModel.togglePlayPause() },
                                modifier = Modifier.size(56.dp),
                                shape = CircleShape
                            ) {
                                Icon(
                                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                            TooltipIconButton(tooltip = "Next point", onClick = { viewModel.seekTo((currentIndex + 1).coerceAtMost(filteredRecords.lastIndex)) }) {
                                Icon(Icons.Default.SkipNext, contentDescription = "Next")
                            }
                        }
                    }

                    item {
                        SpeedSelector(
                            currentSpeed = speed,
                            onSpeedSelected = { viewModel.setSpeed(it) }
                        )
                    }
                }
            }
        }
    }
}

private data class RatSegment(
    val rat: String,
    val startIndex: Int,
    val endIndex: Int,
    val count: Int
)

private fun ratDisplayLabel(rat: String): String = when (rat) {
    "5G_SA" -> "5G SA"
    "5G_NSA" -> "5G NSA"
    "4G" -> "4G LTE"
    "4G_CA" -> "4G CA"
    "3G" -> "3G"
    "2G" -> "2G"
    else -> rat
}

@Composable
private fun RatTimelineBar(
    records: List<CellRecordEntity>,
    currentIndex: Int,
    speedTestMarkers: List<SpeedTestMarker> = emptyList(),
    onMarkerClick: (SpeedTestRecordEntity) -> Unit = {}
) {
    val segments = remember(records) {
        if (records.isEmpty()) return@remember emptyList()
        val result = mutableListOf<RatSegment>()
        var start = 0
        var currentRat = records[0].rat
        for (i in 1 until records.size) {
            val rat = records[i].rat
            if (rat != currentRat) {
                result.add(RatSegment(currentRat, start, i - 1, i - start))
                start = i
                currentRat = rat
            }
        }
        result.add(RatSegment(currentRat, start, records.size - 1, records.size - start))
        result
    }

    val ratCounts = remember(records) {
        records.groupBy { it.rat }.mapValues { it.value.size }
    }
    val total = records.size

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp)) {
        Text(
            text = "RAT Timeline",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Box(modifier = Modifier.fillMaxWidth().height(24.dp)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                if (segments.isEmpty() || total == 0) return@Canvas
                val barWidth = size.width
                val stepX = barWidth / total
                segments.forEach { seg ->
                    val left = stepX * seg.startIndex
                    val right = stepX * (seg.endIndex + 1)
                    drawRect(
                        color = ratColor(seg.rat),
                        topLeft = Offset(left, 0f),
                        size = Size(right - left, size.height)
                    )
                }
                if (currentIndex in records.indices) {
                    val cx = stepX * currentIndex
                    drawLine(Color.White, Offset(cx, 0f), Offset(cx, size.height), strokeWidth = 2f)
                }
                speedTestMarkers.forEach { marker ->
                    val x = stepX * marker.timelineIndex.coerceIn(0, (total - 1).coerceAtLeast(0))
                    val speed = marker.record.downloadBps
                    val color = when {
                        speed == null -> Color(0xFF9E9E9E)
                        speed > 200_000_000 -> Color(0xFF4CAF50)
                        speed > 100_000_000 -> Color(0xFF8BC34A)
                        speed > 50_000_000 -> Color(0xFFFFEB3B)
                        speed > 10_000_000 -> Color(0xFFFF9800)
                        else -> Color(0xFFF44336)
                    }
                    drawCircle(color = color, radius = 6f, center = Offset(x, size.height / 2f))
                    drawCircle(color = Color.White, radius = 3f, center = Offset(x, size.height / 2f))
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        val sortedRats = ratCounts.entries.sortedByDescending { it.value }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            sortedRats.forEach { (rat, count) ->
                val pct = count * 100.0 / total
                Text(
                    text = "${ratDisplayLabel(rat)}: ${"%.1f".format(pct)}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = ratColor(rat)
                )
            }
        }
    }
}

@Composable
private fun SpeedTestSummaryCard(
    markers: List<SpeedTestMarker>,
    highlightedMarker: SpeedTestMarker? = null,
    isSelected: Boolean = false
) {
    val total = markers.size
    val succeeded = markers.count { it.record.succeeded }
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        shape = RoundedCornerShape(6.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f)
        )
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Speed Tests: $total ($succeeded OK)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (highlightedMarker != null) {
                    val label = if (isSelected) "Selected" else "Position"
                    Text(
                        text = "$label: ↓${formatBps(highlightedMarker.record.downloadBps)} ↑${formatBps(highlightedMarker.record.uploadBps)}",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = "No test at this position",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun formatBps(bps: Long?): String {
    if (bps == null) return "?"
    return when {
        bps >= 1_000_000_000 -> String.format(Locale.US, "%.1fG", bps / 1_000_000_000.0)
        bps >= 1_000_000 -> String.format(Locale.US, "%.0fM", bps / 1_000_000.0)
        bps >= 1_000 -> String.format(Locale.US, "%.0fk", bps / 1_000.0)
        else -> "${bps}b"
    }
}

@Composable
private fun ChartGrid(
    records: List<CellRecordEntity>,
    currentIndex: Int
) {
    val rsrpValues = remember(records) { records.map { it.rsrp?.toFloat() } }
    val sinrValues = remember(records) { records.map { it.sinr?.toFloat() } }
    val pingValues = remember(records) { records.map { it.avgLatencyMs?.toFloat() } }
    val lossValues = remember(records) { records.map { it.packetLossPct?.toFloat() } }

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricChart(
                    label = "RSRP",
                    values = rsrpValues,
                    unit = "dBm",
                    currentIndex = currentIndex,
                    color = Color(0xFF2196F3),
                    modifier = Modifier.weight(1f)
                )
                MetricChart(
                    label = "SINR",
                    values = sinrValues,
                    unit = "dB",
                    currentIndex = currentIndex,
                    color = Color(0xFF00BCD4),
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricChart(
                    label = "Ping",
                    values = pingValues,
                    unit = "ms",
                    currentIndex = currentIndex,
                    color = Color(0xFFFF9800),
                    fixedMin = 0f,
                    modifier = Modifier.weight(1f)
                )
                MetricChart(
                    label = "Pkt Loss",
                    values = lossValues,
                    unit = "%",
                    currentIndex = currentIndex,
                    color = Color(0xFFF44336),
                    fixedMin = 0f,
                    fixedMax = 100f,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ReplayMapView(
    filteredRecords: List<CellRecordEntity>,
    currentIndex: Int,
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
            controller.setZoom(16.0)
        }
    }

    DisposableEffect(Unit) {
        mapView.onResume()
        onDispose { mapView.onPause() }
    }

    val currentMarker = remember {
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

    LaunchedEffect(filteredRecords) {
        mapView.overlays.clear()
        val pts = filteredRecords.map { GeoPoint(it.latitude, it.longitude) }
        if (pts.isNotEmpty()) {
            polyline.setPoints(pts)
            mapView.overlays.add(polyline)
            mapView.zoomToBoundingBox(BoundingBox.fromGeoPoints(pts), true, 16)
        }
        mapView.invalidate()
    }

    LaunchedEffect(currentIndex, filteredRecords) {
        val record = filteredRecords.getOrNull(currentIndex)
        if (record != null) {
            val pt = GeoPoint(record.latitude, record.longitude)
            currentMarker.position = pt
            currentMarker.setVisible(true)
            mapView.controller.animateTo(pt)
            if (!mapView.overlays.contains(currentMarker)) {
                mapView.overlays.add(currentMarker)
            }
        } else {
            currentMarker.setVisible(false)
        }
        mapView.invalidate()
    }

    Box(modifier = modifier) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize().clipToBounds(),
            onRelease = { it.onDetach() }
        )
        if (filteredRecords.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("No points", style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun SimFilterRow(
    availableSimSlots: List<Int>,
    selectedSim: Int?,
    onSelect: (Int?) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = selectedSim == null,
            onClick = { onSelect(null) },
            label = { Text("All") }
        )
        availableSimSlots.forEach { slot ->
            FilterChip(
                selected = selectedSim == slot,
                onClick = { onSelect(slot) },
                label = { Text("SIM ${slot + 1}") }
            )
        }
    }
}

@Composable
private fun SpeedSelector(
    currentSpeed: Float,
    onSpeedSelected: (Float) -> Unit
) {
    val speeds = listOf(1f, 2f, 5f, 10f)
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        speeds.forEach { speed ->
            FilterChip(
                selected = currentSpeed == speed,
                onClick = { onSpeedSelected(speed) },
                label = { Text("${speed.toInt()}x") }
            )
        }
    }
}

@Composable
private fun StatsPanel(record: CellRecordWithCaBands?) {
    var expanded by remember { mutableStateOf(false) }
    val hasExpandableData = record != null && (record.record.rat.startsWith("5G_NSA") && record.record.anchorPci != null || record.caBands.isNotEmpty())

    Surface(
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        if (record != null) {
            val entity = record.record
            Column(modifier = Modifier.padding(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                    StatItem("#", "#${(entity.simSlotIndex ?: 0) + 1}", weight = 0.6f, valueColor = ratColor(entity.rat))
                    StatItem("PLMN", formatPlmn(entity.mcc, entity.mnc), weight = 1f)
                    StatItem("RAT", entity.rat, weight = 0.7f, valueColor = ratColor(entity.rat))
                    val bandText = if (record.caBands.isNotEmpty()) {
                        "${BandResolver.formatBand(entity.bandNumber, entity.earfcn, entity.rat)}+${record.caBands.size}"
                    } else {
                        BandResolver.formatBand(entity.bandNumber, entity.earfcn, entity.rat)
                    }
                    StatItem("Band", bandText, weight = 0.6f)
                    StatItem("ARFCN", entity.earfcn?.toString() ?: "---", weight = 0.8f)
                    if (hasExpandableData) {
                        Icon(
                            imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (expanded) "Collapse" else "Expand",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                    Box(Modifier.weight(0.6f)) { }
                    StatItem("Cell ID", formatCellId(entity), weight = 1.2f, valueFontFamily = FontFamily.Monospace)
                    StatItem("PCI", entity.pci?.toString() ?: "---", weight = 0.6f)
                    val rsrp = entity.rsrp
                    StatItem("RSRP", entity.rsrp?.toString() ?: "---", weight = 0.7f, valueColor = rsrpColor(rsrp))
                    val rsrq = entity.rsrq
                    StatItem("RSRQ", entity.rsrq?.toString() ?: "---", weight = 0.6f, valueColor = rsrpColor(rsrq))
                    val sinr = entity.sinr
                    StatItem("SINR", entity.sinr?.toString() ?: "---", weight = 0.6f, valueColor = rsrpColor(sinr))
                }
                if (!expanded && entity.rat.startsWith("5G_NSA") && entity.anchorPci != null) {
                    Spacer(Modifier.height(2.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                        Box(Modifier.weight(0.6f)) { }
                        val aRsrp = entity.anchorRsrp
                        StatItem(
                            "Anchor",
                            "LTE: B${entity.anchorBandNumber ?: "?"} PCI ${entity.anchorPci} RSRP ${entity.anchorRsrp ?: "---"}",
                            weight = 2.5f,
                            valueColor = rsrpColor(aRsrp)
                        )
                    }
                }
                if (expanded && hasExpandableData) {
                    if (entity.rat.startsWith("5G_NSA") && entity.anchorPci != null) {
                        Spacer(Modifier.height(4.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Anchor Cell",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(2.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                            StatItem("Band", "B${entity.anchorBandNumber ?: "?"}", weight = 0.6f)
                            StatItem("ARFCN", entity.anchorEarfcn?.toString() ?: "---", weight = 0.8f)
                            StatItem("PCI", entity.anchorPci?.toString() ?: "---", weight = 0.6f)
                            StatItem("TAC", entity.anchorTac?.toString() ?: "---", weight = 0.7f)
                        }
                        Spacer(Modifier.height(2.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                            Box(Modifier.weight(0.6f)) { }
                            val aRsrp = entity.anchorRsrp
                            val aRsrq = entity.anchorRsrq
                            val aSinr = entity.anchorSinr
                            StatItem("RSRP", entity.anchorRsrp?.toString() ?: "---", weight = 0.7f, valueColor = rsrpColor(aRsrp))
                            StatItem("RSRQ", entity.anchorRsrq?.toString() ?: "---", weight = 0.6f, valueColor = rsrpColor(aRsrq))
                            StatItem("SINR", entity.anchorSinr?.toString() ?: "---", weight = 0.6f, valueColor = rsrpColor(aSinr))
                        }
                    }
                    if (record.caBands.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "CA Bands",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(2.dp))
                        record.caBands.forEach { ca ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                                StatItem("Band", "B${ca.bandNumber ?: "?"}", weight = 0.6f)
                                StatItem("PCI", ca.pci?.toString() ?: "---", weight = 0.6f)
                                StatItem("EARFCN", ca.earfcn?.toString() ?: "---", weight = 0.8f)
                                val caRsrp = ca.rsrp
                                val caRsrq = ca.rsrq
                                val caSinr = ca.sinr
                                Box(Modifier.weight(0.6f)) { }
                                StatItem("RSRP", ca.rsrp?.toString() ?: "---", weight = 0.7f, valueColor = rsrpColor(caRsrp))
                                StatItem("RSRQ", ca.rsrq?.toString() ?: "---", weight = 0.6f, valueColor = rsrpColor(caRsrq))
                                StatItem("SINR", ca.sinr?.toString() ?: "---", weight = 0.6f, valueColor = rsrpColor(caSinr))
                            }
                            Spacer(Modifier.height(2.dp))
                        }
                    }
                }
            }
        } else {
            Text(
                "No record selected",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium
            )
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

private fun formatPlmn(mcc: String?, mnc: String?): String = com.cellrecorder.app.ui.shared.formatPlmn(mcc, mnc)