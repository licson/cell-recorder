package com.cellrecorder.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cellrecorder.app.data.local.dao.RecentMarkerLabelDao
import com.cellrecorder.app.data.local.dao.SessionMarkerDao
import com.cellrecorder.app.data.local.entity.RecentMarkerLabelEntity
import com.cellrecorder.app.data.local.entity.SessionEntity
import com.cellrecorder.app.data.local.entity.SessionMarkerEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class SessionMarkerDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var sessionMarkerDao: SessionMarkerDao
    private lateinit var sessionDao: com.cellrecorder.app.data.local.dao.SessionDao
    private var sessionId: Long = 0

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        sessionMarkerDao = db.sessionMarkerDao()
        sessionDao = db.sessionDao()
        sessionId = runBlocking {
            sessionDao.insert(SessionEntity(name = "Marker Session", createdAt = 1000L))
        }
    }

    @After
    @Throws(IOException::class)
    fun tearDown() {
        db.close()
    }

    @Test
    fun insertAndGetBySessionId() = runBlocking {
        val id = sessionMarkerDao.insert(
            SessionMarkerEntity(sessionId = sessionId, timestamp = 1000L, seq = 1, type = "NOTE", label = "Note 1")
        )
        assertTrue(id > 0)

        val markers = sessionMarkerDao.getBySessionId(sessionId).first()
        assertEquals(1, markers.size)
        val marker = markers[0]
        assertEquals(id, marker.id)
        assertEquals(sessionId, marker.sessionId)
        assertEquals(1000L, marker.timestamp)
        assertEquals(1, marker.seq)
        assertEquals("NOTE", marker.type)
        assertEquals("Note 1", marker.label)
    }

    @Test
    fun update_updatesRowInPlaceWithoutChangingSeq() = runBlocking {
        val id = sessionMarkerDao.insert(
            SessionMarkerEntity(sessionId = sessionId, timestamp = 1000L, seq = 1, type = "NOTE", label = "Before")
        )
        val inserted = sessionMarkerDao.getById(id)!!

        sessionMarkerDao.update(inserted.copy(type = "WAYPOINT", label = "After"))

        val updated = sessionMarkerDao.getById(id)!!
        assertEquals("WAYPOINT", updated.type)
        assertEquals("After", updated.label)
        assertEquals(1, updated.seq)
        assertEquals(inserted.timestamp, updated.timestamp)
        assertEquals(sessionId, updated.sessionId)
    }

    @Test
    fun seqIncrementsPerSession() = runBlocking {
        val session2Id = sessionDao.insert(SessionEntity(name = "Second", createdAt = 2000L))

        sessionMarkerDao.insert(SessionMarkerEntity(sessionId = sessionId, timestamp = 1000L, seq = 1, type = "NOTE", label = null))
        sessionMarkerDao.insert(SessionMarkerEntity(sessionId = sessionId, timestamp = 2000L, seq = 2, type = "NOTE", label = null))

        sessionMarkerDao.insert(SessionMarkerEntity(sessionId = session2Id, timestamp = 1000L, seq = 1, type = "NOTE", label = null))

        val markers1 = sessionMarkerDao.getBySessionId(sessionId).first()
        assertEquals(2, markers1.size)
        assertEquals(1, markers1[0].seq)
        assertEquals(2, markers1[1].seq)

        val markers2 = sessionMarkerDao.getBySessionId(session2Id).first()
        assertEquals(1, markers2.size)
        assertEquals(1, markers2[0].seq)
    }

    @Test
    fun cascadeDeleteOnSessionDelete() = runBlocking {
        sessionMarkerDao.insert(SessionMarkerEntity(sessionId = sessionId, timestamp = 1000L, seq = 1, type = "NOTE", label = null))
        sessionMarkerDao.insert(SessionMarkerEntity(sessionId = sessionId, timestamp = 2000L, seq = 2, type = "NOTE", label = null))

        sessionDao.deleteById(sessionId)

        val markers = sessionMarkerDao.getBySessionId(sessionId).first()
        assertTrue(markers.isEmpty())
    }

    @Test
    fun countBySessionId() = runBlocking {
        sessionMarkerDao.insert(SessionMarkerEntity(sessionId = sessionId, timestamp = 1000L, seq = 1, type = "NOTE", label = null))
        sessionMarkerDao.insert(SessionMarkerEntity(sessionId = sessionId, timestamp = 2000L, seq = 2, type = "NOTE", label = null))

        assertEquals(2, sessionMarkerDao.countBySessionId(sessionId))
    }

    @Test
    fun deleteById() = runBlocking {
        val id = sessionMarkerDao.insert(
            SessionMarkerEntity(sessionId = sessionId, timestamp = 1000L, seq = 1, type = "NOTE", label = null)
        )
        sessionMarkerDao.deleteById(id)

        val markers = sessionMarkerDao.getBySessionId(sessionId).first()
        assertTrue(markers.isEmpty())
    }

    @Test
    fun getBySessionId_ordersByTimestamp() = runBlocking {
        sessionMarkerDao.insert(SessionMarkerEntity(sessionId = sessionId, timestamp = 3000L, seq = 3, type = "NOTE", label = null))
        sessionMarkerDao.insert(SessionMarkerEntity(sessionId = sessionId, timestamp = 1000L, seq = 1, type = "NOTE", label = null))
        sessionMarkerDao.insert(SessionMarkerEntity(sessionId = sessionId, timestamp = 2000L, seq = 2, type = "NOTE", label = null))

        val markers = sessionMarkerDao.getBySessionId(sessionId).first()
        assertEquals(1000L, markers[0].timestamp)
        assertEquals(2000L, markers[1].timestamp)
        assertEquals(3000L, markers[2].timestamp)
    }
}
