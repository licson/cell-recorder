package com.cellrecorder.app.domain.usecase

import com.cellrecorder.app.data.repository.SessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.cellrecorder.app.data.local.entity.SessionEntity
import com.cellrecorder.app.domain.model.SessionSummary
import javax.inject.Inject

class GetSessionsUseCase @Inject constructor(
    private val sessionRepository: SessionRepository
) {
    operator fun invoke(): Flow<List<SessionSummary>> {
        return sessionRepository.getAll().map { entities ->
            entities.map { it.toSummary() }
        }
    }

    private fun SessionEntity.toSummary() = SessionSummary(
        id = id,
        name = name,
        createdAt = createdAt,
        endedAt = endedAt,
        pointCount = pointCount,
        primarySimSlot = primarySimSlot
    )
}