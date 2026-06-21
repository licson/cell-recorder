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

        val succeeded = records.filter { it.succeeded }
        val downloadValues = succeeded.mapNotNull { it.downloadBps }
        val uploadValues = succeeded.mapNotNull { it.uploadBps }

        return SpeedTestSessionAnalytics(
            sampleCount = records.size,
            failureCount = records.size - succeeded.size,
            successRate = if (records.isNotEmpty()) succeeded.size.toDouble() / records.size else 0.0,
            avgDownloadBps = downloadValues.average().toLong().takeIf { downloadValues.isNotEmpty() },
            p95DownloadBps = percentile(downloadValues, 0.95),
            avgUploadBps = uploadValues.average().toLong().takeIf { uploadValues.isNotEmpty() },
            p95UploadBps = percentile(uploadValues, 0.95),
            serverName = succeeded.firstOrNull()?.serverName,
            downloadByRsrp = computeRsrpCorrelation(succeeded) { it.downloadBps },
            downloadByRat = computeRatCorrelation(succeeded),
            downloadBySim = computeSimCorrelation(succeeded),
            uploadByRsrp = computeRsrpCorrelation(uploadRecords(succeeded)) { it.uploadBps }.takeIf { it.isNotEmpty() },
            downloadHistogram = computeDownloadHistogram(downloadValues)
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