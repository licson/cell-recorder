package com.cellrecorder.app.service

data class SimLiveState(
    val subscriptionId: Int = 0,
    val simSlotIndex: Int = 0,
    val plmn: String = "---",
    val rat: String = "---",
    val tac: String = "---",
    val bandNumber: String = "---",
    val earfcn: String = "---",
    val cellId: String = "---",
    val pci: String = "---",
    val rsrp: String = "---",
    val rsrq: String = "---",
    val sinr: String = "---",
    val caBands: List<String> = emptyList()
)

data class RecordingState(
    val sessionId: Long,
    val isRecording: Boolean = false,
    val pointCount: Int = 0,
    val elapsedMs: Long = 0,
    val gpsStatus: String = "---",
    val recordedPath: List<Pair<Double, Double>> = emptyList(),
    val dataSubId: Int = -1,
    val currentLatency: String = "---",
    val currentLatitude: Double = 0.0,
    val currentLongitude: Double = 0.0,
    val currentAltitude: Double = 0.0,
    val errorMessage: String? = null,
    val isExtrapolatingGps: Boolean = false
)