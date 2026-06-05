package com.cellrecorder.app.domain.ping

import com.cellrecorder.app.domain.model.PingOutcome
import com.cellrecorder.app.domain.model.PingResult
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PingEngine @Inject constructor() {

    private val DEFAULT_TIMEOUT_MS = 3000L

    @Deprecated("Use pingFlow() for continuous streaming; this method spawns a new process per call")
    suspend fun ping(
        host: String,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
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
                return PingResult(latencyMs = null, timestamp = timestamp, outcome = PingOutcome.TIMEOUT)
            }
            val output = process.inputStream.bufferedReader().readText()
            val parsed = parseLine(output)
            PingResult(latencyMs = parsed.first, timestamp = timestamp, outcome = parsed.second)
        } catch (_: Exception) {
            PingResult(latencyMs = null, timestamp = timestamp, outcome = PingOutcome.PROCESS_ERROR)
        }
    }

    fun pingFlow(
        host: String,
        intervalSec: Float,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS
    ): Flow<PingResult> = callbackFlow {
        val timeoutSec = (timeoutMs / 1000).coerceAtLeast(1L)

        fun startProcess(): Process {
            return ProcessBuilder("ping", "-O", "-i", intervalSec.toString(), "-W", timeoutSec.toString(), host)
                .redirectErrorStream(true)
                .start()
        }

        var process = startProcess()
        var reader = process.inputStream.bufferedReader()

        launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    val line = reader.readLine()
                    if (line == null) {
                        trySend(PingResult(latencyMs = null, timestamp = System.currentTimeMillis(), outcome = PingOutcome.PROCESS_ERROR))
                        delay(1000)
                        if (!isActive) break
                        process.destroyForcibly()
                        process = startProcess()
                        reader = process.inputStream.bufferedReader()
                        continue
                    }
                    if (!line.contains("icmp_seq=")) continue
                    val parsed = parseLine(line)
                    trySend(PingResult(latencyMs = parsed.first, timestamp = System.currentTimeMillis(), outcome = parsed.second))
                } catch (e: Exception) {
                    if (!isActive) break
                    trySend(PingResult(latencyMs = null, timestamp = System.currentTimeMillis(), outcome = PingOutcome.PROCESS_ERROR))
                    delay(1000)
                    if (!isActive) break
                    process.destroyForcibly()
                    process = startProcess()
                    reader = process.inputStream.bufferedReader()
                }
            }
        }

        awaitClose {
            process.destroyForcibly()
        }
    }

    internal fun parseLine(line: String): Pair<Double?, PingOutcome> {
        val latency = extractLatency(line)
        if (latency != null) return latency to PingOutcome.SUCCESS
        if (parseNoAnswerLine(line)) return null to PingOutcome.TIMEOUT
        if (parseErrorLine(line)) return null to PingOutcome.HOST_UNREACHABLE
        return null to PingOutcome.PROCESS_ERROR
    }

    internal fun extractLatency(line: String): Double? {
        val regex = Regex("""time[=<]\s*(\d+\.?\d*)\s*ms""")
        return regex.find(line)?.groupValues?.get(1)?.toDoubleOrNull()
    }

    internal fun parseNoAnswerLine(line: String): Boolean {
        return line.contains("no answer yet for icmp_seq")
    }

    internal fun parseErrorLine(line: String): Boolean {
        return line.contains("Destination Host Unreachable") ||
                line.contains("Network Unreachable") ||
                line.contains("No route to host")
    }
}