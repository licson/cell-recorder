package com.cellrecorder.app.domain.speedtest

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.cellrecorder.app.domain.speedtest.model.SpeedTestResult
import com.cellrecorder.app.domain.speedtest.model.SpeedTestServerInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpeedTestEngine @Inject constructor(
    @ApplicationContext private val appContext: Context
) {

    private var cachedServer: SpeedTestServerInfo? = null
    private var cachedConfig: SpeedTestProtocolConfig? = null

    suspend fun runTest(
        preferredServerId: Int? = null,
        uploadEnabled: Boolean = true
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
                cachedServer = SpeedTestServerSelector.fetchAndSelect(
                    clientLat = config.client.lat,
                    clientLon = config.client.lon,
                    ignoreIds = config.server.ignoreIds,
                    preferredServerId = preferredServerId,
                    downloadThreads = config.downloadThreads,
                    secure = true
                )
            }
            val server = cachedServer ?: return@withContext SpeedTestResult(
                downloadBps = null, uploadBps = null,
                serverId = null, serverName = null,
                serverHost = null, serverLocation = null,
                succeeded = false, errorMessage = "Server selection failed"
            )

            val serverBaseUrl = server.url.substringBeforeLast("/")

            val downloadResult = SpeedTestMeasurer.measureDownload(
                serverBaseUrl = serverBaseUrl,
                threadsPerUrl = config.download.threadsPerUrl,
                downloadThreads = config.downloadThreads,
                testLengthSec = config.download.testLengthSec,
                secure = true
            )

            val downloadBps = if (downloadResult.elapsedMs > 0) {
                ((downloadResult.bytesTransferred.toDouble() / downloadResult.elapsedMs) * 8_000.0).toLong()
            } else null

            var uploadBps: Long? = null
            if (uploadEnabled) {
                val uploadResult = SpeedTestMeasurer.measureUpload(
                    serverUrl = server.url,
                    uploadSizes = config.uploadSizes,
                    uploadCount = config.uploadCount,
                    uploadMax = config.uploadMax,
                    uploadThreads = config.upload.threads,
                    testLengthSec = config.upload.testLengthSec,
                    secure = true
                )
                uploadBps = if (uploadResult.elapsedMs > 0) {
                    ((uploadResult.bytesTransferred.toDouble() / uploadResult.elapsedMs) * 8_000.0).toLong()
                } else null
            }

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
            cachedServer = null
            cachedConfig = null
            SpeedTestResult(
                downloadBps = null, uploadBps = null,
                serverId = null, serverName = null,
                serverHost = null, serverLocation = null,
                succeeded = false, errorMessage = e.message ?: "Unknown error"
            )
        }
    }

    fun invalidateCache() {
        cachedServer = null
        cachedConfig = null
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
            val url = java.net.URL("https://www.speedtest.net/speedtest-config.php")
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android; en-us) (KHTML, like Gecko) speedtest-cli/2.1.4")
            if (conn.responseCode == 200) {
                val config = SpeedTestConfigParser.parse(conn.inputStream)
                conn.disconnect()
                config
            } else {
                conn.disconnect()
                null
            }
        } catch (_: Exception) {
            null
        }
    }
}