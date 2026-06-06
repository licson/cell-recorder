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
    val downloadHistogram: List<HistogramBin>
)