package com.cellrecorder.app.domain.usecase

import com.cellrecorder.app.data.repository.SessionRepository
import javax.inject.Inject

class CreateSessionUseCase @Inject constructor(
    private val sessionRepository: SessionRepository
) {
    suspend operator fun invoke(name: String): Long {
        return sessionRepository.create(name = name)
    }
}