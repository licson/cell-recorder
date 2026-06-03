package com.cellrecorder.app.domain.usecase

import com.cellrecorder.app.data.repository.CellRecordRepository
import com.cellrecorder.app.data.local.entity.CellRecordEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetSessionPointsUseCase @Inject constructor(
    private val cellRecordRepository: CellRecordRepository
) {
    operator fun invoke(sessionId: Long): Flow<List<CellRecordEntity>> {
        return cellRecordRepository.getBySessionId(sessionId)
    }

    suspend fun getOnce(sessionId: Long): List<CellRecordEntity> {
        return cellRecordRepository.getBySessionIdOnce(sessionId)
    }
}