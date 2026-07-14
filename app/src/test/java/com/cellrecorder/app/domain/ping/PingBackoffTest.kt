package com.cellrecorder.app.domain.ping

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PingBackoffTest {

    @Test
    fun `restart count 0 returns 1 second`() {
        assertEquals(1_000L, PingBackoff.delayForFailure(0))
    }

    @Test
    fun `restart count 1 returns 2 seconds`() {
        assertEquals(2_000L, PingBackoff.delayForFailure(1))
    }

    @Test
    fun `restart count 2 returns 4 seconds`() {
        assertEquals(4_000L, PingBackoff.delayForFailure(2))
    }

    @Test
    fun `restart count 3 returns 8 seconds`() {
        assertEquals(8_000L, PingBackoff.delayForFailure(3))
    }

    @Test
    fun `restart count 4 returns 16 seconds`() {
        assertEquals(16_000L, PingBackoff.delayForFailure(4))
    }

    @Test
    fun `restart count 5 returns 32 seconds`() {
        assertEquals(32_000L, PingBackoff.delayForFailure(5))
    }

    @Test
    fun `restart count 6 returns 60 seconds (capped from 64)`() {
        assertEquals(60_000L, PingBackoff.delayForFailure(6))
    }

    @Test
    fun `restart count 7 returns 60 seconds (cap holds)`() {
        assertEquals(60_000L, PingBackoff.delayForFailure(7))
    }

    @Test
    fun `restart count 100 returns 60 seconds (cap holds for large values)`() {
        assertEquals(60_000L, PingBackoff.delayForFailure(100))
    }

    @Test
    fun `negative restart count returns 1 second (treated as 0)`() {
        assertEquals(1_000L, PingBackoff.delayForFailure(-1))
    }
}
