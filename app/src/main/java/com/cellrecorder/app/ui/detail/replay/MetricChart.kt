package com.cellrecorder.app.ui.detail.replay

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@Composable
fun MetricChart(
    label: String,
    values: List<Float?>,
    unit: String,
    currentIndex: Int,
    color: Color,
    fixedMin: Float? = null,
    fixedMax: Float? = null,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
        ) {
            val validValues = remember(values) { values.filterNotNull() }
            val minVal = remember(validValues, fixedMin) { fixedMin ?: (validValues.minOrNull() ?: 0f) }
            val maxVal = remember(validValues, fixedMax) { fixedMax ?: (validValues.maxOrNull() ?: 1f) }
            val range = remember(minVal, maxVal) { (maxVal - minVal).coerceAtLeast(1f) }
            val density = LocalDensity.current
            val labelPaint = remember {
                android.graphics.Paint().apply {
                    this.color = android.graphics.Color.GRAY
                    textSize = with(density) { 9.sp.toPx() }
                    isAntiAlias = true
                }
            }

            Canvas(modifier = Modifier.fillMaxSize()) {
                if (values.isEmpty() || validValues.isEmpty()) {
                    val text = "No data"
                    val textWidth = labelPaint.measureText(text)
                    drawContext.canvas.nativeCanvas.drawText(
                        text, (size.width - textWidth) / 2f, size.height / 2f + labelPaint.textSize / 3f, labelPaint
                    )
                    return@Canvas
                }

                val padding = 8f
                val rw = size.width - padding * 2
                val rh = size.height - padding * 2

                val count = values.size
                if (count < 2) return@Canvas
                val step = rw / (count - 1)

                for (i in 0..4) {
                    val y = padding + rh * i / 4
                    drawLine(
                        Color.LightGray.copy(alpha = 0.3f),
                        Offset(padding, y),
                        Offset(size.width - padding, y),
                        strokeWidth = 1f
                    )
                }

                val path = Path()
                var started = false
                for (i in values.indices) {
                    val v = values[i] ?: continue
                    val x = padding + step * i
                    val y = padding + rh * (1f - (v - minVal) / range)
                    if (!started) {
                        path.moveTo(x, y)
                        started = true
                    } else {
                        path.lineTo(x, y)
                    }
                }
                drawPath(path, color, style = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round))

                if (currentIndex in values.indices && currentIndex >= 0) {
                    val cx = padding + step * currentIndex
                    drawLine(
                        color.copy(alpha = 0.5f),
                        Offset(cx, padding),
                        Offset(cx, padding + rh),
                        strokeWidth = 1.5f
                    )
                    values[currentIndex]?.let { cv ->
                        val cy = padding + rh * (1f - (cv - minVal) / range)
                        drawCircle(color, radius = 4f, center = Offset(cx, cy))
                        drawCircle(Color.White, radius = 2f, center = Offset(cx, cy))

                        val currentText = "${cv.roundToInt()}$unit"
                        val textW = labelPaint.measureText(currentText)
                        drawContext.canvas.nativeCanvas.drawText(
                            currentText, cx - textW / 2f, padding + rh + 12f, labelPaint
                        )
                    }
                }

                val minText = "${minVal.roundToInt()}$unit"
                val maxText = "${maxVal.roundToInt()}$unit"
                labelPaint.color = android.graphics.Color.GRAY
                drawContext.canvas.nativeCanvas.drawText(minText, padding, padding + rh + 12f, labelPaint)
                drawContext.canvas.nativeCanvas.drawText(
                    maxText, padding, padding + labelPaint.textSize + 2f, labelPaint
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Default),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = unit,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}