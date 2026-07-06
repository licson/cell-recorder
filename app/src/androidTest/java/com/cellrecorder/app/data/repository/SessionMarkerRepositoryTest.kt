package com.cellrecorder.app.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.cellrecorder.app.data.local.AppDatabase
import com.cellrecorder.app.domain.model.MarkerType
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@MediumTest
class SessionMarkerRepositoryTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var sessionMarkerRepository: SessionMarkerRepository

    @Inject
    lateinit var recentMarkerLabelRepository: RecentMarkerLabelRepository

    @Inject
    lateinit var db: AppDatabase

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun insertMarker_computesSeqPerSession() = runBlocking {
        val session1 = db.sessionDao().insert(com.cellrecorder.app.data.local.entity.SessionEntity(name = "S1", createdAt = 1000L))
        val session2 = db.sessionDao().insert(com.cellrecorder.app.data.local.entity.SessionEntity(name = "S2", createdAt = 2000L))

        sessionMarkerRepository.insertMarker(session1, MarkerType.NOTE, null)
        sessionMarkerRepository.insertMarker(session1, MarkerType.NOTE, null)
        sessionMarkerRepository.insertMarker(session2, MarkerType.NOTE, null)

        val s1 = sessionMarkerRepository.getMarkersForSession(session1).first()
        assertEquals(2, s1.size)
        assertEquals(1, s1[0].seq)
        assertEquals(2, s1[1].seq)

        val s2 = sessionMarkerRepository.getMarkersForSession(session2).first()
        assertEquals(1, s2.size)
        assertEquals(1, s2[0].seq)
    }

    @Test
    fun insertMarker_upsertsRecentLabelWhenPresent() = runBlocking {
        val session = db.sessionDao().insert(com.cellrecorder.app.data.local.entity.SessionEntity(name = "S", createdAt = 1000L))

        sessionMarkerRepository.insertMarker(session, MarkerType.NOTE, "Station")
        sessionMarkerRepository.insertMarker(session, MarkerType.NOTE, "Station")

        val recents = recentMarkerLabelRepository.getByTypeOrdered(MarkerType.NOTE)
        assertEquals(1, recents.size)
        assertEquals("Station", recents[0].label)
        assertEquals(2, recents[0].useCount)
    }

    @Test
    fun insertMarker_skipsRecentsForBlankLabel() = runBlocking {
        val session = db.sessionDao().insert(com.cellrecorder.app.data.local.entity.SessionEntity(name = "S", createdAt = 1000L))

        sessionMarkerRepository.insertMarker(session, MarkerType.NOTE, "")
        sessionMarkerRepository.insertMarker(session, MarkerType.NOTE, null)

        val recents = recentMarkerLabelRepository.getByTypeOrdered(MarkerType.NOTE)
        assertTrue(recents.isEmpty())
    }

    @Test
    fun updateMarker_preservesSeqAndSessionAndTimestamp() = runBlocking {
        val session = db.sessionDao().insert(com.cellrecorder.app.data.local.entity.SessionEntity(name = "S", createdAt = 1000L))
        val id = sessionMarkerRepository.insertMarker(session, MarkerType.NOTE, "Before")

        val original = sessionMarkerRepository.getMarkersForSession(session).first().first()

        sessionMarkerRepository.updateMarker(id, MarkerType.WAYPOINT, "After")

        val updated = sessionMarkerRepository.getMarkersForSession(session).first().first()
        assertEquals(id, updated.id)
        assertEquals(original.sessionId, updated.sessionId)
        assertEquals(original.timestamp, updated.timestamp)
        assertEquals(original.seq, updated.seq)
        assertEquals("WAYPOINT", updated.type)
        assertEquals("After", updated.label)
    }

    @Test
    fun updateMarker_upsertsNewLabelWithoutDecrementingOldLabel() = runBlocking {
        val session = db.sessionDao().insert(com.cellrecorder.app.data.local.entity.SessionEntity(name = "S", createdAt = 1000L))
        val id = sessionMarkerRepository.insertMarker(session, MarkerType.NOTE, "Old")

        sessionMarkerRepository.updateMarker(id, MarkerType.WAYPOINT, "New")

        val noteRecents = recentMarkerLabelRepository.getByTypeOrdered(MarkerType.NOTE)
        val waypointRecents = recentMarkerLabelRepository.getByTypeOrdered(MarkerType.WAYPOINT)

        assertEquals(1, noteRecents.size)
        assertEquals("Old", noteRecents[0].label)
        assertEquals(1, noteRecents[0].useCount)

        assertEquals(1, waypointRecents.size)
        assertEquals("New", waypointRecents[0].label)
        assertEquals(1, waypointRecents[0].useCount)
    }

    @Test
    fun deleteMarker() = runBlocking {
        val session = db.sessionDao().insert(com.cellrecorder.app.data.local.entity.SessionEntity(name = "S", createdAt = 1000L))
        val id = sessionMarkerRepository.insertMarker(session, MarkerType.NOTE, null)

        sessionMarkerRepository.deleteMarker(id)

        val markers = sessionMarkerRepository.getMarkersForSession(session).first()
        assertTrue(markers.isEmpty())
    }

    @Test
    fun deleteMarker_preservesRecents() = runBlocking {
        val session = db.sessionDao().insert(com.cellrecorder.app.data.local.entity.SessionEntity(name = "S", createdAt = 1000L))
        val id = sessionMarkerRepository.insertMarker(session, MarkerType.NOTE, "Keep")

        sessionMarkerRepository.deleteMarker(id)

        val recents = recentMarkerLabelRepository.getByTypeOrdered(MarkerType.NOTE)
        assertEquals(1, recents.size)
    }
}
