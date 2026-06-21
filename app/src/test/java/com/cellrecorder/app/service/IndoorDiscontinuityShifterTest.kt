package com.cellrecorder.app.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class IndoorDiscontinuityShifterTest {

    @Nested
    inner class EmptyInput {

        @Test
        fun `empty deque returns empty deque`() {
            val input = ArrayDeque<Int>()
            val result = IndoorDiscontinuityShifter.shift(input)
            assertTrue(result.isEmpty())
        }
    }

    @Nested
    inner class NoDiscontinuityAtZero {

        @Test
        fun `single discontinuity not at zero is decremented by 1`() {
            val input = ArrayDeque<Int>().apply { addLast(5) }
            val result = IndoorDiscontinuityShifter.shift(input)
            assertEquals(1, result.size)
            assertEquals(4, result.first())
        }

        @Test
        fun `multiple discontinuities not at zero are all decremented by 1`() {
            val input = ArrayDeque<Int>().apply { addLast(2); addLast(5); addLast(10) }
            val result = IndoorDiscontinuityShifter.shift(input)
            assertEquals(listOf(1, 4, 9), result.toList())
        }
    }

    @Nested
    inner class DiscontinuityAtZero {

        @Test
        fun `single discontinuity at zero is dropped, returning empty deque`() {
            val input = ArrayDeque<Int>().apply { addLast(0) }
            val result = IndoorDiscontinuityShifter.shift(input)
            assertTrue(result.isEmpty())
        }

        @Test
        fun `discontinuity at zero is dropped and remaining indices are decremented by 1`() {
            val input = ArrayDeque<Int>().apply { addLast(0); addLast(2); addLast(5) }
            val result = IndoorDiscontinuityShifter.shift(input)
            assertEquals(listOf(1, 4), result.toList())
        }

        @Test
        fun `discontinuity at index 1 becomes index 0 after shift when first discontinuity was at 0`() {
            val input = ArrayDeque<Int>().apply { addLast(0); addLast(1) }
            val result = IndoorDiscontinuityShifter.shift(input)
            assertEquals(listOf(0), result.toList())
        }
    }

    @Nested
    inner class PreserveOrder {

        @Test
        fun `order of discontinuities is preserved`() {
            val input = ArrayDeque<Int>().apply { addLast(10); addLast(20); addLast(30) }
            val result = IndoorDiscontinuityShifter.shift(input)
            assertEquals(listOf(9, 19, 29), result.toList())
        }

        @Test
        fun `input deque is not mutated`() {
            val input = ArrayDeque<Int>().apply { addLast(0); addLast(5); addLast(10) }
            IndoorDiscontinuityShifter.shift(input)
            assertEquals(listOf(0, 5, 10), input.toList(), "Helper should not mutate its input")
        }
    }

    @Nested
    inner class EdgeCases {

        @Test
        fun `large discontinuity indices are handled correctly`() {
            val input = ArrayDeque<Int>().apply { addLast(1999); addLast(2000) }
            val result = IndoorDiscontinuityShifter.shift(input)
            assertEquals(listOf(1998, 1999), result.toList())
        }

        @Test
        fun `discontinuity at MAX_PATH_SIZE - 1 is decremented correctly`() {
            val input = ArrayDeque<Int>().apply { addLast(1999) }
            val result = IndoorDiscontinuityShifter.shift(input)
            assertEquals(listOf(1998), result.toList())
        }
    }
}
