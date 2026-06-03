package com.cellrecorder.app.ui.shared

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
fun PermissionDeniedDialog(
    onOpenSettings: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Permissions Required") },
        text = {
            Text(
                "Some permissions were permanently denied. " +
                        "The app cannot function without them. " +
                        "Please enable them in App Settings."
            )
        },
        confirmButton = {
            TextButton(onClick = onOpenSettings) {
                Text("Open Settings")
            }
        }
    )
}