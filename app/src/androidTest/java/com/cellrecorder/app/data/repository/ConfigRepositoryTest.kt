package com.cellrecorder.app.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.cellrecorder.app.data.local.AppDatabase
import com.cellrecorder.app.data.local.entity.AppConfigEntity
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@MediumTest
class ConfigRepositoryTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var repository: ConfigRepository

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
    fun getConfig_returnsDefaults() = runBlocking {
        repository.update(AppConfigEntity())

        val config = repository.getConfig().first()
        assertEquals("8.8.8.8", config.pingDestination)
        assertEquals(1000L, config.pingIntervalMs)
        assertEquals(5000L, config.recordingIntervalMs)
    }

    @Test
    fun updateConfig_roundTrip() = runBlocking {
        repository.update(AppConfigEntity())

        repository.update(AppConfigEntity(pingDestination = "1.1.1.1"))

        val config = repository.getConfig().first()
        assertEquals("1.1.1.1", config.pingDestination)
    }

    @Test
    fun updateConfig_roundTripAllFields() = runBlocking {
        val original = AppConfigEntity(
            id = 1,
            pingDestination = "1.1.1.1",
            pingIntervalMs = 2000,
            pingTimeoutMs = 5000,
            recordingIntervalMs = 10000,
            locationChangeThresholdM = 25f,
            gpsAccuracyThresholdM = 100f,
            maxRecordingDurationMin = 60,
            nrGnbBitLength = 28,
            cellInfoRefreshIntervalSec = 10,
            maxGpsLossExtrapolationSec = 60,
            handoffTimeWindowMs = 3000,
            rsrpDropThresholdDbm = 20,
            rsrpDropTimeWindowMs = 15000,
            latencySpikeSigma = 5.0,
            pciFlapWindowMs = 60000,
            pciFlapCountThreshold = 5,
            coverageGapThresholdMs = 60000,
            mobilityStationaryKmh = 10f,
            mobilityWalkingKmh = 20f,
            indoorAccuracyThresholdM = 50f,
            tunnelSignalLossThresholdMs = 20000,
            speedTestEnabled = true,
            speedTestIntervalMs = 120000,
            speedTestUploadEnabled = false,
            speedTestSecure = false,
            speedTestServerId = "server-123",
            indoorStepLengthM = 0.8f,
            indoorRecordingIntervalMs = 10000
        )
        repository.update(original)

        val config = repository.getConfig().first()
        assertEquals(original, config)
    }

    @Test
    fun updateConfig_nullSpeedTestServerId() = runBlocking {
        repository.update(AppConfigEntity(speedTestServerId = null))
        val config = repository.getConfig().first()
        assertNull(config.speedTestServerId)

        repository.update(AppConfigEntity(speedTestServerId = "custom-server"))
        val config2 = repository.getConfig().first()
        assertEquals("custom-server", config2.speedTestServerId)
    }
}