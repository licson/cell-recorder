package com.cellrecorder.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cellrecorder.app.data.local.dao.CellRecordDao
import com.cellrecorder.app.data.local.entity.CellRecordEntity
import com.cellrecorder.app.data.local.entity.SessionEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class CellRecordDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: CellRecordDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.cellRecordDao()

        db.sessionDao().insert(SessionEntity(id = 1, name = "test", createdAt = System.currentTimeMillis()))
    }

    @Test
    fun insertAndReadRecord() = runBlocking {
        val record = CellRecordEntity(
            sessionId = 1,
            timestamp = System.currentTimeMillis(),
            latitude = 37.7749,
            longitude = -122.4194,
            altitude = 0.0,
            accuracy = 10f,
            rat = "4G"
        )
        val id = dao.insert(record)
        assertNotNull(id)

        val records = dao.getBySessionIdOnce(1)
        assertEquals(1, records.size)
        assertEquals(37.7749, records[0].latitude, 0.0001)
        assertEquals("4G", records[0].rat)
    }

    @After
    @Throws(IOException::class)
    fun tearDown() {
        db.close()
    }
}