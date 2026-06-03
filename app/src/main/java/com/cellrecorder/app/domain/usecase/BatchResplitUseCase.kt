package com.cellrecorder.app.domain.usecase

import com.cellrecorder.app.data.repository.CellRecordRepository
import javax.inject.Inject

class BatchResplitUseCase @Inject constructor(
    private val cellRecordRepository: CellRecordRepository
) {
    suspend operator fun invoke(sessionId: Long, nrBitLen: Int) {
        cellRecordRepository.batchResplit(
            sessionId = sessionId,
            nrBitLen = nrBitLen
        )
    }
}