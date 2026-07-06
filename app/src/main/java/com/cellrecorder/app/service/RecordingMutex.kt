package com.cellrecorder.app.service

import kotlinx.coroutines.sync.Mutex
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecordingMutex @Inject constructor() {
    val mutex = Mutex()
}
