package com.cellrecorder.app.domain.speedtest

/**
 * A structured speedtest debug event emitted by [SpeedTestEngine] at every phase
 * and decision point during a `runTest()` cycle. Consumed by
 * [SpeedTestDebugRingBuffer] for in-app diagnostics and "Share Debug Log" export.
 *
 * @property timestampMs Wall-clock millisecond timestamp of the event.
 * @property phase One of: `config_fetch`, `server_select`, `gauge`, `download`,
 *                 `probe`, `upload`, `done`, `error`.
 * @property status One of: `ok`, `warn`, `fail`, `info`.
 * @property message Human-readable description of the event.
 * @property serverId The server ID associated with the event, if any.
 * @property serverHost The server host associated with the event, if any.
 * @property bytes Byte count associated with the event, if any (e.g. gauge bytes).
 */
data class SpeedTestDebugEvent(
    val timestampMs: Long,
    val phase: String,
    val status: String,
    val message: String,
    val serverId: Long? = null,
    val serverHost: String? = null,
    val bytes: Long? = null
) {
    object Phase {
        const val CONFIG_FETCH = "config_fetch"
        const val SERVER_SELECT = "server_select"
        const val GAUGE = "gauge"
        const val DOWNLOAD = "download"
        const val PROBE = "probe"
        const val UPLOAD = "upload"
        const val DONE = "done"
        const val ERROR = "error"
    }

    object Status {
        const val OK = "ok"
        const val WARN = "warn"
        const val FAIL = "fail"
        const val INFO = "info"
    }
}
