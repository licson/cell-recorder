package com.cellrecorder.app.domain.speedtest

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.cellrecorder.app.BuildConfig
import com.cellrecorder.app.domain.speedtest.model.SpeedTestResult
import com.cellrecorder.app.domain.speedtest.model.SpeedTestServerInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpeedTestEngine @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val httpClient: SpeedTestHttpClient,
    private val debugRingBuffer: SpeedTestDebugRingBuffer
) {

    @Volatile
    private var cachedServer: SpeedTestServerInfo? = null
    @Volatile
    private var cachedConfig: SpeedTestProtocolConfig? = null
    @Volatile
    private var cachedGaugeBps: Long? = null
    @Volatile
    private var gaugeAttempted: Boolean = false

    /**
     * In-memory flag recording whether a successful manual prime has occurred since
     * the last cache invalidation. Set `true` only when [runTest] is called with
     * `primeOnSuccess = true` (the manual launch path) and the test succeeds; set
     * `false` in [invalidateCache]. Read-once semantics via [consumePrimeFlag]:
     * the session that benefits from a warm handoff clears the flag so a second
     * session without a fresh manual prime cold-starts. Does not survive
     * process restart.
     */
    private val primedSinceLastInvalidation = AtomicBoolean(false)

    companion object {
        private const val TAG = "SpeedTestEngine"
        private const val OVERHEAD_COMPENSATION = 1.06
        private const val DISCARD_FASTEST_PCT = 10
        private const val DISCARD_SLOWEST_PCT = 30
    }

    suspend fun runTest(
        preferredServerId: Int? = null,
        uploadEnabled: Boolean = true,
        onStatus: (String) -> Unit = {},
        primeOnSuccess: Boolean = false
    ): SpeedTestResult = withContext(Dispatchers.IO) {
        val startedAt = System.currentTimeMillis()
        try {
            if (isWifiActive()) {
                emit(SpeedTestDebugEvent.Phase.ERROR, SpeedTestDebugEvent.Status.WARN, "SKIPPED_WIFI: active network is WiFi")
                return@withContext SpeedTestResult(
                    downloadBps = null, uploadBps = null,
                    serverId = null, serverName = null,
                    serverHost = null, serverLocation = null,
                    succeeded = false, errorMessage = "SKIPPED_WIFI",
                    startedAt = startedAt, finishedAt = startedAt
                )
            }

            if (cachedConfig == null) {
                cachedConfig = fetchConfig()
            }
            val config = cachedConfig
            if (config == null) {
                emit(SpeedTestDebugEvent.Phase.CONFIG_FETCH, SpeedTestDebugEvent.Status.FAIL, "Config fetch failed")
                return@withContext SpeedTestResult(
                    downloadBps = null, uploadBps = null,
                    serverId = null, serverName = null,
                    serverHost = null, serverLocation = null,
                    succeeded = false, errorMessage = "Config fetch failed",
                    startedAt = startedAt, finishedAt = startedAt
                )
            }
            emit(SpeedTestDebugEvent.Phase.CONFIG_FETCH, SpeedTestDebugEvent.Status.OK, "Config fetched (client=${config.client.lat},${config.client.lon})")

            if (cachedServer == null) {
                onStatus("Discovering")
                cachedServer = SpeedTestServerSelector.fetchAndSelect(
                    clientLat = config.client.lat,
                    clientLon = config.client.lon,
                    ignoreIds = config.server.ignoreIds,
                    preferredServerId = preferredServerId,
                    downloadThreads = config.downloadThreads,
                    secure = true,
                    hasValidClientLocation = config.client.hasValidLocation,
                    httpClient = httpClient
                )
            }
            val server = cachedServer
            if (server == null) {
                emit(SpeedTestDebugEvent.Phase.SERVER_SELECT, SpeedTestDebugEvent.Status.FAIL, "Server selection failed (preferredServerId=$preferredServerId)")
                return@withContext SpeedTestResult(
                    downloadBps = null, uploadBps = null,
                    serverId = null, serverName = null,
                    serverHost = null, serverLocation = null,
                    succeeded = false, errorMessage = "Server selection failed",
                    startedAt = startedAt, finishedAt = startedAt
                )
            }
            emit(
                SpeedTestDebugEvent.Phase.SERVER_SELECT,
                SpeedTestDebugEvent.Status.OK,
                "Server selected: ${server.name} (${server.host}), id=${server.id}",
                serverId = server.id.toLong(),
                serverHost = server.host
            )

            val serverBaseUrl = server.url.substringBeforeLast("/")

            if (cachedGaugeBps == null && !gaugeAttempted) {
                onStatus("Gauging")
                val gaugeResult = SpeedTestMeasurer.gaugeDownload(
                    serverBaseUrl = serverBaseUrl,
                    secure = true,
                    httpClient = httpClient
                )
                cachedGaugeBps = gaugeResult
                gaugeAttempted = true
                if (gaugeResult == null) {
                    emit(SpeedTestDebugEvent.Phase.GAUGE, SpeedTestDebugEvent.Status.WARN, "Gauge returned null", serverId = server.id.toLong(), serverHost = server.host)
                } else {
                    emit(SpeedTestDebugEvent.Phase.GAUGE, SpeedTestDebugEvent.Status.OK, "Gauge: $gaugeResult bps", serverId = server.id.toLong(), serverHost = server.host, bytes = gaugeResult)
                }
            }

            emit(SpeedTestDebugEvent.Phase.DOWNLOAD, SpeedTestDebugEvent.Status.INFO, "Download starting", serverId = server.id.toLong(), serverHost = server.host)
            onStatus("Downloading")
            val downloadResult = SpeedTestMeasurer.measureDownload(
                serverBaseUrl = serverBaseUrl,
                threadsPerUrl = config.download.threadsPerUrl,
                downloadThreads = config.downloadThreads,
                testLengthSec = config.download.testLengthSec,
                secure = true,
                estimatedBps = cachedGaugeBps ?: 0L,
                httpClient = httpClient
            )

            val downloadBps = computeBps(downloadResult)

            var uploadBps: Long? = null
            var uploadFailed = false
            if (uploadEnabled) {
                emit(SpeedTestDebugEvent.Phase.UPLOAD, SpeedTestDebugEvent.Status.INFO, "Upload starting", serverId = server.id.toLong(), serverHost = server.host)
                onStatus("Uploading")
                val uploadResult = SpeedTestMeasurer.measureUpload(
                    serverUrl = server.url,
                    uploadSizes = config.uploadSizes,
                    uploadCount = config.uploadCount,
                    uploadMax = config.uploadMax,
                    uploadThreads = (config.upload.threads * 2).coerceAtMost(16),
                    testLengthSec = config.upload.testLengthSec,
                    secure = true,
                    estimatedBps = cachedGaugeBps ?: 0L,
                    httpClient = httpClient
                )
                uploadBps = computeBps(uploadResult)
                uploadFailed = !uploadResult.anyRequestSucceeded ||
                    (uploadResult.postWarmupBytes == 0L && uploadResult.postWarmupMs > 0)
            }

            val downloadFailed = !downloadResult.anyRequestSucceeded ||
                (downloadResult.postWarmupBytes == 0L && downloadResult.postWarmupMs > 0)
            val measurementFailed = downloadFailed || (uploadEnabled && uploadFailed)

            if (measurementFailed) {
                invalidateCache()
                onStatus("Failed")
                val errMsg = buildErrorMessage(downloadFailed, uploadFailed && uploadEnabled)
                emit(SpeedTestDebugEvent.Phase.ERROR, SpeedTestDebugEvent.Status.FAIL, "Measurement failed: $errMsg", serverId = server.id.toLong(), serverHost = server.host)
                val finishedAt = System.currentTimeMillis()
                return@withContext SpeedTestResult(
                    downloadBps = if (downloadFailed) null else downloadBps,
                    uploadBps = if (uploadEnabled && uploadFailed) null else uploadBps,
                    serverId = server.id,
                    serverName = server.name,
                    serverHost = server.host,
                    serverLocation = server.sponsor,
                    succeeded = false,
                    errorMessage = errMsg,
                    startedAt = startedAt,
                    finishedAt = finishedAt
                )
            }

            onStatus("Completed")
            if (primeOnSuccess) {
                primedSinceLastInvalidation.set(true)
            }
            val finishedAt = System.currentTimeMillis()
            emit(SpeedTestDebugEvent.Phase.DONE, SpeedTestDebugEvent.Status.OK, "Test completed: ↓$downloadBps ↑$uploadBps", serverId = server.id.toLong(), serverHost = server.host)
            SpeedTestResult(
                downloadBps = downloadBps,
                uploadBps = uploadBps,
                serverId = server.id,
                serverName = server.name,
                serverHost = server.host,
                serverLocation = server.sponsor,
                succeeded = true,
                errorMessage = null,
                startedAt = startedAt,
                finishedAt = finishedAt
            )
        } catch (e: Exception) {
            invalidateCache()
            emit(SpeedTestDebugEvent.Phase.ERROR, SpeedTestDebugEvent.Status.FAIL, "Exception: ${e.message ?: e.javaClass.simpleName}")
            SpeedTestResult(
                downloadBps = null, uploadBps = null,
                serverId = null, serverName = null,
                serverHost = null, serverLocation = null,
                succeeded = false, errorMessage = e.message ?: "Unknown error",
                startedAt = startedAt, finishedAt = startedAt
            )
        }
    }

    /**
     * Clears the cached server, cached gauge, and gauge-attempted flag while
     * retaining the cached config. Also clears the debug ring buffer so the
     * next manual launch starts fresh. Used by the manual "Launch Test" button
     * before calling [runTest] to force fresh server selection and gauging
     * without re-fetching the rarely-changing config XML.
     */
    suspend fun reprimeServerAndGauge() {
        cachedServer = null
        cachedGaugeBps = null
        gaugeAttempted = false
        debugRingBuffer.clear()
    }

    /**
     * Reads and resets the [primedSinceLastInvalidation] flag (read-once
     * semantics via `AtomicBoolean.getAndSet`). Called by `RecordingService`
     * at session start: returns `true` if a successful manual prime has warmed
     * the cache since the last invalidation (warm handoff), `false` for a cold
     * start. The flag is always reset after the call so a second session
     * without a fresh manual prime cold-starts.
     */
    fun consumePrimeFlag(): Boolean = primedSinceLastInvalidation.getAndSet(false)

    internal fun computeBps(result: SpeedTestMeasurer.MeasurementResult): Long? {
        val warmupFreeSamples = result.samples.filter { it.warmupMs == 0L }
        if (warmupFreeSamples.isEmpty()) {
            if (result.postWarmupBytes <= 0 || result.postWarmupMs <= 0) return null
            val rawBps = (result.postWarmupBytes.toDouble() / result.postWarmupMs) * 8_000.0
            return (rawBps * OVERHEAD_COMPENSATION).toLong()
        }

        val sorted = warmupFreeSamples
            .map { it.throughputBps }
            .sorted()

        val totalSamples = sorted.size
        val discardSlowest = (totalSamples * DISCARD_SLOWEST_PCT / 100).coerceIn(0, totalSamples - 1)
        val discardFastest = (totalSamples * DISCARD_FASTEST_PCT / 100).coerceIn(0, totalSamples - 1 - discardSlowest)

        val retained = sorted.drop(discardSlowest).dropLast(discardFastest)
        if (retained.isEmpty()) {
            if (result.postWarmupBytes <= 0 || result.postWarmupMs <= 0) return null
            val rawBps = (result.postWarmupBytes.toDouble() / result.postWarmupMs) * 8_000.0
            return (rawBps * OVERHEAD_COMPENSATION).toLong()
        }

        val avgBps = retained.sum() / retained.size
        return (avgBps * OVERHEAD_COMPENSATION).toLong()
    }

    private fun buildErrorMessage(downloadFailed: Boolean, uploadFailed: Boolean): String {
        val parts = mutableListOf<String>()
        if (downloadFailed) parts.add("download")
        if (uploadFailed) parts.add("upload")
        return "No data transferred: ${parts.joinToString(", ")} measurement failed"
    }

    fun invalidateCache() {
        cachedServer = null
        cachedConfig = null
        cachedGaugeBps = null
        gaugeAttempted = false
        primedSinceLastInvalidation.set(false)
    }

    private suspend fun emit(
        phase: String,
        status: String,
        message: String,
        serverId: Long? = null,
        serverHost: String? = null,
        bytes: Long? = null
    ) {
        val event = SpeedTestDebugEvent(
            timestampMs = System.currentTimeMillis(),
            phase = phase,
            status = status,
            message = message,
            serverId = serverId,
            serverHost = serverHost,
            bytes = bytes
        )
        debugRingBuffer.append(event)
        if (status == SpeedTestDebugEvent.Status.WARN || status == SpeedTestDebugEvent.Status.FAIL) {
            Log.w(TAG, "[$phase] $message")
        } else if (BuildConfig.DEBUG) {
            Log.d(TAG, "[$phase] $message")
        }
    }

    private suspend fun isWifiActive(): Boolean = withContext(Dispatchers.IO) {
        try {
            val connectivityManager = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return@withContext false
            val network = connectivityManager.activeNetwork ?: return@withContext false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return@withContext false
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun fetchConfig(): SpeedTestProtocolConfig? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://www.speedtest.net/speedtest-config.php")
                .header("User-Agent", "Mozilla/5.0 (Linux; Android; en-us) (KHTML, like Gecko) speedtest-cli/2.1.4")
                .build()
            val response = httpClient.client.newCall(request).execute()
            response.use { resp ->
                if (resp.isSuccessful) {
                    resp.body?.byteStream()?.use { stream ->
                        SpeedTestConfigParser.parse(stream)
                    }
                } else {
                    null
                }
            }
        } catch (_: Exception) {
            null
        }
    }
}