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
import kotlin.math.exp
import kotlin.math.sin

@Singleton
class SensorFusionCollector @Inject constructor(
    @ApplicationContext private val context: Context,
    private val callbackHandler: CallbackHandlerThread
) {
    private val sensorManager: SensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val gameRotation: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
    private val linearAccel: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)

    private var baselineYaw: Float? = null
    private var smoothedDelta: Float = 0f
    private val _headingDelta = MutableStateFlow(0f)
    val headingDelta: StateFlow<Float> = _headingDelta.asStateFlow()

    private var initialBearing: Float = 0f
    private var initialSpeedMps: Float = 0f
    private val _speedDeltaMps = MutableStateFlow(0f)
    val speedDeltaMps: StateFlow<Float> = _speedDeltaMps.asStateFlow()

    private var rotationMatrix = FloatArray(9)
    private var orientation = FloatArray(3)
    private var hasRotationMatrix = false
    private var lastAccelTimestampNs: Long = 0L

    val isAvailable: Boolean get() = gameRotation != null
    val hasLinearAccel: Boolean get() = linearAccel != null

    private val sensorCallback = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            when (event.sensor.type) {
                Sensor.TYPE_GAME_ROTATION_VECTOR -> onGameRotationChanged(event)
                Sensor.TYPE_LINEAR_ACCELERATION -> onLinearAccelChanged(event)
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    private fun onGameRotationChanged(event: SensorEvent) {
        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
        hasRotationMatrix = true

        SensorManager.getOrientation(rotationMatrix, orientation)
        val yawDeg = Math.toDegrees(orientation[0].toDouble()).toFloat()
        val base = baselineYaw
        if (base == null) {
            baselineYaw = yawDeg
            return
        }
        val rawDelta = yawDeg - base
        smoothedDelta = SensorFusionMath.smoothHeadingDelta(smoothedDelta, rawDelta)
        if (_headingDelta.value != smoothedDelta) {
            _headingDelta.value = smoothedDelta
        }
    }

    private fun onLinearAccelChanged(event: SensorEvent) {
        if (!hasRotationMatrix || initialSpeedMps <= 0f) return

        if (lastAccelTimestampNs == 0L) {
            lastAccelTimestampNs = event.timestamp
            return
        }

        val dtSec = (event.timestamp - lastAccelTimestampNs) / 1_000_000_000f
        lastAccelTimestampNs = event.timestamp
        if (dtSec <= 0f || dtSec > 0.5f) return

        val ax = event.values[0]
        val ay = event.values[1]
        val az = event.values[2]
        val worldAccel = floatArrayOf(
            rotationMatrix[0] * ax + rotationMatrix[3] * ay + rotationMatrix[6] * az,
            rotationMatrix[1] * ax + rotationMatrix[4] * ay + rotationMatrix[7] * az,
            rotationMatrix[2] * ax + rotationMatrix[5] * ay + rotationMatrix[8] * az
        )

        val currentHeadingDeg = (initialBearing + smoothedDelta + 360f) % 360f
        val headingRad = Math.toRadians(currentHeadingDeg.toDouble())
        val forwardAccel = (worldAccel[0] * sin(headingRad) + worldAccel[1] * cos(headingRad)).toFloat()

        _speedDeltaMps.value = SensorFusionMath.decaySpeedDelta(
            currentDeltaMps = _speedDeltaMps.value,
            forwardAccel = forwardAccel,
            dtSec = dtSec,
            initialSpeedMps = initialSpeedMps
        )
    }

    fun start(bearing: Float = 0f, speedMps: Float = 0f) {
        baselineYaw = null
        smoothedDelta = 0f
        _headingDelta.value = 0f
        initialBearing = bearing
        initialSpeedMps = speedMps
        _speedDeltaMps.value = 0f
        hasRotationMatrix = false
        lastAccelTimestampNs = 0L

        val handler = Handler(callbackHandler.looper)
        gameRotation?.let {
            sensorManager.registerListener(sensorCallback, it, SensorManager.SENSOR_DELAY_GAME, handler)
        }
        if (initialSpeedMps > 0f) {
            linearAccel?.let {
                sensorManager.registerListener(sensorCallback, it, SensorManager.SENSOR_DELAY_GAME, handler)
            }
        }
    }

    fun stop() {
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
            Timber.w("Sensor unregister timed out after 5s in SensorFusionCollector.stop")
        }
        baselineYaw = null
        smoothedDelta = 0f
        _headingDelta.value = 0f
        initialBearing = 0f
        initialSpeedMps = 0f
        _speedDeltaMps.value = 0f
        hasRotationMatrix = false
        lastAccelTimestampNs = 0L
    }

    }