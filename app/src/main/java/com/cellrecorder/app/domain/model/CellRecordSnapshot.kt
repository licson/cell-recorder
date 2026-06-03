package com.cellrecorder.app.domain.model

data class CellRecordSnapshot(
    val subscriptionId: Int = 0,
    val simSlotIndex: Int = 0,
    val simDisplayName: String = "",
    val rat: String = "UNKNOWN",
    val networkTypeCode: Int? = null,
    val fullCellIdentity: Long? = null,
    val enbOrGnbId: Long? = null,
    val lcid: Int? = null,
    val cellIdBitLength: Int? = null,
    val pci: Int? = null,
    val tac: Int? = null,
    val bandNumber: Int? = null,
    val earfcn: Int? = null,
    val bandwidthKhz: Int? = null,
    val rsrp: Int? = null,
    val rsrq: Int? = null,
    val sinr: Int? = null,
    val rssi: Int? = null,
    val cqi: Int? = null,
    val timingAdvance: Int? = null,
    val mcc: String? = null,
    val mnc: String? = null,
    val caBands: List<CaBandSnapshot> = emptyList()
)