package com.cellrecorder.app.service

import android.os.HandlerThread
import android.os.Looper
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CallbackHandlerThread @Inject constructor() {
    private val thread = HandlerThread("callback-handler").apply { start() }
    val looper: Looper get() = thread.looper

    fun quit() {
        thread.quitSafely()
    }
}