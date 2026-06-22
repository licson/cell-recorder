package com.cellrecorder.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cellrecorder.app.data.local.dao.ConfigDao
import com.cellrecorder.app.data.local.entity.AppConfigEntity
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
class ConfigDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: ConfigDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.configDao()
    }

    @After
    @Throws(IOException::class)
    fun tearDown() {
        db.close()
    }

    @Test
    fun getReturnsDefaultValuesAfterFirstInsert() = runBlocking {
        dao.update(AppConfigEntity())

        val config = dao.get().first()
        assertNotNull(config)
        config!!.let {
            assertEquals("8.8.8.8", it.pingDestination)
            assertEquals(1000L, it.pingIntervalMs)
            assertEquals(5000L, it.recordingIntervalMs)
            assertEquals(10f, it.locationChangeThresholdM, 0.001f)
            assertEquals(24, it.nrGnbBitLength)
            assertEquals(3.0, it.latencySpikeSigma, 0.001)
            assertEquals(false, it.speedTestEnabled)
            assertEquals(0.7f, it.indoorStepLengthM, 0.001f)
        }
    }

    @Test
    fun updateModifiesConfig() = runBlocking {
        dao.update(AppConfigEntity())

        val modified = AppConfigEntity(
            pingDestination = "1.1.1.1",
            pingIntervalMs = 2000L,
            recordingIntervalMs = 10000L,
            locationChangeThresholdM = 20f,
            nrGnbBitLength = 32
        )
        dao.update(modified)

        val config = dao.get().first()
        assertNotNull(config)
        config!!.let {
            assertEquals("1.1.1.1", it.pingDestination)
            assertEquals(2000L, it.pingIntervalMs)
            assertEquals(10000L, it.recordingIntervalMs)
            assertEquals(20f, it.locationChangeThresholdM, 0.001f)
            assertEquals(32, it.nrGnbBitLength)
        }
    }

    @Test
    fun updatePreservesUnchangedFields() = runBlocking {
        dao.update(AppConfigEntity())

        dao.update(AppConfigEntity(pingDestination = "1.1.1.1"))

        val config = dao.get().first()
        assertNotNull(config)
        config!!.let {
            assertEquals("1.1.1.1", it.pingDestination)
            assertEquals(1000L, it.pingIntervalMs)
        }
    }

    @Test
    fun insertRespectsOnConflictReplace() = runBlocking {
        dao.update(AppConfigEntity(pingDestination = "first"))
        dao.update(AppConfigEntity(pingDestination = "second"))

        val config = dao.get().first()
        assertEquals("second", config!!.pingDestination)
    }

    @Test
    fun roundTripLatencySpikeSigma() = runBlocking {
        dao.update(AppConfigEntity(latencySpikeSigma = 5.5))

        val config = dao.get().first()
        assertNotNull(config)
        assertEquals(5.5, config!!.latencySpikeSigma, 0.001)
    }

    @Test
    fun roundTripIndoorStepLengthM() = runBlocking {
        dao.update(AppConfigEntity(indoorStepLengthM = 0.8f))

        val config = dao.get().first()
        assertNotNull(config)
        assertEquals(0.8f, config!!.indoorStepLengthM, 0.001f)
    }

    @Test
    fun roundTripSpeedTestEnabled() = runBlocking {
        dao.update(AppConfigEntity(speedTestEnabled = true))

        val config = dao.get().first()
        assertNotNull(config)
        assertEquals(true, config!!.speedTestEnabled)
    }

    @Test
    fun roundTripNrGnbBitLength() = runBlocking {
        dao.update(AppConfigEntity(nrGnbBitLength = 28))

        val config = dao.get().first()
        assertNotNull(config)
        assertEquals(28, config!!.nrGnbBitLength)
    }

    @Test
    fun getReturnsNullWhenNoConfigInserted() = runBlocking {
        val config = dao.get().first()
        assertNull(config)
    }
}