package com.cellrecorder.app.ui.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.util.TypedValue
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.cellrecorder.app.data.local.entity.CellRecordEntity
import com.cellrecorder.app.ui.detail.MapDisplayMode
import com.cellrecorder.app.ui.detail.formatCellId
import com.cellrecorder.app.ui.detail.packetLossColorArgb
import com.cellrecorder.app.ui.detail.ratColorArgb
import com.cellrecorder.app.ui.detail.rsrpColorArgb
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

private val rsrpLegendColors = listOf(
    0xFF4CAF50.toInt(),
    0xFF00BCD4.toInt(),
    0xFFFF9800.toInt(),
    0xFFF44336.toInt()
)
private val rsrpLegendLabels = listOf(">-80", "-80~-90", "-90~-100", "<-100")
private val lossLegendLabels = listOf("0%", "20%", "40%", "60%+")

@Composable
fun SessionMapView(
    records: List<CellRecordEntity>,
    displayMode: MapDisplayMode = MapDisplayMode.SIGNAL_TRAILS,
    showLegend: Boolean = true,
    modifier: Modifier = Modifier
) {
    val ctx = LocalContext.current

    LaunchedEffect(Unit) {
        Configuration.getInstance().let { config ->
            config.userAgentValue = ctx.packageName
            config.osmdroidBasePath = ctx.cacheDir
        }
    }

    val mapView = remember {
        MapView(ctx).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(16.0)
        }
    }

    LaunchedEffect(records, displayMode) {
        mapView.overlays.clear()
        if (records.isNotEmpty()) {
            when (displayMode) {
                MapDisplayMode.SIGNAL_TRAILS -> drawSignalTrails(mapView, records)
                MapDisplayMode.PACKET_LOSS -> drawPacketLossTrails(mapView, records)
                MapDisplayMode.CELL_ID -> drawMarkers(mapView, records, ctx, changeExtractor = { formatCellId(it) }, titlePrefix = "Cell ID")
                MapDisplayMode.RAT -> drawMarkers(mapView, records, ctx, changeExtractor = { it.rat }, titlePrefix = "RAT")
                MapDisplayMode.BAND -> drawMarkers(mapView, records, ctx, changeExtractor = { it.bandNumber?.toString() ?: "---" }, titlePrefix = "Band")
            }
        }
        mapView.invalidate()
    }

    Box(modifier = modifier) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize().clipToBounds()
        )

        if (showLegend && records.isNotEmpty()) {
            when (displayMode) {
                MapDisplayMode.SIGNAL_TRAILS -> LegendBox("RSRP (dBm)", rsrpLegendColors, rsrpLegendLabels)
                MapDisplayMode.PACKET_LOSS -> LegendBox("Pkt Loss", rsrpLegendColors, lossLegendLabels)
                else -> { /* no legend for marker modes */ }
            }
        }

        if (records.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("No points", style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

private fun drawSignalTrails(mapView: MapView, records: List<CellRecordEntity>) {
    if (records.size < 2) return

    val allPoints = records.map { GeoPoint(it.latitude, it.longitude) }
    var segStart = 0
    var currentColor = rsrpColorArgb(records[0].rsrp)

    for (i in 1 until records.size) {
        val color = rsrpColorArgb(records[i].rsrp)
        if (color != currentColor) {
            mapView.overlays.add(Polyline().apply {
                outlinePaint.color = currentColor
                outlinePaint.strokeWidth = 5f
                setPoints(allPoints.subList(segStart, i + 1))
            })
            segStart = i
            currentColor = color
        }
    }

    mapView.overlays.add(Polyline().apply {
        outlinePaint.color = currentColor
        outlinePaint.strokeWidth = 5f
        setPoints(allPoints.subList(segStart, records.size))
    })

    mapView.zoomToBoundingBox(BoundingBox.fromGeoPoints(allPoints), true, 16)
}

@Composable
private fun BoxScope.LegendBox(title: String, colors: List<Int>, labels: List<String>) {
    Box(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(8.dp)
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
                RoundedCornerShape(6.dp)
            )
            .padding(8.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
            colors.forEachIndexed { index, color ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(Color(color), RoundedCornerShape(2.dp))
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = labels[index],
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

private fun drawPacketLossTrails(mapView: MapView, records: List<CellRecordEntity>) {
    if (records.size < 2) return

    val allPoints = records.map { GeoPoint(it.latitude, it.longitude) }
    var segStart = 0
    var currentColor = packetLossColorArgb(records[0].packetLossPct)

    for (i in 1 until records.size) {
        val color = packetLossColorArgb(records[i].packetLossPct)
        if (color != currentColor) {
            mapView.overlays.add(Polyline().apply {
                outlinePaint.color = currentColor
                outlinePaint.strokeWidth = 5f
                setPoints(allPoints.subList(segStart, i + 1))
            })
            segStart = i
            currentColor = color
        }
    }

    mapView.overlays.add(Polyline().apply {
        outlinePaint.color = currentColor
        outlinePaint.strokeWidth = 5f
        setPoints(allPoints.subList(segStart, records.size))
    })

    mapView.zoomToBoundingBox(BoundingBox.fromGeoPoints(allPoints), true, 16)
}

private fun drawMarkers(
    mapView: MapView,
    records: List<CellRecordEntity>,
    ctx: Context,
    changeExtractor: (CellRecordEntity) -> String,
    titlePrefix: String
) {
    if (records.isEmpty()) return

    val allPoints = records.map { GeoPoint(it.latitude, it.longitude) }

    var prev = changeExtractor(records[0])
    records.forEachIndexed { index, record ->
        val current = changeExtractor(record)
        if (index == 0 || current != prev) {
            val marker = Marker(mapView).apply {
                position = GeoPoint(record.latitude, record.longitude)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                title = "$titlePrefix: $current"
                snippet = "SIM${(record.simSlotIndex ?: 0) + 1} · ${record.bandNumber?.let { "B$it" } ?: "---"} · RSRP: ${record.rsrp ?: "---"}"
                icon = createDotDrawable(ctx, ratColorArgb(record.rat), 14)
            }
            mapView.overlays.add(marker)
            prev = current
        }
    }

    mapView.overlays.add(Polyline().apply {
        outlinePaint.color = 0xFF9E9E9E.toInt()
        outlinePaint.strokeWidth = 3f
        setPoints(allPoints)
    })

    mapView.zoomToBoundingBox(BoundingBox.fromGeoPoints(allPoints), true, 16)
}

private fun createDotDrawable(ctx: Context, color: Int, sizeDp: Int): android.graphics.drawable.BitmapDrawable {
    val sizePx = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, sizeDp.toFloat(), ctx.resources.displayMetrics
    ).toInt()
    val bitmap = Bitmap.createBitmap(sizePx + 4, sizePx + 4, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)
    val cx = (sizePx + 4) / 2f
    val cy = cx
    val r = sizePx / 2f

    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy, r, fillPaint)

    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = 0xFF333333.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    canvas.drawCircle(cx, cy, r - 1f, borderPaint)

    return android.graphics.drawable.BitmapDrawable(ctx.resources, bitmap)
}