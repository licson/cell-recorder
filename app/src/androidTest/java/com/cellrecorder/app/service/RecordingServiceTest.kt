package com.cellrecorder.app.service

import android.Manifest
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.test.rule.GrantPermissionRule
import com.cellrecorder.app.data.local.AppDatabase
import com.cellrecorder.app.data.local.entity.SessionEntity
import com.cellrecorder.app.data.local.entity.SpeedTestRecordEntity
import com.cellrecorder.app.data.repository.SessionMarkerRepository
import com.cellrecorder.app.data.repository.SpeedTestRecordRepository
import com.cellrecorder.app.domain.speedtest.SpeedTestEngine
import com.cellrecorder.app.domain.speedtest.model.SpeedTestResult
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

@RunWith(AndroidJUnit4::class)
@HiltAndroidTest
@MediumTest
class RecordingServiceTest {

    @get:Rule(order = 0)
    val permissionRule = GrantPermissionRule.grant(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_BACKGROUND_LOCATION,
        Manifest.permission.FOREGROUND_SERVICE_LOCATION,
        Manifest.permission.POST_NOTIFICATIONS,
        Manifest.permission.READ_PHONE_STATE
    )

    @get:Rule(order = 1)
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var db: AppDatabase

    @Inject
    lateinit var sessionMarkerRepository: SessionMarkerRepository

    @Inject
    lateinit var recordingStateManager: RecordingStateManager

    @Inject
    lateinit var speedTestEngine: SpeedTestEngine

    @Inject
    lateinit var speedTestRecordRepository: SpeedTestRecordRepository

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun markNoteAction_inTunnelMode_createsNoteMarker() = runBlocking {
        val sessionId = db.sessionDao().insert(SessionEntity(name = "Tunnel", createdAt = 1000L))
        val context = ApplicationProvider.getApplicationContext<Context>()

        RecordingService.start(context, sessionId, "TUNNEL")
        waitForState { it?.isRecording == true }

        sendMarkNote(context, sessionId)
        waitForMarker(sessionId)

        val markers = sessionMarkerRepository.getMarkersForSession(sessionId).first()
        assertEquals(1, markers.size)
        assertEquals("NOTE", markers[0].type)
        assertTrue(markers[0].label?.startsWith("NOTE #1") == true)

        RecordingService.stop(context)
    }

    @Test
    fun markNoteAction_inOutdoorMode_createsNoteMarker() = runBlocking {
        val sessionId = db.sessionDao().insert(SessionEntity(name = "Outdoor", createdAt = 1000L))
        val context = ApplicationProvider.getApplicationContext<Context>()

        RecordingService.start(context, sessionId, "OUTDOOR")
        waitForState { it?.isRecording == true }

        sendMarkNote(context, sessionId)
        waitForMarker(sessionId)

        val markers = sessionMarkerRepository.getMarkersForSession(sessionId).first()
        assertEquals(1, markers.size)
        assertEquals("NOTE", markers[0].type)

        RecordingService.stop(context)
    }

    @Test
    fun markNoteAction_inIndoorMode_createsNoteMarker() = runBlocking {
        val sessionId = db.sessionDao().insert(SessionEntity(name = "Indoor", createdAt = 1000L))
        val context = ApplicationProvider.getApplicationContext<Context>()

        RecordingService.start(context, sessionId, "INDOOR")
        waitForState { it?.isRecording == true }

        sendMarkNote(context, sessionId)
        waitForMarker(sessionId)

        val markers = sessionMarkerRepository.getMarkersForSession(sessionId).first()
        assertEquals(1, markers.size)
        assertEquals("NOTE", markers[0].type)

        RecordingService.stop(context)
    }

    @Test
    fun tunnelModeRecordingState_reflectsTunnel() = runBlocking {
        val sessionId = db.sessionDao().insert(SessionEntity(name = "Tunnel", createdAt = 1000L))
        val context = ApplicationProvider.getApplicationContext<Context>()

        RecordingService.start(context, sessionId, "TUNNEL")
        waitForState { it?.recordingMode == "TUNNEL" && it.isRecording }

        val state = recordingStateManager.state.value
        assertEquals("TUNNEL", state?.recordingMode)

        RecordingService.stop(context)
    }

    private fun sendMarkNote(context: Context, sessionId: Long) {
        context.startForegroundService(Intent(context, RecordingService::class.java).apply {
            action = RecordingService.ACTION_MARK_NOTE
            putExtra(RecordingService.EXTRA_SESSION_ID, sessionId)
        })
    }

    private suspend fun waitForState(predicate: (RecordingState?) -> Boolean) {
        withTimeoutOrNull(5000) {
            recordingStateManager.state.first { predicate(it) }
        }
    }

    private suspend fun waitForMarker(sessionId: Long) {
        withTimeoutOrNull(5000) {
            sessionMarkerRepository.getMarkersForSession(sessionId).first { it.isNotEmpty() }
        }
    }

    @Test
    fun consumePrimeFlag_returnsFalseAndResets_onColdStart() = runBlocking {
        // After setUp, the engine has no prior prime. invalidateCache ensures clean state.
        speedTestEngine.invalidateCache()
        // Cold start: flag is false → consumePrimeFlag returns false → service would invalidateCache
        assertFalse("consumePrimeFlag should return false on cold start", speedTestEngine.consumePrimeFlag())
        // Read-once: second call also false
        assertFalse("consumePrimeFlag should return false on second call", speedTestEngine.consumePrimeFlag())
    }

    @Test
    fun invalidateCache_resetsPrimeFlag() = runBlocking {
        // invalidateCache sets the flag to false (simulating failure or cold-start)
        speedTestEngine.invalidateCache()
        assertFalse(speedTestEngine.consumePrimeFlag())
    }

    @Test
    fun finishedAt_persistsFromResult_onInsert() = runBlocking {
        val sessionId = db.sessionDao().insert(SessionEntity(name = "FinishedAt Test", createdAt = 1000L))
        val testStart = 5000L
        val testFinish = 9000L

        // Simulate what RecordingService does: insert entity with finishedAt from result
        val result = SpeedTestResult(
            downloadBps = 100_000_000L,
            uploadBps = 20_000_000L,
            serverId = 42,
            serverName = "srv",
            serverHost = "host",
            serverLocation = "loc",
            succeeded = true,
            errorMessage = null,
            startedAt = testStart,
            finishedAt = testFinish
        )
        speedTestRecordRepository.insert(SpeedTestRecordEntity(
            sessionId = sessionId,
            timestamp = testStart,
            finishedAt = result.finishedAt,
            downloadBps = result.downloadBps,
            uploadBps = result.uploadBps,
            serverName = result.serverName,
            serverHost = result.serverHost,
            serverLocation = result.serverLocation,
            serverId = result.serverId?.toLong(),
            succeeded = result.succeeded,
            errorMessage = result.errorMessage,
            networkType = "CELLULAR",
            dataSimSlotIndex = null,
            ratAtTest = null,
            rsrpAtTest = null,
            bandAtTest = null
        ))

        val records = speedTestRecordRepository.getBySessionIdOnce(sessionId)
        assertEquals(1, records.size)
        assertEquals(testStart, records[0].timestamp)
        assertEquals(testFinish, records[0].finishedAt)
    }

    @Test
    fun finishedAt_equalsTimestamp_forSkippedWifiRecord() = runBlocking {
        val sessionId = db.sessionDao().insert(SessionEntity(name = "WiFi Skip Test", createdAt = 1000L))
        val testStart = 7000L

        // Simulate SKIPPED_WIFI: finishedAt = startedAt (instant bail-out)
        val result = SpeedTestResult(
            downloadBps = null,
            uploadBps = null,
            serverId = null,
            serverName = null,
            serverHost = null,
            serverLocation = null,
            succeeded = false,
            errorMessage = "SKIPPED_WIFI",
            startedAt = testStart,
            finishedAt = testStart
        )
        speedTestRecordRepository.insert(SpeedTestRecordEntity(
            sessionId = sessionId,
            timestamp = testStart,
            finishedAt = result.finishedAt,
            succeeded = result.succeeded,
            errorMessage = result.errorMessage,
            networkType = "WIFI",
            downloadBps = null,
            uploadBps = null,
            serverName = null,
            serverHost = null,
            serverLocation = null,
            serverId = null,
            dataSimSlotIndex = null,
            ratAtTest = null,
            rsrpAtTest = null,
            bandAtTest = null
        ))

        val records = speedTestRecordRepository.getBySessionIdOnce(sessionId)
        assertEquals(1, records.size)
        assertEquals(testStart, records[0].timestamp)
        assertEquals(testStart, records[0].finishedAt)
        assertEquals("SKIPPED_WIFI", records[0].errorMessage)
    }

    @Test
    fun consumePrimeFlag_returnsFalse_afterSessionOnlySuccess() = runBlocking {
        // Verify that runTest without primeOnSuccess=true does NOT set the flag.
        // This tests the spec: "Cold start when no successful manual prime".
        speedTestEngine.invalidateCache()
        // Call runTest without primeOnSuccess (defaults to false).
        // On WiFi or without cellular, it'll bail out (SKIPPED_WIFI or similar),
        // but the flag should remain false regardless.
        val result = speedTestEngine.runTest(uploadEnabled = false)
        // Flag should NOT be set (primeOnSuccess was not passed)
        assertFalse("consumePrimeFlag should be false after session-only runTest (no primeOnSuccess)", speedTestEngine.consumePrimeFlag())
    }
}
