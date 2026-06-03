package com.cellrecorder.app.ui.detail.analytics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cellrecorder.app.domain.analytics.model.HistogramBin
import androidx.compose.ui.unit.sp

private val histogramColors = listOf(
    Color(0xFF4CAF50),
    Color(0xFF00BCD4),
    Color(0xFFFF9800),
    Color(0xFFF44336)
)

@Composable
fun SignalHistogram(
    title: String,
    bins: List<HistogramBin>,
    modifier: Modifier = Modifier
) {
    if (bins.isEmpty()) return

    val maxCount = bins.maxOf { it.count }.coerceAtLeast(1)
    val barHeight = 28.dp
    val spacing = 8.dp

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        bins.forEachIndexed { index, bin ->
            val fraction = bin.count.toFloat() / maxCount
            val color = histogramColors[index % histogramColors.size]

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(barHeight),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = bin.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(52.dp)
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(barHeight)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val barW = size.width * fraction
                        drawRoundRect(
                            color = color,
                            topLeft = Offset.Zero,
                            size = Size(barW.coerceAtLeast(4f), size.height),
                            cornerRadius = CornerRadius(4f, 4f)
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = bin.countLabel,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.width(80.dp)
                )
            }
            Spacer(Modifier.height(spacing))
        }
    }
}