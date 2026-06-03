package com.cellrecorder.app.domain.ping

import com.cellrecorder.app.domain.model.PingResult
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PingSlidingWindowTest {

    @Test
    fun `empty window returns null for avg and 0 for loss`() {
        val window = PingSlidingWindow(5)
        assertNull(window.avgLatencyMs())
        assertEquals(0.0, window.packetLossPct())
    }

    @Test
    fun `single ping result`() {
        val window = PingSlidingWindow(5)
        window.add(PingResult(latencyMs = 25.0, timestamp = 1000))
        val avg = window.avgLatencyMs()
        assertNotNull(avg)
        assertEquals(25.0, avg!!, 0.001)
        assertEquals(0.0, window.packetLossPct())
    }

    @Test
    fun `mixed loss and success`() {
        val window = PingSlidingWindow(5)
        window.add(PingResult(latencyMs = 10.0, timestamp = 1))
        window.add(PingResult(latencyMs = null, timestamp = 2))
        window.add(PingResult(latencyMs = 20.0, timestamp = 3))
        val avg = window.avgLatencyMs()
        assertNotNull(avg)
        assertEquals(15.0, avg!!, 0.001)
        assertEquals(33.333, window.packetLossPct(), 0.01)
    }

    @Test
    fun `window slides past capacity`() {
        val window = PingSlidingWindow(3)
        for (i in 1..5) {
            window.add(PingResult(latencyMs = i.toDouble(), timestamp = i.toLong()))
        }
        val avg = window.avgLatencyMs()
        assertNotNull(avg)
        assertEquals(4.0, avg!!, 0.001)
        assertEquals(0.0, window.packetLossPct())
    }

    @Test
    fun `reset clears window`() {
        val window = PingSlidingWindow(3)
        window.add(PingResult(latencyMs = 10.0, timestamp = 1))
        window.reset()
        assertNull(window.avgLatencyMs())
        assertEquals(0.0, window.packetLossPct())
    }

    @Test
    fun `all nulls returns null avg and 100 pct loss`() {
        val window = PingSlidingWindow(3)
        window.add(PingResult(latencyMs = null, timestamp = 1))
        window.add(PingResult(latencyMs = null, timestamp = 2))
        assertNull(window.avgLatencyMs())
        assertEquals(100.0, window.packetLossPct())
    }
}