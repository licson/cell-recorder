package com.cellrecorder.app.domain.usecase

import com.cellrecorder.app.data.local.entity.CellRecordEntity
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

    fun exportCsv(session: SessionEntity, records: List<CellRecordEntity>): ExportData {
        val sb = StringBuilder()
        sb.appendLine("timestamp,lat,lon,alt,accuracy,subscription_id,sim_slot_index,rat,pci,rsrp,rsrq,sinr,enb_gnb_id,lcid,avg_latency_ms,packet_loss_pct,mcc,mnc,band,earfcn,tac,is_location_estimated,location_source")
        for (r in records) {
            sb.appendLine(
                buildString {
                    append(r.timestamp); append(',')
                    append(r.latitude); append(',')
                    append(r.longitude); append(',')
                    append(r.altitude); append(',')
                    append(r.accuracy); append(',')
                    append(r.subscriptionId ?: ""); append(',')
                    append(r.simSlotIndex ?: ""); append(',')
                    append(r.rat); append(',')
                    append(r.pci ?: ""); append(',')
                    append(r.rsrp ?: ""); append(',')
                    append(r.rsrq ?: ""); append(',')
                    append(r.sinr ?: ""); append(',')
                    append(r.enbOrGnbId ?: ""); append(',')
                    append(r.lcid ?: ""); append(',')
                    append(r.avgLatencyMs?.let { String.format("%.1f", it) } ?: ""); append(',')
                    append(r.packetLossPct?.let { String.format("%.0f", it) } ?: ""); append(',')
                    append(r.mcc ?: ""); append(',')
                    append(r.mnc ?: ""); append(',')
                    append(r.bandNumber ?: ""); append(',')
                    append(r.earfcn ?: ""); append(',')
                    append(r.tac ?: ""); append(',')
                    append(r.isLocationEstimated); append(',')
                    append(r.locationSource)
                }
            )
        }
        return ExportData(
            content = sb.toString(),
            mimeType = "text/csv",
            suggestedFilename = "${session.name.replace(" ", "_")}_cell_records.csv"
        )
    }

    fun exportGeoJson(session: SessionEntity, records: List<CellRecordEntity>): ExportData {
        val featureCollection = buildJsonObject {
            put("type", "FeatureCollection")
            put("features", buildJsonArray {
                for (r in records) {
                    add(buildJsonObject {
                        put("type", "Feature")
                        put("geometry", buildJsonObject {
                            put("type", "Point")
                            put("coordinates", buildJsonArray {
                                add(JsonPrimitive(r.longitude))
                                add(JsonPrimitive(r.latitude))
                                add(JsonPrimitive(r.altitude))
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

