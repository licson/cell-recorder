package com.cellrecorder.app.ui.sessionlist

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cellrecorder.app.domain.model.SessionSummary
import com.cellrecorder.app.ui.shared.TooltipIconButton
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SessionListScreen(
    onStartRecording: (Long) -> Unit,
    onOpenSession: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: SessionListViewModel = hiltViewModel()
) {
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val selectedIds by viewModel.selectedIds.collectAsStateWithLifecycle()
    val inSelectionMode by viewModel.selectionMode.collectAsStateWithLifecycle()
    var showCreateDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<SessionSummary?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showExportFormatDialog by remember { mutableStateOf(false) }
    var pendingExportFormat by remember { mutableStateOf<String?>(null) }
    var assignSimTarget by remember { mutableStateOf<SessionSummary?>(null) }
    var deleteTarget by remember { mutableStateOf<SessionSummary?>(null) }
    var showImportFormatDialog by remember { mutableStateOf(false) }
    val importSummary by viewModel.importSummary.collectAsStateWithLifecycle()
    val createdSessionId by viewModel.createdSessionId.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val importFilePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val inputStream = context.contentResolver.openInputStream(it)
                val content = inputStream?.bufferedReader()?.readText() ?: return@let
                inputStream.close()
                val fileName = it.lastPathSegment?.substringAfterLast("/") ?: "import"
                viewModel.importFile(content, fileName)
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to read file: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val dirPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let { treeUri ->
            val format = pendingExportFormat ?: return@let
            val sessionsToExport = sessions.filter { it.id in selectedIds }
            viewModel.exportSelected(sessionsToExport, format, treeUri)
        }
    }

    LaunchedEffect(createdSessionId) {
        createdSessionId?.let { id ->
            viewModel.clearCreatedFlag()
            onStartRecording(id)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    if (inSelectionMode) {
                        Text("${selectedIds.size} selected")
                    } else {
                        Text("Cell Recorder")
                    }
                },
                navigationIcon = {
                    if (inSelectionMode) {
                        TooltipIconButton(tooltip = "Exit selection", onClick = { viewModel.clearSelection() }) {
                            Icon(Icons.Default.Close, contentDescription = "Exit selection")
                        }
                    }
                },
                actions = {
                    if (inSelectionMode) {
                    } else {
                        TooltipIconButton(tooltip = "Select sessions", onClick = {
                            if (selectedIds.isEmpty()) {
                                sessions.forEach { viewModel.toggleSelection(it.id) }
                            } else {
                                viewModel.clearSelection()
                            }
                        }) {
                            Icon(Icons.Default.Checklist, contentDescription = "Select")
                        }
                        TooltipIconButton(tooltip = "Import recording", onClick = { showImportFormatDialog = true }) {
                            Icon(Icons.Default.FileOpen, contentDescription = "Import")
                        }
                        TooltipIconButton(tooltip = "Settings", onClick = onOpenSettings) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (!inSelectionMode) {
                FloatingActionButton(onClick = { showCreateDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "New Session")
                }
            }
        },
        bottomBar = {
            if (inSelectionMode) {
                Surface(tonalElevation = 4.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        OutlinedButton(
                            onClick = { showExportFormatDialog = true }
                        ) {
                            Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Export")
                        }
                        OutlinedButton(
                            onClick = { showDeleteConfirm = true },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Delete")
                        }
                    }
                }
            }
        }
    ) { padding ->
        if (sessions.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No sessions yet.\nTap + to start recording.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            val grouped = remember(sessions) {
                sessions.sortedByDescending { it.createdAt }
                    .groupBy { it.primarySimSlot }
                    .let { map ->
                        val order = listOf(null) + (0..1).toList()
                        order.mapNotNull { slot ->
                            val list = map[slot] ?: return@mapNotNull null
                            slot to list
                        }
                    }
            }

            val listPadding = if (inSelectionMode) padding else PaddingValues(
                    top = padding.calculateTopPadding()
                )
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(listPadding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                grouped.forEach { (slot, groupSessions) ->
                    stickyHeader {
                        SimSectionHeader(slot = slot)
                    }
                    items(groupSessions, key = { it.id }) { session ->
                        SessionCard(
                            session = session,
                            isSelected = session.id in selectedIds,
                            selectionMode = inSelectionMode,
                            onClick = {
                                if (inSelectionMode) {
                                    viewModel.toggleSelection(session.id)
                                } else {
                                    onOpenSession(session.id)
                                }
                            },
                            onRename = { renameTarget = it },
                            onDeleteRequest = { deleteTarget = it },
                            onSetSimSlot = { assignSimTarget = it },
                            onToggleSelection = { viewModel.toggleSelection(session.id) }
                        )
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateSessionDialog(
            suggestedName = generatePresetName(sessions),
            onDismiss = { showCreateDialog = false },
            onConfirm = { name ->
                viewModel.createSession(name)
                showCreateDialog = false
            }
        )
    }

    renameTarget?.let { session ->
        RenameDialog(
            currentName = session.name,
            onDismiss = { renameTarget = null },
            onConfirm = { newName ->
                viewModel.renameSession(session.id, newName)
                renameTarget = null
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete ${selectedIds.size} sessions?") },
            text = { Text("This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteSelected()
                        showDeleteConfirm = false
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

    deleteTarget?.let { session ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete \"${session.name}\"?") },
            text = { Text("This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteSession(session.id)
                        deleteTarget = null
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    assignSimTarget?.let { session ->
        SetSimSlotDialog(
            currentSlot = session.primarySimSlot,
            onDismiss = { assignSimTarget = null },
            onConfirm = { slot ->
                viewModel.updatePrimarySimSlot(session.id, slot)
                assignSimTarget = null
            }
        )
    }

    importSummary?.let { summary ->
        AlertDialog(
            onDismissRequest = { viewModel.clearImportSummary() },
            title = { Text("Import Complete") },
            text = {
                Column {
                    Text("Session: ${summary.sessionName}")
                    Spacer(Modifier.height(4.dp))
                    Text("Imported: ${summary.importedCount} records")
                    if (summary.errorCount > 0) {
                        Text("Skipped: ${summary.errorCount} rows", color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(8.dp))
                        Text("Details:", style = MaterialTheme.typography.labelSmall)
                        summary.errors.take(10).forEach { err ->
                            Text(
                                "Row ${err.line}: ${err.message}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        if (summary.errors.size > 10) {
                            Text("... and ${summary.errors.size - 10} more")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.clearImportSummary() }) {
                    Text("OK")
                }
            }
        )
    }

    if (showImportFormatDialog) {
        AlertDialog(
            onDismissRequest = { showImportFormatDialog = false },
            title = { Text("Import Format") },
            text = { Text("Choose the format of the file to import.") },
            confirmButton = {
                TextButton(onClick = {
                    showImportFormatDialog = false
                    importFilePicker.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "application/csv", "*/*"))
                }) {
                    Text("CSV")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showImportFormatDialog = false
                    importFilePicker.launch(arrayOf("application/geo+json", "application/json", "text/plain", "*/*"))
                }) {
                    Text("GeoJSON")
                }
            }
        )
    }

    if (showExportFormatDialog) {
        AlertDialog(
            onDismissRequest = { showExportFormatDialog = false },
            title = { Text("Export Format") },
            text = { Text("Choose format for the export files.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingExportFormat = "CSV"
                    showExportFormatDialog = false
                    dirPickerLauncher.launch(null)
                }) {
                    Text("CSV")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    pendingExportFormat = "GeoJSON"
                    showExportFormatDialog = false
                    dirPickerLauncher.launch(null)
                }) {
                    Text("GeoJSON")
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SessionCard(
    session: SessionSummary,
    isSelected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onRename: (SessionSummary) -> Unit,
    onDeleteRequest: (SessionSummary) -> Unit,
    onSetSimSlot: (SessionSummary) -> Unit,
    onToggleSelection: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = if (selectionMode) onToggleSelection else onClick,
                onLongClick = {
                    if (selectionMode) onToggleSelection
                    else showMenu = true
                }
            ),
        colors = if (isSelected) CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        ) else CardDefaults.cardColors()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = if (selectionMode) 4.dp else 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggleSelection() },
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = dateFormat.format(Date(session.createdAt)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                session.endedAt?.let { end ->
                    Text(
                        text = formatDuration(end, session.createdAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${session.pointCount} pts",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        if (!selectionMode) {
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(
                    text = { Text("Rename") },
                    onClick = { onRename(session); showMenu = false },
                    leadingIcon = { Icon(Icons.Default.Create, contentDescription = null) }
                )
                DropdownMenuItem(
                    text = { Text("Set SIM slot") },
                    onClick = { onSetSimSlot(session); showMenu = false },
                    leadingIcon = { Icon(Icons.Default.SimCard, contentDescription = null) }
                )
                DropdownMenuItem(
                    text = { Text("Delete") },
                    onClick = { onDeleteRequest(session); showMenu = false },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) }
                )
            }
        }
    }
}

@Composable
private fun SimSectionHeader(slot: Int?) {
    val label = when (slot) {
        0 -> "SIM 1"
        1 -> "SIM 2"
        else -> "Unknown"
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.SimCard,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

private fun generatePresetName(sessions: List<SessionSummary>): String {
    val base = "New Recording Session"
    val existingNames = sessions.map { it.name }.toSet()
    if (base !in existingNames) return base
    var counter = 2
    while (true) {
        val candidate = "$base $counter"
        if (candidate !in existingNames) return candidate
        counter++
    }
}

@Composable
private fun CreateSessionDialog(
    suggestedName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember(suggestedName) { mutableStateOf(suggestedName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Session") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Session Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.ifBlank { "Session ${System.currentTimeMillis() % 10000}" }) },
                enabled = name.isNotBlank()
            ) {
                Text("Start Recording")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun RenameDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf(currentName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename Session") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Session Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank()
            ) {
                Text("Rename")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun SetSimSlotDialog(
    currentSlot: Int?,
    onDismiss: () -> Unit,
    onConfirm: (Int?) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set Primary SIM Slot") },
        text = {
            Column {
                Text("Associate this recording with a SIM slot.", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = currentSlot == null,
                        onClick = { onConfirm(null); onDismiss() },
                        label = { Text("Unknown") }
                    )
                    FilterChip(
                        selected = currentSlot == 0,
                        onClick = { onConfirm(0) },
                        label = { Text("SIM 1") }
                    )
                    FilterChip(
                        selected = currentSlot == 1,
                        onClick = { onConfirm(1) },
                        label = { Text("SIM 2") }
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun formatDuration(end: Long, start: Long): String {
    val totalSec = (end - start) / 1000
    if (totalSec < 60) return "${totalSec}s"
    val min = totalSec / 60
    val sec = totalSec % 60
    if (min < 60) return "${min}m ${sec}s"
    val hours = min / 60
    val mins = min % 60
    return "${hours}h ${mins}m"
}