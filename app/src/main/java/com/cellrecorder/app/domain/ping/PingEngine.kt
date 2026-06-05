package com.cellrecorder.app.domain.ping

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
                return PingResult(latencyMs = null, timestamp = timestamp)
            }
            val output = process.inputStream.bufferedReader().readText()
            val latency = parsePingOutput(output)
            PingResult(latencyMs = latency, timestamp = timestamp)
        } catch (_: Exception) {
            PingResult(latencyMs = null, timestamp = timestamp)
        }
    }

    fun pingFlow(
        host: String,
        intervalSec: Float,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS
    ): Flow<PingResult> = callbackFlow {
        val timeoutSec = (timeoutMs / 1000).coerceAtLeast(1L)

        fun startProcess(): Process {
            return ProcessBuilder("ping", "-i", intervalSec.toString(), "-W", timeoutSec.toString(), host)
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
                        trySend(PingResult(latencyMs = null, timestamp = System.currentTimeMillis()))
                        delay(1000)
                        if (!isActive) break
                        process.destroyForcibly()
                        process = startProcess()
                        reader = process.inputStream.bufferedReader()
                        continue
                    }
                    val latency = parsePingOutput(line)
                    trySend(PingResult(latencyMs = latency, timestamp = System.currentTimeMillis()))
                } catch (e: Exception) {
                    if (!isActive) break
                    trySend(PingResult(latencyMs = null, timestamp = System.currentTimeMillis()))
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

    private fun parsePingOutput(output: String): Double? {
        val regex = Regex("""time[=<]\s*(\d+\.?\d*)\s*ms""")
        return regex.find(output)?.groupValues?.get(1)?.toDoubleOrNull()
    }
}