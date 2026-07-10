package com.cellrecorder.app.domain.speedtest

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory bounded ring buffer of [SpeedTestDebugEvent]s emitted by
 * [SpeedTestEngine]. `@Singleton`-scoped; does not survive process restart.
 *
 * Capacity is 200 events. When capacity is exceeded the oldest event is
 * evicted. Thread-safe: the engine runs on `Dispatchers.IO` with parallel
 * measurement coroutines, so concurrent `append` calls are serialized via a
 * [Mutex].
 *
 * Exposes:
 *  - [events] for live UI consumption (StateFlow).
 *  - [snapshot] for "Share Debug Log" export.
 *  - [clear] for re-prime reset.
 */
@Singleton
class SpeedTestDebugRingBuffer @Inject constructor() {

    private val capacity: Int = DEFAULT_CAPACITY
    private val mutex = Mutex()
    private val buffer: ArrayDeque<SpeedTestDebugEvent> = ArrayDeque(capacity)

    private val _events = MutableStateFlow<List<SpeedTestDebugEvent>>(emptyList())
    val events: StateFlow<List<SpeedTestDebugEvent>> = _events.asStateFlow()

    suspend fun append(event: SpeedTestDebugEvent) {
        mutex.withLock {
            if (buffer.size >= capacity) {
                buffer.removeFirst()
            }
            buffer.addLast(event)
            _events.value = buffer.toList()
        }
    }

    suspend fun clear() {
        mutex.withLock {
            buffer.clear()
            _events.value = emptyList()
        }
    }

    fun snapshot(): List<SpeedTestDebugEvent> = _events.value

    companion object {
        const val DEFAULT_CAPACITY = 200
    }
}
