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
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [DatabaseModule::class]
)
object TestDatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.inMemoryDatabaseBuilder(
            context,
            AppDatabase::class.java
        ).allowMainThreadQueries()
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