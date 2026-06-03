package com.cellrecorder.app.di

import android.content.Context
import cz.mroczis.netmonster.core.INetMonster
import cz.mroczis.netmonster.core.factory.NetMonsterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetMonsterModule {

    @Provides
    @Singleton
    fun provideNetMonster(@ApplicationContext context: Context): INetMonster {
        return NetMonsterFactory.get(context)
    }
}