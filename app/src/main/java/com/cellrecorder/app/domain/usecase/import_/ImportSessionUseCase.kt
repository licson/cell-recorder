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
    val errors: List<ParseError>,
    val recordingMode: String = "OUTDOOR"
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
        val result = csvRecordParser.parse(content, 0)
        val isIndoor = result.records.any { it.relativeX != null || it.relativeY != null }
        val recordingMode = if (isIndoor) "INDOOR" else "OUTDOOR"
        val sessionId = sessionRepository.create(name = sessionName, recordingMode = recordingMode)

        val persistedResult = csvRecordParser.parse(content, sessionId)

        if (persistedResult.records.isNotEmpty()) {
            val ids = cellRecordRepository.insertAll(persistedResult.records)
            for (i in persistedResult.records.indices) {
                val caBands = persistedResult.caBands.getOrElse(i) { emptyList() }
                if (caBands.isNotEmpty()) {
                    cellRecordRepository.insertCaBands(caBands.map { it.copy(cellRecordId = ids[i]) })
                }
            }
            sessionRepository.refreshPointCount(sessionId)
            sessionRepository.updateEndedAt(sessionId, System.currentTimeMillis())
        }

        return ImportSummary(
            sessionName = sessionName,
            importedCount = persistedResult.records.size,
            errorCount = persistedResult.errors.size,
            errors = persistedResult.errors,
            recordingMode = recordingMode
        )
    }

    suspend fun importGeoJson(
        content: String,
        sessionName: String
    ): ImportSummary {
        val geoResult = geoJsonRecordParser.parse(content, 0)
        val isIndoor = geoResult.errors.isEmpty() && content.contains("\"indoorMode\"")
        val recordingMode = if (isIndoor) "INDOOR" else "OUTDOOR"
        val sessionId = sessionRepository.create(name = sessionName, recordingMode = recordingMode)

        val result = geoJsonRecordParser.parse(content, sessionId)

        if (result.records.isNotEmpty()) {
            val ids = cellRecordRepository.insertAll(result.records)
            for (i in result.records.indices) {
                val caBands = result.caBands.getOrElse(i) { emptyList() }
                if (caBands.isNotEmpty()) {
                    cellRecordRepository.insertCaBands(caBands.map { it.copy(cellRecordId = ids[i]) })
                }
            }
            sessionRepository.refreshPointCount(sessionId)
            sessionRepository.updateEndedAt(sessionId, System.currentTimeMillis())
        }

        return ImportSummary(
            sessionName = sessionName,
            importedCount = result.records.size,
            errorCount = result.errors.size,
            errors = result.errors,
            recordingMode = recordingMode
        )
    }
}