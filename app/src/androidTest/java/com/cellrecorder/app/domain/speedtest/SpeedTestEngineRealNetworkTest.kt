package com.cellrecorder.app.domain.speedtest

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.cellrecorder.app.domain.speedtest.model.SpeedTestResult
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

/**
 * Real-network smoke test for [SpeedTestEngine] that exercises the full HTTP/protocol
 * code path against live speedtest.net endpoints: config fetch, server discovery, gauge
 * phase, multi-threaded download, multi-threaded upload, WiFi skip, and cache reuse.
 *
 * This is the only automated test that actually invokes [SpeedTestEngine.runTest] end-to-end.
 * The JVM unit tests in `app/src/test/` cover only the extracted pure-logic helpers
 * (`computeBps`, `computeDeadlineNs`, `createStreamingUploadBody` properties); they do not
 * exercise the OkHttp I/O, XML parsing, warmup/slice timing, or failure-detection paths.
 *
 * ## Why @Ignore by default
 *
 * The class is `@Ignore`'d to keep it out of CI because each test requires:
 * 1. **Real internet access** to `www.speedtest.net` / `c.speedtest.net` — CI emulators
 *    either lack internet or would fail at config fetch with a non-verified error.
 * 2. **An active cellular connection** — [SpeedTestEngine.runTest] auto-skips on WiFi
 *    (returns `SKIPPED_WIFI`), so the cellular tests use `assumeFalse(isWifiActive())`
 *    to skip cleanly rather than fail when the device is on WiFi.
 * 3. **~30 seconds per test cycle** (2s gauge + 11.5s download + 13s upload), making the
 *    full suite too slow for routine CI.
 *
 * These are verified, concrete constraints (not speculation): the engine's WiFi-skip
 * policy is documented in `speedtest/spec.md`, and the test durations follow directly
 * from the `DOWNLOAD_WARMUP_MS`/`UPLOAD_WARMUP_MS`/`testLengthSec` constants in
 * [SpeedTestMeasurer].
 *
 * ## How to run manually
 *
 * 1. Comment out the `@Ignore` annotation below.
 * 2. Connect a physical device with an active **cellular** data connection (tests will
 *    skip cleanly on WiFi; the dedicated WiFi-skip test will run on WiFi).
 * 3. Run:
 *    ```bash
 *    ./gradlew connectedDebugAndroidTest --tests "*.SpeedTestEngineRealNetworkTest"
 *    ```
 * 4. Inspect logcat under tag `SpeedTestEngineRealNetworkTest` for actual throughput
 *    values, selected server, and error messages.
 *
 * ## Scenarios under verification (per `speedtest/spec.md`)
 *
 * - Config Retrieval — GET to `speedtest-config.php`
 * - Server Discovery — server list fetch + Haversine selection + latency ping
 * - Server ID override — invalid preferred ID falls back to discovery
 * - Gauge Phase — 2s adaptive sizing download
 * - Download Measurement — multi-threaded GET, 1.5s warmup, 500ms slices, 10%/30% discard
 * - Upload Measurement — multi-threaded POST, 3s warmup, `Content-Length` declared
 * - WiFi Skip Policy — `SKIPPED_WIFI` result on WiFi
 * - Test Cadence / Cache Reuse — second call reuses server/gauge
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@LargeTest
@Ignore(
    "Real-network speedtest smoke test. Requires (1) internet access to speedtest.net " +
        "endpoints, (2) an active cellular connection (the engine auto-skips on WiFi per " +
        "speedtest/spec.md), and (3) ~30s per test cycle. None of these are available in " +
        "CI/emulator. Manually un-ignore on a physical device on cellular to verify the " +
        "engine end-to-end. See class KDoc for run instructions."
)
class SpeedTestEngineRealNetworkTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var speedTestEngine: SpeedTestEngine

    @Inject
    @ApplicationContext
    lateinit var appContext: Context

    @Before
    fun setUp() {
        hiltRule.inject()
        speedTestEngine.invalidateCache()
    }

    @After
    fun tearDown() {
        speedTestEngine.invalidateCache()
    }

    @Test
    fun `config fetch and server discovery select a real server`() = runBlocking {
        requireCellular()
        val result = speedTestEngine.runTest(uploadEnabled = false)
        skipIfNetworkSwitchedToWifi(result)
        logResult("discovery", result)

        assertTrue(
            "Config fetch should not fail: ${result.errorMessage}",
            result.errorMessage != "Config fetch failed"
        )
        assertTrue(
            "Server selection should not fail: ${result.errorMessage}",
            result.errorMessage != "Server selection failed"
        )
        assertNotNull("serverId should be selected by discovery", result.serverId)
        assertNotNull("serverName should be selected by discovery", result.serverName)
        assertNotNull("serverHost should be selected by discovery", result.serverHost)
        assertNotNull("serverLocation (sponsor) should be selected by discovery", result.serverLocation)
    }

    @Test
    fun `download measurement returns positive downloadBps`() = runBlocking {
        requireCellular()
        val result = speedTestEngine.runTest(uploadEnabled = false)
        skipIfNetworkSwitchedToWifi(result)
        logResult("download-only", result)

        assertTrue("Download should succeed: ${result.errorMessage}", result.succeeded)
        assertNotNull("downloadBps should be non-null on success", result.downloadBps)
        assertTrue(
            "downloadBps should be positive but was ${result.downloadBps}",
            result.downloadBps!! > 0L
        )
    }

    @Test
    fun `upload measurement returns positive uploadBps when enabled`() = runBlocking {
        requireCellular()
        val result = speedTestEngine.runTest(uploadEnabled = true)
        skipIfNetworkSwitchedToWifi(result)
        logResult("download+upload", result)

        assertTrue("Test should succeed: ${result.errorMessage}", result.succeeded)
        assertNotNull("downloadBps should be non-null", result.downloadBps)
        assertTrue(
            "downloadBps should be positive but was ${result.downloadBps}",
            result.downloadBps!! > 0L
        )
        assertNotNull("uploadBps should be non-null when uploadEnabled=true", result.uploadBps)
        assertTrue(
            "uploadBps should be positive but was ${result.uploadBps}",
            result.uploadBps!! > 0L
        )
    }

    @Test
    fun `end-to-end runTest succeeds with both download and upload`() = runBlocking {
        requireCellular()
        val result = speedTestEngine.runTest(uploadEnabled = true)
        skipIfNetworkSwitchedToWifi(result)
        logResult("end-to-end", result)

        assertTrue(
            "End-to-end test should succeed: ${result.errorMessage}",
            result.succeeded
        )
        assertEquals(
            "succeeded=true should have null errorMessage",
            null,
            result.errorMessage
        )
        assertNotNull("downloadBps", result.downloadBps)
        assertNotNull("uploadBps", result.uploadBps)
        assertNotNull("serverId", result.serverId)
        assertNotNull("serverName", result.serverName)
        assertNotNull("serverHost", result.serverHost)
        assertNotNull("serverLocation", result.serverLocation)
        // Successful test: timing fields set, finishedAt > startedAt
        assertTrue("startedAt should be set on success", result.startedAt > 0L)
        assertTrue("finishedAt should be set on success", result.finishedAt > 0L)
        assertTrue(
            "finishedAt ($result.finishedAt) should be > startedAt ($result.startedAt) on success",
            result.finishedAt > result.startedAt
        )
    }

    @Test
    fun `cache reuse returns same server on second call`() = runBlocking {
        requireCellular()
        val first = speedTestEngine.runTest(uploadEnabled = false)
        skipIfNetworkSwitchedToWifi(first)
        logResult("cache-reuse-1", first)
        assumeTrue(
            "First call must succeed to verify cache reuse (got: ${first.errorMessage})",
            first.succeeded
        )

        val second = speedTestEngine.runTest(uploadEnabled = false)
        skipIfNetworkSwitchedToWifi(second)
        logResult("cache-reuse-2", second)

        assertTrue("Second call should succeed: ${second.errorMessage}", second.succeeded)
        assertEquals(
            "Cached server should be reused on second call",
            first.serverId,
            second.serverId
        )
    }

    @Test
    fun `invalid preferred server ID falls back to automatic discovery`() = runBlocking {
        requireCellular()
        val result = speedTestEngine.runTest(
            preferredServerId = INVALID_SERVER_ID,
            uploadEnabled = false
        )
        skipIfNetworkSwitchedToWifi(result)
        logResult("invalid-preferred-id", result)

        assertTrue(
            "Invalid preferred ID should fall back to discovery, not fail with " +
                "'Server selection failed': ${result.errorMessage}",
            result.errorMessage != "Server selection failed"
        )
        assertNotNull(
            "Should still select a server via fallback discovery",
            result.serverId
        )
        assertNotEquals(
            "Selected server should differ from the invalid preferred ID " +
                "(proves fallback was exercised, not a collision with a real server)",
            INVALID_SERVER_ID,
            result.serverId
        )
    }

    @Test
    fun `WiFi skip path returns SKIPPED_WIFI when device is on WiFi`() = runBlocking {
        requireWifi()
        val result = speedTestEngine.runTest(uploadEnabled = false)
        logResult("wifi-skip", result)

        assertEquals(
            "Should skip with SKIPPED_WIFI errorMessage on WiFi",
            "SKIPPED_WIFI",
            result.errorMessage
        )
        assertFalse("Should not succeed on WiFi", result.succeeded)
        assertEquals(
            "downloadBps should be null on WiFi skip",
            null,
            result.downloadBps
        )
        assertEquals(
            "uploadBps should be null on WiFi skip",
            null,
            result.uploadBps
        )
        // Instant bail-out: finishedAt = startedAt (duration zero)
        assertTrue("startedAt should be set on WiFi skip", result.startedAt > 0L)
        assertEquals(
            "finishedAt should equal startedAt for instant bail-out (SKIPPED_WIFI)",
            result.startedAt,
            result.finishedAt
        )
    }

    private fun requireCellular() {
        val onWifi = isWifiActive()
        assumeTrue(
            "This test requires an active cellular connection (engine auto-skips on WiFi). " +
                "Current network is WiFi — skipping.",
            !onWifi
        )
    }

    private fun requireWifi() {
        val onWifi = isWifiActive()
        assumeTrue(
            "This test requires an active WiFi connection. " +
                "Current network is cellular — skipping.",
            onWifi
        )
    }

    /**
     * Guards against the network-type race: if the device switched from cellular to WiFi
     * between [requireCellular] and the [SpeedTestEngine.runTest] call, the engine will
     * return `SKIPPED_WIFI`. Skip cleanly via assumption instead of failing the assertion.
     */
    private fun skipIfNetworkSwitchedToWifi(result: SpeedTestResult) {
        assumeTrue(
            "Network switched to WiFi mid-test (engine returned SKIPPED_WIFI) — skipping",
            result.errorMessage != "SKIPPED_WIFI"
        )
    }

    private fun isWifiActive(): Boolean {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    private fun logResult(label: String, result: SpeedTestResult) {
        val dlMbps = result.downloadBps?.let { it / 1_000_000.0 }
        val ulMbps = result.uploadBps?.let { it / 1_000_000.0 }
        Log.i(
            TAG,
            "[$label] succeeded=${result.succeeded} " +
                "server=${result.serverName}/${result.serverHost} " +
                "download=${dlMbps?.let { "%.2f Mbps".format(it) } ?: "null"} " +
                "upload=${ulMbps?.let { "%.2f Mbps".format(it) } ?: "null"} " +
                "error=${result.errorMessage ?: "none"}"
        )
    }

    companion object {
        private const val TAG = "SpeedTestEngineRealNetworkTest"
        // Int.MAX_VALUE is effectively impossible for speedtest.net to assign as a real
        // server ID, guaranteeing the fallback-to-discovery path is exercised.
        private const val INVALID_SERVER_ID: Int = Int.MAX_VALUE
    }
}
