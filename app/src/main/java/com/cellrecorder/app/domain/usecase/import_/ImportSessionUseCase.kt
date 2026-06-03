package com.cellrecorder.app.domain.usecase.import_

import com.cellrecorder.app.data.local.entity.SessionEntity
import com.cellrecorder.app.data.repository.CellRecordRepository
import com.cellrecorder.app.data.repository.SessionRepository
import javax.inject.Inject
import javax.inject.Singleton

data class ImportSummary(
    val sessionName: String,
    val importedCount: Int,
    val errorCount: Int,
    val errors: List<ParseError>
)

@Singleton
class ImportSessionUseCase @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val cellRecordRepository: CellRecordRepository,
    private val csvRecordParser: CsvRecordParser,
    private val geoJsonRecordParser: GeoJsonRecordParser
) {
    suspend fun importCsv(
        content: String,
        sessionName: String
    ): ImportSummary {
        val sessionId = sessionRepository.create(name = sessionName)

        val result = csvRecordParser.parse(content, sessionId)

        if (result.records.isNotEmpty()) {
            cellRecordRepository.insertAll(result.records)
            sessionRepository.updateEndedAt(sessionId, System.currentTimeMillis())
        }

        return ImportSummary(
            sessionName = sessionName,
            importedCount = result.records.size,
            errorCount = result.errors.size,
            errors = result.errors
        )
    }

    suspend fun importGeoJson(
        content: String,
        sessionName: String
    ): ImportSummary {
        val sessionId = sessionRepository.create(name = sessionName)

        val result = geoJsonRecordParser.parse(content, sessionId)

        if (result.records.isNotEmpty()) {
            cellRecordRepository.insertAll(result.records)
            sessionRepository.updateEndedAt(sessionId, System.currentTimeMillis())
        }

        return ImportSummary(
            sessionName = sessionName,
            importedCount = result.records.size,
            errorCount = result.errors.size,
            errors = result.errors
        )
    }
}