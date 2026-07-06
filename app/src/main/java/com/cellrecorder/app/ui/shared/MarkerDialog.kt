package com.cellrecorder.app.ui.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import com.cellrecorder.app.data.local.entity.RecentMarkerLabelEntity
import com.cellrecorder.app.data.local.entity.SessionMarkerEntity
import androidx.compose.runtime.produceState
import com.cellrecorder.app.domain.model.MarkerType

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MarkerDialog(
    marker: SessionMarkerEntity?,
    loadRecentLabels: suspend (MarkerType) -> List<RecentMarkerLabelEntity>,
    onDismiss: () -> Unit,
    onSave: (type: MarkerType, label: String?) -> Unit,
    onDelete: () -> Unit = {}
) {
    val isEdit = marker != null
    val initialType = remember(marker) {
        marker?.let { MarkerType.fromStorageString(it.type) } ?: MarkerType.NOTE
    }

    var selectedType by remember(marker) { mutableStateOf(initialType) }
    var labelText by remember(marker) { mutableStateOf(marker?.label ?: "") }
    val showLabelField by remember { derivedStateOf { selectedType in labelEnabledTypes } }
    val focusRequester = remember { FocusRequester() }
    val recentLabels by produceState(initialValue = emptyList<RecentMarkerLabelEntity>(), selectedType) {
        value = loadRecentLabels(selectedType)
    }

    LaunchedEffect(selectedType) {
        if (selectedType in labelEnabledTypes) {
            focusRequester.requestFocus()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEdit) "Edit Marker" else "New Marker") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Type", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    MarkerType.entries.forEach { type ->
                        FilterChip(
                            selected = selectedType == type,
                            onClick = { selectedType = type },
                            label = { Text(type.displayName()) }
                        )
                    }
                }

                if (showLabelField) {
                    Spacer(Modifier.height(8.dp))
                    if (recentLabels.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            recentLabels.forEach { recent ->
                                AssistChip(
                                    onClick = { labelText = recent.label },
                                    label = { Text(recent.label) }
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                    OutlinedTextField(
                        value = labelText,
                        onValueChange = { labelText = it },
                        label = { Text("Label") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val finalLabel = if (selectedType in labelEnabledTypes) {
                        labelText.trim().takeIf { it.isNotBlank() }
                    } else null
                    onSave(selectedType, finalLabel)
                    onDismiss()
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            Row {
                if (isEdit) {
                    TextButton(onClick = { onDelete(); onDismiss() }) {
                        Text("Delete")
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}

private val labelEnabledTypes = setOf(MarkerType.WAYPOINT, MarkerType.STOP, MarkerType.NOTE)

private fun MarkerType.displayName(): String {
    val base = storageString
        .replace("_", " ")
        .lowercase()
        .replaceFirstChar { it.uppercase() }
    return when (this) {
        MarkerType.SEGMENT_START -> "Segment Start"
        MarkerType.SEGMENT_END -> "Segment End"
        else -> base
    }
}
