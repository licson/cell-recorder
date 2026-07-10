package com.cellrecorder.app.domain.analytics.model

data class SpeedTestSessionAnalytics(
    val sampleCount: Int,
    val failureCount: Int,
    val successRate: Double,
    val avgDownloadBps: Long?,
    val p95DownloadBps: Long?,
    val avgUploadBps: Long?,
    val p95UploadBps: Long?,
    val serverName: String?,
    val downloadByRsrp: List<CorrelationBin>,
    val downloadByRat: List<CorrelationBin>,
    val downloadBySim: List<CorrelationBin>,
    val uploadByRsrp: List<CorrelationBin>?,
    val downloadHistogram: List<HistogramBin>,
    /**
     * Average test duration in milliseconds across records with a known positive
     * duration (`finishedAt > timestamp > 0`). `null` when no records have a
     * known duration (all legacy or instant bail-out).
     */
    val avgDurationMs: Long? = null
)