package com.cellrecorder.app.domain.usecase.import_

import com.cellrecorder.app.data.local.entity.CellRecordCaBandEntity
import com.cellrecorder.app.data.local.entity.CellRecordEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeoJsonRecordParser @Inject constructor() {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(content: String, sessionId: Long): ParseResult {
        val root: JsonObject = try {
            json.parseToJsonElement(content).jsonObject
        } catch (e: Exception) {
            return ParseResult(emptyList(), errors = listOf(ParseError(0, "Invalid GeoJSON: ${e.message}")))
        }

        if (root["type"]?.jsonPrimitive?.content != "FeatureCollection") {
            return ParseResult(emptyList(), errors = listOf(ParseError(0, "Expected a FeatureCollection")))
        }

        val features = root["features"]?.jsonArray ?: return ParseResult(emptyList(), errors = listOf(ParseError(0, "Missing features array")))

        val records = mutableListOf<CellRecordEntity>()
        val caBandsList = mutableListOf<List<CellRecordCaBandEntity>>()
        val errors = mutableListOf<ParseError>()

        for ((i, featureEl) in features.withIndex()) {
            val featureNum = i + 1
            try {
                val feature = featureEl.jsonObject
                val geom = feature["geometry"]?.jsonObject
                val props = feature["properties"]?.jsonObject

                if (geom == null) {
                    errors.add(ParseError(featureNum, "Feature $featureNum has no geometry"))
                    continue
                }
                if (geom["type"]?.jsonPrimitive?.content != "Point") {
                    errors.add(ParseError(featureNum, "Feature $featureNum is not a Point, skipping"))
                    continue
                }

                val coords = geom["coordinates"]?.jsonArray
                if (coords == null) {
                    errors.add(ParseError(featureNum, "Feature $featureNum has no coordinates"))
                    continue
                }

                val lon = coords.getOrNull(0)?.jsonPrimitive?.doubleOrNull
                val lat = coords.getOrNull(1)?.jsonPrimitive?.doubleOrNull
                val alt = coords.getOrNull(2)?.jsonPrimitive?.doubleOrNull ?: 0.0

                if (lat == null || lon == null) {
                    errors.add(ParseError(featureNum, "Feature $featureNum has invalid coordinates"))
                    continue
                }

                val primitive = { key: String -> props?.get(key) as? JsonPrimitive }

                fun str(key: String): String? = primitive(key)?.content?.takeIf { it.isNotEmpty() }

                fun long(key: String): Long? = primitive(key)?.longOrNull
                fun int(key: String): Int? = primitive(key)?.intOrNull
                fun double(key: String): Double? = primitive(key)?.doubleOrNull

                val ts = long("timestamp")
                if (ts == null) {
                    errors.add(ParseError(featureNum, "Feature $featureNum missing timestamp"))
                    continue
                }

                val caJson = props?.get("caBands") as? JsonArray
                val caBands = parseCaBands(caJson)

                records.add(CellRecordEntity(
                    sessionId = sessionId,
                    timestamp = ts,
                    latitude = lat,
                    longitude = lon,
                    altitude = alt,
                    accuracy = double("accuracy")?.toFloat() ?: 0f,
                    relativeX = double("relativeX"),
                    relativeY = double("relativeY"),
                    subscriptionId = int("subscriptionId"),
                    simSlotIndex = int("simSlotIndex"),
                    rat = str("rat") ?: "UNKNOWN",
                    pci = int("pci"),
                    rsrp = int("rsrp"),
                    rsrq = int("rsrq"),
                    sinr = int("sinr"),
                    enbOrGnbId = long("enbGnbId") ?: long("enb_gnb_id"),
                    lcid = int("lcid"),
                    avgLatencyMs = double("avgLatencyMs") ?: double("avg_latency_ms"),
                    packetLossPct = double("packetLossPct") ?: double("packet_loss_pct"),
                    mcc = str("mcc"),
                    mnc = str("mnc"),
                    bandNumber = int("band"),
                    earfcn = int("earfcn"),
                    tac = int("tac"),
                    anchorEnbOrGnbId = long("anchorEnbGnbId") ?: long("anchor_enb_gnb_id"),
                    anchorLcid = int("anchorLcid"),
                    anchorPci = int("anchorPci"),
                    anchorTac = int("anchorTac"),
                    anchorBandNumber = int("anchorBand") ?: int("anchor_band"),
                    anchorEarfcn = int("anchorEarfcn"),
                    anchorBandwidthKhz = int("anchorBandwidth") ?: int("anchor_bandwidth"),
                    anchorRsrp = int("anchorRsrp"),
                    anchorRsrq = int("anchorRsrq"),
                    anchorSinr = int("anchorSinr"),
                    anchorRssi = int("anchorRssi"),
                    anchorCqi = int("anchorCqi"),
                    anchorTimingAdvance = int("anchorTimingAdvance")
                ))
                caBandsList.add(caBands)
            } catch (e: Exception) {
                errors.add(ParseError(featureNum, "Feature $featureNum error: ${e.message}"))
            }
        }

        return ParseResult(records, caBandsList, errors)
    }

    private fun parseCaBands(arr: JsonArray?): List<CellRecordCaBandEntity> {
        if (arr == null) return emptyList()
        return arr.mapNotNull { el ->
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
    }
}