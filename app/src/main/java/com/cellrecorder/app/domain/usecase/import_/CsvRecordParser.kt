package com.cellrecorder.app.domain.usecase.import_

import com.cellrecorder.app.data.local.entity.CellRecordCaBandEntity
import com.cellrecorder.app.data.local.entity.CellRecordEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.intOrNull
import java.util.logging.Logger
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
        "relative_x" to "relativeX",
        "relative_y" to "relativeY",
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
        "bandwidth" to "bandwidthKhz",
        "earfcn" to "earfcn",
        "tac" to "tac",
        "is_location_estimated" to "isLocationEstimated",
        "location_source" to "locationSource",
        "ca_bands" to "caBands",
        "anchor_enb_gnb_id" to "anchorEnbOrGnbId",
        "anchor_lcid" to "anchorLcid",
        "anchor_pci" to "anchorPci",
        "anchor_tac" to "anchorTac",
        "anchor_band" to "anchorBandNumber",
        "anchor_earfcn" to "anchorEarfcn",
        "anchor_bandwidth" to "anchorBandwidthKhz",
        "anchor_rsrp" to "anchorRsrp",
        "anchor_rsrq" to "anchorRsrq",
        "anchor_sinr" to "anchorSinr",
        "anchor_rssi" to "anchorRssi",
        "anchor_cqi" to "anchorCqi",
        "anchor_timing_advance" to "anchorTimingAdvance"
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

        val hasLatLon = colIdx.contains("latitude") && colIdx.contains("longitude")
        val hasRelative = colIdx.contains("relativeX") && colIdx.contains("relativeY")
        if (!colIdx.contains("timestamp") || (!hasLatLon && !hasRelative)) {
            return ParseResult(emptyList(), errors = listOf(ParseError(0, "Missing required columns: timestamp with either lat,lon or relative_x,relative_y")))
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
                    caBandsList.add(caBands)
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
        ): Pair<CellRecordEntity?, List<CellRecordCaBandEntity>> {
        val str = { key: String -> idx[key]?.let { cols.getOrNull(it) }?.takeIf { it.isNotEmpty() } }
        val long = { key: String -> str(key)?.toLongOrNull() }
        val int = { key: String -> str(key)?.toIntOrNull() }
        val double = { key: String -> str(key)?.toDoubleOrNull() }
        val float = { key: String -> str(key)?.toFloatOrNull() }

        val timestamp = long("timestamp")
        val lat = double("latitude")
        val lon = double("longitude")
        val relX = double("relativeX")
        val relY = double("relativeY")
        val hasLatLon = lat != null && lon != null
        val hasRelative = relX != null && relY != null

        if (timestamp == null || (!hasLatLon && !hasRelative)) {
            errors.add(ParseError(lineNum, "Missing or invalid timestamp and coordinates"))
            return null to emptyList()
        }

        val caBands = parseCaBands(str("caBands"))

        return CellRecordEntity(
            sessionId = sessionId,
            timestamp = timestamp,
            latitude = lat ?: 0.0,
            longitude = lon ?: 0.0,
            altitude = double("altitude") ?: 0.0,
            accuracy = float("accuracy") ?: 0f,
            relativeX = relX,
            relativeY = relY,
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
            bandNumber = int("bandNumber"),
            earfcn = int("earfcn"),
            bandwidthKhz = int("bandwidthKhz"),
            tac = int("tac"),
            isLocationEstimated = str("isLocationEstimated")?.toBoolean() ?: false,
            locationSource = str("locationSource") ?: "GPS",
            anchorEnbOrGnbId = long("anchorEnbOrGnbId"),
            anchorLcid = int("anchorLcid"),
            anchorPci = int("anchorPci"),
            anchorTac = int("anchorTac"),
            anchorBandNumber = int("anchorBandNumber"),
            anchorEarfcn = int("anchorEarfcn"),
            anchorBandwidthKhz = int("anchorBandwidthKhz"),
            anchorRsrp = int("anchorRsrp"),
            anchorRsrq = int("anchorRsrq"),
            anchorSinr = int("anchorSinr"),
            anchorRssi = int("anchorRssi"),
            anchorCqi = int("anchorCqi"),
            anchorTimingAdvance = int("anchorTimingAdvance")
        ) to caBands
    }

    private fun parseCaBands(jsonStr: String?): List<CellRecordCaBandEntity> {
        if (jsonStr.isNullOrBlank()) return emptyList()
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
                    timingAdvance = obj["timingAdvance"]?.jsonPrimitive?.intOrNull,
                    bandwidthKhz = obj["bandwidth"]?.jsonPrimitive?.intOrNull
                )
            }
        } catch (e: Exception) {
            logger.warning("Malformed ca_bands JSON ignored: ${e.message}")
            emptyList()
        }
    }

    companion object {
        private val logger = Logger.getLogger(CsvRecordParser::class.java.name)
    }
}