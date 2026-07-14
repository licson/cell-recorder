package com.cellrecorder.app.service

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
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
    @ApplicationContext private val context: Context,
    private val callbackHandler: CallbackHandlerThread
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
    @Volatile private var originResetTimeMs: Long = System.currentTimeMillis()
    @Volatile private var _originResetCount: Int = 0
    private var driftRate: Double = 0.02
    @Volatile private var lastStepTimeMs: Long = 0L

    private var isUsingAccelFallback: Boolean = false
    private var stepDetectorRegistered: Boolean = false
    private var accelerometerRegistered: Boolean = false
    private var rotationRegistered: Boolean = false
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
    val originResetTimestampMs: Long get() = originResetTimeMs
    val originResetCount: Int get() = _originResetCount

    fun secondsSinceLastStep(): Long =
        if (lastStepTimeMs == 0L) -1L else (System.currentTimeMillis() - lastStepTimeMs) / 1000L

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
        val mag = IndoorStepDetector.magnitude(values[0], values[1], values[2])

        if (baselineSamples < BASELINE_CALIBRATION_SAMPLES) {
            gravityBaseline = IndoorStepDetector.calibrateBaseline(gravityBaseline, mag, baselineSamples)
            baselineSamples++
            filteredMagnitude = mag
            return
        }

        filteredMagnitude = ACCEL_ALPHA * mag + (1f - ACCEL_ALPHA) * filteredMagnitude

        val now = System.currentTimeMillis()
        if (IndoorStepDetector.isStep(filteredMagnitude, gravityBaseline) &&
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
        driftRate = IndoorStepDetector.driftRateForElapsedMinutes(elapsedMin)
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
        stepDetectorRegistered = false
        accelerometerRegistered = false
        rotationRegistered = false
        stepLengthM = stepLength
        relativeX = 0.0
        relativeY = 0.0
        currentHeadingRad = 0.0
        stepCount = 0
        hasHeading = false
        originResetTimeMs = System.currentTimeMillis()
        _originResetCount = 0
        driftRate = 0.02
        lastStepTimeMs = 0L
        filteredMagnitude = 0f
        gravityBaseline = 9.81f
        baselineSamples = 0

        val handler = Handler(callbackHandler.looper)
        val stepSensor = stepDetector
        if (stepSensor != null) {
            stepDetectorRegistered = sensorManager.registerListener(
                sensorCallback, stepSensor, SensorManager.SENSOR_DELAY_UI, handler
            )
        }
        if (!stepDetectorRegistered && accelerometer != null) {
            accelerometerRegistered = sensorManager.registerListener(
                sensorCallback, accelerometer, SensorManager.SENSOR_DELAY_GAME, handler
            )
            isUsingAccelFallback = accelerometerRegistered
        }
        val rotation = gameRotation ?: rotationVector
        if (rotation != null) {
            rotationRegistered = sensorManager.registerListener(
                sensorCallback, rotation, SensorManager.SENSOR_DELAY_GAME, handler
            )
        }
        emitUpdate()
    }

    fun stop() {
        if (!isStarted) return
        isStarted = false
        val latch = CountDownLatch(1)
        Handler(callbackHandler.looper).post {
            try {
                sensorManager.unregisterListener(sensorCallback)
            } finally {
                latch.countDown()
            }
        }
        val acquired = latch.await(5, TimeUnit.SECONDS)
        if (!acquired) {
            Timber.w("Sensor unregister timed out after 5s in IndoorPositionCollector.stop")
        }
        relativeX = 0.0
        relativeY = 0.0
        currentHeadingRad = 0.0
        stepCount = 0
        hasHeading = false
        originResetTimeMs = System.currentTimeMillis()
        _originResetCount = 0
        driftRate = 0.02
        lastStepTimeMs = 0L
        isUsingAccelFallback = false
        stepDetectorRegistered = false
        accelerometerRegistered = false
        rotationRegistered = false
    }

    fun resetOrigin() {
        relativeX = 0.0
        relativeY = 0.0
        stepCount = 0
        hasHeading = false
        originResetTimeMs = System.currentTimeMillis()
        _originResetCount += 1
        driftRate = 0.02
        lastStepTimeMs = 0L
        emitUpdate()
    }

    fun isAnyStepDetectionActive(): Boolean = isStarted &&
        (stepDetectorRegistered || accelerometerRegistered)
}