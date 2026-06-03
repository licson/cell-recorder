package com.cellrecorder.app.service

class GpsStateMachine {

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

    fun reset() {
        hasGpsFix = false
        lastValidLocation = null
        lastAccurateFixTime = 0L
        isExtrapolating = false
        gpsLostAtMs = 0L
        gpsSettlingUntilMs = 0L
    }

    fun updateMotion(speed: Float?, bearing: Float?) {
        if (speed != null) lastKnownSpeedMps = speed
        if (bearing != null) lastKnownBearing = bearing
    }

    fun recordAccurateFix(location: LocationUpdate, nowMs: Long) {
        lastAccurateFixTime = nowMs
        lastValidLocation = location
        if (!hasGpsFix) hasGpsFix = true
    }

    fun startExtrapolating(nowMs: Long) {
        isExtrapolating = true
        gpsLostAtMs = nowMs
    }

    fun stopExtrapolating() {
        isExtrapolating = false
        gpsLostAtMs = 0L
    }

    fun setSettlingUntil(ms: Long) {
        gpsSettlingUntilMs = ms
    }

    fun isFixLost(nowMs: Long, gpsLossDelayMs: Long): Boolean {
        val timeSinceAccurateFix = nowMs - lastAccurateFixTime
        return timeSinceAccurateFix > gpsLossDelayMs && hasGpsFix && !isExtrapolating && nowMs >= gpsSettlingUntilMs
    }

    fun extrapolationAgeSec(nowMs: Long): Float {
        return if (gpsLostAtMs > 0L) (nowMs - gpsLostAtMs) / 1000f else 0f
    }

    fun isInSettling(nowMs: Long): Boolean {
        return nowMs < gpsSettlingUntilMs
    }

    fun estimatedAccuracy(extrapolationAgeSec: Float): Float {
        return 50f + extrapolationAgeSec * 3f
    }
}