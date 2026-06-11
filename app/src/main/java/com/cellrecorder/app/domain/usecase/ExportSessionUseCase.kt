package com.cellrecorder.app.domain.usecase

import com.cellrecorder.app.data.local.entity.CellRecordEntity
import com.cellrecorder.app.data.local.entity.CellRecordWithCaBands
import com.cellrecorder.app.data.local.entity.SessionEntity
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

data class ExportData(
    val content: String,
    val mimeType: String,
    val suggestedFilename: String
)

@Singleton
class ExportSessionUseCase @Inject constructor() {

    private fun csvField(value: Any?): String {
        val s = value?.toString() ?: ""
        return if (s.contains(',') || s.contains('"') || s.contains('\n') || s.contains('\r')) {
            "\"${s.replace("\"", "\"\"")}\""
        } else {
            s
        }
    }

    fun exportCsv(session: SessionEntity, records: List<CellRecordWithCaBands>): ExportData {
        val sb = StringBuilder()
        val isIndoor = session.recordingMode == "INDOOR"
        sb.appendLine("timestamp,lat,lon,alt,accuracy,relative_x,relative_y,subscription_id,sim_slot_index,rat,pci,rsrp,rsrq,sinr,enb_gnb_id,lcid,avg_latency_ms,packet_loss_pct,mcc,mnc,band,earfcn,tac,is_location_estimated,location_source,ca_bands,anchor_enb_gnb_id,anchor_lcid,anchor_pci,anchor_tac,anchor_band,anchor_earfcn,anchor_bandwidth,anchor_rsrp,anchor_rsrq,anchor_sinr,anchor_rssi,anchor_cqi,anchor_timing_advance")
        for (wrapper in records) {
            val r = wrapper.record
            val caJson = if (wrapper.caBands.isNotEmpty()) {
                buildJsonArray {
                    for (ca in wrapper.caBands) {
                        add(buildJsonObject {
                            ca.bandNumber?.let { put("band", it) }
                            ca.earfcn?.let { put("earfcn", it) }
                            ca.pci?.let { put("pci", it) }
                            ca.rsrp?.let { put("rsrp", it) }
                            ca.rsrq?.let { put("rsrq", it) }
                            ca.sinr?.let { put("sinr", it) }
                            ca.rssi?.let { put("rssi", it) }
                            ca.cqi?.let { put("cqi", it) }
                            ca.timingAdvance?.let { put("timingAdvance", it) }
                        })
                    }
                }.toString()
            } else ""
            sb.appendLine(
                buildString {
                    append(r.timestamp); append(',')
                    append(r.latitude); append(',')
                    append(r.longitude); append(',')
                    append(r.altitude); append(',')
                    append(r.accuracy); append(',')
                    append(csvField(r.relativeX)); append(',')
                    append(csvField(r.relativeY)); append(',')
                    append(csvField(r.subscriptionId)); append(',')
                    append(csvField(r.simSlotIndex)); append(',')
                    append(csvField(r.rat)); append(',')
                    append(csvField(r.pci)); append(',')
                    append(csvField(r.rsrp)); append(',')
                    append(csvField(r.rsrq)); append(',')
                    append(csvField(r.sinr)); append(',')
                    append(csvField(r.enbOrGnbId)); append(',')
                    append(csvField(r.lcid)); append(',')
                    append(r.avgLatencyMs?.let { String.format("%.1f", it) } ?: ""); append(',')
                    append(r.packetLossPct?.let { String.format("%.0f", it) } ?: ""); append(',')
                    append(csvField(r.mcc)); append(',')
                    append(csvField(r.mnc)); append(',')
                    append(csvField(r.bandNumber)); append(',')
                    append(csvField(r.earfcn)); append(',')
                    append(csvField(r.tac)); append(',')
                    append(r.isLocationEstimated); append(',')
                    append(csvField(r.locationSource)); append(',')
                    append(csvField(caJson)); append(',')
                    append(csvField(r.anchorEnbOrGnbId)); append(',')
                    append(csvField(r.anchorLcid)); append(',')
                    append(csvField(r.anchorPci)); append(',')
                    append(csvField(r.anchorTac)); append(',')
                    append(csvField(r.anchorBandNumber)); append(',')
                    append(csvField(r.anchorEarfcn)); append(',')
                    append(csvField(r.anchorBandwidthKhz)); append(',')
                    append(csvField(r.anchorRsrp)); append(',')
                    append(csvField(r.anchorRsrq)); append(',')
                    append(csvField(r.anchorSinr)); append(',')
                    append(csvField(r.anchorRssi)); append(',')
                    append(csvField(r.anchorCqi)); append(',')
                    append(csvField(r.anchorTimingAdvance))
                }
            )
        }
        return ExportData(
            content = sb.toString(),
            mimeType = "text/csv",
            suggestedFilename = "${session.name.replace(" ", "_")}_cell_records.csv"
        )
    }

    fun exportGeoJson(session: SessionEntity, records: List<CellRecordWithCaBands>): ExportData {
        val isIndoor = session.recordingMode == "INDOOR"
        val featureCollection = buildJsonObject {
            put("type", "FeatureCollection")
            if (isIndoor) {
                put("indoorMode", true)
                put("coordinateReference", "relative")
            }
            put("features", buildJsonArray {
                for (wrapper in records) {
                    val r = wrapper.record
                    val (coordLon, coordLat, coordAlt) = if (isIndoor) {
                        Triple(
                            (r.relativeX ?: 0.0) / 111320.0,
                            (r.relativeY ?: 0.0) / 111320.0,
                            0.0
                        )
                    } else {
                        Triple(r.longitude, r.latitude, r.altitude)
                    }
                    add(buildJsonObject {
                        put("type", "Feature")
                        put("geometry", buildJsonObject {
                            put("type", "Point")
                            put("coordinates", buildJsonArray {
                                add(JsonPrimitive(coordLon))
                                add(JsonPrimitive(coordLat))
                                add(JsonPrimitive(coordAlt))
                            })
                        })
                        put("properties", buildJsonObject {
                            put("timestamp", r.timestamp)
                            put("subscriptionId", r.subscriptionId)
                            put("simSlotIndex", r.simSlotIndex)
                            put("rat", r.rat)
                            put("pci", r.pci)
                            put("rsrp", r.rsrp)
                            put("rsrq", r.rsrq)
                            put("sinr", r.sinr)
                            put("enbGnbId", r.enbOrGnbId)
                            put("lcid", r.lcid)
                            put("avgLatencyMs", r.avgLatencyMs)
                            put("packetLossPct", r.packetLossPct)
                            put("mcc", r.mcc)
                            put("mnc", r.mnc)
                            put("band", r.bandNumber)
                            put("earfcn", r.earfcn)
                            put("tac", r.tac)
                            put("isLocationEstimated", r.isLocationEstimated)
                            put("locationSource", r.locationSource)
                            if (isIndoor) {
                                r.relativeX?.let { put("relativeX", it) }
                                r.relativeY?.let { put("relativeY", it) }
                            }
                            if (wrapper.caBands.isNotEmpty()) {
                                put("caBands", buildJsonArray {
                                    for (ca in wrapper.caBands) {
                                        add(buildJsonObject {
                                            ca.bandNumber?.let { put("band", it) }
                                            ca.earfcn?.let { put("earfcn", it) }
                                            ca.pci?.let { put("pci", it) }
                                            ca.rsrp?.let { put("rsrp", it) }
                                            ca.rsrq?.let { put("rsrq", it) }
                                            ca.sinr?.let { put("sinr", it) }
                                            ca.rssi?.let { put("rssi", it) }
                                            ca.cqi?.let { put("cqi", it) }
                                            ca.timingAdvance?.let { put("timingAdvance", it) }
                                        })
                                    }
                                })
                            }
                            r.anchorEnbOrGnbId?.let { put("anchorEnbGnbId", it) }
                            r.anchorLcid?.let { put("anchorLcid", it) }
                            r.anchorPci?.let { put("anchorPci", it) }
                            r.anchorTac?.let { put("anchorTac", it) }
                            r.anchorBandNumber?.let { put("anchorBand", it) }
                            r.anchorEarfcn?.let { put("anchorEarfcn", it) }
                            r.anchorBandwidthKhz?.let { put("anchorBandwidth", it) }
                            r.anchorRsrp?.let { put("anchorRsrp", it) }
                            r.anchorRsrq?.let { put("anchorRsrq", it) }
                            r.anchorSinr?.let { put("anchorSinr", it) }
                            r.anchorRssi?.let { put("anchorRssi", it) }
                            r.anchorCqi?.let { put("anchorCqi", it) }
                            r.anchorTimingAdvance?.let { put("anchorTimingAdvance", it) }
                        })
                    })
                }
            })
        }
        return ExportData(
            content = featureCollection.toString(),
            mimeType = "application/geo+json",
            suggestedFilename = "${session.name.replace(" ", "_")}_cell_records.geojson"
        )
    }
}