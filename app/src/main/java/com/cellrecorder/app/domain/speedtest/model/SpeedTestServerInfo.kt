package com.cellrecorder.app.domain.speedtest.model

data class SpeedTestServerInfo(
    val id: Int,
    val name: String,
    val host: String,
    val url: String,
    val lat: Double,
    val lon: Double,
    val sponsor: String,
    val latencyMs: Double = 0.0
)