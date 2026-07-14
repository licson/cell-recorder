package com.cellrecorder.app.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PointCountPolicyTest {

    @Test
    fun `empty batch produces zero increment`() {
        assertEquals(0, PointCountPolicy.incrementFor(0))
    }

    @Test
    fun `partial batch produces succeeded count`() {
        assertEquals(2, PointCountPolicy.incrementFor(2))
    }

    @Test
    fun `full batch produces full count`() {
        assertEquals(5, PointCountPolicy.incrementFor(5))
    }

    @Test
    fun `negative inserted count is clamped to zero (defensive)`() {
        assertEquals(0, PointCountPolicy.incrementFor(-1))
    }

    @Test
    fun `single insert produces increment of one`() {
        assertEquals(1, PointCountPolicy.incrementFor(1))
    }
}
