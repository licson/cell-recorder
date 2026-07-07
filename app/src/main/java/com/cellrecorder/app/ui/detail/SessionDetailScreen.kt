package com.cellrecorder.app.ui.detail

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.layout.onGloballyPositioned
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import androidx.compose.ui.platform.LocalDensity

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.cellrecorder.app.data.local.entity.CellRecordWithCaBands
import com.cellrecorder.app.data.local.entity.SessionMarkerEntity
import com.cellrecorder.app.domain.model.BandResolver
import com.cellrecorder.app.domain.usecase.ExportData
import com.cellrecorder.app.ui.detail.analytics.AnalyticsPanel
import com.cellrecorder.app.ui.map.SessionMapView
import com.cellrecorder.app.ui.shared.IndoorPathCanvas
import com.cellrecorder.app.ui.shared.IndoorPathLegend
import com.cellrecorder.app.ui.shared.MarkerDialog
import com.cellrecorder.app.ui.shared.TooltipIconButton
import java.util.Locale
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SessionDetailScreen(
    sessionId: Long,
    onNavigateBack: () -> Unit,
    onOpenReplay: () -> Unit,
    viewModel: SessionDetailViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val session by viewModel.session.collectAsStateWithLifecycle()
    val records by viewModel.records.collectAsStateWithLifecycle()
    val filteredRecords by viewModel.filteredRecords.collectAsStateWithLifecycle()
    val allGrouped by viewModel.allGrouped.collectAsStateWithLifecycle()
    val visibleWindow by viewModel.visibleWindow.collectAsStateWithLifecycle()
    val selectedRecord by viewModel.selectedRecord.collectAsStateWithLifecycle()
    val exportData by viewModel.exportData.collectAsStateWithLifecycle()
    val analytics by viewModel.analytics.collectAsStateWithLifecycle()
    val speedTestAnalytics by viewModel.speedTestAnalytics.collectAsStateWithLifecycle()
    val speedTestExportData by viewModel.speedTestExportData.collectAsStateWithLifecycle()
    val markerExportData by viewModel.markerExportData.collectAsStateWithLifecycle()
    val mapDisplayMode by viewModel.mapDisplayMode.collectAsStateWithLifecycle()
    val showAnalytics by viewModel.showAnalytics.collectAsStateWithLifecycle()
    val selectedSim by viewModel.selectedSim.collectAsStateWithLifecycle()
    val availableSimSlots by viewModel.availableSimSlots.collectAsStateWithLifecycle()
    val markers by viewModel.markers.collectAsStateWithLifecycle()
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showMapModeMenu by remember { mutableStateOf(false) }
    var showSimMenu by remember { mutableStateOf(false) }
    var showMarkerDialog by remember { mutableStateOf(false) }
    var editingMarker by remember { mutableStateOf<SessionMarkerEntity?>(null) }
    val isIndoor = session?.recordingMode == "INDOOR"
    val isTunnel = session?.recordingMode == "TUNNEL"

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("*/*")
    ) { uri: Uri? ->
        uri?.let {
            try {
                context.contentResolver.openOutputStream(it)?.use { stream ->
                    stream.write((exportData?.content ?: "").toByteArray())
                }
                Toast.makeText(context, "Exported successfully", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
        viewModel.clearExportData()
    }

    LaunchedEffect(exportData) {
        exportData?.let { data ->
            exportLauncher.launch(data.suggestedFilename)
        }
    }

    val speedTestExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("*/*")
    ) { uri: Uri? ->
        uri?.let {
            try {
                context.contentResolver.openOutputStream(it)?.use { stream ->
                    stream.write((speedTestExportData?.content ?: "").toByteArray())
                }
                Toast.makeText(context, "Speedtest data exported", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Speedtest export failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
        viewModel.clearSpeedTestExportData()
    }

    val markerExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("*/*")
    ) { uri: Uri? ->
        uri?.let {
            try {
                context.contentResolver.openOutputStream(it)?.use { stream ->
                    stream.write((markerExportData?.content ?: "").toByteArray())
                }
                Toast.makeText(context, "Markers exported", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Markers export failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
        viewModel.clearMarkerExportData()
    }

    LaunchedEffect(speedTestExportData) {
        speedTestExportData?.let { data ->
            speedTestExportLauncher.launch(data.suggestedFilename)
        }
    }

    LaunchedEffect(markerExportData) {
        markerExportData?.let { data ->
            markerExportLauncher.launch(data.suggestedFilename)
        }
    }

    LaunchedEffect(sessionId) {
        viewModel.loadSession(sessionId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = session?.name ?: "Session",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        session?.let { s ->
                            val df = remember { SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()) }
                            Text(
                                text = df.format(Date(s.createdAt)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    TooltipIconButton(tooltip = "Back", onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {}
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())
        ) {
            if (isTunnel) {
                TunnelPlaceholderPanel(
                    session = session,
                    records = records,
                    markers = markers,
                    modifier = Modifier.fillMaxWidth().height(if (showAnalytics) 400.dp else 200.dp)
                )
            } else if (isIndoor) {
                IndoorPathCanvas(
                    pathPoints = records.map { it.record.relativeX to it.record.relativeY }.filter { it.first != null }
                        .map { it.first!! to it.second!! },
                    modifier = Modifier.fillMaxWidth().height(if (showAnalytics) 400.dp else 200.dp)
                )
                IndoorPathLegend()
            } else {
                SessionMapView(
                    records = if (showAnalytics) filteredRecords.map { it.record } else records.map { it.record },
                    displayMode = mapDisplayMode,
                    showLegend = showAnalytics,
                    modifier = Modifier.fillMaxWidth().height(if (showAnalytics) 400.dp else 200.dp)
                )
            }

            if (showAnalytics) {
                Surface(
                    tonalElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            OutlinedButton(
                                onClick = { showMapModeMenu = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(mapDisplayMode.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Spacer(Modifier.width(4.dp))
                                Icon(Icons.Default.ArrowDropDown, contentDescription = "Map mode")
                            }
                            DropdownMenu(
                                expanded = showMapModeMenu,
                                onDismissRequest = { showMapModeMenu = false }
                            ) {
                                MapDisplayMode.entries.forEach { mode ->
                                    DropdownMenuItem(
                                        text = { Text(mode.label) },
                                        onClick = {
                                            viewModel.setMapDisplayMode(mode)
                                            showMapModeMenu = false
                                        }
                                    )
                                }
                            }
                        }
                        if (availableSimSlots.size > 1) {
                            Box(modifier = Modifier.weight(1f)) {
                                OutlinedButton(
                                    onClick = { showSimMenu = true },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    val simLabel = selectedSim?.let { "SIM ${it + 1}" } ?: "All SIMs"
                                    Text(simLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Spacer(Modifier.width(4.dp))
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = "SIM filter")
                                }
                                DropdownMenu(
                                    expanded = showSimMenu,
                                    onDismissRequest = { showSimMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("All SIMs") },
                                        onClick = {
                                            viewModel.setSimFilter(null)
                                            showSimMenu = false
                                        }
                                    )
                                    availableSimSlots.forEach { slot ->
                                        DropdownMenuItem(
                                            text = { Text("SIM ${slot + 1}") },
                                            onClick = {
                                                viewModel.setSimFilter(slot)
                                                showSimMenu = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            ActionButtonsRow(
                showAnalytics = showAnalytics,
                showMoreMenu = showMoreMenu,
                onToggleAnalytics = { viewModel.toggleAnalytics() },
                onReplay = onOpenReplay,
                onResplit = { viewModel.batchResplit(sessionId) },
                onExportCsv = { viewModel.exportCsv() },
                onExportGeoJson = { viewModel.exportGeoJson() },
                onDeleteClick = { showDeleteConfirm = true },
                onMoreClick = { showMoreMenu = true },
                onDismissMore = { showMoreMenu = false }
            )

            if (showAnalytics) {
                AnalyticsPanel(
                    analytics = analytics,
                    speedTestAnalytics = speedTestAnalytics,
                    modifier = Modifier.weight(1f).fillMaxWidth()
                )
            } else {
                MarkersSection(
                    markers = markers,
                    onEdit = { marker ->
                        editingMarker = marker
                        showMarkerDialog = true
                    },
                    onDelete = { viewModel.deleteMarker(it.id) }
                )
                RecordsList(
                    allGrouped = allGrouped,
                    visibleWindow = visibleWindow,
                    onRecordClick = { viewModel.selectRecord(it) },
                    onUpdateVisibleWindow = { first, last ->
                        viewModel.updateVisibleWindow(first, last)
                    },
                    isIndoor = isIndoor,
                    isTunnel = isTunnel,
                    modifier = Modifier.weight(1f).fillMaxWidth()
                )
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete this session?") },
            text = { Text("This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteSession(sessionId)
                        onNavigateBack()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    val selected = selectedRecord
    if (selected != null) {
        RecordDetailSheet(
            wrapper = selected,
            onDismiss = { viewModel.selectRecord(null) }
        )
    }

    if (showMarkerDialog) {
        MarkerDialog(
            marker = editingMarker,
            loadRecentLabels = { viewModel.getRecentLabels(it) },
            onDismiss = { showMarkerDialog = false },
            onSave = { type, label ->
                editingMarker?.let { marker ->
                    viewModel.editMarker(marker.id, type, label)
                }
            },
            onDelete = editingMarker?.let { marker ->
                {
                    viewModel.deleteMarker(marker.id)
                    showMarkerDialog = false
                }
            } ?: {}
        )
    }
}

@Composable
private fun MarkersSection(
    markers: List<SessionMarkerEntity>,
    onEdit: (SessionMarkerEntity) -> Unit,
    onDelete: (SessionMarkerEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    if (markers.isEmpty()) return
    var expanded by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    Surface(
        tonalElevation = 1.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Markers (${markers.size})",
                    style = MaterialTheme.typography.titleSmall
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.ArrowDropDown else Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    modifier = Modifier.size(20.dp)
                )
            }
            if (expanded) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    markers.forEach { marker ->
                        MarkerRow(
                            marker = marker,
                            dateFormat = dateFormat,
                            onEdit = { onEdit(marker) },
                            onDelete = { onDelete(marker) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MarkerRow(
    marker: SessionMarkerEntity,
    dateFormat: SimpleDateFormat,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val typeColor = markerTypeColor(marker.type)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "#${marker.seq}",
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.width(40.dp)
        )
        SuggestionChip(
            onClick = {},
            label = { Text(marker.type.replace("_", " ")) },
            colors = SuggestionChipDefaults.suggestionChipColors(
                containerColor = typeColor.copy(alpha = 0.15f),
                labelColor = typeColor
            )
        )
        Text(
            text = marker.label ?: "—",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = dateFormat.format(Date(marker.timestamp)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        IconButton(onClick = onEdit) {
            Icon(Icons.Default.Edit, contentDescription = "Edit")
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
        }
    }
}

private fun markerTypeColor(type: String): Color = when (type) {
    "WAYPOINT" -> Color(0xFF2196F3)
    "SEGMENT_START" -> Color(0xFF4CAF50)
    "SEGMENT_END" -> Color(0xFFF44336)
    "STOP" -> Color(0xFFFF9800)
    "NOTE" -> Color(0xFF9E9E9E)
    else -> Color(0xFF9E9E9E)
}

@Composable
private fun TunnelPlaceholderPanel(
    session: com.cellrecorder.app.data.local.entity.SessionEntity?,
    records: List<CellRecordWithCaBands>,
    markers: List<SessionMarkerEntity>,
    modifier: Modifier = Modifier
) {
    val totalDurationSec = if (records.size >= 2) {
        val tsMin = records.minOf { it.record.timestamp }
        val tsMax = records.maxOf { it.record.timestamp }
        ((tsMax - tsMin) / 1000.0).coerceAtLeast(0.0)
    } else 0.0
    Surface(
        tonalElevation = 1.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Tunnel recording in progress",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            session?.name?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "Points: ${records.size}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Duration: ${String.format(Locale.US, "%.0f", totalDurationSec)} s",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Markers: ${markers.size}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "No GPS coordinates in tunnel mode. Use the Mark button to stamp landmarks.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ColumnHeadersRow(
    modifier: Modifier = Modifier,
    isIndoor: Boolean = false,
    isTunnel: Boolean = false
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "#",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(48.dp)
            )
            Text(
                text = "SIM",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "PLMN",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "Band",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "RSRP (dBm)",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "RSRQ (dB)",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            if (isIndoor) {
                Text(
                    text = "relX (m)",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "relY (m)",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
            } else if (isTunnel) {
                Text(
                    text = "src",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
            } else {
                Text(
                    text = "Ping (ms)",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun TimestampGroupRow(
    group: TimestampGroup,
    onRecordClick: (CellRecordWithCaBands) -> Unit,
    modifier: Modifier = Modifier,
    isIndoor: Boolean = false,
    isTunnel: Boolean = false
) {
    Column(modifier = modifier.padding(vertical = 4.dp)) {
        group.records.forEachIndexed { index, wrapper ->
            val isFirst = index == 0
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onRecordClick(wrapper) }
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isFirst) {
                    Text(
                        text = group.serialNumber.toString(),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        ),
                        modifier = Modifier.width(48.dp)
                    )
                } else {
                    Spacer(Modifier.width(48.dp))
                }
                SimRecordRow(wrapper = wrapper, modifier = Modifier.weight(1f), isIndoor = isIndoor, isTunnel = isTunnel)
            }
        }
    }
}

@Composable
private fun SimRecordRow(
    wrapper: CellRecordWithCaBands,
    modifier: Modifier = Modifier,
    isIndoor: Boolean = false,
    isTunnel: Boolean = false
) {
    val record = wrapper.record
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "SIM${(record.simSlotIndex ?: 0) + 1}",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.weight(1f)
        )
        Text(
            text = formatPlmn(record.mcc, record.mnc),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.weight(1f)
        )
        val bandText = if (wrapper.caBands.isNotEmpty()) {
            "${BandResolver.formatBand(record.bandNumber, record.earfcn, record.rat)}+${wrapper.caBands.size}"
        } else {
            BandResolver.formatBand(record.bandNumber, record.earfcn, record.rat)
        }
        Text(
            text = bandText,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = record.rsrp?.toString() ?: "---",
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.weight(1f)
        )
        Text(
            text = record.rsrq?.toString() ?: "---",
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.weight(1f)
        )
        if (isIndoor) {
            Text(
                text = record.relativeX?.let { String.format(Locale.US, "%.1f", it) } ?: "---",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.weight(1f)
            )
            Text(
                text = record.relativeY?.let { String.format(Locale.US, "%.1f", it) } ?: "---",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.weight(1f)
            )
        } else if (isTunnel) {
            Text(
                text = record.locationSource,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.weight(1f)
            )
        } else {
            Text(
                text = record.avgLatencyMs?.let { String.format(Locale.US, "%.0f ms", it) } ?: "---",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.weight(1f)
            )
        }
        if (record.rat.startsWith("5G_NSA") && record.anchorPci != null) {
            Text(
                text = "Anchor: B${record.anchorBandNumber ?: "?"} PCI ${record.anchorPci} RSRP ${record.anchorRsrp?.toString() ?: "---"}",
                style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.primary),
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

private fun formatPlmn(mcc: String?, mnc: String?): String = com.cellrecorder.app.ui.shared.formatPlmn(mcc, mnc)

@Composable
private fun ActionButtonsRow(
    showAnalytics: Boolean,
    showMoreMenu: Boolean,
    onToggleAnalytics: () -> Unit,
    onReplay: () -> Unit,
    onResplit: () -> Unit,
    onExportCsv: () -> Unit,
    onExportGeoJson: () -> Unit,
    onDeleteClick: () -> Unit,
    onMoreClick: () -> Unit,
    onDismissMore: () -> Unit
) {
    Surface(
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ActionButton(
                icon = if (showAnalytics) Icons.Default.ArrowDropDown else Icons.Default.BarChart,
                label = if (showAnalytics) "Data" else "Analytics",
                onClick = onToggleAnalytics
            )
            ActionButton(
                icon = Icons.Default.Replay,
                label = "Replay",
                onClick = onReplay
            )

            Box {
                ActionButton(
                    icon = Icons.Default.MoreVert,
                    label = "More",
                    onClick = onMoreClick
                )
                DropdownMenu(
                    expanded = showMoreMenu,
                    onDismissRequest = onDismissMore
                ) {
                    DropdownMenuItem(
                        text = { Text("Re-split") },
                        onClick = { onResplit(); onDismissMore() }
                    )
                    DropdownMenuItem(
                        text = { Text("Export CSV") },
                        onClick = { onExportCsv(); onDismissMore() }
                    )
                    DropdownMenuItem(
                        text = { Text("Export GeoJSON") },
                        onClick = { onExportGeoJson(); onDismissMore() }
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        onClick = { onDeleteClick(); onDismissMore() }
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecordsList(
    allGrouped: List<TimestampGroup>,
    visibleWindow: IntRange,
    onRecordClick: (CellRecordWithCaBands) -> Unit,
    onUpdateVisibleWindow: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
    isIndoor: Boolean = false,
    isTunnel: Boolean = false
) {
    val listState = rememberLazyListState()
    val measuredHeights = remember { mutableStateMapOf<Int, Int>() }
    val density = LocalDensity.current

    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo }
            .map { visible ->
                val dataIndices = visible
                    .map { it.index - 1 }
                    .filter { it >= 0 }
                if (dataIndices.isEmpty()) null
                else dataIndices.first() to dataIndices.last()
            }
            .distinctUntilChanged()
            .collect { range ->
                range?.let { (first, last) ->
                    onUpdateVisibleWindow(first, last)
                }
            }
    }

    LaunchedEffect(allGrouped) {
        measuredHeights.clear()
    }

    LazyColumn(
        modifier = modifier,
        state = listState
    ) {
        stickyHeader(key = "headers") {
            ColumnHeadersRow(modifier = Modifier.fillParentMaxWidth(), isIndoor = isIndoor, isTunnel = isTunnel)
        }

        items(
            count = allGrouped.size,
            key = { index -> allGrouped[index].serialNumber }
        ) { index ->
            val group = allGrouped[index]
            if (index in visibleWindow) {
                TimestampGroupRow(
                    group = group,
                    onRecordClick = onRecordClick,
                    isIndoor = isIndoor,
                    isTunnel = isTunnel,
                    modifier = Modifier.onGloballyPositioned { coordinates ->
                        measuredHeights[index] = coordinates.size.height
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
            } else {
                val heightPx = measuredHeights[index]
                val placeholderHeight = if (heightPx != null) {
                    with(density) { heightPx.toDp() }
                } else {
                    56.dp
                }
                Box(modifier = Modifier.fillMaxWidth().height(placeholderHeight))
            }
        }
    }
}