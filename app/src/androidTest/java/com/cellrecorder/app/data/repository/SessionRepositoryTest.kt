package com.cellrecorder.app.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.cellrecorder.app.data.local.AppDatabase
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
class SessionRepositoryTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var repository: SessionRepository

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
    fun create_insertsSession() = runBlocking {
        val id = repository.create(name = "Test Session", recordingMode = "OUTDOOR")
        assertTrue(id > 0)

        val session = repository.getById(id).first()
        assertNotNull(session)
        assertEquals("Test Session", session!!.name)
    }

    @Test
    fun getAll_returnsAll() = runBlocking {
        repository.create(name = "A", recordingMode = "OUTDOOR")
        repository.create(name = "B", recordingMode = "OUTDOOR")

        val sessions = repository.getAll().first()
        assertEquals(2, sessions.size)
    }

    @Test
    fun updateName() = runBlocking {
        val id = repository.create(name = "Original", recordingMode = "OUTDOOR")
        repository.updateName(id, "Updated")

        val session = repository.getById(id).first()
        assertEquals("Updated", session!!.name)
    }

    @Test
    fun updateEndedAt() = runBlocking {
        val id = repository.create(name = "Test", recordingMode = "OUTDOOR", createdAt = 0L)
        repository.updateEndedAt(id, 5000L)

        val session = repository.getById(id).first()
        assertEquals(5000L, session!!.endedAt)
    }

    @Test
    fun deleteById() = runBlocking {
        val id = repository.create(name = "Delete me", recordingMode = "OUTDOOR")
        repository.deleteById(id)

        val session = repository.getById(id).first()
        assertNull(session)
    }

    @Test
    fun getTotalDurationMs() = runBlocking {
        repository.create(name = "A", recordingMode = "OUTDOOR", createdAt = 1000L)
        val sessions = repository.getAll().first()
        val id = sessions.first().id
        repository.updateEndedAt(id, 5000L)

        val total = repository.getTotalDurationMs().first()
        assertEquals(4000L, total)
    }

    @Test
    fun getTotalSessionCount() = runBlocking {
        repository.create(name = "A", recordingMode = "OUTDOOR")
        repository.create(name = "B", recordingMode = "OUTDOOR")

        val count = repository.getTotalSessionCount().first()
        assertEquals(2, count)
    }
}