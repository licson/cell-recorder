package com.cellrecorder.app.util

import com.cellrecorder.app.data.local.entity.AppConfigEntity
import com.cellrecorder.app.data.local.entity.CellRecordCaBandEntity
import com.cellrecorder.app.data.local.entity.CellRecordEntity
import com.cellrecorder.app.data.local.entity.SessionEntity
import com.cellrecorder.app.data.local.entity.SpeedTestRecordEntity

object TestDataFactory {

    fun session(
        id: Long = 0,
        name: String = "Test Session",
        createdAt: Long = 1000L,
        endedAt: Long? = null,
        pointCount: Int = 0,
        primarySimSlot: Int? = null,
        recordingMode: String = "OUTDOOR"
    ): SessionEntity = SessionEntity(
        id = id,
        name = name,
        createdAt = createdAt,
        endedAt = endedAt,
        pointCount = pointCount,
        primarySimSlot = primarySimSlot,
        recordingMode = recordingMode
    )

    fun cellRecord(
        id: Long = 0,
        sessionId: Long = 1,
        timestamp: Long = 2000L,
        latitude: Double = 37.7749,
        longitude: Double = -122.4194,
        altitude: Double = 0.0,
        accuracy: Float = 10f,
        rat: String = "4G",
        fullCellIdentity: Long? = null,
        enbOrGnbId: Long? = null,
        lcid: Int? = null,
        cellIdBitLength: Int? = null,
        pci: Int? = 101,
        tac: Int? = 1,
        bandNumber: Int? = 3,
        earfcn: Int? = 1300,
        rsrp: Int? = -100,
        rsrq: Int? = -10,
        sinr: Int? = 15,
        mcc: String? = "310",
        mnc: String? = "410",
        simSlotIndex: Int? = 0,
        isLocationEstimated: Boolean = false,
        locationSource: String = "GPS"
    ): CellRecordEntity = CellRecordEntity(
        id = id,
        sessionId = sessionId,
        timestamp = timestamp,
        latitude = latitude,
        longitude = longitude,
        altitude = altitude,
        accuracy = accuracy,
        rat = rat,
        fullCellIdentity = fullCellIdentity,
        enbOrGnbId = enbOrGnbId,
        lcid = lcid,
        cellIdBitLength = cellIdBitLength,
        pci = pci,
        tac = tac,
        bandNumber = bandNumber,
        earfcn = earfcn,
        rsrp = rsrp,
        rsrq = rsrq,
        sinr = sinr,
        mcc = mcc,
        mnc = mnc,
        simSlotIndex = simSlotIndex,
        isLocationEstimated = isLocationEstimated,
        locationSource = locationSource
    )

    fun caBand(
        cellRecordId: Long = 1,
        bandNumber: Int? = 3,
        earfcn: Int? = 1300,
        pci: Int? = 102,
        rsrp: Int? = -95,
        rsrq: Int? = -8,
        sinr: Int? = 20
    ): CellRecordCaBandEntity = CellRecordCaBandEntity(
        cellRecordId = cellRecordId,
        bandNumber = bandNumber,
        earfcn = earfcn,
        pci = pci,
        rsrp = rsrp,
        rsrq = rsrq,
        sinr = sinr
    )

    fun speedTestRecord(
        id: Long = 0,
        sessionId: Long = 1,
        timestamp: Long = 3000L,
        downloadBps: Long? = 100_000_000L,
        uploadBps: Long? = 20_000_000L,
        succeeded: Boolean = true
    ): SpeedTestRecordEntity = SpeedTestRecordEntity(
        id = id,
        sessionId = sessionId,
        timestamp = timestamp,
        downloadBps = downloadBps,
        uploadBps = uploadBps,
        serverName = null,
        serverHost = null,
        serverLocation = null,
        serverId = null,
        dataSimSlotIndex = null,
        ratAtTest = null,
        rsrpAtTest = null,
        bandAtTest = null,
        succeeded = succeeded,
        errorMessage = null,
        networkType = null
    )

    fun appConfig(): AppConfigEntity = AppConfigEntity()
}