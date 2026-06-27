package com.cellrecorder.app.domain.model

data class CaBandSnapshot(
    val bandNumber: Int? = null,
    val earfcn: Int? = null,
    val pci: Int? = null,
    val rsrp: Int? = null,
    val rsrq: Int? = null,
    val sinr: Int? = null,
    val rssi: Int? = null,
    val cqi: Int? = null,
    val timingAdvance: Int? = null,
    val bandwidthKhz: Int? = null
)