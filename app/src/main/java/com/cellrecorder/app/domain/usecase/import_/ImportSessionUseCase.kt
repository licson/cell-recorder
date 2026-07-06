package com.cellrecorder.app.domain.usecase.import_

import com.cellrecorder.app.data.local.entity.SessionEntity
import com.cellrecorder.app.data.local.entity.SessionMarkerEntity
import com.cellrecorder.app.data.repository.CellRecordRepository
import com.cellrecorder.app.data.repository.SessionMarkerRepository
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
    private val sessionMarkerRepository: SessionMarkerRepository,
    private val csvRecordParser: CsvRecordParser,
    private val geoJsonRecordParser: GeoJsonRecordParser
) {
    suspend fun importCsv(
        content: String,
        sessionName: String,
        markersContent: String? = null
    ): ImportSummary {
        val result = csvRecordParser.parse(content, 0)
        val hasLocationSourceTunnel = result.records.any { it.locationSource == "TUNNEL" }
        val hasRelativeCoords = result.records.any { it.relativeX != null || it.relativeY != null }
        val hasMarkers = !markersContent.isNullOrBlank()
        val recordingMode = when {
            hasMarkers || hasLocationSourceTunnel -> "TUNNEL"
            hasRelativeCoords -> "INDOOR"
            else -> "OUTDOOR"
        }
        val sessionId = sessionRepository.create(name = sessionName, recordingMode = recordingMode)

        val persistedRecords = result.records.map { it.copy(sessionId = sessionId) }
        val persistedCaBands = result.caBands

        if (persistedRecords.isNotEmpty()) {
            val ids = cellRecordRepository.insertAll(persistedRecords)
            for (i in persistedRecords.indices) {
                val caBands = persistedCaBands.getOrElse(i) { emptyList() }
                if (caBands.isNotEmpty()) {
                    cellRecordRepository.insertCaBands(caBands.map { it.copy(cellRecordId = ids[i]) })
                }
            }
            sessionRepository.refreshPointCount(sessionId)
            sessionRepository.updateEndedAt(sessionId, System.currentTimeMillis())
        }

        if (hasMarkers) {
            val markers = parseMarkersCsv(markersContent!!)
            if (markers.isNotEmpty()) {
                sessionMarkerRepository.insertAll(markers.map { it.copy(sessionId = sessionId) })
            }
        }

        return ImportSummary(
            sessionName = sessionName,
            importedCount = persistedRecords.size,
            errorCount = result.errors.size,
            errors = result.errors,
            recordingMode = recordingMode
        )
    }

    private fun parseMarkersCsv(content: String): List<SessionMarkerEntity> {
        val lines = content.lines().filter { it.isNotBlank() }
        if (lines.size < 2) return emptyList()
        val headers = parseCsvLine(lines[0])
        val colIdx = headers.map { it.trim().lowercase() }.withIndex().associate { it.value to it.index }
        val tsIdx = colIdx["timestamp"] ?: return emptyList()
        val seqIdx = colIdx["seq"] ?: return emptyList()
        val typeIdx = colIdx["type"] ?: return emptyList()
        val labelIdx = colIdx["label"] ?: return emptyList()

        return lines.drop(1).mapNotNull { line ->
            try {
                val cols = parseCsvLine(line)
                val timestamp = cols.getOrNull(tsIdx)?.toLongOrNull() ?: return@mapNotNull null
                val seq = cols.getOrNull(seqIdx)?.toIntOrNull() ?: return@mapNotNull null
                val type = cols.getOrNull(typeIdx)?.trim() ?: return@mapNotNull null
                val label = cols.getOrNull(labelIdx)?.trim()?.takeIf { it.isNotBlank() }
                SessionMarkerEntity(
                    sessionId = 0,
                    timestamp = timestamp,
                    seq = seq,
                    type = type,
                    label = label
                )
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        for (ch in line) {
            when {
                ch == '"' -> inQuotes = !inQuotes
                ch == ',' && !inQuotes -> {
                    result.add(current.toString().trim())
                    current.clear()
                }
                else -> current.append(ch)
            }
        }
        result.add(current.toString().trim())
        return result
    }

    suspend fun importGeoJson(
        content: String,
        sessionName: String
    ): ImportSummary {
        val geoResult = geoJsonRecordParser.parse(content, 0)
        val isIndoor = content.contains("\"indoorMode\"")
        val isTunnel = content.contains("\"tunnelMode\"")
        val recordingMode = when {
            isTunnel -> "TUNNEL"
            isIndoor -> "INDOOR"
            else -> "OUTDOOR"
        }
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

        if (result.markers.isNotEmpty()) {
            sessionMarkerRepository.insertAll(result.markers)
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