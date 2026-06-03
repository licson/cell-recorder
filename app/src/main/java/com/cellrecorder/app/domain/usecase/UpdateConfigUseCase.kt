package com.cellrecorder.app.domain.usecase

import com.cellrecorder.app.data.repository.ConfigRepository
import com.cellrecorder.app.data.local.entity.AppConfigEntity
import javax.inject.Inject

class UpdateConfigUseCase @Inject constructor(
    private val configRepository: ConfigRepository
) {
    suspend operator fun invoke(config: AppConfigEntity) = configRepository.update(config)
}