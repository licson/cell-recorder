package com.cellrecorder.app.domain.speedtest

import com.cellrecorder.app.domain.speedtest.SpeedTestMeasurer.MeasurementResult
import com.cellrecorder.app.domain.speedtest.SpeedTestMeasurer.ThroughputSample
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.math.abs

/**
 * Pure-logic unit tests for [SpeedTestEngine.computeBps] — the throughput aggregation
 * that discards the slowest 30% and fastest 10% of post-warmup slices, averages the
 * remaining 60%, and applies the 1.06x overhead compensation.
 *
 * The Android-coupled [SpeedTestEngine.runTest] flow (OkHttp, ConnectivityManager) is
 * deferred to instrumented tests; the high-value aggregation logic under test here is
 * the computation whose regression risk is highest.
 */
class SpeedTestEngineTest {

    private val overheadCompensation = 1.06

    private fun sample(throughputBps: Double, warmupMs: Long = 0L): ThroughputSample =
        ThroughputSample(
            bytesTransferred = (throughputBps / 8_000.0).toLong(),
            elapsedMs = 1L,
            throughputBps = throughputBps,
            warmupMs = warmupMs
        )

    private fun result(
        samples: List<ThroughputSample> = emptyList(),
        postWarmupBytes: Long = 0L,
        postWarmupMs: Long = 0L,
        anyRequestSucceeded: Boolean = true
    ): MeasurementResult = MeasurementResult(
        totalBytes = postWarmupBytes,
        totalElapsedMs = postWarmupMs,
        postWarmupBytes = postWarmupBytes,
        postWarmupMs = postWarmupMs,
        samples = samples,
        anyRequestSucceeded = anyRequestSucceeded
    )

    @Nested
    inner class ComputeBpsFallback {

        @Test
        fun `returns null when no samples and no post-warmup bytes`() {
            val engine = SpeedTestEngine(mockAppContext(), mockHttpClient())
            assertNull(engine.computeBps(result(samples = emptyList(), postWarmupBytes = 0L, postWarmupMs = 5_000L)))
        }

        @Test
        fun `returns null when no samples and post-warmup ms is zero`() {
            val engine = SpeedTestEngine(mockAppContext(), mockHttpClient())
            assertNull(engine.computeBps(result(samples = emptyList(), postWarmupBytes = 1_000_000L, postWarmupMs = 0L)))
        }

        @Test
        fun `fallback uses post-warmup bytes over ms with overhead compensation`() {
            val engine = SpeedTestEngine(mockAppContext(), mockHttpClient())
            val bytes = 10_000_000L
            val ms = 1_000L
            val expected = ((bytes.toDouble() / ms) * 8_000.0 * overheadCompensation).toLong()

            val bps = engine.computeBps(result(samples = emptyList(), postWarmupBytes = bytes, postWarmupMs = ms))
            assertEquals(expected, bps)
        }

        @Test
        fun `fallback applies overhead compensation`() {
            val engine = SpeedTestEngine(mockAppContext(), mockHttpClient())
            val bytes = 1_250_000L
            val ms = 1_000L
            val rawBps = (bytes.toDouble() / ms) * 8_000.0

            val bps = engine.computeBps(result(samples = emptyList(), postWarmupBytes = bytes, postWarmupMs = ms))
            assertNotNull(bps)
            assertTrue(abs(bps!! - rawBps * overheadCompensation) < 1.0)
        }
    }

    @Nested
    inner class ComputeBpsDiscardMath {

        @Test
        fun `single sample is retained and compensated`() {
            val engine = SpeedTestEngine(mockAppContext(), mockHttpClient())
            val bps = engine.computeBps(result(samples = listOf(sample(10_000_000.0))))
            assertNotNull(bps)
            assertEquals((10_000_000.0 * overheadCompensation).toLong(), bps)
        }

        @Test
        fun `two samples retain the middle range without discarding all`() {
            val engine = SpeedTestEngine(mockAppContext(), mockHttpClient())
            val samples = listOf(sample(5_000_000.0), sample(15_000_000.0))
            val bps = engine.computeBps(result(samples = samples))
            assertNotNull(bps)
            // With 2 samples: discardSlowest = (2*30/100)=0, discardFastest = (2*10/100)=0
            // Both retained, average = 10M, compensated
            val expected = ((5_000_000.0 + 15_000_000.0) / 2.0 * overheadCompensation).toLong()
            assertEquals(expected, bps)
        }

        @Test
        fun `ten samples discard slowest 30 percent and fastest 10 percent`() {
            val engine = SpeedTestEngine(mockAppContext(), mockHttpClient())
            val throughputs = listOf(1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 10.0)
                .map { it * 1_000_000.0 }
            val samples = throughputs.map { sample(it) }

            val bps = engine.computeBps(result(samples = samples))
            assertNotNull(bps)

            // 10 samples: discardSlowest = 3, discardFastest = 1 → retain [4,5,6,7,8,9]M
            val retained = listOf(4.0, 5.0, 6.0, 7.0, 8.0, 9.0).map { it * 1_000_000.0 }
            val expectedAvg = retained.average()
            val expected = (expectedAvg * overheadCompensation).toLong()
            assertEquals(expected, bps)
        }

        @Test
        fun `twenty samples discard slowest 6 and fastest 2`() {
            val engine = SpeedTestEngine(mockAppContext(), mockHttpClient())
            val throughputs = (1..20).map { it * 1_000_000.0 }
            val samples = throughputs.map { sample(it) }

            val bps = engine.computeBps(result(samples = samples))
            assertNotNull(bps)

            // 20 samples: discardSlowest = 6, discardFastest = 2 → retain [7..18]M
            val retained = (7..18).map { it * 1_000_000.0 }
            val expectedAvg = retained.average()
            val expected = (expectedAvg * overheadCompensation).toLong()
            assertEquals(expected, bps)
        }

        @Test
        fun `all-same throughputs return that value with compensation`() {
            val engine = SpeedTestEngine(mockAppContext(), mockHttpClient())
            val samples = (1..10).map { sample(50_000_000.0) }
            val bps = engine.computeBps(result(samples = samples))
            assertNotNull(bps)
            assertEquals((50_000_000.0 * overheadCompensation).toLong(), bps)
        }

        @Test
        fun `warmup-tagged samples are excluded from discard math`() {
            val engine = SpeedTestEngine(mockAppContext(), mockHttpClient())
            val samples = listOf(
                sample(999_000_000.0, warmupMs = 500L),  // warmup, excluded
                sample(10_000_000.0, warmupMs = 0L),
                sample(10_000_000.0, warmupMs = 0L)
            )
            val bps = engine.computeBps(result(samples = samples))
            assertNotNull(bps)
            assertEquals((10_000_000.0 * overheadCompensation).toLong(), bps)
        }
    }

    @Nested
    inner class ComputeBpsEdgeCases {

        @Test
        fun `empty samples falls back to post-warmup bytes`() {
            val engine = SpeedTestEngine(mockAppContext(), mockHttpClient())
            // The retained.isEmpty() fallback path (samples present but all discarded)
            // is not reachable with the current coerceIn bounds, so this tests the
            // warmupFreeSamples.isEmpty() fallback instead — same fallback formula.
            val bytes = 5_000_000L
            val ms = 500L
            val expected = ((bytes.toDouble() / ms) * 8_000.0 * overheadCompensation).toLong()
            val bps = engine.computeBps(result(samples = emptyList(), postWarmupBytes = bytes, postWarmupMs = ms))
            assertEquals(expected, bps)
        }

        @Test
        fun `zero-throughput sample produces zero bps not null`() {
            val engine = SpeedTestEngine(mockAppContext(), mockHttpClient())
            val bps = engine.computeBps(result(
                samples = listOf(sample(0.0)),
                postWarmupBytes = 0L,
                postWarmupMs = 0L
            ))
            // sample(0.0) → throughputBps=0.0 is a valid sample, retained, avg=0
            assertEquals(0L, bps)
        }
    }

    private fun mockAppContext(): android.content.Context =
        io.mockk.mockk(relaxed = true)

    private fun mockHttpClient(): SpeedTestHttpClient =
        io.mockk.mockk(relaxed = true)
}
