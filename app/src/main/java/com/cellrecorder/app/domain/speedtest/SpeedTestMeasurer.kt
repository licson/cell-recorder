package com.cellrecorder.app.domain.speedtest

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL

object SpeedTestMeasurer {

    private const val CHUNK_SIZE = 10240
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 30_000
    private val DOWNLOAD_SIZES = listOf(350, 500, 750, 1000, 1500, 2000, 2500, 3000, 3500, 4000)

    data class MeasurementResult(
        val bytesTransferred: Long,
        val elapsedMs: Long
    )

    suspend fun measureDownload(
        serverBaseUrl: String,
        threadsPerUrl: Int,
        downloadThreads: Int,
        testLengthSec: Int,
        secure: Boolean
    ): MeasurementResult = withContext(Dispatchers.IO) {
        val urls = mutableListOf<String>()
        for (size in DOWNLOAD_SIZES) {
            repeat(threadsPerUrl) { i ->
                urls.add("${serverBaseUrl}/random${size}x${size}.jpg?x=${System.currentTimeMillis()}.$i")
            }
        }

        val semaphore = Semaphore(downloadThreads.coerceAtLeast(1))
        val startTime = System.nanoTime()
        val deadlineNs = startTime + testLengthSec * 1_000_000_000L
        var totalBytes = 0L

        coroutineScope {
            val jobs = urls.map { url ->
                launch {
                    semaphore.acquire()
                    try {
                        if (System.nanoTime() >= deadlineNs || !isActive) return@launch

                        val conn = URL(url).openConnection() as HttpURLConnection
                        conn.connectTimeout = CONNECT_TIMEOUT_MS
                        conn.readTimeout = READ_TIMEOUT_MS
                        conn.setRequestProperty("User-Agent", buildUserAgent())
                        conn.setRequestProperty("Cache-Control", "no-cache")

                        try {
                            conn.connect()
                            val input = conn.inputStream
                            val buffer = ByteArray(CHUNK_SIZE)
                            var bytesRead: Int

                            while (isActive && System.nanoTime() < deadlineNs) {
                                bytesRead = input.read(buffer)
                                if (bytesRead == -1) break
                                totalBytes += bytesRead
                            }

                            input.close()
                        } catch (_: Exception) {
                        } finally {
                            conn.disconnect()
                        }
                    } finally {
                        semaphore.release()
                    }
                }
            }
            jobs.forEach { it.join() }
        }

        val elapsedNs = System.nanoTime() - startTime
        MeasurementResult(
            bytesTransferred = totalBytes,
            elapsedMs = elapsedNs / 1_000_000
        )
    }

    suspend fun measureUpload(
        serverUrl: String,
        uploadSizes: List<Int>,
        uploadCount: Int,
        uploadMax: Int,
        uploadThreads: Int,
        testLengthSec: Int,
        secure: Boolean
    ): MeasurementResult = withContext(Dispatchers.IO) {
        val requests = mutableListOf<Pair<String, Int>>()
        for (size in uploadSizes) {
            repeat(uploadCount) {
                requests.add(serverUrl to size)
            }
        }
        val actualRequests = requests.take(uploadMax)

        val semaphore = Semaphore(uploadThreads.coerceAtLeast(1))
        val startTime = System.nanoTime()
        val deadlineNs = startTime + testLengthSec * 1_000_000_000L
        var totalBytes = 0L

        coroutineScope {
            val jobs = actualRequests.map { (url, size) ->
                launch {
                    semaphore.acquire()
                    try {
                        if (System.nanoTime() >= deadlineNs || !isActive) return@launch

                        val payload = buildUploadPayload(size)
                        val conn = URL(url).openConnection() as HttpURLConnection
                        conn.connectTimeout = CONNECT_TIMEOUT_MS
                        conn.readTimeout = READ_TIMEOUT_MS
                        conn.doOutput = true
                        conn.requestMethod = "POST"
                        conn.setRequestProperty("User-Agent", buildUserAgent())
                        conn.setRequestProperty("Content-Length", size.toString())
                        conn.setRequestProperty("Cache-Control", "no-cache")

                        try {
                            conn.connect()
                            val output: OutputStream = conn.outputStream
                            var offset = 0
                            var written = 0L

                            while (isActive && System.nanoTime() < deadlineNs && offset < payload.size) {
                                val chunk = minOf(CHUNK_SIZE, payload.size - offset)
                                output.write(payload, offset, chunk)
                                output.flush()
                                offset += chunk
                                written += chunk
                            }

                            output.close()
                            conn.inputStream.use { it.read(ByteArray(11)) }
                            totalBytes += written
                        } catch (_: Exception) {
                        } finally {
                            conn.disconnect()
                        }
                    } finally {
                        semaphore.release()
                    }
                }
            }
            jobs.forEach { it.join() }
        }

        val elapsedNs = System.nanoTime() - startTime
        MeasurementResult(
            bytesTransferred = totalBytes,
            elapsedMs = elapsedNs / 1_000_000
        )
    }

    private fun buildUploadPayload(size: Int): ByteArray {
        val chars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        val multiplier = kotlin.math.round(size.toDouble() / chars.length).toInt()
        val content = "content1=" + (chars.repeat(multiplier)).substring(0, size - 9)
        return content.toByteArray(Charsets.UTF_8)
    }

    private fun buildUserAgent(): String {
        return "Mozilla/5.0 (Linux; Android; en-us) (KHTML, like Gecko) speedtest-cli/2.1.4"
    }
}