package com.cellrecorder.app.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class IndoorStepDetectorTest {

    @Nested
    inner class IsStep {

        @Test
        fun `filtered magnitude below threshold returns false`() {
            // baseline gravity 9.81, threshold 1.15, so step needs > 11.2815
            assertFalse(IndoorStepDetector.isStep(filteredMagnitude = 11.0f, gravityBaseline = 9.81f))
        }

        @Test
        fun `filtered magnitude exactly at threshold returns false (strict greater than)`() {
            val baseline = 9.81f
            val threshold = 1.15f
            val atThreshold = baseline * threshold
            assertFalse(IndoorStepDetector.isStep(atThreshold, baseline))
        }

        @Test
        fun `filtered magnitude above threshold returns true`() {
            assertTrue(IndoorStepDetector.isStep(filteredMagnitude = 11.5f, gravityBaseline = 9.81f))
        }

        @Test
        fun `custom threshold changes the step boundary`() {
            val baseline = 10f
            assertTrue(IndoorStepDetector.isStep(11.5f, baseline, threshold = 1.1f))
            assertFalse(IndoorStepDetector.isStep(11.0f, baseline, threshold = 1.1f))
        }
    }

    @Nested
    inner class CalibrateBaseline {

        @Test
        fun `first sample becomes the baseline when count is 0`() {
            val result = IndoorStepDetector.calibrateBaseline(currentBaseline = 0f, sampleMagnitude = 9.81f, sampleCount = 0)
            assertEquals(9.81f, result, 1e-6f)
        }

        @Test
        fun `second sample averages with first when count is 1`() {
            val result = IndoorStepDetector.calibrateBaseline(currentBaseline = 9.81f, sampleMagnitude = 9.83f, sampleCount = 1)
            assertEquals((9.81f + 9.83f) / 2f, result, 1e-6f)
        }

        @Test
        fun `third sample weighted average with two prior`() {
            val result = IndoorStepDetector.calibrateBaseline(currentBaseline = 9.81f, sampleMagnitude = 9.83f, sampleCount = 2)
            // (9.81 * 2 + 9.83) / 3
            assertEquals((9.81f * 2f + 9.83f) / 3f, result, 1e-6f)
        }

        @Test
        fun `after 20 samples the new sample contributes 1 21st of the baseline`() {
            val current = 10.0f
            val sample = 20.0f
            val result = IndoorStepDetector.calibrateBaseline(current, sample, sampleCount = 20)
            assertEquals((10f * 20f + 20f) / 21f, result, 1e-6f)
        }
    }

    @Nested
    inner class DriftRateForElapsedMinutes {

        @Test
        fun `zero elapsed minutes returns base drift rate 0 02`() {
            assertEquals(0.02, IndoorStepDetector.driftRateForElapsedMinutes(0.0), 1e-9)
        }

        @Test
        fun `one minute returns 0 024`() {
            assertEquals(0.024, IndoorStepDetector.driftRateForElapsedMinutes(1.0), 1e-9)
        }

        @Test
        fun `five minutes returns 0 04`() {
            assertEquals(0.04, IndoorStepDetector.driftRateForElapsedMinutes(5.0), 1e-9)
        }

        @Test
        fun `drift rate caps at 0 20 for very long elapsed times`() {
            assertEquals(0.20, IndoorStepDetector.driftRateForElapsedMinutes(50.0), 1e-9)
            assertEquals(0.20, IndoorStepDetector.driftRateForElapsedMinutes(100.0), 1e-9)
            assertEquals(0.20, IndoorStepDetector.driftRateForElapsedMinutes(1000.0), 1e-9)
        }

        @Test
        fun `at the cap boundary the slope crosses max around 45 minutes`() {
            val atCap = IndoorStepDetector.driftRateForElapsedMinutes(45.0)
            assertEquals(0.20, atCap, 1e-9, "Drift should be exactly 0.20 at 45 min")
        }

        @Test
        fun `just below the cap boundary returns slope-derived value`() {
            val result = IndoorStepDetector.driftRateForElapsedMinutes(44.999)
            assertTrue(result < 0.20, "Drift below cap should be less than 0.20")
        }

        @Test
        fun `custom base slope and max override defaults`() {
            val result = IndoorStepDetector.driftRateForElapsedMinutes(10.0, base = 0.05, slope = 0.01, max = 0.5)
            assertEquals(0.15, result, 1e-9, "0.05 + 10 * 0.01 = 0.15")
        }

        @Test
        fun `custom max caps the drift rate`() {
            val result = IndoorStepDetector.driftRateForElapsedMinutes(100.0, base = 0.05, slope = 0.01, max = 0.3)
            assertEquals(0.3, result, 1e-9)
        }
    }

    @Nested
    inner class Magnitude {

        @Test
        fun `zero vector has zero magnitude`() {
            assertEquals(0f, IndoorStepDetector.magnitude(0f, 0f, 0f), 1e-6f)
        }

        @Test
        fun `unit vector along x has magnitude 1`() {
            assertEquals(1f, IndoorStepDetector.magnitude(1f, 0f, 0f), 1e-6f)
        }

        @Test
        fun `gravity vector approximates 9 81`() {
            val result = IndoorStepDetector.magnitude(0f, 0f, 9.81f)
            assertEquals(9.81f, result, 1e-4f)
        }

        @Test
        fun `3-4-5 right triangle has magnitude 5`() {
            val result = IndoorStepDetector.magnitude(3f, 4f, 0f)
            assertEquals(5f, result, 1e-6f)
        }
    }
}
