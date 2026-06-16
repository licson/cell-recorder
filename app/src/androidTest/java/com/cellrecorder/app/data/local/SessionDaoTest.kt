package com.cellrecorder.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cellrecorder.app.data.local.dao.SessionDao
import com.cellrecorder.app.data.local.entity.SessionEntity
import com.cellrecorder.app.util.TestDataFactory
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
class SessionDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: SessionDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.sessionDao()
    }

    @After
    @Throws(IOException::class)
    fun tearDown() {
        db.close()
    }

    @Test
    fun insertAndGetById() = runBlocking {
        val session = TestDataFactory.session(name = "Test 1")
        val id = dao.insert(session)
        assertTrue(id > 0)

        val loaded = dao.getById(id).first()
        assertNotNull(loaded)
        assertEquals("Test 1", loaded!!.name)
    }

    @Test
    fun getAll_returnsAllDescending() = runBlocking {
        val id1 = dao.insert(TestDataFactory.session(name = "A", createdAt = 1000L))
        val id2 = dao.insert(TestDataFactory.session(name = "B", createdAt = 2000L))

        val sessions = dao.getAll().first()
        assertEquals(2, sessions.size)
        assertEquals("B", sessions[0].name)
        assertEquals("A", sessions[1].name)
    }

    @Test
    fun updateName() = runBlocking {
        val id = dao.insert(TestDataFactory.session(name = "Old"))
        dao.updateName(id, "New")

        val loaded = dao.getById(id).first()
        assertEquals("New", loaded!!.name)
    }

    @Test
    fun updateEndedAt() = runBlocking {
        val id = dao.insert(TestDataFactory.session(createdAt = 1000L))
        dao.updateEndedAt(id, 5000L)

        val loaded = dao.getById(id).first()
        assertEquals(5000L, loaded!!.endedAt)
    }

    @Test
    fun incrementPointCount() = runBlocking {
        val id = dao.insert(TestDataFactory.session())
        dao.incrementPointCount(id)

        val loaded = dao.getById(id).first()
        assertEquals(1, loaded!!.pointCount)
    }

    @Test
    fun updatePrimarySimSlot() = runBlocking {
        val id = dao.insert(TestDataFactory.session())
        dao.updatePrimarySimSlot(id, 0)

        val loaded = dao.getById(id).first()
        assertEquals(0, loaded!!.primarySimSlot)
    }

    @Test
    fun clearPrimarySimSlot() = runBlocking {
        val id = dao.insert(TestDataFactory.session(primarySimSlot = 0))
        dao.updatePrimarySimSlot(id, null)

        val loaded = dao.getById(id).first()
        assertNull(loaded!!.primarySimSlot)
    }

    @Test
    fun deleteById_removesSession() = runBlocking {
        val id = dao.insert(TestDataFactory.session(name = "Delete me"))
        dao.deleteById(id)

        val loaded = dao.getById(id).first()
        assertNull(loaded)
    }

    @Test
    fun getTotalDurationMs() = runBlocking {
        dao.insert(TestDataFactory.session(createdAt = 1000L, endedAt = 5000L))
        dao.insert(TestDataFactory.session(createdAt = 2000L, endedAt = 4000L))

        val total = dao.getTotalDurationMs().first()
        assertEquals(6000L, total)
    }

    @Test
    fun getTotalSessionCount() = runBlocking {
        dao.insert(TestDataFactory.session(name = "A"))
        dao.insert(TestDataFactory.session(name = "B"))

        val count = dao.getTotalSessionCount().first()
        assertEquals(2, count)
    }
}