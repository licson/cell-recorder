package com.cellrecorder.app.di

import android.content.Context
import androidx.room.Room
import com.cellrecorder.app.data.local.AppDatabase
import com.cellrecorder.app.data.local.dao.CellRecordDao
import com.cellrecorder.app.data.local.dao.ConfigDao
import com.cellrecorder.app.data.local.dao.RecentMarkerLabelDao
import com.cellrecorder.app.data.local.dao.SessionDao
import com.cellrecorder.app.data.local.dao.SessionMarkerDao
import com.cellrecorder.app.data.local.dao.SpeedTestRecordDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME
        ).addCallback(AppDatabase.CALLBACK)
         .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5, AppDatabase.MIGRATION_5_6, AppDatabase.MIGRATION_6_7, AppDatabase.MIGRATION_7_8, AppDatabase.MIGRATION_8_9, AppDatabase.MIGRATION_9_10, AppDatabase.MIGRATION_10_11, AppDatabase.MIGRATION_11_12, AppDatabase.MIGRATION_12_13, AppDatabase.MIGRATION_13_14, AppDatabase.MIGRATION_14_15)
         .build()
    }

    @Provides
    fun provideSessionDao(db: AppDatabase): SessionDao = db.sessionDao()

    @Provides
    fun provideCellRecordDao(db: AppDatabase): CellRecordDao = db.cellRecordDao()

    @Provides
    fun provideConfigDao(db: AppDatabase): ConfigDao = db.configDao()

    @Provides
    fun provideSpeedTestRecordDao(db: AppDatabase): SpeedTestRecordDao = db.speedTestRecordDao()

    @Provides
    fun provideSessionMarkerDao(db: AppDatabase): SessionMarkerDao = db.sessionMarkerDao()

    @Provides
    fun provideRecentMarkerLabelDao(db: AppDatabase): RecentMarkerLabelDao = db.recentMarkerLabelDao()
}