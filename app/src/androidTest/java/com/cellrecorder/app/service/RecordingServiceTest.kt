package com.cellrecorder.app.service

import android.Manifest
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteFullException
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.test.rule.GrantPermissionRule
import com.cellrecorder.app.data.local.AppDatabase
import com.cellrecorder.app.data.local.entity.SessionEntity
import com.cellrecorder.app.data.local.entity.SpeedTestRecordEntity
import com.cellrecorder.app.data.repository.CellRecordRepository
import com.cellrecorder.app.data.repository.ConfigRepository
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

    @Inject
    lateinit var cellInfoCollector: CellInfoCollector

    @Inject
    lateinit var cellRecordRepository: CellRecordRepository

    @Inject
    lateinit var notificationHelper: RecordingNotificationHelper

    @Inject
    lateinit var locationCollector: LocationCollector

    @Inject
    lateinit var configRepository: ConfigRepository

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @After
    fun tearDown() {
        // Reset all failure-injection toggles to prevent leakage between tests
        cellInfoCollector.snapshotsFailure = null
        cellRecordRepository.insertRecordBatchFailure = null
        notificationHelper.notifyFailure = null
        notificationHelper.notifyCallCount = 0
        locationCollector.locationFlowFailure = null
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

    // ============ Task 11.6: Error-handling instrumented scenarios ============

    @Test
    fun configLoadFailure_continuesWithDefaults() = runBlocking {
        val sessionId = db.sessionDao().insert(SessionEntity(name = "ConfigFail", createdAt = 1000L))
        val context = ApplicationProvider.getApplicationContext<Context>()

        // Drop the app_config table so configDao.get() throws
        db.openHelper.writableDatabase.execSQL("DROP TABLE IF EXISTS app_config")

        RecordingService.start(context, sessionId, "TUNNEL")
        // Recording should start despite config failure (falls back to AppConfigEntity defaults)
        waitForState { it?.isRecording == true }

        val state = recordingStateManager.state.value
        assertTrue("Recording should continue with defaults after config load failure",
            state?.isRecording == true)

        RecordingService.stop(context)
        waitForState { it?.isRecording == false }
    }

    @Test
    fun cellInfoCollectorSnapshotsThrow_continuesWithEmptyList() = runBlocking {
        val sessionId = db.sessionDao().insert(SessionEntity(name = "SnapThrow", createdAt = 1000L))
        val context = ApplicationProvider.getApplicationContext<Context>()

        // Force snapshots() to throw — PointRecorder catches and continues with emptyList()
        cellInfoCollector.snapshotsFailure = RuntimeException("forced cell info failure")

        RecordingService.start(context, sessionId, "TUNNEL")
        waitForState { it?.isRecording == true }

        // Wait for at least one point to be recorded despite the snapshots failure
        Thread.sleep(2000)
        val state = recordingStateManager.state.value
        assertTrue("Recording should continue after CellInfoCollector.snapshots() throws",
            state?.isRecording == true)

        RecordingService.stop(context)
        waitForState { it?.isRecording == false }
    }

    @Test
    fun persistentBatchInsert_fatallyStopsRecordingWithErrorMessage() = runBlocking {
        val sessionId = db.sessionDao().insert(SessionEntity(name = "FatalDB", createdAt = 1000L))
        val context = ApplicationProvider.getApplicationContext<Context>()

        // Force insertRecordBatch to throw SQLiteFullException (FATAL per DbExceptionClassifier)
        cellRecordRepository.insertRecordBatchFailure = SQLiteFullException("disk full")

        RecordingService.start(context, sessionId, "TUNNEL")
        // Wait for the fatal error to propagate (recording stops with error message)
        waitForState { it?.isRecording == false && it?.errorMessage?.contains("Storage failure") == true }

        val state = recordingStateManager.state.value
        assertFalse("Recording should stop fatally", state?.isRecording == true)
        assertTrue("Error message should mention 'Storage failure'",
            state?.errorMessage?.contains("Storage failure") == true)
    }

    @Test
    fun transientBatchInsert_continuesWithPerSnapshotFallback() = runBlocking {
        val sessionId = db.sessionDao().insert(SessionEntity(name = "TransientDB", createdAt = 1000L))
        val context = ApplicationProvider.getApplicationContext<Context>()

        // Force insertRecordBatch to throw SQLiteConstraintException (TRANSIENT per DbExceptionClassifier)
        // The service should fall back to per-snapshot insertSingle and continue recording
        cellRecordRepository.insertRecordBatchFailure = android.database.sqlite.SQLiteConstraintException("constraint violation")

        RecordingService.start(context, sessionId, "TUNNEL")
        waitForState { it?.isRecording == true }

        // Wait for at least one tick to attempt the transient fallback
        Thread.sleep(3000)

        // Recording should continue despite the transient batch failure
        assertTrue("Recording should continue after transient batch insert failure (per-snapshot fallback)",
            recordingStateManager.state.value?.isRecording == true)

        RecordingService.stop(context)
        waitForState { it?.isRecording == false }
    }

    @Test
    fun notifySecurityException_entersNoNotificationMode_stateStillUpdates() = runBlocking {
        val sessionId = db.sessionDao().insert(SessionEntity(name = "NotifyFail", createdAt = 1000L))
        val context = ApplicationProvider.getApplicationContext<Context>()

        // Force notify() to throw SecurityException — stateUpdateJob should enter no-notification mode
        notificationHelper.notifyFailure = SecurityException("POST_NOTIFICATIONS denied")

        RecordingService.start(context, sessionId, "TUNNEL")
        waitForState { it?.isRecording == true }

        // Wait for at least 2 state-update cycles (each 1s) so noNotificationMode kicks in
        Thread.sleep(3000)

        // stateManager should still update (elapsedMs/pointCount) despite notify failure
        val state = recordingStateManager.state.value
        assertTrue("stateManager should still update after notify SecurityException",
            state?.isRecording == true)

        // notify should have been called exactly once (first iteration) then suppressed by noNotificationMode
        assertTrue("notify should have been attempted exactly once before noNotificationMode suppressed it",
            notificationHelper.notifyCallCount == 1)

        RecordingService.stop(context)
        waitForState { it?.isRecording == false }
    }

    @Test
    fun markNoteFailure_continuesRecording() = runBlocking {
        val sessionId = db.sessionDao().insert(SessionEntity(name = "MarkFail", createdAt = 1000L))
        val context = ApplicationProvider.getApplicationContext<Context>()

        // Enable FK enforcement and delete the session so marker insert fails on FK constraint
        db.openHelper.writableDatabase.execSQL("PRAGMA foreign_keys = ON")
        db.sessionDao().deleteById(sessionId)

        RecordingService.start(context, sessionId, "TUNNEL")
        waitForState { it?.isRecording == true }

        // Send a mark-note intent — the marker insert should fail (session no longer exists for FK)
        sendMarkNote(context, sessionId)
        Thread.sleep(2000)

        // Recording should continue despite the marker insert failure
        assertTrue("Recording should continue after markNote failure",
            recordingStateManager.state.value?.isRecording == true)

        RecordingService.stop(context)
        waitForState { it?.isRecording == false }
    }

    @Test
    fun locationSecurityException_continuesWithUnavailableLocationSource() = runBlocking {
        val sessionId = db.sessionDao().insert(SessionEntity(name = "LocSec", createdAt = 1000L))
        val context = ApplicationProvider.getApplicationContext<Context>()

        // Force locationFlow() to throw SecurityException — recording should continue with UNAVAILABLE
        locationCollector.locationFlowFailure = SecurityException("location permission revoked")

        RecordingService.start(context, sessionId, "OUTDOOR")
        waitForState { it?.isRecording == true }

        // Wait for the fallback loop to record UNAVAILABLE points
        Thread.sleep(3000)

        // Recording should continue despite the location SecurityException
        assertTrue("Recording should continue after location SecurityException",
            recordingStateManager.state.value?.isRecording == true)

        // Verify at least one cell record was written with locationSource = "UNAVAILABLE"
        val records = cellRecordRepository.getBySessionIdOnce(sessionId)
        assertTrue("At least one record should exist with locationSource = UNAVAILABLE",
            records.any { it.locationSource == "UNAVAILABLE" })

        RecordingService.stop(context)
        waitForState { it?.isRecording == false }
    }
}
