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
import com.cellrecorder.app.data.repository.SessionMarkerRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
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
}
