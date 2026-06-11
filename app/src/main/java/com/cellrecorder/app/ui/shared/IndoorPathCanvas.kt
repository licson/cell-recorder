package com.cellrecorder.app.ui.shared

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min

@Composable
fun IndoorPathCanvas(
    pathPoints: List<Pair<Double, Double>>,
    currentPosition: Pair<Double, Double>? = null,
    originPosition: Pair<Double, Double>? = null,
    driftRadiusM: Double = 0.0,
    discontinuityIndices: Set<Int> = emptySet(),
    signalColors: Map<Int, Color> = emptyMap(),
    modifier: Modifier = Modifier
) {
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF5F5F5))
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY
                )
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(0.1f, 10f)
                        offsetX += pan.x
                        offsetY += pan.y
                    }
                }
        ) {
            val bounds = pathBounds(pathPoints)
            val padding = 40f
            val canvasW = size.width - padding * 2
            val canvasH = size.height - padding * 2
            val rangeX = max(bounds.third.toFloat() - bounds.first.toFloat(), 1f)
            val rangeY = max(bounds.fourth.toFloat() - bounds.second.toFloat(), 1f)
            val pixelPerMeter = min(canvasW / rangeX, canvasH / rangeY).coerceAtMost(200f)
            val cx = padding + (bounds.first.toFloat() * -1 + 0f) * pixelPerMeter
            val cy = padding + (bounds.second.toFloat() * -1 + 0f) * pixelPerMeter

            fun toCanvas(p: Pair<Double, Double>): Offset {
                return Offset(
                    padding + (p.first.toFloat() - bounds.first.toFloat()) * pixelPerMeter,
                    padding + (p.second.toFloat() - bounds.second.toFloat()) * pixelPerMeter
                )
            }

            drawGrid(bounds, pixelPerMeter, padding)

            if (pathPoints.size > 1) {
                var segmentStart = 0
                for (i in 0 until pathPoints.size - 1) {
                    if (discontinuityIndices.contains(i)) {
                        segmentStart = i + 1
                        continue
                    }
                    val from = toCanvas(pathPoints[i])
                    val to2 = toCanvas(pathPoints[i + 1])
                    val color = signalColors[i] ?: Color(0xFF2196F3)
                    drawLine(
                        color = color,
                        start = from,
                        end = to2,
                        strokeWidth = 4f
                    )
                }

                for (discIdx in discontinuityIndices) {
                    if (discIdx < pathPoints.size) {
                        val pt = toCanvas(pathPoints[discIdx])
                        drawCircle(Color(0xFFFF9800), 8f, pt)
                        drawCircle(Color.White, 4f, pt)
                    }
                }
            }

            originPosition?.let { origin ->
                val o = toCanvas(origin)
                drawCircle(Color(0xFF4CAF50), 10f, o)
                drawCircle(Color.White, 4f, o)
                val crossSize = 6f
                drawLine(Color(0xFF4CAF50), Offset(o.x - crossSize, o.y), Offset(o.x + crossSize, o.y), strokeWidth = 2f)
                drawLine(Color(0xFF4CAF50), Offset(o.x, o.y - crossSize), Offset(o.x, o.y + crossSize), strokeWidth = 2f)
            }

            currentPosition?.let { pos ->
                val pt = toCanvas(pos)
                if (driftRadiusM > 0) {
                    val radiusPx = driftRadiusM.toFloat() * pixelPerMeter
                    drawCircle(
                        color = Color(0x33FF0000),
                        radius = radiusPx,
                        center = pt
                    )
                    drawCircle(
                        color = Color(0x66FF0000),
                        radius = radiusPx,
                        center = pt,
                        style = Stroke(width = 2f)
                    )
                }
                drawCircle(Color(0xFF2196F3), 12f, pt)
                drawCircle(Color.White, 5f, pt)
            }
        }
    }
}

private fun pathBounds(points: List<Pair<Double, Double>>): Quadruple<Double, Double, Double, Double> {
    if (points.isEmpty()) return Quadruple(-10.0, -10.0, 10.0, 10.0)
    var minX = Double.MAX_VALUE
    var minY = Double.MAX_VALUE
    var maxX = -Double.MAX_VALUE
    var maxY = -Double.MAX_VALUE
    for (p in points) {
        if (p.first < minX) minX = p.first
        if (p.first > maxX) maxX = p.first
        if (p.second < minY) minY = p.second
        if (p.second > maxY) maxY = p.second
    }
    val margin = 2.0
    return Quadruple(minX - margin, minY - margin, maxX + margin, maxY + margin)
}

private class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

private fun DrawScope.drawGrid(
    bounds: Quadruple<Double, Double, Double, Double>,
    pixelPerMeter: Float,
    padding: Float
) {
    val gridColor = Color(0x22000000)
    val gridSpacing = when {
        pixelPerMeter > 100 -> 1f
        pixelPerMeter > 50 -> 2f
        pixelPerMeter > 20 -> 5f
        else -> 10f
    }
    var gx = (bounds.first / gridSpacing).toInt() * gridSpacing
    while (gx <= bounds.third) {
        val x = padding + ((gx - bounds.first).toFloat()) * pixelPerMeter
        drawLine(gridColor, Offset(x, 0f), Offset(x, Float.MAX_VALUE), strokeWidth = 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f)))
        gx += gridSpacing
    }
    var gy = (bounds.second / gridSpacing).toInt() * gridSpacing
    while (gy <= bounds.fourth) {
        val y = padding + ((gy - bounds.second).toFloat()) * pixelPerMeter
        drawLine(gridColor, Offset(0f, y), Offset(Float.MAX_VALUE, y), strokeWidth = 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f)))
        gy += gridSpacing
    }
}

@Composable
fun TrackingConfidenceIndicator(
    trackingConfidence: String,
    timeSinceResetMs: Long?,
    stepCount: Int?,
    driftM: Double?,
    modifier: Modifier = Modifier
) {
    val bgColor = when (trackingConfidence) {
        "Confident" -> Color(0xFF4CAF50)
        "Degrading" -> Color(0xFFFF9800)
        else -> Color(0xFFF44336)
    }
    val timeStr = timeSinceResetMs?.let { ms ->
        val sec = ms / 1000
        "%02d:%02d".format(sec / 60, sec % 60)
    } ?: "---"

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(bgColor.copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Text(
            text = "$trackingConfidence · ${timeStr} since reset · ${stepCount ?: 0} steps · ${String.format("%.1f", driftM ?: 0.0)}m drift",
            style = MaterialTheme.typography.bodySmall,
            color = bgColor
        )
    }
}