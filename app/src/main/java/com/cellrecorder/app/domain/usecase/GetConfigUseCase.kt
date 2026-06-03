package com.cellrecorder.app.domain.usecase

import com.cellrecorder.app.data.repository.ConfigRepository
import com.cellrecorder.app.data.local.entity.AppConfigEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetConfigUseCase @Inject constructor(
    private val configRepository: ConfigRepository
) {
    operator fun invoke(): Flow<AppConfigEntity> = configRepository.getConfig()
}