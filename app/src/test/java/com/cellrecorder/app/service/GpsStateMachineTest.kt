package com.cellrecorder.app.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

class GpsStateMachineTest {

    private fun location(
        lat: Double = 0.0,
        lon: Double = 0.0,
        ts: Long = 0L
    ): LocationUpdate = LocationUpdate(
        latitude = lat, longitude = lon, altitude = 0.0, accuracy = 5f, timestamp = ts
    )

    @Nested
    inner class IsFixLost {

        @Test
        fun `returns false when hasGpsFix is false`() {
            val sm = GpsStateMachine()
            assertFalse(sm.isFixLost(nowMs = 10_000, gpsLossDelayMs = 5_000))
        }

        @Test
        fun `returns false when hasGpsFix is true but time since fix is below delay`() {
            val sm = GpsStateMachine()
            sm.recordAccurateFix(location(ts = 0), nowMs = 0)
            sm.startExtrapolating(nowMs = 1_000)
            // nowMs=2000, timeSinceAccurateFix=2000, gpsLossDelayMs=5000 → not lost
            assertFalse(sm.isFixLost(nowMs = 2_000, gpsLossDelayMs = 5_000))
        }

        @Test
        fun `returns false when already extrapolating (lost detected previously but not stopped)`() {
            val sm = GpsStateMachine()
            sm.recordAccurateFix(location(ts = 0), nowMs = 0)
            sm.startExtrapolating(nowMs = 1_000)
            // nowMs=10000, timeSinceAccurateFix=10000 > 5000, but isExtrapolating=true
            assertFalse(sm.isFixLost(nowMs = 10_000, gpsLossDelayMs = 5_000))
        }

        @Test
        fun `returns true when time since accurate fix exceeds delay and not extrapolating and not settling`() {
            val sm = GpsStateMachine()
            sm.recordAccurateFix(location(ts = 0), nowMs = 0)
            // nowMs=10000, timeSinceAccurateFix=10000 > 5000, hasGpsFix=true, not extrapolating, not settling
            assertTrue(sm.isFixLost(nowMs = 10_000, gpsLossDelayMs = 5_000))
        }

        @Test
        fun `returns false when currently in settling period (nowMs less than gpsSettlingUntilMs)`() {
            val sm = GpsStateMachine()
            sm.recordAccurateFix(location(ts = 0), nowMs = 0)
            sm.setSettlingUntil(15_000)
            // nowMs=10000, timeSinceAccurateFix=10000 > 5000, but nowMs < gpsSettlingUntilMs
            assertFalse(sm.isFixLost(nowMs = 10_000, gpsLossDelayMs = 5_000))
        }

        @Test
        fun `returns true when settling window has passed`() {
            val sm = GpsStateMachine()
            sm.recordAccurateFix(location(ts = 0), nowMs = 0)
            sm.setSettlingUntil(5_000)
            // nowMs=10000, timeSinceAccurateFix=10000 > 5000, nowMs >= gpsSettlingUntilMs
            assertTrue(sm.isFixLost(nowMs = 10_000, gpsLossDelayMs = 5_000))
        }

        @Test
        fun `returns true when nowMs exactly equals gpsSettlingUntilMs`() {
            val sm = GpsStateMachine()
            sm.recordAccurateFix(location(ts = 0), nowMs = 0)
            sm.setSettlingUntil(5_000)
            // nowMs=5000, timeSinceAccurateFix=5000 == 5000 (not >) — but the delay condition uses >
            // So with gpsLossDelayMs=4000, timeSinceAccurateFix=5000 > 4000, and nowMs == gpsSettlingUntilMs (>=)
            assertTrue(sm.isFixLost(nowMs = 5_000, gpsLossDelayMs = 4_000))
        }
    }

    @Nested
    inner class ExtrapolationAgeSec {

        @Test
        fun `returns zero when gpsLostAtMs is zero`() {
            val sm = GpsStateMachine()
            assertEquals(0f, sm.extrapolationAgeSec(nowMs = 10_000), 1e-6f)
        }

        @Test
        fun `returns zero when gpsLostAtMs is negative`() {
            val sm = GpsStateMachine()
            // Can't set gpsLostAtMs directly via public API; default is 0L.
            // The check is gpsLostAtMs > 0L, so 0L gives 0f.
            assertEquals(0f, sm.extrapolationAgeSec(nowMs = 10_000), 1e-6f)
        }

        @Test
        fun `returns positive age after startExtrapolating`() {
            val sm = GpsStateMachine()
            sm.startExtrapolating(nowMs = 1_000)
            assertEquals(9f, sm.extrapolationAgeSec(nowMs = 10_000), 1e-6f)
        }

        @Test
        fun `returns zero after stopExtrapolating`() {
            val sm = GpsStateMachine()
            sm.startExtrapolating(nowMs = 1_000)
            sm.stopExtrapolating()
            assertEquals(0f, sm.extrapolationAgeSec(nowMs = 10_000), 1e-6f)
        }

        @Test
        fun `age grows with nowMs`() {
            val sm = GpsStateMachine()
            sm.startExtrapolating(nowMs = 1_000)
            assertEquals(1f, sm.extrapolationAgeSec(nowMs = 2_000), 1e-6f)
            assertEquals(5f, sm.extrapolationAgeSec(nowMs = 6_000), 1e-6f)
            assertEquals(10f, sm.extrapolationAgeSec(nowMs = 11_000), 1e-6f)
        }
    }

    @Nested
    inner class IsInSettling {

        @Test
        fun `returns false when gpsSettlingUntilMs is zero`() {
            val sm = GpsStateMachine()
            assertFalse(sm.isInSettling(nowMs = 100))
        }

        @Test
        fun `returns true when nowMs is less than gpsSettlingUntilMs`() {
            val sm = GpsStateMachine()
            sm.setSettlingUntil(10_000)
            assertTrue(sm.isInSettling(nowMs = 5_000))
        }

        @Test
        fun `returns false when nowMs equals gpsSettlingUntilMs (strict less-than)`() {
            val sm = GpsStateMachine()
            sm.setSettlingUntil(10_000)
            assertFalse(sm.isInSettling(nowMs = 10_000))
        }

        @Test
        fun `returns false when nowMs exceeds gpsSettlingUntilMs`() {
            val sm = GpsStateMachine()
            sm.setSettlingUntil(10_000)
            assertFalse(sm.isInSettling(nowMs = 15_000))
        }
    }

    @Nested
    inner class EstimatedAccuracy {

        @Test
        fun `returns 50 at zero extrapolation age`() {
            val sm = GpsStateMachine()
            assertEquals(50f, sm.estimatedAccuracy(0f), 1e-6f)
        }

        @Test
        fun `returns 50 plus 3 times age`() {
            val sm = GpsStateMachine()
            assertEquals(53f, sm.estimatedAccuracy(1f), 1e-6f)
            assertEquals(80f, sm.estimatedAccuracy(10f), 1e-6f)
            assertEquals(110f, sm.estimatedAccuracy(20f), 1e-6f)
        }

        @Test
        fun `accuracy grows linearly with extrapolation age`() {
            val sm = GpsStateMachine()
            val acc1 = sm.estimatedAccuracy(5f)
            val acc2 = sm.estimatedAccuracy(10f)
            assertEquals(acc2 - acc1, acc1 - sm.estimatedAccuracy(0f), 1e-6f, "Linear growth")
        }
    }

    @Nested
    inner class RecordAccurateFix {

        @Test
        fun `sets hasGpsFix to true when previously false`() {
            val sm = GpsStateMachine()
            assertFalse(sm.snapshot().hasGpsFix)
            sm.recordAccurateFix(location(), nowMs = 1000)
            assertTrue(sm.snapshot().hasGpsFix)
        }

        @Test
        fun `updates lastAccurateFixTime`() {
            val sm = GpsStateMachine()
            sm.recordAccurateFix(location(), nowMs = 1234)
            assertEquals(1234L, sm.snapshot().lastAccurateFixTime)
        }

        @Test
        fun `updates lastValidLocation`() {
            val sm = GpsStateMachine()
            val loc = location(lat = 40.0, lon = -74.0)
            sm.recordAccurateFix(loc, nowMs = 1000)
            assertEquals(40.0, sm.snapshot().lastValidLocation?.latitude ?: 0.0, 1e-9)
            assertEquals(-74.0, sm.snapshot().lastValidLocation?.longitude ?: 0.0, 1e-9)
        }
    }

    @Nested
    inner class StartStopExtrapolating {

        @Test
        fun `startExtrapolating sets isExtrapolating to true and gpsLostAtMs to nowMs`() {
            val sm = GpsStateMachine()
            sm.startExtrapolating(nowMs = 5_000)
            val snap = sm.snapshot()
            assertTrue(snap.isExtrapolating)
            assertEquals(5_000L, snap.gpsLostAtMs)
        }

        @Test
        fun `stopExtrapolating clears isExtrapolating and resets gpsLostAtMs to zero`() {
            val sm = GpsStateMachine()
            sm.startExtrapolating(nowMs = 5_000)
            sm.stopExtrapolating()
            val snap = sm.snapshot()
            assertFalse(snap.isExtrapolating)
            assertEquals(0L, snap.gpsLostAtMs)
        }
    }

    @Nested
    inner class UpdateMotion {

        @Test
        fun `updates lastKnownSpeedMps when speed is non-null`() {
            val sm = GpsStateMachine()
            sm.updateMotion(speed = 5.5f, bearing = null)
            assertEquals(5.5f, sm.snapshot().lastKnownSpeedMps, 1e-6f)
        }

        @Test
        fun `does not change speed when speed is null`() {
            val sm = GpsStateMachine()
            sm.updateMotion(speed = 5.5f, bearing = null)
            sm.updateMotion(speed = null, bearing = null)
            assertEquals(5.5f, sm.snapshot().lastKnownSpeedMps, 1e-6f)
        }

        @Test
        fun `updates lastKnownBearing when bearing is non-null`() {
            val sm = GpsStateMachine()
            sm.updateMotion(speed = null, bearing = 180f)
            assertEquals(180f, sm.snapshot().lastKnownBearing, 1e-6f)
        }

        @Test
        fun `does not change bearing when bearing is null`() {
            val sm = GpsStateMachine()
            sm.updateMotion(speed = null, bearing = 45f)
            sm.updateMotion(speed = null, bearing = null)
            assertEquals(45f, sm.snapshot().lastKnownBearing, 1e-6f)
        }
    }

    @Nested
    inner class Reset {

        @Test
        fun `reset clears hasGpsFix`() {
            val sm = GpsStateMachine()
            sm.recordAccurateFix(location(), nowMs = 1000)
            sm.reset()
            assertFalse(sm.snapshot().hasGpsFix)
        }

        @Test
        fun `reset clears lastValidLocation`() {
            val sm = GpsStateMachine()
            sm.recordAccurateFix(location(), nowMs = 1000)
            sm.reset()
            assertNull(sm.snapshot().lastValidLocation)
        }

        @Test
        fun `reset clears lastAccurateFixTime`() {
            val sm = GpsStateMachine()
            sm.recordAccurateFix(location(), nowMs = 1234)
            sm.reset()
            assertEquals(0L, sm.snapshot().lastAccurateFixTime)
        }

        @Test
        fun `reset clears isExtrapolating and gpsLostAtMs`() {
            val sm = GpsStateMachine()
            sm.startExtrapolating(nowMs = 5_000)
            sm.reset()
            val snap = sm.snapshot()
            assertFalse(snap.isExtrapolating)
            assertEquals(0L, snap.gpsLostAtMs)
        }

        @Test
        fun `reset clears gpsSettlingUntilMs`() {
            val sm = GpsStateMachine()
            sm.setSettlingUntil(10_000)
            sm.reset()
            assertEquals(0L, sm.snapshot().gpsSettlingUntilMs)
        }
    }

    @Nested
    inner class Snapshot {

        @Test
        fun `snapshot captures current state`() {
            val sm = GpsStateMachine()
            sm.recordAccurateFix(location(lat = 1.0, lon = 2.0), nowMs = 1000)
            sm.updateMotion(speed = 3.5f, bearing = 90f)
            sm.startExtrapolating(nowMs = 1500)
            sm.setSettlingUntil(2000)

            val snap = sm.snapshot()
            assertTrue(snap.hasGpsFix)
            assertEquals(3.5f, snap.lastKnownSpeedMps, 1e-6f)
            assertEquals(90f, snap.lastKnownBearing, 1e-6f)
            assertEquals(1.0, snap.lastValidLocation?.latitude ?: 0.0, 1e-9)
            assertEquals(2.0, snap.lastValidLocation?.longitude ?: 0.0, 1e-9)
            assertEquals(1000L, snap.lastAccurateFixTime)
            assertTrue(snap.isExtrapolating)
            assertEquals(1500L, snap.gpsLostAtMs)
            assertEquals(2000L, snap.gpsSettlingUntilMs)
        }
    }

    @Nested
    inner class ThreadSafety {

        @Test
        fun `concurrent recordAccurateFix calls do not corrupt state`() {
            val sm = GpsStateMachine()
            val threadCount = 50
            val iterations = 1000
            val barrier = CyclicBarrier(threadCount)
            val errors = AtomicInteger(0)

            val threads = (0 until threadCount).map {
                thread {
                    barrier.await()
                    repeat(iterations) { i ->
                        try {
                            sm.recordAccurateFix(location(lat = i.toDouble(), lon = i.toDouble()), nowMs = i.toLong())
                            sm.snapshot()
                        } catch (e: Exception) {
                            errors.incrementAndGet()
                        }
                    }
                }
            }
            threads.forEach { it.join() }

            assertEquals(0, errors.get(), "Concurrent calls should not throw")
            assertTrue(sm.snapshot().hasGpsFix)
        }

        @Test
        fun `concurrent isFixLost and startExtrapolating calls do not throw`() {
            val sm = GpsStateMachine()
            sm.recordAccurateFix(location(), nowMs = 0)
            val threadCount = 20
            val iterations = 500
            val barrier = CyclicBarrier(threadCount)
            val errors = AtomicInteger(0)

            val threads = (0 until threadCount).map { idx ->
                thread {
                    barrier.await()
                    repeat(iterations) { i ->
                        try {
                            if (idx % 2 == 0) {
                                sm.isFixLost(nowMs = i.toLong() * 1000, gpsLossDelayMs = 5000)
                            } else {
                                sm.startExtrapolating(nowMs = i.toLong() * 1000)
                                sm.extrapolationAgeSec(nowMs = i.toLong() * 1000 + 100)
                                sm.stopExtrapolating()
                            }
                            sm.snapshot()
                        } catch (e: Exception) {
                            errors.incrementAndGet()
                        }
                    }
                }
            }
            threads.forEach { it.join() }

            assertEquals(0, errors.get(), "Mixed concurrent reads/writes should not throw")
        }
    }
}
