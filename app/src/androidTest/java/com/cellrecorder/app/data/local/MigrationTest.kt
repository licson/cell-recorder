package com.cellrecorder.app.data.local

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.cellrecorder.app.data.local.entity.CellRecordEntity
import com.cellrecorder.app.data.local.entity.SessionEntity
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private companion object {
        const val TEST_DB = "migration-test"
    }

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java.canonicalName,
        listOf()
    )

    private lateinit var db: SupportSQLiteDatabase

    @Before
    fun setUp() {
        // Create database at version 5
        db = helper.createDatabase(TEST_DB, 5)
        db.execSQL("""
            INSERT OR REPLACE INTO app_config (id) VALUES (1)
        """)
    }

    @Test
    fun migrateFrom5To6() {
        helper.runMigrationsAndValidate(
            TEST_DB,
            6,
            true,
            AppDatabase.MIGRATION_5_6
        )
    }

    @After
    @Throws(IOException::class)
    fun tearDown() {
        try {
            db.close()
        } catch (_: Exception) { }
    }
}