package com.cellrecorder.app.domain.model

enum class PingOutcome {
    SUCCESS,
    TIMEOUT,
    HOST_UNREACHABLE,
    PROCESS_ERROR
}

data class PingResult(
    val latencyMs: Double?,
    val timestamp: Long,
    val outcome: PingOutcome
)