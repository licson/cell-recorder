package com.cellrecorder.app.ui.navigation

object Routes {
    const val LIVE_INFO = "live_info"
    const val SESSION_LIST = "session_list"
    const val STATISTICS = "statistics"
    const val RECORDING = "recording/{sessionId}"
    const val SESSION_DETAIL = "session_detail/{sessionId}"
    const val REPLAY = "replay/{sessionId}"
    const val SETTINGS = "settings"

    fun recording(sessionId: Long) = "recording/$sessionId"
    fun sessionDetail(sessionId: Long) = "session_detail/$sessionId"
    fun replay(sessionId: Long) = "replay/$sessionId"
}