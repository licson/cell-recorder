package com.cellrecorder.app.domain.speedtest

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

object SpeedTestMeasurer {

    private const val TAG = "SpeedTestMeasurer"
    private const val CHUNK_SIZE = 1048576
    private val DOWNLOAD_SIZES = listOf(350, 500, 750, 1000, 1500, 2000, 2500, 3000, 3500, 4000)

    private const val DOWNLOAD_WARMUP_MS = 1500L
    private const val UPLOAD_WARMUP_MS = 3000L
    private const val SLICE_INTERVAL_MS = 500L
    private const val GAUGE_DURATION_NS = 2_000_000_000L

    private val MEDIA_TYPE_OCTET_STREAM: MediaType =
        "application/octet-stream".toMediaType()

    /**
     * Tiny pre-upload probe: issues a single small HTTP POST (~1 KB payload of
     * the same `content1=...` shape used by the full upload) with a 5-second
     * timeout. Used by [SpeedTestEngine] to detect carrier-hostile or
     * server-broken upload conditions before burning the 3-second upload
     * warmup. Returns `null` on success, or a human-readable failure reason
     * on non-2xx response, exception, or timeout.
     */
    suspend fun probeUpload(
        serverUrl: String,
        secure: Boolean,
        httpClient: SpeedTestHttpClient
    ): String? = withContext(Dispatchers.IO) {
        val url = if (secure && serverUrl.startsWith("http:")) {
            "https://${serverUrl.substring(7)}"
        } else serverUrl

        val probePayloadSize = 1024
        val payload = buildUploadPayload(probePayloadSize)
        try {
            val body = object : RequestBody() {
                override fun contentType(): MediaType = MEDIA_TYPE_OCTET_STREAM
                override fun contentLength(): Long = payload.size.toLong()
                override fun writeTo(sink: BufferedSink) {
                    sink.write(payload)
                    sink.flush()
                }
            }
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", buildUserAgent())
                .header("Cache-Control", "no-cache")
                .post(body)
                .build()
            httpClient.client.newBuilder()
                .callTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                .build()
                .newCall(request)
                .execute()
                .use { resp ->
                    if (resp.isSuccessful) null
                    else "HTTP ${resp.code}"
                }
        } catch (e: Exception) {
            e.message ?: e.javaClass.simpleName
        }
    }

    data class ThroughputSample(
        val bytesTransferred: Long,
        val elapsedMs: Long,
        val throughputBps: Double,
        val warmupMs: Long
    )

    data class MeasurementResult(
        val totalBytes: Long,
        val totalElapsedMs: Long,
        val postWarmupBytes: Long,
        val postWarmupMs: Long,
        val samples: List<ThroughputSample>,
        val anyRequestSucceeded: Boolean = false
    )

    suspend fun gaugeDownload(
        serverBaseUrl: String,
        secure: Boolean,
        httpClient: SpeedTestHttpClient
    ): Long? = withContext(Dispatchers.IO) {
        val baseUrl = if (secure && serverBaseUrl.startsWith("http:")) {
            "https://${serverBaseUrl.substring(7)}"
        } else serverBaseUrl

        val gaugeUrl = "${baseUrl}/random350x350.jpg?x=${System.currentTimeMillis()}.gauge"

        try {
            val request = Request.Builder()
                .url(gaugeUrl)
                .header("User-Agent", buildUserAgent())
                .header("Cache-Control", "no-cache")
                .build()

            val startTime = System.nanoTime()
            val response = httpClient.client.newCall(request).execute()
            response.use { resp ->
                if (!resp.isSuccessful) return@use null

                val body = resp.body?.byteStream() ?: return@use null
                val buffer = ByteArray(CHUNK_SIZE)
                var totalBytes = 0L
                val deadlineNs = startTime + GAUGE_DURATION_NS

                while (System.nanoTime() < deadlineNs) {
                    val bytesRead = body.read(buffer)
                    if (bytesRead == -1) break
                    totalBytes += bytesRead
                }

                val elapsedMs = (System.nanoTime() - startTime) / 1_000_000
                if (totalBytes <= 0 || elapsedMs <= 0) null
                else ((totalBytes.toDouble() / elapsedMs) * 8_000.0).toLong()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Gauge download failed: ${e.message}")
            null
        }
    }

    suspend fun measureDownload(
        serverBaseUrl: String,
        threadsPerUrl: Int,
        downloadThreads: Int,
        testLengthSec: Int,
        secure: Boolean,
        estimatedBps: Long,
        httpClient: SpeedTestHttpClient
    ): MeasurementResult = withContext(Dispatchers.IO) {
        val baseUrl = if (secure && serverBaseUrl.startsWith("http:")) {
            "https://${serverBaseUrl.substring(7)}"
        } else serverBaseUrl

        val sizes = selectDownloadSizes(estimatedBps)
        val urls = mutableListOf<String>()
        for (size in sizes) {
            repeat(threadsPerUrl) { i ->
                urls.add("${baseUrl}/random${size}x${size}.jpg?x=${System.currentTimeMillis()}.$i")
            }
        }

        val startTime = System.nanoTime()
        val deadlineNs = computeDeadlineNs(startTime, testLengthSec, DOWNLOAD_WARMUP_MS)
        val warmupDeadlineNs = startTime + DOWNLOAD_WARMUP_MS * 1_000_000L
        val totalBytes = AtomicLong(0)
        val postWarmupBytes = AtomicLong(0)
        val anySuccess = AtomicBoolean(false)
        val samples = mutableListOf<ThroughputSample>()
        val sliceNs = SLICE_INTERVAL_MS * 1_000_000L
        val nextSliceNs = AtomicLong(warmupDeadlineNs + sliceNs)
        val lastSliceBytes = AtomicLong(0)
        val lastSliceTime = AtomicLong(warmupDeadlineNs)
        val sliceLock = Any()
        val urlCount = urls.size
        val urlIdx = AtomicLong(0)
        if (urlCount == 0) {
            return@withContext buildResult(0, 0, 0, 0, emptyList(), false)
        }

        coroutineScope {
            val jobs = (0 until downloadThreads.coerceAtLeast(1)).map {
                launch {
                    while (isActive) {
                        val now = System.nanoTime()
                        if (now >= deadlineNs) break
                        try {
                            val url = urls[(urlIdx.getAndIncrement().toInt() and 0x7fffffff) % urlCount]
                            val request = Request.Builder()
                                .url(url)
                                .header("User-Agent", buildUserAgent())
                                .header("Cache-Control", "no-cache")
                                .build()
                            val response = httpClient.client.newCall(request).execute()
                            response.use { resp ->
                                if (!resp.isSuccessful) return@use
                                anySuccess.set(true)

                                val input = resp.body?.byteStream() ?: return@use
                                val buffer = ByteArray(CHUNK_SIZE)

                                while (isActive) {
                                    val innerNow = System.nanoTime()
                                    if (innerNow >= deadlineNs) break

                                    val bytesRead = input.read(buffer)
                                    if (bytesRead == -1) break

                                    totalBytes.addAndGet(bytesRead.toLong())

                                    if (innerNow >= warmupDeadlineNs) {
                                        postWarmupBytes.addAndGet(bytesRead.toLong())
                                    }

                                    if (innerNow >= nextSliceNs.get()) {
                                        synchronized(sliceLock) {
                                            val sliceNow = System.nanoTime()
                                            if (sliceNow >= nextSliceNs.get()) {
                                                recordSlice(sliceNow, nextSliceNs, lastSliceBytes, lastSliceTime, postWarmupBytes, sliceNs, samples)
                                            }
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Download request failed: ${e.message}")
                        }
                    }
                }
            }
            jobs.forEach { it.join() }
        }

        val elapsedNs = System.nanoTime() - startTime
        val postWarmupNs = (elapsedNs - (warmupDeadlineNs - startTime)).coerceAtLeast(0L)
        val snapshot = synchronized(sliceLock) { samples.toList() }
        buildResult(totalBytes.get(), elapsedNs / 1_000_000, postWarmupBytes.get(), postWarmupNs / 1_000_000, snapshot, anySuccess.get())
    }

    suspend fun measureUpload(
        serverUrl: String,
        uploadSizes: List<Int>,
        uploadCount: Int,
        uploadMax: Int,
        uploadThreads: Int,
        testLengthSec: Int,
        secure: Boolean,
        estimatedBps: Long,
        httpClient: SpeedTestHttpClient
    ): MeasurementResult = withContext(Dispatchers.IO) {
        val url = if (secure && serverUrl.startsWith("http:")) {
            "https://${serverUrl.substring(7)}"
        } else serverUrl

        val requests = mutableListOf<Pair<String, Int>>()
        for (size in uploadSizes) {
            repeat(uploadCount) {
                requests.add(url to size)
            }
        }
        val actualRequests = requests.take(uploadMax)

        val startTime = System.nanoTime()
        val deadlineNs = computeDeadlineNs(startTime, testLengthSec, UPLOAD_WARMUP_MS)
        val warmupDeadlineNs = startTime + UPLOAD_WARMUP_MS * 1_000_000L
        val totalBytes = AtomicLong(0)
        val postWarmupBytes = AtomicLong(0)
        val anySuccess = AtomicBoolean(false)
        val samples = mutableListOf<ThroughputSample>()
        val sliceNs = SLICE_INTERVAL_MS * 1_000_000L
        val nextSliceNs = AtomicLong(warmupDeadlineNs + sliceNs)
        val lastSliceBytes = AtomicLong(0)
        val lastSliceTime = AtomicLong(warmupDeadlineNs)
        val sliceLock = Any()
        val requestCount = actualRequests.size
        val requestIdx = AtomicLong(0)
        if (requestCount == 0) {
            return@withContext buildResult(0, 0, 0, 0, emptyList(), false)
        }

        coroutineScope {
            val jobs = (0 until uploadThreads.coerceAtLeast(1)).map {
                launch {
                    while (isActive) {
                        val now = System.nanoTime()
                        if (now >= deadlineNs) break
                        try {
                            val (reqUrl, size) = actualRequests[(requestIdx.getAndIncrement().toInt() and 0x7fffffff) % requestCount]

                            val streamingBody = createStreamingUploadBody(
                                payload = buildUploadPayload(size),
                                totalBytes = totalBytes,
                                postWarmupBytes = postWarmupBytes,
                                warmupDeadlineNs = warmupDeadlineNs,
                                deadlineNs = deadlineNs,
                                sliceNs = sliceNs,
                                nextSliceNs = nextSliceNs,
                                lastSliceBytes = lastSliceBytes,
                                lastSliceTime = lastSliceTime,
                                samples = samples,
                                sliceLock = sliceLock
                            )

                            val request = Request.Builder()
                                .url(reqUrl)
                                .header("User-Agent", buildUserAgent())
                                .header("Cache-Control", "no-cache")
                                .post(streamingBody)
                                .build()

                            val response = httpClient.client.newCall(request).execute()
                            response.use { resp ->
                                if (!resp.isSuccessful) return@use
                                anySuccess.set(true)
                                resp.body?.bytes()
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Upload request failed: ${e.message}")
                        }
                    }
                }
            }
            jobs.forEach { it.join() }
        }

        val elapsedNs = System.nanoTime() - startTime
        val postWarmupNs = (elapsedNs - (warmupDeadlineNs - startTime)).coerceAtLeast(0L)
        val snapshot = synchronized(sliceLock) { samples.toList() }
        buildResult(totalBytes.get(), elapsedNs / 1_000_000, postWarmupBytes.get(), postWarmupNs / 1_000_000, snapshot, anySuccess.get())
    }

    internal fun createStreamingUploadBody(
        payload: ByteArray,
        totalBytes: AtomicLong,
        postWarmupBytes: AtomicLong,
        warmupDeadlineNs: Long,
        deadlineNs: Long,
        sliceNs: Long,
        nextSliceNs: AtomicLong,
        lastSliceBytes: AtomicLong,
        lastSliceTime: AtomicLong,
        samples: MutableList<ThroughputSample>,
        sliceLock: Any
    ): RequestBody {
        return object : RequestBody() {
            override fun contentType(): MediaType = MEDIA_TYPE_OCTET_STREAM

            override fun contentLength(): Long = payload.size.toLong()

            override fun writeTo(sink: BufferedSink) {
                var offset = 0
                while (offset < payload.size) {
                    val now = System.nanoTime()
                    if (now >= deadlineNs) break

                    val chunk = minOf(CHUNK_SIZE, payload.size - offset)
                    sink.write(payload, offset, chunk)
                    sink.flush()
                    offset += chunk

                    totalBytes.addAndGet(chunk.toLong())
                    if (now >= warmupDeadlineNs) {
                        postWarmupBytes.addAndGet(chunk.toLong())
                    }

                    if (now >= nextSliceNs.get()) {
                        synchronized(sliceLock) {
                            val sliceNow = System.nanoTime()
                            if (sliceNow >= nextSliceNs.get()) {
                                recordSlice(sliceNow, nextSliceNs, lastSliceBytes, lastSliceTime, postWarmupBytes, sliceNs, samples)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun recordSlice(
        now: Long,
        nextSliceNs: AtomicLong,
        lastSliceBytes: AtomicLong,
        lastSliceTime: AtomicLong,
        bytesCounter: AtomicLong,
        sliceNs: Long,
        samples: MutableList<ThroughputSample>
    ) {
        val prevBytes = lastSliceBytes.getAndSet(bytesCounter.get())
        val prevTime = lastSliceTime.getAndSet(now)
        val sliceBytes = bytesCounter.get() - prevBytes
        val sliceElapsed = (now - prevTime) / 1_000_000
        if (sliceElapsed > 0 && sliceBytes > 0) {
            samples.add(ThroughputSample(
                bytesTransferred = sliceBytes,
                elapsedMs = sliceElapsed,
                throughputBps = (sliceBytes.toDouble() / sliceElapsed) * 8_000.0,
                warmupMs = 0L
            ))
        }
        nextSliceNs.set(now + sliceNs)
    }

    private fun buildResult(
        totalBytes: Long,
        totalElapsedMs: Long,
        postWarmupBytes: Long,
        postWarmupMs: Long,
        samples: List<ThroughputSample>,
        anyRequestSucceeded: Boolean
    ): MeasurementResult {
        return MeasurementResult(
            totalBytes = totalBytes,
            totalElapsedMs = totalElapsedMs,
            postWarmupBytes = postWarmupBytes,
            postWarmupMs = postWarmupMs,
            samples = samples,
            anyRequestSucceeded = anyRequestSucceeded
        )
    }

    internal fun computeDeadlineNs(startTimeNs: Long, testLengthSec: Int, warmupMs: Long): Long =
        startTimeNs + (testLengthSec * 1000L + warmupMs) * 1_000_000L

    private fun selectDownloadSizes(estimatedBps: Long): List<Int> {
        val mbps = estimatedBps / 1_000_000
        if (mbps <= 0) return DOWNLOAD_SIZES.take(3)
        if (mbps < 10) return DOWNLOAD_SIZES.take(3)
        if (mbps < 100) return DOWNLOAD_SIZES
        return DOWNLOAD_SIZES.drop(3)
    }

    private val uploadPayloadCache = ConcurrentHashMap<Int, ByteArray>()

    private fun buildUploadPayload(size: Int): ByteArray =
        uploadPayloadCache.computeIfAbsent(size) {
            val chars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ"
            val multiplier = kotlin.math.round(it.toDouble() / chars.length).toInt()
            val content = "content1=" + (chars.repeat(multiplier)).substring(0, it - 9)
            content.toByteArray(Charsets.UTF_8)
        }

    private fun buildUserAgent(): String {
        return "Mozilla/5.0 (Linux; Android; en-us) (KHTML, like Gecko) speedtest-cli/2.1.4"
    }
}