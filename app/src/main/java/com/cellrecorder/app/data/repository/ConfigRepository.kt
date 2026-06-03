package com.cellrecorder.app.data.repository

import com.cellrecorder.app.data.local.dao.ConfigDao
import com.cellrecorder.app.data.local.entity.AppConfigEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConfigRepository @Inject constructor(
    private val configDao: ConfigDao
) {
    fun getConfig(): Flow<AppConfigEntity> = configDao.get().map { it ?: AppConfigEntity() }

    suspend fun update(config: AppConfigEntity) = configDao.update(config)
}