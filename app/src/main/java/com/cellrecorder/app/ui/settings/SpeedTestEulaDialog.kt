package com.cellrecorder.app.ui.settings

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri

@Composable
fun SpeedTestEulaDialog(
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDecline,
        title = {
            Text(
                text = "Speed Test",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Speed tests use Ookla's Speedtest.net infrastructure. By enabling speed tests, you agree to Speedtest.net's Terms of Use and Privacy Policy.",
                    style = MaterialTheme.typography.bodyMedium
                )

                Text(
                    text = "Each test uses approximately 5-15 MB of cellular data (download only) or 10-30 MB (with upload). With a 60-second interval, expect ~300-1800 MB per hour of recording.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Start
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, "https://www.speedtest.net/about/eula".toUri())
                            context.startActivity(intent)
                        }
                    ) {
                        Text("View Terms", style = MaterialTheme.typography.bodySmall)
                    }

                    TextButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, "https://www.speedtest.net/about/privacy".toUri())
                            context.startActivity(intent)
                        }
                    ) {
                        Text("View Privacy", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onAccept) {
                Text("Accept & Enable")
            }
        },
        dismissButton = {
            TextButton(onClick = onDecline) {
                Text("Decline")
            }
        }
    )
}