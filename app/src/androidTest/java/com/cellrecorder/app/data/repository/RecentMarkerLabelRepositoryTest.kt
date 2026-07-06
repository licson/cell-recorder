package com.cellrecorder.app.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.cellrecorder.app.data.local.AppDatabase
import com.cellrecorder.app.data.local.entity.RecentMarkerLabelEntity
import com.cellrecorder.app.domain.model.MarkerType
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@MediumTest
class RecentMarkerLabelRepositoryTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

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
    fun getByTypeOrdered_capsAt20() = runBlocking {
        repeat(25) { index ->
            db.recentMarkerLabelDao().upsert(
                RecentMarkerLabelEntity(
                    type = MarkerType.NOTE.toStorageString(),
                    label = "Label $index",
                    useCount = 1,
                    lastUsed = index.toLong() * 1000
                )
            )
        }

        val recents = recentMarkerLabelRepository.getByTypeOrdered(MarkerType.NOTE)
        assertEquals(20, recents.size)
        assertEquals("Label 24", recents[0].label)
    }
}
