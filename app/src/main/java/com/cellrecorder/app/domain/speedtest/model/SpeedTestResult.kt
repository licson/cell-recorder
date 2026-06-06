package com.cellrecorder.app.domain.speedtest.model

data class SpeedTestResult(
    val downloadBps: Long?,
    val uploadBps: Long?,
    val serverId: Int?,
    val serverName: String?,
    val serverHost: String?,
    val serverLocation: String?,
    val succeeded: Boolean,
    val errorMessage: String?
)