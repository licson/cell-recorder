package com.cellrecorder.app.domain.speedtest.model

data class SpeedTestResult(
    val downloadBps: Long?,
    val uploadBps: Long?,
    val serverId: Int?,
    val serverName: String?,
    val serverHost: String?,
    val serverLocation: String?,
    val succeeded: Boolean,
    val errorMessage: String?,
    /**
     * Wall-clock millisecond timestamp captured at engine entry (start of test).
     * Always set by `SpeedTestEngine.runTest()`.
     */
    val startedAt: Long = 0L,
    /**
     * Wall-clock millisecond timestamp captured when `runTest()` returns.
     * For instant bail-outs (SKIPPED_WIFI, config/selection failure, exception)
     * this equals [startedAt]. For successful tests this is greater than
     * [startedAt].
     */
    val finishedAt: Long = 0L
)
