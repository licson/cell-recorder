package com.cellrecorder.app.data.local

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.cellrecorder.app.data.local.dao.RecentMarkerLabelDao
import com.cellrecorder.app.data.local.dao.SessionMarkerDao
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@MediumTest
class MarkerDaoHiltWiringTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var sessionMarkerDao: SessionMarkerDao

    @Inject
    lateinit var recentMarkerLabelDao: RecentMarkerLabelDao

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
    fun sessionMarkerDao_isWired() {
        assertNotNull(sessionMarkerDao)
    }

    @Test
    fun recentMarkerLabelDao_isWired() {
        assertNotNull(recentMarkerLabelDao)
    }

    @Test
    fun sessionMarkerDao_canQuery() = runBlocking {
        assertTrue(sessionMarkerDao.countBySessionId(0L) >= 0)
    }

    @Test
    fun recentMarkerLabelDao_canQuery() = runBlocking {
        assertTrue(recentMarkerLabelDao.getByTypeOrdered("NOTE").isEmpty())
    }
}
