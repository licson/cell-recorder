package com.cellrecorder.app.domain.usecase

import com.cellrecorder.app.data.local.entity.CellRecordWithCaBands
import com.cellrecorder.app.data.repository.CellRecordRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetSessionPointsUseCase @Inject constructor(
    private val cellRecordRepository: CellRecordRepository
) {
    operator fun invoke(sessionId: Long): Flow<List<CellRecordWithCaBands>> {
        return cellRecordRepository.getBySessionIdWithCaBands(sessionId)
    }

    suspend fun getOnce(sessionId: Long): List<CellRecordWithCaBands> {
        return cellRecordRepository.getBySessionIdOnceWithCaBands(sessionId)
    }
}