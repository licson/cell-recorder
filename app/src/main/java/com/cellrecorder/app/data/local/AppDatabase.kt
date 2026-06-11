package com.cellrecorder.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.cellrecorder.app.data.local.dao.CellRecordDao
import com.cellrecorder.app.data.local.dao.ConfigDao
import com.cellrecorder.app.data.local.dao.SessionDao
import com.cellrecorder.app.data.local.dao.SpeedTestRecordDao
import com.cellrecorder.app.data.local.entity.AppConfigEntity
import com.cellrecorder.app.data.local.entity.CellRecordCaBandEntity
import com.cellrecorder.app.data.local.entity.CellRecordEntity
import com.cellrecorder.app.data.local.entity.SessionEntity
import com.cellrecorder.app.data.local.entity.SpeedTestRecordEntity

@Database(
    entities = [
        SessionEntity::class,
        CellRecordEntity::class,
        CellRecordCaBandEntity::class,
        AppConfigEntity::class,
        SpeedTestRecordEntity::class
    ],
    version = 11,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun sessionDao(): SessionDao
    abstract fun cellRecordDao(): CellRecordDao
    abstract fun configDao(): ConfigDao
    abstract fun speedTestRecordDao(): SpeedTestRecordDao

    companion object {
        const val DATABASE_NAME = "cell_recorder.db"

        val MIGRATION_3_4 = Migration(3, 4) { db ->
            db.execSQL("ALTER TABLE sessions ADD COLUMN primarySimSlot INTEGER")
        }

        val MIGRATION_4_5 = Migration(4, 5) { db ->
            db.execSQL("ALTER TABLE cell_records ADD COLUMN isLocationEstimated INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE cell_records ADD COLUMN locationSource TEXT NOT NULL DEFAULT 'GPS'")
            db.execSQL("ALTER TABLE app_config ADD COLUMN maxGpsLossExtrapolationSec INTEGER NOT NULL DEFAULT 120")
        }

        val MIGRATION_6_7 = Migration(6, 7) { db ->
            db.execSQL("""CREATE TABLE IF NOT EXISTS `cell_record_ca_bands` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `cellRecordId` INTEGER NOT NULL,
                `bandNumber` INTEGER,
                `earfcn` INTEGER,
                `pci` INTEGER,
                `rsrp` INTEGER,
                `rsrq` INTEGER,
                `sinr` INTEGER,
                `rssi` INTEGER,
                `cqi` INTEGER,
                `timingAdvance` INTEGER,
                FOREIGN KEY (`cellRecordId`) REFERENCES `cell_records`(`id`) ON DELETE CASCADE
            )""")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cell_record_ca_bands_cellRecordId` ON `cell_record_ca_bands` (`cellRecordId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cell_record_ca_bands_bandNumber` ON `cell_record_ca_bands` (`bandNumber`)")
        }

        val MIGRATION_7_8 = Migration(7, 8) { db ->
            db.execSQL("ALTER TABLE cell_records ADD COLUMN anchorEnbOrGnbId INTEGER")
            db.execSQL("ALTER TABLE cell_records ADD COLUMN anchorLcid INTEGER")
            db.execSQL("ALTER TABLE cell_records ADD COLUMN anchorPci INTEGER")
            db.execSQL("ALTER TABLE cell_records ADD COLUMN anchorTac INTEGER")
            db.execSQL("ALTER TABLE cell_records ADD COLUMN anchorBandNumber INTEGER")
            db.execSQL("ALTER TABLE cell_records ADD COLUMN anchorEarfcn INTEGER")
            db.execSQL("ALTER TABLE cell_records ADD COLUMN anchorBandwidthKhz INTEGER")
            db.execSQL("ALTER TABLE cell_records ADD COLUMN anchorRsrp INTEGER")
            db.execSQL("ALTER TABLE cell_records ADD COLUMN anchorRsrq INTEGER")
            db.execSQL("ALTER TABLE cell_records ADD COLUMN anchorSinr INTEGER")
            db.execSQL("ALTER TABLE cell_records ADD COLUMN anchorRssi INTEGER")
            db.execSQL("ALTER TABLE cell_records ADD COLUMN anchorCqi INTEGER")
            db.execSQL("ALTER TABLE cell_records ADD COLUMN anchorTimingAdvance INTEGER")
        }

        val MIGRATION_5_6 = Migration(5, 6) { db ->
            db.execSQL("ALTER TABLE app_config ADD COLUMN handoffTimeWindowMs INTEGER NOT NULL DEFAULT 5000")
            db.execSQL("ALTER TABLE app_config ADD COLUMN rsrpDropThresholdDbm INTEGER NOT NULL DEFAULT 15")
            db.execSQL("ALTER TABLE app_config ADD COLUMN rsrpDropTimeWindowMs INTEGER NOT NULL DEFAULT 10000")
            db.execSQL("ALTER TABLE app_config ADD COLUMN latencySpikeSigma REAL NOT NULL DEFAULT 3.0")
            db.execSQL("ALTER TABLE app_config ADD COLUMN pciFlapWindowMs INTEGER NOT NULL DEFAULT 30000")
            db.execSQL("ALTER TABLE app_config ADD COLUMN pciFlapCountThreshold INTEGER NOT NULL DEFAULT 3")
            db.execSQL("ALTER TABLE app_config ADD COLUMN coverageGapThresholdMs INTEGER NOT NULL DEFAULT 30000")
            db.execSQL("ALTER TABLE app_config ADD COLUMN mobilityStationaryKmh REAL NOT NULL DEFAULT 5.0")
            db.execSQL("ALTER TABLE app_config ADD COLUMN mobilityWalkingKmh REAL NOT NULL DEFAULT 15.0")
            db.execSQL("ALTER TABLE app_config ADD COLUMN indoorAccuracyThresholdM REAL NOT NULL DEFAULT 30.0")
            db.execSQL("ALTER TABLE app_config ADD COLUMN tunnelSignalLossThresholdMs INTEGER NOT NULL DEFAULT 10000")
        }

        val MIGRATION_8_9 = Migration(8, 9) { db ->
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cell_records_simSlotIndex_rat` ON `cell_records` (`simSlotIndex`, `rat`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cell_records_bandNumber` ON `cell_records` (`bandNumber`)")
        }

        val MIGRATION_9_10 = Migration(9, 10) { db ->
            db.execSQL("""CREATE TABLE IF NOT EXISTS `speed_test_records` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `sessionId` INTEGER NOT NULL,
                `timestamp` INTEGER NOT NULL,
                `downloadBps` INTEGER,
                `uploadBps` INTEGER,
                `serverName` TEXT,
                `serverHost` TEXT,
                `serverLocation` TEXT,
                `serverId` INTEGER,
                `dataSimSlotIndex` INTEGER,
                `ratAtTest` TEXT,
                `rsrpAtTest` INTEGER,
                `bandAtTest` INTEGER,
                `succeeded` INTEGER NOT NULL,
                `errorMessage` TEXT,
                `networkType` TEXT,
                FOREIGN KEY (`sessionId`) REFERENCES `sessions`(`id`) ON DELETE CASCADE
            )""")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_speed_test_records_sessionId` ON `speed_test_records` (`sessionId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_speed_test_records_timestamp` ON `speed_test_records` (`timestamp`)")
            db.execSQL("ALTER TABLE app_config ADD COLUMN speedTestEnabled INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE app_config ADD COLUMN speedTestIntervalMs INTEGER NOT NULL DEFAULT 60000")
            db.execSQL("ALTER TABLE app_config ADD COLUMN speedTestUploadEnabled INTEGER NOT NULL DEFAULT 1")
            db.execSQL("ALTER TABLE app_config ADD COLUMN speedTestSecure INTEGER NOT NULL DEFAULT 1")
            db.execSQL("ALTER TABLE app_config ADD COLUMN speedTestServerId TEXT")
        }

        val MIGRATION_10_11 = Migration(10, 11) { db ->
            db.execSQL("ALTER TABLE sessions ADD COLUMN recordingMode TEXT NOT NULL DEFAULT 'OUTDOOR'")
            db.execSQL("ALTER TABLE cell_records ADD COLUMN relativeX REAL")
            db.execSQL("ALTER TABLE cell_records ADD COLUMN relativeY REAL")
            db.execSQL("ALTER TABLE app_config ADD COLUMN indoorStepLengthM REAL NOT NULL DEFAULT 0.7")
            db.execSQL("ALTER TABLE app_config ADD COLUMN indoorRecordingIntervalMs INTEGER NOT NULL DEFAULT 5000")
        }

        val CALLBACK = object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                db.execSQL(
                    """INSERT OR IGNORE INTO app_config
                       (id, pingDestination, pingIntervalMs, pingTimeoutMs, recordingIntervalMs,
                        locationChangeThresholdM, gpsAccuracyThresholdM, maxRecordingDurationMin,
                        nrGnbBitLength, cellInfoRefreshIntervalSec, maxGpsLossExtrapolationSec,
                        handoffTimeWindowMs, rsrpDropThresholdDbm, rsrpDropTimeWindowMs,
                        latencySpikeSigma, pciFlapWindowMs, pciFlapCountThreshold,
                        coverageGapThresholdMs, mobilityStationaryKmh, mobilityWalkingKmh,
indoorAccuracyThresholdM, tunnelSignalLossThresholdMs,
                         speedTestEnabled, speedTestIntervalMs, speedTestUploadEnabled, speedTestSecure,
                         indoorStepLengthM, indoorRecordingIntervalMs)
                        VALUES (1, '8.8.8.8', 1000, 3000, 5000, 10.0, 50.0, 120, 24, 5, 120,
                                5000, 15, 10000, 3.0, 30000, 3, 30000, 5.0, 15.0, 30.0, 10000,
                                0, 60000, 1, 1, 0.7, 5000)"""
                )
            }

            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                db.execSQL("PRAGMA foreign_keys = ON")
            }
        }
    }
}