package com.cellrecorder.app.domain.ping

import com.cellrecorder.app.domain.model.PingOutcome
import com.cellrecorder.app.domain.model.PingResult

class PingSlidingWindow(private val windowSize: Int = 5) {

    private val buffer = ArrayDeque<PingResult>(windowSize)

    fun add(result: PingResult) {
        if (buffer.size >= windowSize) {
            buffer.removeFirst()
        }
        buffer.addLast(result)
    }

    fun avgLatencyMs(): Double? {
        val valid = buffer.filter { it.outcome == PingOutcome.SUCCESS }.mapNotNull { it.latencyMs }
        if (valid.isEmpty()) return null
        return valid.average()
    }

    fun packetLossPct(): Double {
        if (buffer.size < windowSize) return 0.0
        val lossCount = buffer.count { it.outcome != PingOutcome.SUCCESS }
        return (lossCount.toDouble() / windowSize) * 100.0
    }

    fun reset() {
        buffer.clear()
    }
}