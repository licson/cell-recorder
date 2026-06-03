package com.cellrecorder.app.domain.usecase.import_

import com.cellrecorder.app.data.local.entity.CellRecordEntity
import javax.inject.Inject
import javax.inject.Singleton

data class ParseError(val line: Int, val message: String)

data class ParseResult(
    val records: List<CellRecordEntity>,
    val errors: List<ParseError>
)

@Singleton
class CsvRecordParser @Inject constructor() {

    private val columnMap = mapOf(
        "timestamp" to "timestamp",
        "lat" to "latitude",
        "lon" to "longitude",
        "alt" to "altitude",
        "accuracy" to "accuracy",
        "subscription_id" to "subscriptionId",
        "sim_slot_index" to "simSlotIndex",
        "rat" to "rat",
        "pci" to "pci",
        "rsrp" to "rsrp",
        "rsrq" to "rsrq",
        "sinr" to "sinr",
        "enb_gnb_id" to "enbOrGnbId",
        "lcid" to "lcid",
        "avg_latency_ms" to "avgLatencyMs",
        "packet_loss_pct" to "packetLossPct",
        "mcc" to "mcc",
        "mnc" to "mnc",
        "band" to "bandNumber",
        "earfcn" to "earfcn",
        "tac" to "tac"
    )

    fun parse(content: String, sessionId: Long): ParseResult {
        val lines = content.lines().filter { it.isNotBlank() }
        if (lines.size < 2) return ParseResult(emptyList(), listOf(ParseError(0, "File has no data rows")))

        val headers = parseCsvLine(lines[0])
        val colIdx = mutableMapOf<String, Int>()
        headers.forEachIndexed { i, h ->
            val key = h.trim().lowercase()
            columnMap[key]?.let { colIdx[it] = i }
        }

        if (!colIdx.contains("timestamp") || !colIdx.contains("latitude") || !colIdx.contains("longitude")) {
            return ParseResult(emptyList(), listOf(ParseError(0, "Missing required columns: timestamp, lat, lon")))
        }

        val records = mutableListOf<CellRecordEntity>()
        val errors = mutableListOf<ParseError>()

        for ((i, line) in lines.drop(1).withIndex()) {
            val dataLine = i + 2
            try {
                val cols = parseCsvLine(line)
                val record = parseRow(cols, colIdx, sessionId, dataLine, errors)
                if (record != null) records.add(record)
            } catch (e: Exception) {
                errors.add(ParseError(dataLine, e.message ?: "Unexpected error"))
            }
        }

        return ParseResult(records, errors)
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

    private fun parseRow(
        cols: List<String>,
        idx: Map<String, Int>,
        sessionId: Long,
        lineNum: Int,
        errors: MutableList<ParseError>
    ): CellRecordEntity? {
        val str = { key: String -> idx[key]?.let { cols.getOrNull(it) }?.takeIf { it.isNotEmpty() } }
        val long = { key: String -> str(key)?.toLongOrNull() }
        val int = { key: String -> str(key)?.toIntOrNull() }
        val double = { key: String -> str(key)?.toDoubleOrNull() }
        val float = { key: String -> str(key)?.toFloatOrNull() }

        val timestamp = long("timestamp")
        val lat = double("latitude")
        val lon = double("longitude")

        if (timestamp == null || lat == null || lon == null) {
            errors.add(ParseError(lineNum, "Missing or invalid timestamp, lat, or lon"))
            return null
        }

        return CellRecordEntity(
            sessionId = sessionId,
            timestamp = timestamp,
            latitude = lat,
            longitude = lon,
            altitude = double("altitude") ?: 0.0,
            accuracy = float("accuracy") ?: 0f,
            subscriptionId = int("subscriptionId"),
            simSlotIndex = int("simSlotIndex"),
            rat = str("rat") ?: "UNKNOWN",
            pci = int("pci"),
            rsrp = int("rsrp"),
            rsrq = int("rsrq"),
            sinr = int("sinr"),
            enbOrGnbId = long("enbOrGnbId"),
            lcid = int("lcid"),
            avgLatencyMs = double("avgLatencyMs"),
            packetLossPct = double("packetLossPct"),
            mcc = str("mcc"),
            mnc = str("mnc"),
            bandNumber = int("band"),
            earfcn = int("earfcn"),
            tac = int("tac")
        )
    }
}