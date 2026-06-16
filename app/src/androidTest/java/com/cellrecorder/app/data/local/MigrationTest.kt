package com.cellrecorder.app.data.local

import androidx.room.migration.AutoMigrationSpec
import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
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
    fun migrateFrom3To4() {
        helper.createDatabase(TEST_DB, 3)
        helper.runMigrationsAndValidate(TEST_DB, 4, true, AppDatabase.MIGRATION_3_4)
    }

    @Test
    fun migrateFrom4To5() {
        helper.createDatabase(TEST_DB, 4)
        helper.runMigrationsAndValidate(TEST_DB, 5, true, AppDatabase.MIGRATION_4_5)
    }

    @Test
    fun migrateFrom5To6() {
        helper.createDatabase(TEST_DB, 5)
        helper.runMigrationsAndValidate(TEST_DB, 6, true, AppDatabase.MIGRATION_5_6)
    }

    @Test
    fun migrateFrom6To7() {
        helper.createDatabase(TEST_DB, 6)
        helper.runMigrationsAndValidate(TEST_DB, 7, true, AppDatabase.MIGRATION_6_7)
    }

    @Test
    fun migrateFrom7To8() {
        helper.createDatabase(TEST_DB, 7)
        helper.runMigrationsAndValidate(TEST_DB, 8, true, AppDatabase.MIGRATION_7_8)
    }

    @Test
    fun migrateFrom8To9() {
        helper.createDatabase(TEST_DB, 8)
        helper.runMigrationsAndValidate(TEST_DB, 9, true, AppDatabase.MIGRATION_8_9)
    }

    @Test
    fun migrateFrom9To10() {
        helper.createDatabase(TEST_DB, 9)
        helper.runMigrationsAndValidate(TEST_DB, 10, true, AppDatabase.MIGRATION_9_10)
    }

    @Test
    fun migrateFrom10To11() {
        helper.createDatabase(TEST_DB, 10)
        helper.runMigrationsAndValidate(TEST_DB, 11, true, AppDatabase.MIGRATION_10_11)
    }

    @Test
    fun migrateFrom11To12() {
        helper.createDatabase(TEST_DB, 11)
        helper.runMigrationsAndValidate(TEST_DB, 12, true, AppDatabase.MIGRATION_11_12)
    }

    @Test
    fun migrateFullChain3To12() {
        helper.createDatabase(TEST_DB, 3)
        helper.runMigrationsAndValidate(TEST_DB, 12, true,
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
    }

    @Test
    fun migrateFullChain5To12() {
        helper.createDatabase(TEST_DB, 5)
        helper.runMigrationsAndValidate(TEST_DB, 12, true,
            AppDatabase.MIGRATION_5_6,
            AppDatabase.MIGRATION_6_7,
            AppDatabase.MIGRATION_7_8,
            AppDatabase.MIGRATION_8_9,
            AppDatabase.MIGRATION_9_10,
            AppDatabase.MIGRATION_10_11,
            AppDatabase.MIGRATION_11_12
        )
    }
}