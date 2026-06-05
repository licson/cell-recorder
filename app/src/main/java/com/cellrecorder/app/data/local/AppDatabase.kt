package com.cellrecorder.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.cellrecorder.app.data.local.dao.CellRecordDao
import com.cellrecorder.app.data.local.dao.ConfigDao
import com.cellrecorder.app.data.local.dao.SessionDao
import com.cellrecorder.app.data.local.entity.AppConfigEntity
import com.cellrecorder.app.data.local.entity.CellRecordCaBandEntity
import com.cellrecorder.app.data.local.entity.CellRecordEntity
import com.cellrecorder.app.data.local.entity.SessionEntity

@Database(
    entities = [
        SessionEntity::class,
        CellRecordEntity::class,
        CellRecordCaBandEntity::class,
        AppConfigEntity::class
    ],
    version = 7,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun sessionDao(): SessionDao
    abstract fun cellRecordDao(): CellRecordDao
    abstract fun configDao(): ConfigDao

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
                        indoorAccuracyThresholdM, tunnelSignalLossThresholdMs)
                       VALUES (1, '8.8.8.8', 1000, 3000, 5000, 10.0, 50.0, 120, 24, 5, 120,
                               5000, 15, 10000, 3.0, 30000, 3, 30000, 5.0, 15.0, 30.0, 10000)"""
                )
            }

            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                db.execSQL("PRAGMA foreign_keys = ON")
            }
        }
    }
}