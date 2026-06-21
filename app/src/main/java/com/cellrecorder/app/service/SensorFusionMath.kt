package com.cellrecorder.app.service

import kotlin.math.exp

/**
 * Pure math helpers for [SensorFusionCollector], extracted so the heading-smoothing and
 * speed-delta-decay logic is unit-testable in isolation without instantiating the
 * Hilt-injected Android-sensor machinery.
 *
 * - [smoothHeadingDelta] wraps an absolute heading delta to the [-180, 180] range and
 *   applies an exponential moving average (0.85 / 0.15 weighting).
 * - [decaySpeedDelta] applies exponential decay to the current speed delta over a time
 *   step, integrates the forward acceleration into the delta, then clamps to
 *   ±0.5 × initial speed.
 */
object SensorFusionMath {

    /**
     * Wraps [rawDelta] to the [-180, 180] range and applies an exponential moving average
     * with weights 0.85 (prior) and 0.15 (new).
     */
    fun smoothHeadingDelta(smoothed: Float, rawDelta: Float): Float {
        var wrapped = rawDelta
        if (wrapped > 180f) wrapped -= 360f
        if (wrapped < -180f) wrapped += 360f
        return 0.85f * smoothed + 0.15f * wrapped
    }

    /**
     * Decays [currentDeltaMps] by `exp(-dtSec / tau)`, integrates [forwardAccel] over the
     * time step, and clamps the result to `±0.5 × initialSpeedMps`.
     */
    fun decaySpeedDelta(
        currentDeltaMps: Float,
        forwardAccel: Float,
        dtSec: Float,
        initialSpeedMps: Float,
        tau: Float = 10f
    ): Float {
        val decay = exp(-dtSec / tau).toFloat()
        val newDelta = currentDeltaMps * decay + forwardAccel * dtSec
        val maxAdjust = 0.5f * initialSpeedMps
        return newDelta.coerceIn(-maxAdjust, maxAdjust)
    }
}
