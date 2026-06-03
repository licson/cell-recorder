package com.cellrecorder.app.domain.usecase

import com.cellrecorder.app.data.repository.SessionRepository
import javax.inject.Inject

class StopRecordingUseCase @Inject constructor(
    private val sessionRepository: SessionRepository
) {
    suspend operator fun invoke(sessionId: Long) {
        sessionRepository.updateEndedAt(sessionId, System.currentTimeMillis())
    }
}