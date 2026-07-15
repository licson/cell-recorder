package com.cellrecorder.app.domain.analytics

import com.cellrecorder.app.data.local.entity.SpeedTestRecordEntity
import com.cellrecorder.app.domain.analytics.model.CorrelationBin
import com.cellrecorder.app.domain.analytics.model.HistogramBin
import com.cellrecorder.app.domain.analytics.model.SimValue
import com.cellrecorder.app.domain.analytics.model.SpeedTestSessionAnalytics
import kotlin.math.ceil

object SpeedTestAnalyticsEngine {

    private val DOWNLOAD_BINS = listOf(
        0L, 5_000_000L, 10_000_000L, 25_000_000L, 50_000_000L,
        100_000_000L, 200_000_000L, 500_000_000L
    )

    private val RSRP_BINS = listOf(
        ">-80" to (-80..Int.MAX_VALUE),
        "-80~-90" to (-90 until -80),
        "-90~-100" to (-100 until -90),
        "<-100" to (Int.MIN_VALUE until -100)
    )

    fun analyze(records: List<SpeedTestRecordEntity>): SpeedTestSessionAnalytics? {
        if (records.isEmpty()) return null

        val measuredRecords = records.filter { it.errorMessage != "SKIPPED_WIFI" }
        if (measuredRecords.isEmpty()) {
            return SpeedTestSessionAnalytics(
                sampleCount = 0,
                failureCount = 0,
                successRate = 0.0,
                avgDownloadBps = null,
                p95DownloadBps = null,
                avgUploadBps = null,
                p95UploadBps = null,
                serverName = null,
                downloadByRsrp = emptyList(),
                downloadByRat = emptyList(),
                downloadBySim = emptyList(),
                uploadByRsrp = null,
                downloadHistogram = emptyList(),
                avgDurationMs = null
            )
        }

        // Download samples come from records where the download phase produced
        // a non-null `downloadBps` (download ran, regardless of upload
        // outcome). This retroactively re-includes legacy rows where the old
        // whole-test `succeeded = false` but `downloadBps` was set.
        val downloadValues = measuredRecords.mapNotNull { it.downloadBps }
        // Upload samples come from records where the upload phase produced a
        // non-null `uploadBps` (upload ran and succeeded).
        val uploadValues = measuredRecords.mapNotNull { it.uploadBps }
        val durations = measuredRecords
            .filter { it.finishedAt > 0 && it.finishedAt > it.timestamp }
            .map { it.finishedAt - it.timestamp }

        // Success rate is computed from `downloadSucceeded` (download is the
        // headline metric; a partial-success cycle counts as success here).
        val succeededCount = measuredRecords.count { it.downloadSucceeded }

        return SpeedTestSessionAnalytics(
            sampleCount = measuredRecords.size,
            failureCount = measuredRecords.size - succeededCount,
            successRate = if (measuredRecords.isNotEmpty()) succeededCount.toDouble() / measuredRecords.size else 0.0,
            avgDownloadBps = downloadValues.average().toLong().takeIf { downloadValues.isNotEmpty() },
            p95DownloadBps = percentile(downloadValues, 0.95),
            avgUploadBps = uploadValues.average().toLong().takeIf { uploadValues.isNotEmpty() },
            p95UploadBps = percentile(uploadValues, 0.95),
            serverName = measuredRecords.firstOrNull { it.downloadSucceeded }?.serverName,
            downloadByRsrp = computeRsrpCorrelation(measuredRecords) { it.downloadBps },
            downloadByRat = computeRatCorrelation(measuredRecords),
            downloadBySim = computeSimCorrelation(measuredRecords),
            uploadByRsrp = computeRsrpCorrelation(uploadRecords(measuredRecords)) { it.uploadBps }.takeIf { it.isNotEmpty() },
            downloadHistogram = computeDownloadHistogram(downloadValues),
            avgDurationMs = durations.average().toLong().takeIf { durations.isNotEmpty() }
        )
    }

    private fun uploadRecords(records: List<SpeedTestRecordEntity>): List<SpeedTestRecordEntity> {
        return records.filter { it.uploadBps != null }
    }

    private fun computeRsrpCorrelation(
        records: List<SpeedTestRecordEntity>,
        valueSelector: (SpeedTestRecordEntity) -> Long?
    ): List<CorrelationBin> {
        return RSRP_BINS.map { (label, range) ->
            val matching = records.filter { r -> r.rsrpAtTest != null && r.rsrpAtTest!! in range }
            CorrelationBin(
                label = label,
                values = listOf(
                    SimValue(
                        simSlotIndex = 0,
                        value = matching.mapNotNull(valueSelector).average().takeIf { it > 0 }
                    )
                )
            )
        }
    }

    private fun computeRatCorrelation(records: List<SpeedTestRecordEntity>): List<CorrelationBin> {
        return records.filter { it.ratAtTest != null }
            .groupBy { it.ratAtTest ?: "UNKNOWN" }
            .map { (rat, group) ->
                CorrelationBin(
                    label = rat,
                    values = listOf(
                        SimValue(
                            simSlotIndex = 0,
                            value = group.mapNotNull { it.downloadBps }.average().takeIf { it > 0 }
                        )
                    )
                )
            }
    }

    private fun computeSimCorrelation(records: List<SpeedTestRecordEntity>): List<CorrelationBin> {
        return records.filter { it.dataSimSlotIndex != null }
            .groupBy { it.dataSimSlotIndex }
            .map { (sim, group) ->
                CorrelationBin(
                    label = "SIM ${sim?.plus(1) ?: "?"}",
                    values = listOf(
                        SimValue(
                            simSlotIndex = sim ?: 0,
                            value = group.mapNotNull { it.downloadBps }.average().takeIf { it > 0 }
                        )
                    )
                )
            }
    }

    private fun computeDownloadHistogram(values: List<Long>): List<HistogramBin> {
        if (values.isEmpty()) return emptyList()

        val total = values.size.toDouble()
        val bins = mutableListOf<HistogramBin>()

        for (i in 0 until DOWNLOAD_BINS.size - 1) {
            val lower = DOWNLOAD_BINS[i]
            val upper = DOWNLOAD_BINS[i + 1]
            val count = values.count { it in lower until upper }
            if (count > 0 || i == 0 || i == DOWNLOAD_BINS.size - 2) {
                bins.add(HistogramBin(
                    label = formatBpsBin(lower, upper),
                    count = count,
                    countLabel = if (total > 0) "$count (${"%.1f".format(count * 100.0 / total)}%)" else "$count"
                ))
            }
        }

        val above = DOWNLOAD_BINS.last()
        val count = values.count { it >= above }
        if (count > 0) {
            bins.add(HistogramBin(
                label = ">${formatBps(above)}",
                count = count,
                countLabel = if (total > 0) "$count (${"%.1f".format(count * 100.0 / total)}%)" else "$count"
            ))
        }

        return bins
    }

    private fun formatBpsBin(lower: Long, upper: Long): String {
        return "${formatBps(lower)}~${formatBps(upper)}"
    }

    private fun formatBps(bps: Long): String {
        return when {
            bps >= 1_000_000_000 -> "${bps / 1_000_000_000}Gbps"
            bps >= 1_000_000 -> "${bps / 1_000_000}Mbps"
            bps >= 1_000 -> "${bps / 1_000}kbps"
            else -> "${bps}bps"
        }
    }

    internal fun percentile(values: List<Long>, p: Double): Long? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val rank = ceil(p * sorted.size).toInt().coerceIn(1, sorted.size)
        return sorted[rank - 1]
    }
}