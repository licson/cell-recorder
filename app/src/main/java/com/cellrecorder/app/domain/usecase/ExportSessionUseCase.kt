package com.cellrecorder.app.domain.usecase

import com.cellrecorder.app.data.local.entity.CellRecordEntity
import com.cellrecorder.app.data.local.entity.SessionEntity
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
        val sb = StringBuilder()
        sb.appendLine("""{"type":"FeatureCollection","features":[""")
        for ((i, r) in records.withIndex()) {
            if (i > 0) sb.appendLine(",")
            sb.appendLine("""{"type":"Feature","geometry":{"type":"Point","coordinates":[${r.longitude},${r.latitude},${r.altitude}]},"properties":{""")
            sb.appendLine(""""timestamp":${r.timestamp},"subscriptionId":${r.subscriptionId ?: "null"},"simSlotIndex":${r.simSlotIndex ?: "null"},"rat":"${r.rat}","pci":${r.pci ?: "null"},"rsrp":${r.rsrp ?: "null"},"rsrq":${r.rsrq ?: "null"},"sinr":${r.sinr ?: "null"},"enbGnbId":${r.enbOrGnbId ?: "null"},"lcid":${r.lcid ?: "null"},"avgLatencyMs":${r.avgLatencyMs ?: "null"},"packetLossPct":${r.packetLossPct ?: "null"},"mcc":"${r.mcc ?: ""}","mnc":"${r.mnc ?: ""}","band":${r.bandNumber ?: "null"},"earfcn":${r.earfcn ?: "null"},"tac":${r.tac ?: "null"},"isLocationEstimated":${r.isLocationEstimated},"locationSource":"${r.locationSource}""")
            sb.append("}}")
        }
        sb.appendLine("]}")
        return ExportData(
            content = sb.toString(),
            mimeType = "application/geo+json",
            suggestedFilename = "${session.name.replace(" ", "_")}_cell_records.geojson"
        )
    }
}
