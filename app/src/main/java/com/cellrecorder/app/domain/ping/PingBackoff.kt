package com.cellrecorder.app.domain.ping

/**
 * Pure-logic exponential backoff calculator for ping process restarts.
 *
 * Sequence: 1s → 2s → 4s → 8s → 16s → 32s → 60s (cap) for restartCount 0..6+.
 * The cap prevents unbounded growth while ensuring connectivity recovery is
 * detected within approximately one minute.
 */
object PingBackoff {

    private const val CAP_MS = 60_000L

    fun delayForFailure(restartCount: Int): Long {
        if (restartCount <= 0) return 1_000L
        // 2^restartCount seconds, capped at 60s.
        // restartCount=0 -> 1s, 1 -> 2s, 2 -> 4s, 3 -> 8s, 4 -> 16s, 5 -> 32s, 6 -> 64s (capped to 60s)
        val rawMs = 1_000L shl restartCount
        return rawMs.coerceAtMost(CAP_MS)
    }
}
