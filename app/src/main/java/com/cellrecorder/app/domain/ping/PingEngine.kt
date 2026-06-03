package com.cellrecorder.app.domain.ping

import com.cellrecorder.app.domain.model.PingResult
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class PingEngine @Inject constructor() {

    suspend fun ping(
        host: String,
        timeoutMs: Long,
        timestamp: Long = System.currentTimeMillis()
    ): PingResult {
        val timeoutSec = (timeoutMs / 1000).coerceAtLeast(1L)
        return try {
            val process = ProcessBuilder("ping", "-c", "1", "-W", timeoutSec.toString(), host)
                .redirectErrorStream(true)
                .start()
            val finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            if (!finished) {
                process.destroyForcibly()
                return PingResult(latencyMs = null, timestamp = timestamp)
            }
            val output = process.inputStream.bufferedReader().readText()
            val latency = parsePingOutput(output)
            PingResult(latencyMs = latency, timestamp = timestamp)
        } catch (_: Exception) {
            PingResult(latencyMs = null, timestamp = timestamp)
        }
    }

    private fun parsePingOutput(output: String): Double? {
        val regex = Regex("""time[=<]\s*(\d+\.?\d*)\s*ms""")
        return regex.find(output)?.groupValues?.get(1)?.toDoubleOrNull()
    }
}