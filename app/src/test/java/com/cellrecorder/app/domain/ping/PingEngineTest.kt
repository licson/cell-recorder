package com.cellrecorder.app.domain.ping

import app.cash.turbine.test
import com.cellrecorder.app.domain.model.PingOutcome
import com.cellrecorder.app.domain.model.PingResult
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.unmockkConstructor
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.InputStream

class PingEngineTest {

    private val engine = PingEngine()

    // --- parseNoAnswerLine ---

    @Test
    fun `no answer yet for icmp_seq detected`() {
        assertTrue(engine.parseNoAnswerLine("no answer yet for icmp_seq=3"))
    }

    @Test
    fun `no answer yet with multi-digit seq detected`() {
        assertTrue(engine.parseNoAnswerLine("no answer yet for icmp_seq=142"))
    }

    @Test
    fun `non-matching line returns false for no answer`() {
        assertFalse(engine.parseNoAnswerLine("64 bytes from 8.8.8.8: icmp_seq=1 ttl=117 time=12.3 ms"))
    }

    @Test
    fun `empty string returns false for no answer`() {
        assertFalse(engine.parseNoAnswerLine(""))
    }

    // --- parseErrorLine ---

    @Test
    fun `Destination Host Unreachable detected`() {
        assertTrue(engine.parseErrorLine("From 192.168.1.1 icmp_seq=1 Destination Host Unreachable"))
    }

    @Test
    fun `Network Unreachable detected`() {
        assertTrue(engine.parseErrorLine("From 10.0.0.1 icmp_seq=2 Network Unreachable"))
    }

    @Test
    fun `No route to host detected`() {
        assertTrue(engine.parseErrorLine("From 10.0.0.1 icmp_seq=3 No route to host"))
    }

    @Test
    fun `success line not detected as error`() {
        assertFalse(engine.parseErrorLine("64 bytes from 8.8.8.8: icmp_seq=1 ttl=117 time=12.3 ms"))
    }

    @Test
    fun `empty string not detected as error`() {
        assertFalse(engine.parseErrorLine(""))
    }

    // --- extractLatency ---

    @Test
    fun `extract latency from success line`() {
        val line = "64 bytes from 8.8.8.8: icmp_seq=1 ttl=117 time=12.3 ms"
        val result = engine.extractLatency(line)
        assertNotNull(result)
        assertEquals(12.3, result!!, 0.001)
    }

    @Test
    fun `extract latency with equals sign`() {
        val line = "64 bytes from 8.8.8.8: icmp_seq=1 ttl=117 time=45.67 ms"
        val result = engine.extractLatency(line)
        assertNotNull(result)
        assertEquals(45.67, result!!, 0.001)
    }

    @Test
    fun `extract latency with integer value`() {
        val line = "64 bytes from 8.8.8.8: icmp_seq=1 ttl=117 time=5 ms"
        val result = engine.extractLatency(line)
        assertNotNull(result)
        assertEquals(5.0, result!!, 0.001)
    }

    @Test
    fun `extract latency returns null for no-answer line`() {
        assertNull(engine.extractLatency("no answer yet for icmp_seq=3"))
    }

    @Test
    fun `extract latency returns null for error line`() {
        assertNull(engine.extractLatency("From 192.168.1.1 icmp_seq=1 Destination Host Unreachable"))
    }

    // --- parseLine ---

    @Test
    fun `parseLine returns SUCCESS for reply`() {
        val (latency, outcome) = engine.parseLine("64 bytes from 8.8.8.8: icmp_seq=1 ttl=117 time=12.3 ms")
        assertNotNull(latency)
        assertEquals(12.3, latency!!, 0.001)
        assertEquals(PingOutcome.SUCCESS, outcome)
    }

    @Test
    fun `parseLine returns TIMEOUT for no answer`() {
        val (latency, outcome) = engine.parseLine("no answer yet for icmp_seq=3")
        assertNull(latency)
        assertEquals(PingOutcome.TIMEOUT, outcome)
    }

    @Test
    fun `parseLine returns HOST_UNREACHABLE for destination unreachable`() {
        val (latency, outcome) = engine.parseLine("From 192.168.1.1 icmp_seq=1 Destination Host Unreachable")
        assertNull(latency)
        assertEquals(PingOutcome.HOST_UNREACHABLE, outcome)
    }

    @Test
    fun `parseLine returns HOST_UNREACHABLE for network unreachable`() {
        val (latency, outcome) = engine.parseLine("From 10.0.0.1 icmp_seq=2 Network Unreachable")
        assertNull(latency)
        assertEquals(PingOutcome.HOST_UNREACHABLE, outcome)
    }

    @Test
    fun `parseLine returns PROCESS_ERROR for unparseable line`() {
        val (latency, outcome) = engine.parseLine("some random unparseable output")
        assertNull(latency)
        assertEquals(PingOutcome.PROCESS_ERROR, outcome)
    }

    @Test
    fun `parseLine returns PROCESS_ERROR for empty line`() {
        val (latency, outcome) = engine.parseLine("")
        assertNull(latency)
        assertEquals(PingOutcome.PROCESS_ERROR, outcome)
    }

    // --- -O flag integration (through parseLine) ---

    @Test
    fun `no answer yet line produces TIMEOUT outcome immediately`() {
        val result = engine.parseLine("no answer yet for icmp_seq=5")
        assertEquals(PingOutcome.TIMEOUT, result.second)
        assertNull(result.first)
    }

    @Test
    fun `multiple no answer lines each produce TIMEOUT`() {
        val r1 = engine.parseLine("no answer yet for icmp_seq=1")
        val r2 = engine.parseLine("no answer yet for icmp_seq=2")
        assertEquals(PingOutcome.TIMEOUT, r1.second)
        assertEquals(PingOutcome.TIMEOUT, r2.second)
    }

    /**
     * Streaming tests for `PingEngine.pingFlow()`. These mock `ProcessBuilder.start()` to
     * return a stubbed `Process` whose `inputStream` is a `ByteArrayInputStream` containing
     * sample ping output. The flow is collected via Turbine.
     *
     * If `ProcessBuilder` mocking proves unstable across environments, defer these tests
     * to `androidTest` (per design.md Decision 4 fallback) — the parse helpers above are
     * the high-value logic and remain covered here.
     */
    @Nested
    inner class PingFlow {

        private fun stubProcess(vararg lines: String): Process {
            val data = lines.joinToString(separator = "\n", postfix = "\n") { it }.toByteArray(Charsets.US_ASCII)
            val stream: InputStream = ByteArrayInputStream(data)
            val proc = mockk<Process>()
            every { proc.inputStream } returns stream
            every { proc.waitFor(any(), any()) } returns true
            every { proc.destroyForcibly() } returns proc
            return proc
        }

        @Test
        fun `pingFlow emits SUCCESS for a line with latency`() = runTest {
            mockkConstructor(ProcessBuilder::class)
            try {
                val proc = stubProcess("64 bytes from 8.8.8.8: icmp_seq=0 ttl=117 time=12.3 ms")
                every { anyConstructed<ProcessBuilder>().start() } returns proc

                engine.pingFlow(host = "8.8.8.8", intervalSec = 1f).test {
                    val item = awaitItem()
                    assertEquals(PingOutcome.SUCCESS, item.outcome)
                    assertEquals(12.3, item.latencyMs ?: 0.0, 1e-3)
                    cancelAndIgnoreRemainingEvents()
                }
            } finally {
                unmockkConstructor(ProcessBuilder::class)
            }
        }

        @Test
        fun `pingFlow emits TIMEOUT for a no-answer line`() = runTest {
            mockkConstructor(ProcessBuilder::class)
            try {
                val proc = stubProcess("no answer yet for icmp_seq=1")
                every { anyConstructed<ProcessBuilder>().start() } returns proc

                engine.pingFlow(host = "8.8.8.8", intervalSec = 1f).test {
                    val item = awaitItem()
                    assertEquals(PingOutcome.TIMEOUT, item.outcome)
                    assertNull(item.latencyMs)
                    cancelAndIgnoreRemainingEvents()
                }
            } finally {
                unmockkConstructor(ProcessBuilder::class)
            }
        }

        @Test
        fun `pingFlow emits HOST_UNREACHABLE for an error line`() = runTest {
            mockkConstructor(ProcessBuilder::class)
            try {
                val proc = stubProcess("From 192.168.1.1 icmp_seq=1 Destination Host Unreachable")
                every { anyConstructed<ProcessBuilder>().start() } returns proc

                engine.pingFlow(host = "8.8.8.8", intervalSec = 1f).test {
                    val item = awaitItem()
                    assertEquals(PingOutcome.HOST_UNREACHABLE, item.outcome)
                    assertNull(item.latencyMs)
                    cancelAndIgnoreRemainingEvents()
                }
            } finally {
                unmockkConstructor(ProcessBuilder::class)
            }
        }
    }
}