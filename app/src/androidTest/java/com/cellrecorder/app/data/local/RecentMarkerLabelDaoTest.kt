package com.cellrecorder.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cellrecorder.app.data.local.dao.RecentMarkerLabelDao
import com.cellrecorder.app.data.local.dao.SessionDao
import com.cellrecorder.app.data.local.entity.RecentMarkerLabelEntity
import com.cellrecorder.app.data.local.entity.SessionEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class RecentMarkerLabelDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var recentLabelDao: RecentMarkerLabelDao
    private lateinit var sessionDao: SessionDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        recentLabelDao = db.recentMarkerLabelDao()
        sessionDao = db.sessionDao()
    }

    @After
    @Throws(IOException::class)
    fun tearDown() {
        db.close()
    }

    @Test
    fun upsertInsertsNewRow() = runBlocking {
        val entity = RecentMarkerLabelEntity(type = "NOTE", label = "Station", useCount = 1, lastUsed = 1000L)
        recentLabelDao.upsert(entity)

        val loaded = recentLabelDao.getByTypeAndLabel("NOTE", "Station")!!
        assertEquals("NOTE", loaded.type)
        assertEquals("Station", loaded.label)
        assertEquals(1, loaded.useCount)
        assertEquals(1000L, loaded.lastUsed)
    }

    @Test
    fun upsertWithIncrementedEntity_incrementsUseCountAndLastUsed() = runBlocking {
        recentLabelDao.upsert(RecentMarkerLabelEntity(type = "NOTE", label = "Station", useCount = 1, lastUsed = 1000L))
        val existing = recentLabelDao.getByTypeAndLabel("NOTE", "Station")!!

        recentLabelDao.upsert(existing.copy(useCount = existing.useCount + 1, lastUsed = 2000L))

        val updated = recentLabelDao.getByTypeAndLabel("NOTE", "Station")!!
        assertEquals(2, updated.useCount)
        assertEquals(2000L, updated.lastUsed)
    }

    @Test
    fun getByTypeOrdered_returnsRowsSortedByLastUsedDescending() = runBlocking {
        recentLabelDao.upsert(RecentMarkerLabelEntity(type = "NOTE", label = "Old", useCount = 1, lastUsed = 1000L))
        recentLabelDao.upsert(RecentMarkerLabelEntity(type = "NOTE", label = "Recent", useCount = 1, lastUsed = 3000L))
        recentLabelDao.upsert(RecentMarkerLabelEntity(type = "NOTE", label = "Middle", useCount = 1, lastUsed = 2000L))

        val ordered = recentLabelDao.getByTypeOrdered("NOTE")
        assertEquals(3, ordered.size)
        assertEquals("Recent", ordered[0].label)
        assertEquals("Middle", ordered[1].label)
        assertEquals("Old", ordered[2].label)
    }

    @Test
    fun getByTypeOrdered_filtersByType() = runBlocking {
        recentLabelDao.upsert(RecentMarkerLabelEntity(type = "NOTE", label = "A", useCount = 1, lastUsed = 1000L))
        recentLabelDao.upsert(RecentMarkerLabelEntity(type = "WAYPOINT", label = "B", useCount = 1, lastUsed = 2000L))

        val noteLabels = recentLabelDao.getByTypeOrdered("NOTE")
        assertEquals(1, noteLabels.size)
        assertEquals("A", noteLabels[0].label)
    }

    @Test
    fun survivesSessionDeletion() = runBlocking {
        val sessionId = sessionDao.insert(SessionEntity(name = "ToDelete", createdAt = 1000L))
        recentLabelDao.upsert(RecentMarkerLabelEntity(type = "NOTE", label = "Survivor", useCount = 1, lastUsed = 1000L))

        sessionDao.deleteById(sessionId)

        val loaded = recentLabelDao.getByTypeAndLabel("NOTE", "Survivor")
        assertNotNull(loaded)
    }

    @Test
    fun deleteAll() = runBlocking {
        recentLabelDao.upsert(RecentMarkerLabelEntity(type = "NOTE", label = "A", useCount = 1, lastUsed = 1000L))
        recentLabelDao.deleteAll()

        val ordered = recentLabelDao.getByTypeOrdered("NOTE")
        assertTrue(ordered.isEmpty())
    }
}
