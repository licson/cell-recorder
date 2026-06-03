package com.cellrecorder.app.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecordingStateManager @Inject constructor() {

    private val _state = MutableStateFlow<RecordingState?>(null)
    val state: StateFlow<RecordingState?> = _state.asStateFlow()

    var currentState: RecordingState?
        get() = _state.value
        set(value) { _state.value = value }

    fun update(transform: (RecordingState?) -> RecordingState?) {
        _state.value = transform(_state.value)
    }
}