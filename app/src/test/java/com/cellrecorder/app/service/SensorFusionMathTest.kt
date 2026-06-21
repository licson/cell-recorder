package com.cellrecorder.app.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.math.exp

class SensorFusionMathTest {

    @Nested
    inner class SmoothHeadingDelta {

        @Test
        fun `raw delta of 5 degrees with smoothed 0 produces 0 75`() {
            val result = SensorFusionMath.smoothHeadingDelta(smoothed = 0f, rawDelta = 5f)
            assertEquals(0.75f, result, 1e-6f)
        }

        @Test
        fun `raw delta of 350 degrees wraps to -10 then smooths from 0 to -1 5`() {
            val result = SensorFusionMath.smoothHeadingDelta(smoothed = 0f, rawDelta = 350f)
            assertEquals(-1.5f, result, 1e-6f)
        }

        @Test
        fun `raw delta of -350 degrees wraps to +10 then smooths`() {
            val result = SensorFusionMath.smoothHeadingDelta(smoothed = 0f, rawDelta = -350f)
            assertEquals(1.5f, result, 1e-6f)
        }

        @Test
        fun `raw delta of 0 produces 0 85 of smoothed value`() {
            val result = SensorFusionMath.smoothHeadingDelta(smoothed = 10f, rawDelta = 0f)
            assertEquals(8.5f, result, 1e-6f)
        }

        @Test
        fun `equal smoothed and raw produces unchanged value`() {
            val result = SensorFusionMath.smoothHeadingDelta(smoothed = 10f, rawDelta = 10f)
            assertEquals(10f, result, 1e-6f)
        }

        @Test
        fun `raw delta of 180 wraps to 180 (boundary inclusive)`() {
            val result = SensorFusionMath.smoothHeadingDelta(smoothed = 0f, rawDelta = 180f)
            // 180 is not > 180, so no wrap; result = 0.15 * 180 = 27
            assertEquals(27f, result, 1e-3f)
        }

        @Test
        fun `raw delta of -180 wraps to -180 (boundary inclusive)`() {
            val result = SensorFusionMath.smoothHeadingDelta(smoothed = 0f, rawDelta = -180f)
            assertEquals(-27f, result, 1e-3f)
        }

        @Test
        fun `raw delta of 181 wraps to -179`() {
            val result = SensorFusionMath.smoothHeadingDelta(smoothed = 0f, rawDelta = 181f)
            // 181 > 180 → wrapped = 181 - 360 = -179
            assertEquals(0.15f * -179f, result, 1e-3f)
        }

        @Test
        fun `raw delta of -181 wraps to 179`() {
            val result = SensorFusionMath.smoothHeadingDelta(smoothed = 0f, rawDelta = -181f)
            // -181 < -180 → wrapped = -181 + 360 = 179
            assertEquals(0.15f * 179f, result, 1e-3f)
        }

        @Test
        fun `accumulated smoothing over multiple steps converges toward raw`() {
            var smoothed = 0f
            val raw = 10f
            for (i in 1..20) {
                smoothed = SensorFusionMath.smoothHeadingDelta(smoothed, raw)
            }
            assertEquals(10f, smoothed, 0.5f, "After 20 iterations, smoothed should approach raw")
        }
    }

    @Nested
    inner class DecaySpeedDelta {

        @Test
        fun `zero delta and zero accel yields zero`() {
            val result = SensorFusionMath.decaySpeedDelta(
                currentDeltaMps = 0f, forwardAccel = 0f, dtSec = 1f, initialSpeedMps = 10f
            )
            assertEquals(0f, result, 1e-6f)
        }

        @Test
        fun `zero delta with positive accel integrates forward accel over time`() {
            val result = SensorFusionMath.decaySpeedDelta(
                currentDeltaMps = 0f, forwardAccel = 1f, dtSec = 0.1f, initialSpeedMps = 10f
            )
            val decay = exp(-0.1 / 10.0).toFloat()
            val expected = 0f * decay + 1f * 0.1f
            assertEquals(expected, result, 1e-6f)
        }

        @Test
        fun `delta decays exponentially toward zero with no accel`() {
            val result = SensorFusionMath.decaySpeedDelta(
                currentDeltaMps = 10f, forwardAccel = 0f, dtSec = 1.0f, initialSpeedMps = 100f
            )
            val expected = 10f * exp(-1.0 / 10.0).toFloat()
            assertEquals(expected, result, 1e-4f)
        }

        @Test
        fun `delta decays to 36 8 percent after one tau (10 seconds)`() {
            val result = SensorFusionMath.decaySpeedDelta(
                currentDeltaMps = 10f, forwardAccel = 0f, dtSec = 10.0f, initialSpeedMps = 100f
            )
            val expected = 10f * exp(-1.0).toFloat() // ~3.678
            assertEquals(expected, result, 1e-3f)
        }

        @Test
        fun `delta is clamped to positive 0 5 times initialSpeedMps`() {
            val result = SensorFusionMath.decaySpeedDelta(
                currentDeltaMps = 100f, forwardAccel = 0f, dtSec = 0.001f, initialSpeedMps = 10f
            )
            assertEquals(5f, result, 1e-6f, "Delta should clamp to 0.5 * initialSpeed")
        }

        @Test
        fun `delta is clamped to negative 0 5 times initialSpeedMps`() {
            val result = SensorFusionMath.decaySpeedDelta(
                currentDeltaMps = -100f, forwardAccel = 0f, dtSec = 0.001f, initialSpeedMps = 10f
            )
            assertEquals(-5f, result, 1e-6f, "Delta should clamp to -0.5 * initialSpeed")
        }

        @Test
        fun `zero initial speed clamps delta to zero`() {
            val result = SensorFusionMath.decaySpeedDelta(
                currentDeltaMps = 10f, forwardAccel = 5f, dtSec = 1f, initialSpeedMps = 0f
            )
            assertEquals(0f, result, 1e-6f, "With zero initial speed, maxAdjust=0 so delta is clamped to 0")
        }

        @Test
        fun `custom tau affects decay rate`() {
            val resultTau1 = SensorFusionMath.decaySpeedDelta(
                currentDeltaMps = 10f, forwardAccel = 0f, dtSec = 1f, initialSpeedMps = 100f, tau = 1f
            )
            val resultTau10 = SensorFusionMath.decaySpeedDelta(
                currentDeltaMps = 10f, forwardAccel = 0f, dtSec = 1f, initialSpeedMps = 100f, tau = 10f
            )
            assertEquals(true, resultTau1 < resultTau10, "Smaller tau means faster decay, so result with tau=1 should be smaller")
        }
    }
}
