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
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

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

    private var relativeX: Double = 0.0
    private var relativeY: Double = 0.0
    private var currentHeadingRad: Double = 0.0
    private var stepCount: Int = 0
    private var hasHeading: Boolean = false

    private var stepLengthM: Float = 0.7f
    private var originResetTimeMs: Long = System.currentTimeMillis()
    private var driftRate: Double = 0.02

    private val _positionUpdate = MutableStateFlow(IndoorPositionUpdate())
    val positionUpdate: StateFlow<IndoorPositionUpdate> = _positionUpdate.asStateFlow()

    private var isStarted = false

    val hasStepDetector: Boolean get() = stepDetector != null
    val hasGameRotationVector: Boolean get() = gameRotation != null
    val hasRotationVector: Boolean get() = rotationVector != null

    private val sensorCallback = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            when (event.sensor.type) {
                Sensor.TYPE_STEP_DETECTOR -> onStepDetected()
                Sensor.TYPE_GAME_ROTATION_VECTOR -> onRotationChanged(event.values)
                Sensor.TYPE_ROTATION_VECTOR -> onRotationChanged(event.values)
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    private fun onStepDetected() {
        if (!hasHeading) return
        stepCount++
        val d = stepLengthM.toDouble()
        relativeX += d * cos(currentHeadingRad)
        relativeY += d * sin(currentHeadingRad)
        updateDrift()
        emitUpdate()
    }

    private fun onRotationChanged(values: FloatArray) {
        val yawDeg = calculateYaw(values)
        currentHeadingRad = Math.toRadians(yawDeg.toDouble())
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
        stepLengthM = stepLength
        relativeX = 0.0
        relativeY = 0.0
        currentHeadingRad = 0.0
        stepCount = 0
        hasHeading = false
        originResetTimeMs = System.currentTimeMillis()
        driftRate = 0.02

        val handler = Handler(Looper.getMainLooper())
        stepDetector?.let {
            sensorManager.registerListener(sensorCallback, it, SensorManager.SENSOR_DELAY_UI, handler)
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
    }

    fun resetOrigin() {
        relativeX = 0.0
        relativeY = 0.0
        stepCount = 0
        hasHeading = false
        originResetTimeMs = System.currentTimeMillis()
        driftRate = 0.02
        emitUpdate()
    }

    private fun calculateYaw(values: FloatArray): Float {
        val w = values.getOrElse(3) { 1f }
        val x = values[0]
        val y = values[1]
        val z = values[2]
        val yaw = -atan2(
            2.0 * (w.toDouble() * z.toDouble() + x.toDouble() * y.toDouble()),
            1.0 - 2.0 * (y.toDouble() * y.toDouble() + z.toDouble() * z.toDouble())
        )
        return Math.toDegrees(yaw).toFloat()
    }
}