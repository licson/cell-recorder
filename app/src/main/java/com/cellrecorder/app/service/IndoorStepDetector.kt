package com.cellrecorder.app.service

import kotlin.math.sqrt

/**
 * Pure math helpers for [IndoorPositionCollector], extracted so the step-detection
 * and drift-rate logic is unit-testable without sensor-event plumbing.
 *
 * - [isStep] applies the 1.15×gravity threshold to the filtered magnitude.
 * - [calibrateBaseline] returns the new running average when accumulating a sample
 *   during the 20-sample calibration window.
 * - [driftRateForElapsedMinutes] returns the drift rate, growing linearly from 0.02
 *   at 0 minutes by 0.004 per minute, capped at 0.20.
 */
object IndoorStepDetector {

    /** Step threshold above gravity baseline (1.15× gravity). */
    const val DEFAULT_STEP_THRESHOLD = 1.15f

    /** Drift rate base value. */
    const val DEFAULT_DRIFT_BASE = 0.02

    /** Drift rate growth per elapsed minute. */
    const val DEFAULT_DRIFT_SLOPE = 0.004

    /** Maximum drift rate (cap). */
    const val DEFAULT_DRIFT_MAX = 0.20

    /** Returns true if [filteredMagnitude] exceeds [gravityBaseline] × [threshold]. */
    fun isStep(
        filteredMagnitude: Float,
        gravityBaseline: Float,
        threshold: Float = DEFAULT_STEP_THRESHOLD
    ): Boolean {
        return filteredMagnitude > gravityBaseline * threshold
    }

    /**
     * Returns the new running-average baseline after incorporating [sampleMagnitude]
     * into the existing [currentBaseline] accumulated over [sampleCount] prior samples.
     * Equivalent to `(currentBaseline * sampleCount + sampleMagnitude) / (sampleCount + 1)`
     * but matches the inline implementation's order of operations.
     */
    fun calibrateBaseline(
        currentBaseline: Float,
        sampleMagnitude: Float,
        sampleCount: Int
    ): Float {
        val denom = (sampleCount + 1).toFloat()
        return currentBaseline * sampleCount / denom + sampleMagnitude / denom
    }

    /**
     * Returns the drift rate for [elapsedMin] minutes since the origin was reset.
     * Linear growth from [DEFAULT_DRIFT_BASE] (0.02) by [DEFAULT_DRIFT_SLOPE] (0.004)
     * per minute, capped at [DEFAULT_DRIFT_MAX] (0.20).
     */
    fun driftRateForElapsedMinutes(
        elapsedMin: Double,
        base: Double = DEFAULT_DRIFT_BASE,
        slope: Double = DEFAULT_DRIFT_SLOPE,
        max: Double = DEFAULT_DRIFT_MAX
    ): Double {
        return (base + elapsedMin * slope).coerceAtMost(max)
    }

    /** Computes the magnitude (Euclidean norm) of a 3-vector. */
    fun magnitude(x: Float, y: Float, z: Float): Float {
        return sqrt((x * x + y * y + z * z).toDouble()).toFloat()
    }
}
