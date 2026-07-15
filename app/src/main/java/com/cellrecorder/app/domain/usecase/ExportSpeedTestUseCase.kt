package com.cellrecorder.app.domain.usecase

import com.cellrecorder.app.data.local.entity.SpeedTestRecordEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExportSpeedTestUseCase @Inject constructor() {

    fun exportCsv(sessionName: String, records: List<SpeedTestRecordEntity>): ExportData? {
        if (records.isEmpty()) return null

        val sb = StringBuilder()
        sb.appendLine("timestamp,finished_at,download_bps,upload_bps,server_name,server_host,server_location,download_succeeded,upload_succeeded,error_message,data_sim_slot,rat_at_test,rsrp_at_test,band_at_test,network_type")

        for (r in records) {
            sb.appendLine(
                buildString {
                    append(r.timestamp); append(',')
                    append(r.finishedAt); append(',')
                    append(r.downloadBps ?: ""); append(',')
                    append(r.uploadBps ?: ""); append(',')
                    append(csvField(r.serverName)); append(',')
                    append(csvField(r.serverHost)); append(',')
                    append(csvField(r.serverLocation)); append(',')
                    append(if (r.downloadSucceeded) "1" else "0"); append(',')
                    append(when (r.uploadSucceeded) { true -> "1"; false -> "0"; null -> "" }); append(',')
                    append(csvField(r.errorMessage)); append(',')
                    append(r.dataSimSlotIndex ?: ""); append(',')
                    append(csvField(r.ratAtTest)); append(',')
                    append(r.rsrpAtTest ?: ""); append(',')
                    append(r.bandAtTest ?: ""); append(',')
                    append(csvField(r.networkType))
                }
            )
        }

        return ExportData(
            content = sb.toString(),
            mimeType = "text/csv",
            suggestedFilename = "${sessionName.replace(" ", "_")}_speedtest.csv"
        )
    }

    private fun csvField(value: Any?): String {
        val s = value?.toString() ?: ""
        return if (s.contains(',') || s.contains('"') || s.contains('\n') || s.contains('\r')) {
            "\"${s.replace("\"", "\"\"")}\""
        } else {
            s
        }
    }
}