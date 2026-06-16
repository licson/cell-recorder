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
}