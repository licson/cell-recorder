package com.cellrecorder.app.domain.speedtest

import android.util.Log
import android.util.Xml
import com.cellrecorder.app.domain.speedtest.model.SpeedTestServerInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import kotlin.math.*

object SpeedTestServerSelector {

    private const val TAG = "SpeedTestServerSelector"
    private const val SERVER_LIST_TIMEOUT_MS = 10_000
    private const val LATENCY_PING_TIMEOUT_MS = 5_000
    private const val CLOSEST_COUNT = 5
    private const val PING_SAMPLES = 3

    private val SERVER_LIST_URLS = listOf(
        "https://www.speedtest.net/speedtest-servers-static.php",
        "http://c.speedtest.net/speedtest-servers-static.php",
        "https://www.speedtest.net/speedtest-servers.php",
        "http://c.speedtest.net/speedtest-servers.php"
    )

    suspend fun fetchAndSelect(
        clientLat: Double,
        clientLon: Double,
        ignoreIds: List<Int>,
        preferredServerId: Int? = null,
        downloadThreads: Int = 8,
        secure: Boolean = true,
        hasValidClientLocation: Boolean = true,
        httpClient: SpeedTestHttpClient
    ): SpeedTestServerInfo? = withContext(Dispatchers.IO) {
        val servers = fetchServerList(downloadThreads, secure, httpClient) ?: return@withContext null

        if (preferredServerId != null) {
            val server = servers.find { it.id == preferredServerId }
            if (server != null) return@withContext server
        }

        val filtered = servers.filter { it.id !in ignoreIds }
        if (filtered.isEmpty()) return@withContext null

        val closest = if (hasValidClientLocation) {
            filtered
                .map { it.copy(latencyMs = haversineKm(clientLat, clientLon, it.lat, it.lon)) }
                .sortedBy { it.latencyMs }
                .take(CLOSEST_COUNT)
        } else {
            filtered.take(CLOSEST_COUNT * 2)
        }
        if (closest.isEmpty()) return@withContext null

        val best = pingServers(closest, secure, httpClient) ?: closest.first()
        best
    }

    private suspend fun fetchServerList(
        downloadThreads: Int,
        secure: Boolean,
        httpClient: SpeedTestHttpClient
    ): List<SpeedTestServerInfo>? = withContext(Dispatchers.IO) {
        val scheme = if (secure) "https" else "http"

        for (urlString in SERVER_LIST_URLS) {
            try {
                val finalUrl = urlString.replaceFirst(Regex("^https?:"), "$scheme:") +
                        "?threads=$downloadThreads"
                val request = Request.Builder()
                    .url(finalUrl)
                    .header("Cache-Control", "no-cache")
                    .header("User-Agent", buildUserAgent())
                    .build()
                    val response = httpClient.client.newCall(request).execute()
                response.use { resp ->
                    if (!resp.isSuccessful) return@use

                    val servers = mutableListOf<SpeedTestServerInfo>()
                    val parser = Xml.newPullParser()
                    val bodyStream = resp.body?.byteStream() ?: return@use
                    parser.setInput(bodyStream, null)
                    parser.nextTag()
                    parser.require(XmlPullParser.START_TAG, null, "settings")
                    while (parser.next() != XmlPullParser.END_TAG) {
                        if (parser.eventType != XmlPullParser.START_TAG) continue
                        if (parser.name == "servers") {
                            while (parser.next() != XmlPullParser.END_TAG) {
                                if (parser.eventType != XmlPullParser.START_TAG) continue
                                if (parser.name == "server") {
                                    val server = parseServerElement(parser)
                                    if (server != null) servers.add(server)
                                } else {
                                    skipTag(parser)
                                }
                            }
                        } else {
                            skipTag(parser)
                        }
                    }
                    return@withContext servers
                }
            } catch (_: Exception) {
                continue
            }
        }
        null
    }

    private fun parseServerElement(parser: XmlPullParser): SpeedTestServerInfo? {
        return try {
            val id = parser.getAttributeValue(null, "id")?.toIntOrNull() ?: return null
            val url = parser.getAttributeValue(null, "url") ?: return null
            val lat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull() ?: return null
            val lon = parser.getAttributeValue(null, "lon")?.toDoubleOrNull() ?: return null
            val name = parser.getAttributeValue(null, "name") ?: ""
            val sponsor = parser.getAttributeValue(null, "sponsor") ?: ""
            val host = parser.getAttributeValue(null, "host") ?: ""
            parser.next()
            SpeedTestServerInfo(id = id, name = name, host = host, url = url, lat = lat, lon = lon, sponsor = sponsor)
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun pingServers(
        servers: List<SpeedTestServerInfo>,
        secure: Boolean,
        httpClient: SpeedTestHttpClient
    ): SpeedTestServerInfo? = withContext(Dispatchers.IO) {
        var best: SpeedTestServerInfo? = null
        var bestLatency = Double.MAX_VALUE

        for (server in servers) {
            try {
                val latencies = mutableListOf<Double>()
                var baseUrl = server.url.substringBeforeLast("/")
                if (secure && baseUrl.startsWith("http:")) {
                    baseUrl = "https://${baseUrl.substring(7)}"
                }

                for (i in 0 until PING_SAMPLES) {
                    val latencyUrl = "$baseUrl/latency.txt?x=${System.currentTimeMillis()}.$i"
                    try {
                        val start = System.nanoTime()
                        val request = Request.Builder()
                            .url(latencyUrl)
                            .header("User-Agent", buildUserAgent())
                            .header("Accept-Encoding", "identity")
                            .build()
                        val response = httpClient.client.newCall(request).execute()
                        response.use { resp ->
                            if (resp.isSuccessful) {
                                val bodyBytes = resp.body?.bytes() ?: ByteArray(0)
                                val elapsed = (System.nanoTime() - start) / 1_000_000.0
                                val text = String(bodyBytes, 0, bodyBytes.size.coerceAtMost(9), Charsets.US_ASCII)
                                if (text == "test=test" && elapsed < LATENCY_PING_TIMEOUT_MS) {
                                    latencies.add(elapsed)
                                } else {
                                    latencies.add(3600.0)
                                }
                            } else {
                                latencies.add(3600.0)
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Latency ping failed for ${server.host}: ${e.message}")
                        latencies.add(3600.0)
                    }
                }

                if (latencies.isNotEmpty()) {
                    val avgLatency = latencies.sum() / latencies.size
                    if (avgLatency < bestLatency) {
                        bestLatency = avgLatency
                        best = server.copy(latencyMs = avgLatency)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Ping server failed: ${e.message}")
                continue
            }
        }

        best
    }

    private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return R * c
    }

    private fun buildUserAgent(): String {
        return "Mozilla/5.0 (Linux; Android; en-us) (KHTML, like Gecko) speedtest-cli/2.1.4"
    }

    private fun skipTag(parser: XmlPullParser) {
        var depth = 1
        while (depth > 0) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> depth++
                XmlPullParser.END_TAG -> depth--
            }
        }
    }
}