package com.cellrecorder.app.data.local

import androidx.room.migration.AutoMigrationSpec
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun migrateFrom12To13() {
        val db = helper.createDatabase(TEST_DB, 12)
        db.execSQL(
            """INSERT INTO sessions (id, name, createdAt, pointCount, recordingMode) VALUES (1, 'test', 1000, 5, 'OUTDOOR')"""
        )
        db.execSQL(
            """INSERT INTO cell_records (id, sessionId, timestamp, latitude, longitude, altitude, accuracy, rat, isLocationEstimated, locationSource)
               VALUES (1, 1, 100, 1.0, 2.0, 3.0, 5.0, 'LTE', 0, 'GPS')"""
        )
        db.execSQL(
            """INSERT INTO cell_record_ca_bands (id, cellRecordId, bandNumber, earfcn, pci, rsrp, rsrq, sinr, rssi, cqi, timingAdvance)
               VALUES (1, 1, 3, 1800, 42, -85, -90, 8, -75, 7, 14)"""
        )
        db.close()

        val migratedDb = helper.runMigrationsAndValidate(TEST_DB, 13, true, AppDatabase.MIGRATION_12_13)

        val cellCursor = migratedDb.query("SELECT rat FROM cell_records WHERE id = 1")
        assertTrue("Cell record should survive migration", cellCursor.moveToFirst())
        assertEquals("LTE", cellCursor.getString(0))
        cellCursor.close()

        val caCursor = migratedDb.query("SELECT bandNumber, earfcn, pci, bandwidthKhz FROM cell_record_ca_bands WHERE id = 1")
        assertTrue("CA band row should survive migration", caCursor.moveToFirst())
        assertEquals(3, caCursor.getInt(0))
        assertEquals(1800, caCursor.getInt(1))
        assertEquals(42, caCursor.getInt(2))
        assertTrue("bandwidthKhz column should exist and be null after migration", caCursor.isNull(3))
        caCursor.close()
    }

    @Test
    fun migrateFrom13To14() {
        val db = helper.createDatabase(TEST_DB, 13)
        db.execSQL(
            """INSERT INTO sessions (id, name, createdAt, pointCount, recordingMode) VALUES (1, 'test', 1000, 5, 'OUTDOOR')"""
        )
        db.execSQL(
            """INSERT INTO cell_records (id, sessionId, timestamp, latitude, longitude, altitude, accuracy, rat, isLocationEstimated, locationSource)
               VALUES (1, 1, 100, 1.0, 2.0, 3.0, 5.0, 'LTE', 0, 'GPS')"""
        )
        db.close()

        val migratedDb = helper.runMigrationsAndValidate(TEST_DB, 14, true, AppDatabase.MIGRATION_13_14)

        val sessionCursor = migratedDb.query("SELECT name FROM sessions WHERE id = 1")
        assertTrue("Session row should survive migration", sessionCursor.moveToFirst())
        assertEquals("test", sessionCursor.getString(0))
        sessionCursor.close()

        val cellCursor = migratedDb.query("SELECT rat FROM cell_records WHERE id = 1")
        assertTrue("Cell record should survive migration", cellCursor.moveToFirst())
        assertEquals("LTE", cellCursor.getString(0))
        cellCursor.close()

        val markerCursor = migratedDb.query("SELECT count(*) FROM session_markers")
        assertTrue(markerCursor.moveToFirst())
        assertEquals(0, markerCursor.getInt(0))
        markerCursor.close()

        val markerColumns = migratedDb.query("PRAGMA table_info(session_markers)")
        val markerColumnNames = mutableSetOf<String>()
        while (markerColumns.moveToNext()) {
            markerColumnNames.add(markerColumns.getString(1))
        }
        markerColumns.close()
        assertTrue(markerColumnNames.containsAll(setOf("id", "sessionId", "timestamp", "seq", "type", "label")))

        val recentsCursor = migratedDb.query("SELECT count(*) FROM recent_marker_labels")
        assertTrue(recentsCursor.moveToFirst())
        assertEquals(0, recentsCursor.getInt(0))
        recentsCursor.close()

        val recentsColumns = migratedDb.query("PRAGMA table_info(recent_marker_labels)")
        val recentsColumnNames = mutableSetOf<String>()
        while (recentsColumns.moveToNext()) {
            recentsColumnNames.add(recentsColumns.getString(1))
        }
        recentsColumns.close()
        assertTrue(recentsColumnNames.containsAll(setOf("type", "label", "useCount", "lastUsed")))
    }

    @Test
    fun migrateFrom14To15() {
        val db = helper.createDatabase(TEST_DB, 14)
        db.execSQL(
            """INSERT INTO sessions (id, name, createdAt, pointCount, recordingMode) VALUES (1, 'test', 1000, 5, 'OUTDOOR')"""
        )
        db.execSQL(
            """INSERT INTO cell_records (id, sessionId, timestamp, latitude, longitude, altitude, accuracy, rat, isLocationEstimated, locationSource)
               VALUES (1, 1, 100, 1.0, 2.0, 3.0, 5.0, 'LTE', 0, 'GPS')"""
        )
        db.execSQL(
            """INSERT INTO session_markers (id, sessionId, timestamp, seq, type, label)
               VALUES (1, 1, 200, 1, 'NOTE', 'note1')"""
        )
        db.execSQL(
            """INSERT INTO speed_test_records (id, sessionId, timestamp, downloadBps, uploadBps, serverName, serverHost, serverLocation, serverId, dataSimSlotIndex, ratAtTest, rsrpAtTest, bandAtTest, succeeded, errorMessage, networkType)
               VALUES (1, 1, 500, 100000000, 20000000, 'srv', 'host', 'loc', 42, 0, '4G', -90, 3, 1, NULL, 'CELLULAR')"""
        )
        db.close()

        val migratedDb = helper.runMigrationsAndValidate(TEST_DB, 15, true, AppDatabase.MIGRATION_14_15)

        // Column exists with NOT NULL DEFAULT 0
        val columnsCursor = migratedDb.query("PRAGMA table_info(speed_test_records)")
        val columnSpecs = mutableMapOf<String, Pair<Int, String?>>()
        while (columnsCursor.moveToNext()) {
            val name = columnsCursor.getString(1)
            val notNull = columnsCursor.getInt(3)
            val dflt = if (columnsCursor.isNull(4)) null else columnsCursor.getString(4)
            columnSpecs[name] = notNull to dflt
        }
        columnsCursor.close()
        assertTrue("finishedAt column should exist", columnSpecs.containsKey("finishedAt"))
        val (notNull, dflt) = columnSpecs["finishedAt"]!!
        assertEquals("finishedAt should be NOT NULL", 1, notNull)
        assertEquals("finishedAt default should be 0", "0", dflt)

        // Legacy row backfilled to 0
        val speedCursor = migratedDb.query("SELECT timestamp, finishedAt FROM speed_test_records WHERE id = 1")
        assertTrue("Speedtest row should survive migration", speedCursor.moveToFirst())
        assertEquals(500L, speedCursor.getLong(0))
        assertEquals(0L, speedCursor.getLong(1))
        speedCursor.close()

        // Existing cell_records / session_markers round-trip unchanged
        val cellCursor = migratedDb.query("SELECT rat FROM cell_records WHERE id = 1")
        assertTrue("Cell record should survive migration", cellCursor.moveToFirst())
        assertEquals("LTE", cellCursor.getString(0))
        cellCursor.close()

        val markerCursor = migratedDb.query("SELECT type, label FROM session_markers WHERE id = 1")
        assertTrue("Session marker should survive migration", markerCursor.moveToFirst())
        assertEquals("NOTE", markerCursor.getString(0))
        assertEquals("note1", markerCursor.getString(1))
        markerCursor.close()
    }

    @Test
    fun migrateFrom15To16() {
        val db = helper.createDatabase(TEST_DB, 15)
        db.execSQL(
            """INSERT INTO sessions (id, name, createdAt, pointCount, recordingMode) VALUES (1, 'test', 1000, 5, 'OUTDOOR')"""
        )
        // Three legacy rows representing the cases the migration must handle:
        //  (a) fully successful test (succeeded=1, both bps set)
        //  (b) partial-success legacy row: succeeded=0 but downloadBps IS NOT NULL
        //      → must be retroactively re-included: downloadSucceeded=1
        //  (c) SKIPPED_WIFI instant bail-out: succeeded=0, both bps NULL, errorMessage='SKIPPED_WIFI'
        //      → downloadSucceeded=0, uploadSucceeded=NULL
        db.execSQL(
            """INSERT INTO speed_test_records (id, sessionId, timestamp, finishedAt, downloadBps, uploadBps, serverName, serverHost, serverLocation, serverId, dataSimSlotIndex, ratAtTest, rsrpAtTest, bandAtTest, succeeded, errorMessage, networkType)
               VALUES (1, 1, 500, 700, 100000000, 20000000, 'srv', 'host', 'loc', 42, 0, '4G', -90, 3, 1, NULL, 'CELLULAR')"""
        )
        db.execSQL(
            """INSERT INTO speed_test_records (id, sessionId, timestamp, finishedAt, downloadBps, uploadBps, serverName, serverHost, serverLocation, serverId, dataSimSlotIndex, ratAtTest, rsrpAtTest, bandAtTest, succeeded, errorMessage, networkType)
               VALUES (2, 1, 800, 1000, 50000000, NULL, 'srv', 'host', 'loc', 42, 0, '4G', -85, 3, 0, 'No data transferred: upload measurement failed', 'CELLULAR')"""
        )
        db.execSQL(
            """INSERT INTO speed_test_records (id, sessionId, timestamp, finishedAt, downloadBps, uploadBps, serverName, serverHost, serverLocation, serverId, dataSimSlotIndex, ratAtTest, rsrpAtTest, bandAtTest, succeeded, errorMessage, networkType)
               VALUES (3, 1, 1200, 1200, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, 'SKIPPED_WIFI', 'WIFI')"""
        )
        db.close()

        val migratedDb = helper.runMigrationsAndValidate(TEST_DB, 16, true, AppDatabase.MIGRATION_15_16)

        // Row (a): fully successful → downloadSucceeded=1, uploadSucceeded=1
        val rowA = migratedDb.query("SELECT downloadSucceeded, uploadSucceeded, downloadBps, uploadBps, errorMessage, networkType FROM speed_test_records WHERE id = 1")
        assertTrue("Row A should survive migration", rowA.moveToFirst())
        assertEquals(1, rowA.getInt(0))
        assertEquals(1, rowA.getInt(1))
        assertEquals(100000000L, rowA.getLong(2))
        assertEquals(20000000L, rowA.getLong(3))
        assertTrue("Row A errorMessage should be null", rowA.isNull(4))
        assertEquals("CELLULAR", rowA.getString(5))
        rowA.close()

        // Row (b): legacy partial-success → retroactively downloadSucceeded=1, uploadSucceeded=NULL
        val rowB = migratedDb.query("SELECT downloadSucceeded, uploadSucceeded, downloadBps, uploadBps, errorMessage FROM speed_test_records WHERE id = 2")
        assertTrue("Row B should survive migration", rowB.moveToFirst())
        assertEquals("Legacy partial row should have downloadSucceeded=1", 1, rowB.getInt(0))
        assertTrue("Legacy partial row should have uploadSucceeded NULL", rowB.isNull(1))
        assertEquals(50000000L, rowB.getLong(2))
        assertTrue("Row B uploadBps should be null", rowB.isNull(3))
        assertEquals("No data transferred: upload measurement failed", rowB.getString(4))
        rowB.close()

        // Row (c): SKIPPED_WIFI → downloadSucceeded=0, uploadSucceeded=NULL
        val rowC = migratedDb.query("SELECT downloadSucceeded, uploadSucceeded, downloadBps, uploadBps, errorMessage, networkType FROM speed_test_records WHERE id = 3")
        assertTrue("Row C should survive migration", rowC.moveToFirst())
        assertEquals(0, rowC.getInt(0))
        assertTrue("Row C uploadSucceeded should be null", rowC.isNull(1))
        assertTrue("Row C downloadBps should be null", rowC.isNull(2))
        assertTrue("Row C uploadBps should be null", rowC.isNull(3))
        assertEquals("SKIPPED_WIFI", rowC.getString(4))
        assertEquals("WIFI", rowC.getString(5))
        rowC.close()

        // The legacy `succeeded` column MUST be gone
        val columnsCursor = migratedDb.query("PRAGMA table_info(speed_test_records)")
        val columnNames = mutableSetOf<String>()
        while (columnsCursor.moveToNext()) {
            columnNames.add(columnsCursor.getString(1))
        }
        columnsCursor.close()
        assertTrue("downloadSucceeded column should exist", columnNames.contains("downloadSucceeded"))
        assertTrue("uploadSucceeded column should exist", columnNames.contains("uploadSucceeded"))
        assertFalse("succeeded column should be dropped", columnNames.contains("succeeded"))
    }

    @Test
    fun migrateFullChain1To16() {
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
            TEST_DB, 16, true,
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
            AppDatabase.MIGRATION_11_12,
            AppDatabase.MIGRATION_12_13,
            AppDatabase.MIGRATION_13_14,
            AppDatabase.MIGRATION_14_15,
            AppDatabase.MIGRATION_15_16
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