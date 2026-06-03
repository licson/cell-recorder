package com.cellrecorder.app.ui.shared

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@Composable
fun PermissionRationaleDialog(
    onGrant: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Permissions Required") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                PermissionItem(
                    icon = Icons.Default.LocationOn,
                    title = "Fine Location",
                    description = "Required to record GPS coordinates alongside cell tower data during recording."
                )
                PermissionItem(
                    icon = Icons.Default.LocationOn,
                    title = "Background Location",
                    description = "Required to continue recording when the app is not in the foreground, such as when your phone is in your pocket."
                )
                PermissionItem(
                    icon = Icons.Default.Phone,
                    title = "Phone State",
                    description = "Required to read cell tower information including signal strength, Cell ID, RAT type, and frequency bands."
                )
                PermissionItem(
                    icon = Icons.Default.Notifications,
                    title = "Notifications",
                    description = "Required to show a persistent notification while recording is active."
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onGrant) {
                Text("Grant Permissions")
            }
        }
    )
}

@Composable
private fun PermissionItem(
    icon: ImageVector,
    title: String,
    description: String
) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}