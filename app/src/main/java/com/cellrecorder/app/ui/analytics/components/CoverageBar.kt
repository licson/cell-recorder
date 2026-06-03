package com.cellrecorder.app.ui.analytics.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cellrecorder.app.domain.analytics.model.RatCoverage
import com.cellrecorder.app.ui.detail.ratColor

@Composable
fun CoverageBar(
    coverage: List<RatCoverage>,
    modifier: Modifier = Modifier
) {
    if (coverage.isEmpty()) return

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Coverage by RAT",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val total = coverage.sumOf { it.percentage }
                    if (total <= 0) return@Canvas
                    var left = 0f
                    coverage.forEach { item ->
                        val fraction = (item.percentage / total).toFloat()
                        val segWidth = size.width * fraction
                        drawRect(
                            color = ratColor(item.rat),
                            topLeft = Offset(left, 0f),
                            size = Size(segWidth, size.height)
                        )
                        left += segWidth
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            val legendItems = coverage.filter { it.percentage > 0 }
            val row1 = legendItems.take(3)
            val row2 = legendItems.drop(3)
            if (row1.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    row1.forEach { item ->
                        LegendItem(
                            color = ratColor(item.rat),
                            label = item.rat,
                            percentage = item.percentage,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    repeat(3 - row1.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            if (row2.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    row2.forEach { item ->
                        LegendItem(
                            color = ratColor(item.rat),
                            label = item.rat,
                            percentage = item.percentage,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    repeat(3 - row2.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun LegendItem(
    color: androidx.compose.ui.graphics.Color,
    label: String,
    percentage: Double,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = "$label ${"%.1f".format(percentage)}%",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}