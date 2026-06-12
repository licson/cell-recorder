package com.cellrecorder.app.service

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class IndoorPositionUpdate(
    val relativeX: Double = 0.0,
    val relativeY: Double = 0.0,
    val headingRad: Double = 0.0,
    val stepCount: Int = 0,
    val estimatedDriftM: Double = 0.0,
    val timestamp: Long = System.currentTimeMillis()
)

@Singleton
class IndoorPositionCollector @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val sensorManager: SensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val stepDetector: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
    private val gameRotation: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
    private val rotationVector: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val accelerometer: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private var relativeX: Double = 0.0
    private var relativeY: Double = 0.0
    private var currentHeadingRad: Double = 0.0
    private var stepCount: Int = 0
    private var hasHeading: Boolean = false

    private var stepLengthM: Float = 0.7f
    private var originResetTimeMs: Long = System.currentTimeMillis()
    private var driftRate: Double = 0.02
    private var lastStepTimeMs: Long = 0L

    private var isUsingAccelFallback: Boolean = false
    private val STEP_COOLDOWN_MS = 350L

    private var filteredMagnitude: Float = 0f
    private var gravityBaseline: Float = 9.81f
    private val ACCEL_ALPHA = 0.1f
    private val STEP_THRESHOLD = 1.15f
    private var baselineSamples: Int = 0
    private val BASELINE_CALIBRATION_SAMPLES = 20

    private val rotationMatrix = FloatArray(9)
    private val orientation = FloatArray(3)

    private val _positionUpdate = MutableStateFlow(IndoorPositionUpdate())
    val positionUpdate: StateFlow<IndoorPositionUpdate> = _positionUpdate.asStateFlow()

    private var isStarted = false

    val hasStepDetector: Boolean get() = stepDetector != null
    val hasGameRotationVector: Boolean get() = gameRotation != null
    val hasRotationVector: Boolean get() = rotationVector != null
    val hasAccelerometer: Boolean get() = accelerometer != null
    val isAccelerometerFallbackActive: Boolean get() = isUsingAccelFallback
    val lastStepTimestampMs: Long get() = lastStepTimeMs

    private val sensorCallback = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            when (event.sensor.type) {
                Sensor.TYPE_STEP_DETECTOR -> onStepDetected()
                Sensor.TYPE_ACCELEROMETER -> onAccelerometerEvent(event.values)
                Sensor.TYPE_GAME_ROTATION_VECTOR -> onRotationChanged(event.values)
                Sensor.TYPE_ROTATION_VECTOR -> onRotationChanged(event.values)
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    private fun onStepDetected() {
        if (!hasHeading) return
        stepCount++
        lastStepTimeMs = System.currentTimeMillis()
        val d = stepLengthM.toDouble()
        relativeX += d * cos(currentHeadingRad)
        relativeY += d * sin(currentHeadingRad)
        updateDrift()
        emitUpdate()
    }

    private fun onAccelerometerEvent(values: FloatArray) {
        val mag = sqrt(
            (values[0] * values[0] + values[1] * values[1] + values[2] * values[2]).toDouble()
        ).toFloat()

        if (baselineSamples < BASELINE_CALIBRATION_SAMPLES) {
            gravityBaseline = gravityBaseline * baselineSamples / (baselineSamples + 1) +
                    mag / (baselineSamples + 1)
            baselineSamples++
            filteredMagnitude = mag
            return
        }

        filteredMagnitude = ACCEL_ALPHA * mag + (1f - ACCEL_ALPHA) * filteredMagnitude

        val now = System.currentTimeMillis()
        if (filteredMagnitude > gravityBaseline * STEP_THRESHOLD &&
            now - lastStepTimeMs >= STEP_COOLDOWN_MS) {
            onStepDetected()
        }
    }

    private fun onRotationChanged(values: FloatArray) {
        SensorManager.getRotationMatrixFromVector(rotationMatrix, values)
        SensorManager.getOrientation(rotationMatrix, orientation)
        currentHeadingRad = orientation[0].toDouble()
        hasHeading = true
        emitUpdate()
    }

    private fun updateDrift() {
        val elapsedMin = (System.currentTimeMillis() - originResetTimeMs) / 60_000.0
        driftRate = (0.02 + elapsedMin * 0.004).coerceAtMost(0.20)
    }

    private fun emitUpdate() {
        val distanceWalked = stepCount * stepLengthM.toDouble()
        val drift = distanceWalked * driftRate
        _positionUpdate.value = IndoorPositionUpdate(
            relativeX = relativeX,
            relativeY = relativeY,
            headingRad = currentHeadingRad,
            stepCount = stepCount,
            estimatedDriftM = drift,
            timestamp = System.currentTimeMillis()
        )
    }

    fun start(stepLength: Float = 0.7f) {
        if (isStarted) return
        isStarted = true
        isUsingAccelFallback = false
        stepLengthM = stepLength
        relativeX = 0.0
        relativeY = 0.0
        currentHeadingRad = 0.0
        stepCount = 0
        hasHeading = false
        originResetTimeMs = System.currentTimeMillis()
        driftRate = 0.02
        lastStepTimeMs = 0L
        filteredMagnitude = 0f
        gravityBaseline = 9.81f
        baselineSamples = 0

        val handler = Handler(Looper.getMainLooper())
        val stepSensor = stepDetector
        var stepRegistered = false
        if (stepSensor != null) {
            stepRegistered = sensorManager.registerListener(
                sensorCallback, stepSensor, SensorManager.SENSOR_DELAY_UI, handler
            )
        }
        if (!stepRegistered && accelerometer != null) {
            sensorManager.registerListener(
                sensorCallback, accelerometer, SensorManager.SENSOR_DELAY_GAME, handler
            )
            isUsingAccelFallback = true
        }
        val rotation = gameRotation ?: rotationVector
        rotation?.let {
            sensorManager.registerListener(sensorCallback, it, SensorManager.SENSOR_DELAY_GAME, handler)
        }
        emitUpdate()
    }

    fun stop() {
        if (!isStarted) return
        isStarted = false
        sensorManager.unregisterListener(sensorCallback)
        relativeX = 0.0
        relativeY = 0.0
        currentHeadingRad = 0.0
        stepCount = 0
        hasHeading = false
        originResetTimeMs = System.currentTimeMillis()
        driftRate = 0.02
        lastStepTimeMs = 0L
        isUsingAccelFallback = false
    }

    fun resetOrigin() {
        relativeX = 0.0
        relativeY = 0.0
        stepCount = 0
        hasHeading = false
        originResetTimeMs = System.currentTimeMillis()
        driftRate = 0.02
        lastStepTimeMs = 0L
        emitUpdate()
    }

    fun isAnyStepDetectionActive(): Boolean = isStarted &&
        ((stepDetector != null) || (isUsingAccelFallback && accelerometer != null))
}