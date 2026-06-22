package com.cellrecorder.app.data.local

import androidx.room.migration.AutoMigrationSpec
import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private companion object {
        const val TEST_DB = "migration-test"
    }

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        listOf<AutoMigrationSpec>()
    )

    @Test
    fun migrateFrom1To2() {
        val db = helper.createDatabase(TEST_DB, 1)
        db.execSQL(
            """INSERT INTO sessions (id, name, createdAt, pointCount) VALUES (1, 'test', 1000, 5)"""
        )
        db.execSQL(
            """INSERT INTO cell_records (id, sessionId, timestamp, latitude, longitude, altitude, accuracy, rat)
               VALUES (1, 1, 100, 1.0, 2.0, 3.0, 5.0, 'LTE')"""
        )
        db.close()

        val migratedDb = helper.runMigrationsAndValidate(TEST_DB, 2, true, AppDatabase.MIGRATION_1_2)

        val cursor = migratedDb.query("SELECT id, rat FROM cell_records WHERE id = 1")
        assertTrue("Cell record should survive migration", cursor.moveToFirst())
        assertEquals(1, cursor.getLong(0))
        assertEquals("LTE", cursor.getString(1))
        cursor.close()
    }

    @Test
    fun migrateFrom2To3() {
        val db = helper.createDatabase(TEST_DB, 2)
        db.execSQL(
            """INSERT INTO app_config (id, pingDestination, pingIntervalMs, pingTimeoutMs,
               recordingIntervalMs, locationChangeThresholdM, gpsAccuracyThresholdM,
               maxRecordingDurationMin, nrGnbBitLength, lteEnbSplitShift)
               VALUES (1, '8.8.8.8', 1000, 3000, 5000, 10.0, 50.0, 120, 24, 8)"""
        )
        db.close()

        val migratedDb = helper.runMigrationsAndValidate(TEST_DB, 3, true, AppDatabase.MIGRATION_2_3)

        val cursor = migratedDb.query(
            "SELECT pingDestination, nrGnbBitLength, cellInfoRefreshIntervalSec FROM app_config WHERE id = 1"
        )
        assertTrue("Config row should survive migration", cursor.moveToFirst())
        assertEquals("8.8.8.8", cursor.getString(0))
        assertEquals(24, cursor.getInt(1))
        assertEquals(5, cursor.getInt(2))
        cursor.close()
    }

    @Test
    fun migrateFrom3To4() {
        val db = helper.createDatabase(TEST_DB, 3)
        db.execSQL(
            """INSERT INTO sessions (id, name, createdAt, pointCount) VALUES (1, 'test', 1000, 5)"""
        )
        db.close()

        val migratedDb = helper.runMigrationsAndValidate(TEST_DB, 4, true, AppDatabase.MIGRATION_3_4)

        val cursor = migratedDb.query("SELECT name, pointCount FROM sessions WHERE id = 1")
        assertTrue("Session row should survive migration", cursor.moveToFirst())
        assertEquals("test", cursor.getString(0))
        assertEquals(5, cursor.getInt(1))
        cursor.close()
    }

    @Test
    fun migrateFrom4To5() {
        val db = helper.createDatabase(TEST_DB, 4)
        db.execSQL(
            """INSERT INTO app_config (id, pingDestination, pingIntervalMs, pingTimeoutMs,
               recordingIntervalMs, locationChangeThresholdM, gpsAccuracyThresholdM,
               maxRecordingDurationMin, nrGnbBitLength, cellInfoRefreshIntervalSec)
               VALUES (1, '8.8.8.8', 1000, 3000, 5000, 10.0, 50.0, 120, 24, 5)"""
        )
        db.execSQL(
            """INSERT INTO cell_records (id, sessionId, timestamp, latitude, longitude, altitude, accuracy, rat)
               VALUES (1, 1, 100, 1.0, 2.0, 3.0, 5.0, 'LTE')"""
        )
        db.close()

        val migratedDb = helper.runMigrationsAndValidate(TEST_DB, 5, true, AppDatabase.MIGRATION_4_5)

        val cursor = migratedDb.query("SELECT rat FROM cell_records WHERE id = 1")
        assertTrue("Cell record should survive migration", cursor.moveToFirst())
        assertEquals("LTE", cursor.getString(0))
        cursor.close()
    }

    @Test
    fun migrateFrom5To6() {
        val db = helper.createDatabase(TEST_DB, 5)
        db.execSQL(
            """INSERT INTO app_config (id, pingDestination, pingIntervalMs, pingTimeoutMs,
               recordingIntervalMs, locationChangeThresholdM, gpsAccuracyThresholdM,
               maxRecordingDurationMin, nrGnbBitLength, cellInfoRefreshIntervalSec, maxGpsLossExtrapolationSec)
               VALUES (1, '8.8.8.8', 1000, 3000, 5000, 10.0, 50.0, 120, 24, 5, 120)"""
        )
        db.close()

        val migratedDb = helper.runMigrationsAndValidate(TEST_DB, 6, true, AppDatabase.MIGRATION_5_6)

        val cursor = migratedDb.query("SELECT pingDestination FROM app_config WHERE id = 1")
        assertTrue("Config row should survive migration", cursor.moveToFirst())
        assertEquals("8.8.8.8", cursor.getString(0))
        cursor.close()
    }

    @Test
    fun migrateFrom6To7() {
        val db = helper.createDatabase(TEST_DB, 6)
        db.execSQL(
            """INSERT INTO sessions (id, name, createdAt, pointCount) VALUES (1, 'test', 1000, 5)"""
        )
        db.execSQL(
            """INSERT INTO cell_records (id, sessionId, timestamp, latitude, longitude, altitude, accuracy, rat, isLocationEstimated, locationSource)
               VALUES (1, 1, 100, 1.0, 2.0, 3.0, 5.0, 'LTE', 0, 'GPS')"""
        )
        db.close()

        val migratedDb = helper.runMigrationsAndValidate(TEST_DB, 7, true, AppDatabase.MIGRATION_6_7)

        val cursor = migratedDb.query("SELECT rat FROM cell_records WHERE id = 1")
        assertTrue("Cell record should survive migration", cursor.moveToFirst())
        assertEquals("LTE", cursor.getString(0))
        cursor.close()
    }

    @Test
    fun migrateFrom7To8() {
        val db = helper.createDatabase(TEST_DB, 7)
        db.execSQL(
            """INSERT INTO cell_records (id, sessionId, timestamp, latitude, longitude, altitude, accuracy, rat, isLocationEstimated, locationSource)
               VALUES (1, 1, 100, 1.0, 2.0, 3.0, 5.0, 'LTE', 0, 'GPS')"""
        )
        db.close()

        val migratedDb = helper.runMigrationsAndValidate(TEST_DB, 8, true, AppDatabase.MIGRATION_7_8)

        val cursor = migratedDb.query("SELECT rat FROM cell_records WHERE id = 1")
        assertTrue("Cell record should survive migration", cursor.moveToFirst())
        assertEquals("LTE", cursor.getString(0))
        cursor.close()
    }

    @Test
    fun migrateFrom8To9() {
        val db = helper.createDatabase(TEST_DB, 8)
        db.execSQL(
            """INSERT INTO app_config (id, pingDestination, pingIntervalMs, pingTimeoutMs,
               recordingIntervalMs, locationChangeThresholdM, gpsAccuracyThresholdM,
               maxRecordingDurationMin, nrGnbBitLength, cellInfoRefreshIntervalSec, maxGpsLossExtrapolationSec,
               handoffTimeWindowMs, rsrpDropThresholdDbm, rsrpDropTimeWindowMs, latencySpikeSigma,
               pciFlapWindowMs, pciFlapCountThreshold, coverageGapThresholdMs,
               mobilityStationaryKmh, mobilityWalkingKmh, indoorAccuracyThresholdM, tunnelSignalLossThresholdMs)
               VALUES (1, '8.8.8.8', 1000, 3000, 5000, 10.0, 50.0, 120, 24, 5, 120,
                       5000, 15, 10000, 3.0, 30000, 3, 30000, 5.0, 15.0, 30.0, 10000)"""
        )
        db.close()

        val migratedDb = helper.runMigrationsAndValidate(TEST_DB, 9, true, AppDatabase.MIGRATION_8_9)

        val cursor = migratedDb.query("SELECT pingDestination FROM app_config WHERE id = 1")
        assertTrue("Config row should survive migration", cursor.moveToFirst())
        assertEquals("8.8.8.8", cursor.getString(0))
        cursor.close()
    }

    @Test
    fun migrateFrom9To10() {
        helper.createDatabase(TEST_DB, 9).close()
        helper.runMigrationsAndValidate(TEST_DB, 10, true, AppDatabase.MIGRATION_9_10)
    }

    @Test
    fun migrateFrom10To11() {
        val db = helper.createDatabase(TEST_DB, 10)
        db.execSQL(
            """INSERT INTO sessions (id, name, createdAt, pointCount) VALUES (1, 'test', 1000, 5)"""
        )
        db.execSQL(
            """INSERT INTO cell_records (id, sessionId, timestamp, latitude, longitude, altitude, accuracy, rat, isLocationEstimated, locationSource)
               VALUES (1, 1, 100, 1.0, 2.0, 3.0, 5.0, 'LTE', 0, 'GPS')"""
        )
        db.close()

        val migratedDb = helper.runMigrationsAndValidate(TEST_DB, 11, true, AppDatabase.MIGRATION_10_11)

        val cursor = migratedDb.query("SELECT name FROM sessions WHERE id = 1")
        assertTrue("Session row should survive migration", cursor.moveToFirst())
        assertEquals("test", cursor.getString(0))
        cursor.close()
    }

    @Test
    fun migrateFrom11To12() {
        val db = helper.createDatabase(TEST_DB, 11)
        db.execSQL(
            """INSERT INTO sessions (id, name, createdAt, pointCount, recordingMode) VALUES (1, 'test', 1000, 5, 'OUTDOOR')"""
        )
        db.close()

        val migratedDb = helper.runMigrationsAndValidate(TEST_DB, 12, true, AppDatabase.MIGRATION_11_12)

        val cursor = migratedDb.query("SELECT name FROM sessions WHERE id = 1")
        assertTrue("Session row should survive migration", cursor.moveToFirst())
        assertEquals("test", cursor.getString(0))
        cursor.close()
    }

    @Test
    fun migrateFullChain1To12() {
        val db = helper.createDatabase(TEST_DB, 1)
        db.execSQL(
            """INSERT INTO sessions (id, name, createdAt, pointCount) VALUES (1, 'full-chain', 1000, 5)"""
        )
        db.execSQL(
            """INSERT INTO cell_records (id, sessionId, timestamp, latitude, longitude, altitude, accuracy, rat)
               VALUES (1, 1, 100, 1.0, 2.0, 3.0, 5.0, 'LTE')"""
        )
        db.execSQL(
            """INSERT INTO app_config (id, pingDestination, pingIntervalMs, pingTimeoutMs,
               recordingIntervalMs, locationChangeThresholdM, gpsAccuracyThresholdM,
               maxRecordingDurationMin, nrGnbBitLength, lteEnbSplitShift)
               VALUES (1, '8.8.8.8', 1000, 3000, 5000, 10.0, 50.0, 120, 24, 8)"""
        )
        db.close()

        val migratedDb = helper.runMigrationsAndValidate(
            TEST_DB, 12, true,
            AppDatabase.MIGRATION_1_2,
            AppDatabase.MIGRATION_2_3,
            AppDatabase.MIGRATION_3_4,
            AppDatabase.MIGRATION_4_5,
            AppDatabase.MIGRATION_5_6,
            AppDatabase.MIGRATION_6_7,
            AppDatabase.MIGRATION_7_8,
            AppDatabase.MIGRATION_8_9,
            AppDatabase.MIGRATION_9_10,
            AppDatabase.MIGRATION_10_11,
            AppDatabase.MIGRATION_11_12
        )

        val sessionCursor = migratedDb.query("SELECT name, pointCount FROM sessions WHERE id = 1")
        assertTrue("Session should survive full chain", sessionCursor.moveToFirst())
        assertEquals("full-chain", sessionCursor.getString(0))
        assertEquals(5, sessionCursor.getInt(1))
        sessionCursor.close()

        val cellCursor = migratedDb.query("SELECT rat FROM cell_records WHERE id = 1")
        assertTrue("Cell record should survive full chain", cellCursor.moveToFirst())
        assertEquals("LTE", cellCursor.getString(0))
        cellCursor.close()

        val configCursor = migratedDb.query("SELECT pingDestination, nrGnbBitLength FROM app_config WHERE id = 1")
        assertTrue("Config should survive full chain", configCursor.moveToFirst())
        assertEquals("8.8.8.8", configCursor.getString(0))
        assertEquals(24, configCursor.getInt(1))
        configCursor.close()
    }
}