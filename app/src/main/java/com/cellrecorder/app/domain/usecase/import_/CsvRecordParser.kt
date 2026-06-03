package com.cellrecorder.app.domain.usecase.import_

import com.cellrecorder.app.data.local.entity.CellRecordCaBandEntity
import com.cellrecorder.app.data.local.entity.CellRecordEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.intOrNull
import javax.inject.Inject
import javax.inject.Singleton

data class ParseError(val line: Int, val message: String)

data class ParseResult(
    val records: List<CellRecordEntity>,
    val caBands: List<List<CellRecordCaBandEntity>> = emptyList(),
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
        "tac" to "tac",
        "ca_bands" to "caBands"
    )

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(content: String, sessionId: Long): ParseResult {
        val lines = content.lines().filter { it.isNotBlank() }
        if (lines.size < 2) return ParseResult(emptyList(), errors = listOf(ParseError(0, "File has no data rows")))

        val headers = parseCsvLine(lines[0])
        val colIdx = mutableMapOf<String, Int>()
        headers.forEachIndexed { i, h ->
            val key = h.trim().lowercase()
            columnMap[key]?.let { colIdx[it] = i }
        }

        if (!colIdx.contains("timestamp") || !colIdx.contains("latitude") || !colIdx.contains("longitude")) {
            return ParseResult(emptyList(), errors = listOf(ParseError(0, "Missing required columns: timestamp, lat, lon")))
        }

        val records = mutableListOf<CellRecordEntity>()
        val caBandsList = mutableListOf<List<CellRecordCaBandEntity>>()
        val errors = mutableListOf<ParseError>()

        for ((i, line) in lines.drop(1).withIndex()) {
            val dataLine = i + 2
            try {
                val cols = parseCsvLine(line)
                val (record, caBands) = parseRow(cols, colIdx, sessionId, dataLine, errors)
                if (record != null) {
                    records.add(record)
                    caBandsList.add(caBands ?: emptyList())
                }
            } catch (e: Exception) {
                errors.add(ParseError(dataLine, e.message ?: "Unexpected error"))
            }
        }

        return ParseResult(records, caBandsList, errors)
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
    ): Pair<CellRecordEntity?, List<CellRecordCaBandEntity>?> {
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
            return null to null
        }

        val caBands = parseCaBands(str("caBands"))

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
        ) to caBands
    }

    private fun parseCaBands(jsonStr: String?): List<CellRecordCaBandEntity>? {
        if (jsonStr.isNullOrBlank()) return null
        return try {
            val arr = json.parseToJsonElement(jsonStr).jsonArray
            arr.map { el ->
                val obj = el.jsonObject
                CellRecordCaBandEntity(
                    cellRecordId = 0,
                    bandNumber = obj["band"]?.jsonPrimitive?.intOrNull,
                    earfcn = obj["earfcn"]?.jsonPrimitive?.intOrNull,
                    pci = obj["pci"]?.jsonPrimitive?.intOrNull,
                    rsrp = obj["rsrp"]?.jsonPrimitive?.intOrNull,
                    rsrq = obj["rsrq"]?.jsonPrimitive?.intOrNull,
                    sinr = obj["sinr"]?.jsonPrimitive?.intOrNull,
                    rssi = obj["rssi"]?.jsonPrimitive?.intOrNull,
                    cqi = obj["cqi"]?.jsonPrimitive?.intOrNull,
                    timingAdvance = obj["timingAdvance"]?.jsonPrimitive?.intOrNull
                )
            }
        } catch (e: Exception) {
            null
        }
    }
}