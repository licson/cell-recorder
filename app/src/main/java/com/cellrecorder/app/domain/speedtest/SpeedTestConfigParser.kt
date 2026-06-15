package com.cellrecorder.app.domain.speedtest

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream

data class SpeedTestClientConfig(
    val ip: String,
    val lat: Double,
    val lon: Double,
    val isp: String,
    val hasValidLocation: Boolean = true
)

data class SpeedTestDownloadConfig(
    val threadsPerUrl: Int,
    val testLengthSec: Int
)

data class SpeedTestUploadConfig(
    val threads: Int,
    val ratio: Int,
    val testLengthSec: Int,
    val maxChunkCount: Int
)

data class SpeedTestServerConfig(
    val ignoreIds: List<Int>,
    val threadCount: Int
)

data class SpeedTestProtocolConfig(
    val client: SpeedTestClientConfig,
    val download: SpeedTestDownloadConfig,
    val upload: SpeedTestUploadConfig,
    val server: SpeedTestServerConfig
) {
    val downloadThreads: Int get() = server.threadCount * 2
    val uploadSizes: List<Int> get() {
        val base = listOf(32768, 65536, 131072, 262144, 524288, 1048576, 7340032)
        return base.drop(upload.ratio - 1)
    }
    val uploadCount: Int get() {
        val sizeCount = uploadSizes.size
        return (upload.maxChunkCount.toDouble() / sizeCount).let { kotlin.math.ceil(it).toInt() }
    }
    val uploadMax: Int get() = uploadCount * uploadSizes.size
}

object SpeedTestConfigParser {

    fun parse(input: InputStream): SpeedTestProtocolConfig {
        val parser = Xml.newPullParser()
        parser.setInput(input, null)
        parser.nextTag()

        var clientIp = ""
        var clientLat = 0.0
        var clientLon = 0.0
        var clientIsp = ""
        var hasValidLocation = false
        var ignoreIds = emptyList<Int>()
        var threadCount = 4
        var downloadThreadsPerUrl = 4
        var downloadTestLength = 10
        var uploadThreads = 4
        var uploadRatio = 4
        var uploadTestLength = 10
        var uploadMaxChunkCount = 4

        parser.require(XmlPullParser.START_TAG, null, "settings")
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            when (parser.name) {
                "client" -> {
                    clientIp = parser.getAttributeValue(null, "ip") ?: ""
                    clientLat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull() ?: 0.0
                    clientLon = parser.getAttributeValue(null, "lon")?.toDoubleOrNull() ?: 0.0
                    clientIsp = parser.getAttributeValue(null, "isp") ?: ""
                    hasValidLocation = parser.getAttributeValue(null, "lat") != null &&
                            parser.getAttributeValue(null, "lon") != null &&
                            !(clientLat == 0.0 && clientLon == 0.0)
                    parser.next()
                }
                "server-config" -> {
                    val ids = parser.getAttributeValue(null, "ignoreids") ?: ""
                    ignoreIds = ids.split(",").mapNotNull { it.trim().toIntOrNull() }
                    threadCount = parser.getAttributeValue(null, "threadcount")?.toIntOrNull() ?: 4
                    parser.next()
                }
                "download" -> {
                    downloadThreadsPerUrl = parser.getAttributeValue(null, "threadsperurl")?.toIntOrNull() ?: 4
                    downloadTestLength = parser.getAttributeValue(null, "testlength")?.toIntOrNull() ?: 10
                    parser.next()
                }
                "upload" -> {
                    uploadThreads = parser.getAttributeValue(null, "threads")?.toIntOrNull() ?: 4
                    uploadRatio = parser.getAttributeValue(null, "ratio")?.toIntOrNull() ?: 4
                    uploadTestLength = parser.getAttributeValue(null, "testlength")?.toIntOrNull() ?: 10
                    uploadMaxChunkCount = parser.getAttributeValue(null, "maxchunkcount")?.toIntOrNull() ?: 4
                    parser.next()
                }
                else -> skipTag(parser)
            }
        }

        return SpeedTestProtocolConfig(
            client = SpeedTestClientConfig(ip = clientIp, lat = clientLat, lon = clientLon, isp = clientIsp, hasValidLocation = hasValidLocation),
            download = SpeedTestDownloadConfig(threadsPerUrl = downloadThreadsPerUrl, testLengthSec = downloadTestLength),
            upload = SpeedTestUploadConfig(threads = uploadThreads, ratio = uploadRatio, testLengthSec = uploadTestLength, maxChunkCount = uploadMaxChunkCount),
            server = SpeedTestServerConfig(ignoreIds = ignoreIds, threadCount = threadCount)
        )
    }

    private fun skipTag(parser: XmlPullParser) {
        if (parser.eventType != XmlPullParser.START_TAG) return
        var depth = 1
        while (depth > 0) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> depth++
                XmlPullParser.END_TAG -> depth--
            }
        }
    }
}