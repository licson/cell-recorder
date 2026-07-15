package com.cellrecorder.app.domain.speedtest.model

data class SpeedTestResult(
    val downloadBps: Long?,
    val uploadBps: Long?,
    val serverId: Int?,
    val serverName: String?,
    val serverHost: String?,
    val serverLocation: String?,
    /**
     * `true` when the download phase ran and produced a non-null `downloadBps`.
     * `false` when download failed or the test bailed out before the download
     * phase (WiFi skip, config/selection failure, exception).
     */
    val downloadSucceeded: Boolean,
    /**
     * `null` when upload was not run (upload disabled in config, WiFi skip,
     * instant bail-out, or pre-upload probe skipped the upload phase).
     * `false` when upload ran but failed.
     * `true` only when upload ran and succeeded.
     */
    val uploadSucceeded: Boolean?,
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
