package com.cellrecorder.app.domain.speedtest

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import java.util.concurrent.atomic.AtomicLong

object SpeedTestMeasurer {

    private const val TAG = "SpeedTestMeasurer"
    private const val CHUNK_SIZE = 65536
    private val DOWNLOAD_SIZES = listOf(350, 500, 750, 1000, 1500, 2000, 2500, 3000, 3500, 4000)

    private const val DOWNLOAD_WARMUP_MS = 1500L
    private const val UPLOAD_WARMUP_MS = 3000L
    private const val SLICE_INTERVAL_MS = 500L
    private const val GAUGE_DURATION_NS = 2_000_000_000L

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
        val samples: List<ThroughputSample>
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

        val semaphore = Semaphore(downloadThreads.coerceAtLeast(1))
        val startTime = System.nanoTime()
        val deadlineNs = startTime + (testLengthSec + DOWNLOAD_WARMUP_MS / 1000) * 1_000_000_000L
        val warmupDeadlineNs = startTime + DOWNLOAD_WARMUP_MS * 1_000_000L
        val totalBytes = AtomicLong(0)
        val postWarmupBytes = AtomicLong(0)
        val samples = mutableListOf<ThroughputSample>()
        val sliceNs = SLICE_INTERVAL_MS * 1_000_000L
        val nextSliceNs = AtomicLong(warmupDeadlineNs + sliceNs)
        val lastSliceBytes = AtomicLong(0)
        val lastSliceTime = AtomicLong(warmupDeadlineNs)
        val sliceLock = Any()

        coroutineScope {
            val jobs = urls.map { url ->
                launch {
                    semaphore.withPermit {
                        try {
                            if (System.nanoTime() >= deadlineNs || !isActive) return@launch

                            val request = Request.Builder()
                                .url(url)
                                .header("User-Agent", buildUserAgent())
                                .header("Cache-Control", "no-cache")
                                .build()
                            val response = httpClient.client.newCall(request).execute()
                            response.use { resp ->
                                if (!resp.isSuccessful) return@use

                                val input = resp.body?.byteStream() ?: return@use
                                val buffer = ByteArray(CHUNK_SIZE)

                                while (isActive) {
                                    val now = System.nanoTime()
                                    if (now >= deadlineNs) break

                                    val bytesRead = input.read(buffer)
                                    if (bytesRead == -1) break

                                    totalBytes.addAndGet(bytesRead.toLong())

                                    if (now >= warmupDeadlineNs) {
                                        postWarmupBytes.addAndGet(bytesRead.toLong())
                                    }

                                    if (now >= nextSliceNs.get()) {
                                        synchronized(sliceLock) {
                                            val sliceNow = System.nanoTime()
                                            if (sliceNow >= nextSliceNs.get()) {
                                                recordSlice(sliceNow, nextSliceNs, lastSliceBytes, lastSliceTime, totalBytes, sliceNs, samples)
                                            }
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Download job failed: ${e.message}")
                        }
                    }
                }
            }
            jobs.forEach { it.join() }
        }

        val elapsedNs = System.nanoTime() - startTime
        val postWarmupNs = (elapsedNs - (warmupDeadlineNs - startTime)).coerceAtLeast(0L)
        val snapshot = synchronized(sliceLock) { samples.toList() }
        buildResult(totalBytes.get(), elapsedNs / 1_000_000, postWarmupBytes.get(), postWarmupNs / 1_000_000, snapshot)
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

        val semaphore = Semaphore(uploadThreads.coerceAtLeast(1))
        val startTime = System.nanoTime()
        val deadlineNs = startTime + (testLengthSec + UPLOAD_WARMUP_MS / 1000) * 1_000_000_000L
        val warmupDeadlineNs = startTime + UPLOAD_WARMUP_MS * 1_000_000L
        val totalBytes = AtomicLong(0)
        val postWarmupBytes = AtomicLong(0)
        val samples = mutableListOf<ThroughputSample>()
        val sliceNs = SLICE_INTERVAL_MS * 1_000_000L
        val nextSliceNs = AtomicLong(warmupDeadlineNs + sliceNs)
        val lastSliceBytes = AtomicLong(0)
        val lastSliceTime = AtomicLong(warmupDeadlineNs)
        val sliceLock = Any()

        coroutineScope {
            val jobs = actualRequests.map { (reqUrl, size) ->
                launch {
                    semaphore.withPermit {
                        try {
                            if (System.nanoTime() >= deadlineNs || !isActive) return@launch

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
                                resp.body?.bytes()
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Upload job failed: ${e.message}")
                        }
                    }
                }
            }
            jobs.forEach { it.join() }
        }

        val elapsedNs = System.nanoTime() - startTime
        val postWarmupNs = (elapsedNs - (warmupDeadlineNs - startTime)).coerceAtLeast(0L)
        val snapshot = synchronized(sliceLock) { samples.toList() }
        buildResult(totalBytes.get(), elapsedNs / 1_000_000, postWarmupBytes.get(), postWarmupNs / 1_000_000, snapshot)
    }

    private fun createStreamingUploadBody(
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
            override fun contentType(): MediaType? = null

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
                                recordSlice(sliceNow, nextSliceNs, lastSliceBytes, lastSliceTime, totalBytes, sliceNs, samples)
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
        totalBytes: AtomicLong,
        sliceNs: Long,
        samples: MutableList<ThroughputSample>
    ) {
        val prevBytes = lastSliceBytes.getAndSet(totalBytes.get())
        val prevTime = lastSliceTime.getAndSet(now)
        val sliceBytes = totalBytes.get() - prevBytes
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
        samples: List<ThroughputSample>
    ): MeasurementResult {
        return MeasurementResult(
            totalBytes = totalBytes,
            totalElapsedMs = totalElapsedMs,
            postWarmupBytes = postWarmupBytes,
            postWarmupMs = postWarmupMs,
            samples = samples
        )
    }

    private fun selectDownloadSizes(estimatedBps: Long): List<Int> {
        val mbps = estimatedBps / 1_000_000
        if (mbps <= 0) return DOWNLOAD_SIZES.take(3)
        if (mbps < 10) return DOWNLOAD_SIZES.take(3)
        if (mbps < 100) return DOWNLOAD_SIZES
        return DOWNLOAD_SIZES.drop(3)
    }

    @Volatile
    private var sharedPayloadCache: Pair<Int, ByteArray>? = null

    private fun buildUploadPayload(size: Int): ByteArray {
        sharedPayloadCache?.let { (cachedSize, cached) ->
            if (cachedSize == size) return cached
        }
        val chars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        val multiplier = kotlin.math.round(size.toDouble() / chars.length).toInt()
        val content = "content1=" + (chars.repeat(multiplier)).substring(0, size - 9)
        val payload = content.toByteArray(Charsets.UTF_8)
        sharedPayloadCache = size to payload
        return payload
    }

    private fun buildUserAgent(): String {
        return "Mozilla/5.0 (Linux; Android; en-us) (KHTML, like Gecko) speedtest-cli/2.1.4"
    }
}