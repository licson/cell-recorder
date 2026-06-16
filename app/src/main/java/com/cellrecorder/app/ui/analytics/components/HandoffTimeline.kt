package com.cellrecorder.app.ui.analytics.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cellrecorder.app.domain.analytics.model.AnomalyFlag
import com.cellrecorder.app.domain.analytics.model.AnomalyType
import com.cellrecorder.app.domain.analytics.model.CoverageGap
import com.cellrecorder.app.domain.analytics.model.HandoffEvent
import com.cellrecorder.app.domain.analytics.model.HandoffType
import com.cellrecorder.app.domain.analytics.model.TimelineSegment
import com.cellrecorder.app.ui.detail.ratColor

private val TIMELINE_HEIGHT = 56.dp
private const val PX_PER_SEC = 2f
private const val LABEL_AREA_PX = 18f

@Composable
fun HandoffTimeline(
    segments: List<TimelineSegment>,
    handoffs: List<HandoffEvent>,
    anomalies: List<AnomalyFlag>,
    gaps: List<CoverageGap>,
    modifier: Modifier = Modifier
) {
    if (segments.isEmpty()) return

    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val labelColor = MaterialTheme.colorScheme.onSurface
    val textStyle = remember { TextStyle(color = labelColor.copy(alpha = 0.7f), fontSize = 10.sp) }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Session Timeline",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        val totalMs = segments.last().endTime - segments.first().startTime
        val totalSec = (totalMs / 1000).coerceAtLeast(1)
        val canvasWidthDp = (totalSec * PX_PER_SEC).coerceAtLeast(400f)

        val tickIntervalSec = when {
            totalSec <= 60 -> 1L
            totalSec <= 300 -> 10L
            totalSec <= 1800 -> 20L
            else -> 30L
        }

        val numTicks = (totalSec / tickIntervalSec).toInt().coerceAtLeast(1)
        val spacingPx = with(density) { (canvasWidthDp / numTicks).dp.toPx() }
        val skipFactor = when {
            spacingPx < 12 -> 5
            spacingPx < 20 -> 3
            spacingPx < 30 -> 2
            else -> 1
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        ) {
            Canvas(
                modifier = Modifier
                    .width(canvasWidthDp.dp.coerceAtMost(8000.dp))
                    .height(TIMELINE_HEIGHT)
            ) {
                val w = size.width
                val canvasH = size.height
                val contentH = canvasH - LABEL_AREA_PX
                val startMs = segments.first().startTime
                val durationMs = (segments.last().endTime - startMs).coerceAtLeast(1L)

                fun xFromTime(ts: Long): Float =
                    ((ts - startMs).toFloat() / durationMs * w).coerceIn(0f, w)

                // Background RAT segments (content area only)
                segments.forEach { seg ->
                    val left = xFromTime(seg.startTime)
                    val right = xFromTime(seg.endTime)
                    drawRect(
                        color = ratColor(seg.rat).copy(alpha = 0.6f),
                        topLeft = Offset(left, 0f),
                        size = Size((right - left).coerceAtLeast(1f), contentH)
                    )
                }

                // Coverage gaps (content area only)
                gaps.forEach { gap ->
                    val left = xFromTime(gap.startTime)
                    val right = xFromTime(gap.endTime)
                    drawRect(
                        color = Color.Red.copy(alpha = 0.25f),
                        topLeft = Offset(left, 0f),
                        size = Size((right - left).coerceAtLeast(2f), contentH)
                    )
                }

                // Handoff markers (content area only)
                handoffs.forEach { ev ->
                    val x = xFromTime(ev.timestamp)
                    val color = when (ev.type) {
                        HandoffType.INTRA_SITE_PCI_CHANGE -> Color(0xFF42A5F5)
                        HandoffType.RAT_CHANGE -> Color(0xFFAB47BC)
                        HandoffType.BAND_CHANGE -> Color(0xFFFFA726)
                        HandoffType.NSA_ANCHOR_CHANGE -> Color(0xFFEF5350)
                        HandoffType.UNKNOWN_CELL_CHANGE -> Color(0xFF78909C)
                        HandoffType.INTER_SITE -> if (ev.latencyDeltaMs != null && ev.latencyDeltaMs!! > 0) Color.Red
                            else if (ev.packetLossDeltaPct != null && ev.packetLossDeltaPct!! > 0) Color.Red
                            else Color(0xFF66BB6A)
                    }
                    drawLine(
                        color = color,
                        start = Offset(x, 0f),
                        end = Offset(x, contentH),
                        strokeWidth = 2f
                    )
                }

                // Anomaly markers (content area only)
                anomalies.forEach { a ->
                    val x = xFromTime(a.timestamp)
                    val dotColor = when (a.type) {
                        AnomalyType.RSRP_DROP -> Color(0xFFFF9800)
                        AnomalyType.LATENCY_SPIKE -> Color.Red
                        AnomalyType.PCI_FLAP -> Color(0xFF9C27B0)
                        AnomalyType.MISSING_PING_CLUSTER -> Color(0xFF607D8B)
                    }
                    drawCircle(
                        color = dotColor,
                        radius = 5f,
                        center = Offset(x, contentH * 0.35f)
                    )
                }

                // Separator line between content and labels
                drawLine(
                    color = labelColor.copy(alpha = 0.15f),
                    start = Offset(0f, contentH),
                    end = Offset(w, contentH),
                    strokeWidth = 0.5f
                )

                // Tick marks and time labels (label area only)
                val tickBottom = canvasH - 3f
                val tickTop = contentH + 3f

                var tickTime = startMs + tickIntervalSec * 1000
                var tickIndex = 0
                while (tickTime < segments.last().endTime) {
                    tickIndex++
                    val x = xFromTime(tickTime)

                    drawLine(
                        color = labelColor.copy(alpha = 0.4f),
                        start = Offset(x, tickTop),
                        end = Offset(x, tickBottom),
                        strokeWidth = 1f
                    )

                    if (tickIndex % skipFactor == 0) {
                        val elapsedSec = (tickTime - startMs) / 1000
                        val label = when {
                            elapsedSec < 60 -> "${elapsedSec}s"
                            else -> {
                                val mm = elapsedSec / 60
                                val ss = elapsedSec % 60
                                "${mm}:${"%02d".format(ss)}"
                            }
                        }
                        val textResult = textMeasurer.measure(text = label, style = textStyle)
                        val textY = tickTop + (tickBottom - tickTop - textResult.size.height) / 2f
                        drawText(
                            textLayoutResult = textResult,
                            topLeft = Offset(x - textResult.size.width / 2f, textY)
                        )
                    }

                    tickTime += tickIntervalSec * 1000
                }
            }
        }
    }
}