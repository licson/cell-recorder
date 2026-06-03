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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cellrecorder.app.domain.analytics.model.CorrelationBin

private val simColors = listOf(
    Color(0xFF1976D2),
    Color(0xFF388E3C),
    Color(0xFFE64A19),
    Color(0xFF7B1FA2)
)

@Composable
fun CorrelationChart(
    title: String,
    yAxisUnit: String,
    bins: List<CorrelationBin>,
    modifier: Modifier = Modifier
) {
    if (bins.isEmpty()) return

    val allSims = bins.firstOrNull()?.values?.map { it.simSlotIndex }?.sorted() ?: emptyList()
    if (allSims.isEmpty()) return

    val allValues = bins.flatMap { bin -> bin.values.mapNotNull { it.value } }
    val maxVal = allValues.maxOrNull() ?: 1.0
    val chartHeight = 150.dp
    val yAxisWidth = 48.dp
    val gridSteps = listOf(0.0, 0.25, 0.5, 0.75, 1.0)

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "$title ($yAxisUnit)",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        Row {
            Column(
                modifier = Modifier
                    .width(yAxisWidth)
                    .height(chartHeight),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                gridSteps.reversed().forEach { fraction ->
                    val value = maxVal * fraction
                    Text(
                        text = if (value >= 10) "${value.toInt()}" else "${"%.1f".format(value)}",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(Modifier.width(4.dp))

            Box(modifier = Modifier.weight(1f).height(chartHeight)) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val chartRight = size.width
                    val chartBottom = size.height

                    gridSteps.forEach { fraction ->
                        val y = chartBottom - (fraction * chartBottom).toFloat()
                        drawLine(
                            color = Color.Gray.copy(alpha = 0.2f),
                            start = Offset(0f, y),
                            end = Offset(chartRight, y)
                        )
                    }

                    val totalGroups = bins.size
                    val simCount = allSims.size
                    val groupWidth = chartRight / totalGroups
                    val barWidth = (groupWidth / (simCount + 1)).coerceAtMost(24.dp.toPx())
                    val gap = (groupWidth - barWidth * simCount) / (simCount + 1)

                    bins.forEachIndexed { binIdx, bin ->
                        allSims.forEachIndexed { simIdx, sim ->
                            val value = bin.values.find { it.simSlotIndex == sim }?.value
                            if (value != null && maxVal > 0) {
                                val barH = (value / maxVal * chartBottom).toFloat().coerceAtLeast(2f)
                                val left = binIdx * groupWidth + gap + simIdx * (barWidth + gap)
                                drawRoundRect(
                                    color = simColors[simIdx % simColors.size],
                                    topLeft = Offset(left, chartBottom - barH),
                                    size = Size(barWidth, barH),
                                    cornerRadius = CornerRadius(3f, 3f)
                                )
                            }
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            bins.forEach { bin ->
                Text(
                    text = bin.label,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(60.dp)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            allSims.forEachIndexed { idx, sim ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .padding(end = 2.dp)
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawRoundRect(
                                color = simColors[idx % simColors.size],
                                cornerRadius = CornerRadius(2f, 2f)
                            )
                        }
                    }
                    Text(
                        text = "SIM ${sim + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}