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

@Singleton
class SensorFusionCollector @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val sensorManager: SensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val gameRotation: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)

    private var baselineYaw: Float? = null
    private var smoothedDelta: Float = 0f
    private val _headingDelta = MutableStateFlow(0f)
    val headingDelta: StateFlow<Float> = _headingDelta.asStateFlow()

    val isAvailable: Boolean get() = gameRotation != null

    private val sensorCallback = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (event.sensor.type != Sensor.TYPE_GAME_ROTATION_VECTOR) return
            val yaw = calculateYaw(event.values)
            if (baselineYaw == null) {
                baselineYaw = yaw
                return
            }
            var rawDelta = yaw - baselineYaw!!
            if (rawDelta > 180f) rawDelta -= 360f
            if (rawDelta < -180f) rawDelta += 360f
            smoothedDelta = 0.85f * smoothedDelta + 0.15f * rawDelta
            if (_headingDelta.value != smoothedDelta) {
                _headingDelta.value = smoothedDelta
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    fun start() {
        baselineYaw = null
        smoothedDelta = 0f
        _headingDelta.value = 0f
        gameRotation?.let {
            sensorManager.registerListener(
                sensorCallback,
                it,
                SensorManager.SENSOR_DELAY_GAME,
                Handler(Looper.getMainLooper())
            )
        }
    }

    fun stop() {
        sensorManager.unregisterListener(sensorCallback)
        baselineYaw = null
        smoothedDelta = 0f
        _headingDelta.value = 0f
    }

    private fun calculateYaw(values: FloatArray): Float {
        val w = values.getOrElse(3) { 1f }
        val x = values[0]
        val y = values[1]
        val z = values[2]
        val yaw = atan2(
            2.0 * (w.toDouble() * z.toDouble() + x.toDouble() * y.toDouble()),
            1.0 - 2.0 * (y.toDouble() * y.toDouble() + z.toDouble() * z.toDouble())
        )
        return Math.toDegrees(yaw).toFloat()
    }
}