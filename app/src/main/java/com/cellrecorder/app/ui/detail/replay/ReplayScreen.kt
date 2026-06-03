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
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.cellrecorder.app.data.local.entity.CellRecordEntity
import com.cellrecorder.app.ui.shared.TooltipIconButton
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
    val records by viewModel.records.collectAsState()
    val filteredRecords by viewModel.filteredRecords.collectAsState()
    val currentIndex by viewModel.currentIndex.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val speed by viewModel.speed.collectAsState()
    val selectedSim by viewModel.selectedSim.collectAsState()
    val availableSimSlots by viewModel.availableSimSlots.collectAsState()

    LaunchedEffect(sessionId) {
        viewModel.loadSession(sessionId)
    }

    val currentRecord = filteredRecords.getOrNull(currentIndex)

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
                    .padding(padding)
            ) {
                ReplayMapView(
                    filteredRecords = filteredRecords,
                    currentIndex = currentIndex,
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(0.5f)
                )

                Column(
                    modifier = Modifier
                        .weight(0.5f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                ) {
                    StatsPanel(record = currentRecord)

                    RatTimelineBar(
                        records = filteredRecords,
                        currentIndex = currentIndex
                    )

                    ChartGrid(
                        records = filteredRecords,
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
                    .padding(padding)
            ) {
                ReplayMapView(
                    filteredRecords = filteredRecords,
                    currentIndex = currentIndex,
                    modifier = Modifier.fillMaxWidth().height(350.dp)
                )

                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth()
                ) {
                    item {
                        StatsPanel(record = currentRecord)
                    }

                    item {
                        RatTimelineBar(
                            records = filteredRecords,
                            currentIndex = currentIndex
                        )
                    }

                    item {
                        ChartGrid(
                            records = filteredRecords,
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
    currentIndex: Int
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
private fun ChartGrid(
    records: List<CellRecordEntity>,
    currentIndex: Int
) {
    val rsrpValues = remember(records) { records.map { it.rsrp?.toFloat() } }
    val sinrValues = remember(records) { records.map { it.sinr?.toFloat() } }
    val pingValues = remember(records) { records.map { it.avgLatencyMs?.toFloat() } }
    val lossValues = remember(records) { records.map { it.packetLossPct?.toFloat() } }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp)) {
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
            modifier = Modifier.fillMaxSize().clipToBounds()
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
private fun StatsPanel(record: CellRecordEntity?) {
    Surface(
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        if (record != null) {
            Column(modifier = Modifier.padding(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                    StatItem("#", "#${(record.simSlotIndex ?: 0) + 1}", weight = 0.6f, valueColor = ratColor(record.rat))
                    StatItem("PLMN", formatPlmn(record.mcc, record.mnc), weight = 1f)
                    StatItem("RAT", record.rat, weight = 0.7f, valueColor = ratColor(record.rat))
                    StatItem("Band", record.bandNumber?.let { "B$it" } ?: "---", weight = 0.6f)
                    StatItem("ARFCN", record.earfcn?.toString() ?: "---", weight = 0.8f)
                    StatItem("Ping", record.avgLatencyMs?.let { String.format("%.0f ms", it) } ?: "---", weight = 0.8f)
                }
                Spacer(Modifier.height(2.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                    Box(Modifier.weight(0.6f)) { }
                    StatItem("Cell ID", formatCellId(record), weight = 1.2f, valueFontFamily = FontFamily.Monospace)
                    StatItem("PCI", record.pci?.toString() ?: "---", weight = 0.6f)
                    StatItem("RSRP", record.rsrp?.toString() ?: "---", weight = 0.7f)
                    StatItem("RSRQ", record.rsrq?.toString() ?: "---", weight = 0.6f)
                    StatItem("SINR", record.sinr?.toString() ?: "---", weight = 0.6f)
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

private fun formatPlmn(mcc: String?, mnc: String?): String {
    if (mcc != null && mnc != null) return "$mcc-$mnc"
    return "---"
}

private fun formatCellId(record: CellRecordEntity): String {
    if (record.enbOrGnbId != null && record.lcid != null) {
        return "${record.enbOrGnbId}:${record.lcid}"
    }
    return record.fullCellIdentity?.toString() ?: "---"
}

private fun ratColor(rat: String): Color = when {
    rat.startsWith("5G") -> Color(0xFF00BCD4)
    rat.startsWith("4G") -> Color(0xFF2196F3)
    rat == "3G" -> Color(0xFFFF9800)
    rat == "2G" -> Color(0xFFF44336)
    else -> Color.Gray
}