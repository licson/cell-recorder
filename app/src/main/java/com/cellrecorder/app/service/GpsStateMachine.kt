package com.cellrecorder.app.service

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

data class GpsStateSnapshot(
    val hasGpsFix: Boolean,
    val lastKnownSpeedMps: Float,
    val lastKnownBearing: Float,
    val lastValidLocation: LocationUpdate?,
    val lastAccurateFixTime: Long,
    val isExtrapolating: Boolean,
    val gpsLostAtMs: Long,
    val gpsSettlingUntilMs: Long
)

class GpsStateMachine {
    private val lock = ReentrantLock()

    var hasGpsFix: Boolean = false
        internal set
    var lastKnownSpeedMps: Float = 0f
        internal set
    var lastKnownBearing: Float = 0f
        internal set
    var lastValidLocation: LocationUpdate? = null
        internal set
    var lastAccurateFixTime: Long = 0L
        internal set
    var isExtrapolating: Boolean = false
        internal set
    var gpsLostAtMs: Long = 0L
        internal set
    var gpsSettlingUntilMs: Long = 0L
        internal set

    fun snapshot(): GpsStateSnapshot = lock.withLock {
        GpsStateSnapshot(
            hasGpsFix = hasGpsFix,
            lastKnownSpeedMps = lastKnownSpeedMps,
            lastKnownBearing = lastKnownBearing,
            lastValidLocation = lastValidLocation,
            lastAccurateFixTime = lastAccurateFixTime,
            isExtrapolating = isExtrapolating,
            gpsLostAtMs = gpsLostAtMs,
            gpsSettlingUntilMs = gpsSettlingUntilMs
        )
    }

    fun reset() {
        lock.withLock {
            hasGpsFix = false
            lastValidLocation = null
            lastAccurateFixTime = 0L
            isExtrapolating = false
            gpsLostAtMs = 0L
            gpsSettlingUntilMs = 0L
        }
    }

    fun updateMotion(speed: Float?, bearing: Float?) {
        lock.withLock {
            if (speed != null) lastKnownSpeedMps = speed
            if (bearing != null) lastKnownBearing = bearing
        }
    }

    fun recordAccurateFix(location: LocationUpdate, nowMs: Long) {
        lock.withLock {
            lastAccurateFixTime = nowMs
            lastValidLocation = location
            if (!hasGpsFix) hasGpsFix = true
        }
    }

    fun startExtrapolating(nowMs: Long) {
        lock.withLock {
            isExtrapolating = true
            gpsLostAtMs = nowMs
        }
    }

    fun stopExtrapolating() {
        lock.withLock {
            isExtrapolating = false
            gpsLostAtMs = 0L
        }
    }

    fun setSettlingUntil(ms: Long) {
        lock.withLock {
            gpsSettlingUntilMs = ms
        }
    }

    fun isFixLost(nowMs: Long, gpsLossDelayMs: Long): Boolean {
        return lock.withLock {
            val timeSinceAccurateFix = nowMs - lastAccurateFixTime
            timeSinceAccurateFix > gpsLossDelayMs && hasGpsFix && !isExtrapolating && nowMs >= gpsSettlingUntilMs
        }
    }

    fun extrapolationAgeSec(nowMs: Long): Float {
        return lock.withLock {
            if (gpsLostAtMs > 0L) (nowMs - gpsLostAtMs) / 1000f else 0f
        }
    }

    fun isInSettling(nowMs: Long): Boolean {
        return lock.withLock { nowMs < gpsSettlingUntilMs }
    }

    fun estimatedAccuracy(extrapolationAgeSec: Float): Float {
        return 50f + extrapolationAgeSec * 3f
    }
}