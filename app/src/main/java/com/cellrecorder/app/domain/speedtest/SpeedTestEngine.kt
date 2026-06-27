package com.cellrecorder.app.domain.speedtest

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.cellrecorder.app.domain.speedtest.model.SpeedTestResult
import com.cellrecorder.app.domain.speedtest.model.SpeedTestServerInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpeedTestEngine @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val httpClient: SpeedTestHttpClient
) {

    @Volatile
    private var cachedServer: SpeedTestServerInfo? = null
    @Volatile
    private var cachedConfig: SpeedTestProtocolConfig? = null
    @Volatile
    private var cachedGaugeBps: Long? = null
    @Volatile
    private var gaugeAttempted: Boolean = false

    companion object {
        private const val OVERHEAD_COMPENSATION = 1.06
        private const val DISCARD_FASTEST_PCT = 10
        private const val DISCARD_SLOWEST_PCT = 30
    }

    suspend fun runTest(
        preferredServerId: Int? = null,
        uploadEnabled: Boolean = true,
        onStatus: (String) -> Unit = {}
    ): SpeedTestResult = withContext(Dispatchers.IO) {
        try {
            if (isWifiActive()) {
                return@withContext SpeedTestResult(
                    downloadBps = null, uploadBps = null,
                    serverId = null, serverName = null,
                    serverHost = null, serverLocation = null,
                    succeeded = false, errorMessage = "SKIPPED_WIFI"
                )
            }

            if (cachedConfig == null) {
                cachedConfig = fetchConfig()
            }
            val config = cachedConfig ?: return@withContext SpeedTestResult(
                downloadBps = null, uploadBps = null,
                serverId = null, serverName = null,
                serverHost = null, serverLocation = null,
                succeeded = false, errorMessage = "Config fetch failed"
            )

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
            val server = cachedServer ?: return@withContext SpeedTestResult(
                downloadBps = null, uploadBps = null,
                serverId = null, serverName = null,
                serverHost = null, serverLocation = null,
                succeeded = false, errorMessage = "Server selection failed"
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
            }

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
                onStatus("Uploading")
                val uploadResult = SpeedTestMeasurer.measureUpload(
                    serverUrl = server.url,
                    uploadSizes = config.uploadSizes,
                    uploadCount = config.uploadCount,
                    uploadMax = config.uploadMax,
                    uploadThreads = config.upload.threads,
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
                return@withContext SpeedTestResult(
                    downloadBps = if (downloadFailed) null else downloadBps,
                    uploadBps = if (uploadEnabled && uploadFailed) null else uploadBps,
                    serverId = server.id,
                    serverName = server.name,
                    serverHost = server.host,
                    serverLocation = server.sponsor,
                    succeeded = false,
                    errorMessage = buildErrorMessage(downloadFailed, uploadFailed && uploadEnabled)
                )
            }

            onStatus("Completed")
            SpeedTestResult(
                downloadBps = downloadBps,
                uploadBps = uploadBps,
                serverId = server.id,
                serverName = server.name,
                serverHost = server.host,
                serverLocation = server.sponsor,
                succeeded = true,
                errorMessage = null
            )
        } catch (e: Exception) {
            SpeedTestResult(
                downloadBps = null, uploadBps = null,
                serverId = null, serverName = null,
                serverHost = null, serverLocation = null,
                succeeded = false, errorMessage = e.message ?: "Unknown error"
            )
        }
    }

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