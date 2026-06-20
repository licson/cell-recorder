package com.cellrecorder.app.service

import android.content.Context
import android.telephony.SubscriptionInfo
import com.cellrecorder.app.data.local.entity.AppConfigEntity
import com.cellrecorder.app.data.local.entity.CellRecordCaBandEntity
import com.cellrecorder.app.data.local.entity.CellRecordEntity
import com.cellrecorder.app.data.repository.CellRecordRepository
import com.cellrecorder.app.data.repository.SessionRepository
import com.cellrecorder.app.domain.ping.PingSlidingWindow
import java.util.Locale
import javax.inject.Inject

private const val MAX_PATH_SIZE = 2000

private const val INDOOR_DRIFT_RATE_INITIAL = 0.02
private const val INDOOR_DRIFT_RATE_PER_MIN = 0.004
private const val INDOOR_DRIFT_RATE_MAX = 0.20

class PointRecorder @Inject constructor(
    private val cellRecordRepository: CellRecordRepository,
    private val sessionRepository: SessionRepository,
    private val cellInfoCollector: CellInfoCollector,
    private val stateManager: RecordingStateManager
) {
    var totalPointCount: Int = 0
        @Synchronized get
        private set
    var lastRecordedLocation: LocationUpdate? = null
        @Synchronized get
        internal set
    var lastRecordedTime: Long = 0L
        @Synchronized get
        internal set

    private val _recordedPath = ArrayDeque<Pair<Double, Double>>(MAX_PATH_SIZE)
    val recordedPathSnapshot: List<Pair<Double, Double>>
        @Synchronized
        get() = _recordedPath.toList()

    private val _discontinuities = ArrayDeque<Int>()
    val recordedDiscontinuitiesSnapshot: Set<Int>
        @Synchronized
        get() = _discontinuities.toSet()

    private var lastOriginResetCount: Int = 0

    fun reset() {
        totalPointCount = 0
        lastRecordedLocation = null
        lastRecordedTime = 0L
        synchronized(this) {
            _recordedPath.clear()
            _discontinuities.clear()
        }
        lastOriginResetCount = 0
    }

    suspend fun recordPoint(
        location: LocationUpdate,
        isEstimated: Boolean,
        source: String,
        sessionId: Long,
        config: AppConfigEntity,
        activeSubs: Map<Int, SubscriptionInfo>,
        pingWindow: PingSlidingWindow
    ) {
        val snapshots = cellInfoCollector.snapshots(config)
        val pingAvg = pingWindow.avgLatencyMs()
        val pingLoss = pingWindow.packetLossPct()

        val records = mutableListOf<CellRecordEntity>()
        val caBandsByRecord = mutableListOf<List<CellRecordCaBandEntity>>()

        for (snapshot in snapshots) {
            try {
                val caEntities = if (snapshot.caBands.isNotEmpty()) {
                    snapshot.caBands.map { ca ->
                        CellRecordCaBandEntity(
                            cellRecordId = 0,
                            bandNumber = ca.bandNumber,
                            earfcn = ca.earfcn,
                            pci = ca.pci,
                            rsrp = ca.rsrp,
                            rsrq = ca.rsrq,
                            sinr = ca.sinr,
                            rssi = ca.rssi,
                            cqi = ca.cqi,
                            timingAdvance = ca.timingAdvance
                        )
                    }
                } else emptyList()

                records.add(CellRecordEntity(
                    sessionId = sessionId,
                    timestamp = System.currentTimeMillis(),
                    latitude = location.latitude,
                    longitude = location.longitude,
                    altitude = location.altitude,
                    accuracy = location.accuracy,
                    rat = snapshot.rat,
                    networkTypeCode = snapshot.networkTypeCode,
                    fullCellIdentity = snapshot.fullCellIdentity,
                    enbOrGnbId = snapshot.enbOrGnbId,
                    lcid = snapshot.lcid,
                    cellIdBitLength = snapshot.cellIdBitLength,
                    pci = snapshot.pci,
                    tac = snapshot.tac,
                    bandNumber = snapshot.bandNumber,
                    earfcn = snapshot.earfcn,
                    bandwidthKhz = snapshot.bandwidthKhz,
                    rsrp = snapshot.rsrp,
                    rsrq = snapshot.rsrq,
                    sinr = snapshot.sinr,
                    rssi = snapshot.rssi,
                    cqi = snapshot.cqi,
                    timingAdvance = snapshot.timingAdvance,
                    mcc = snapshot.mcc,
                    mnc = snapshot.mnc,
                    subscriptionId = snapshot.subscriptionId,
                    simSlotIndex = activeSubs[snapshot.subscriptionId]?.simSlotIndex,
                    avgLatencyMs = pingAvg,
                    packetLossPct = pingLoss,
                    isLocationEstimated = isEstimated,
                    locationSource = source,
                    anchorEnbOrGnbId = snapshot.anchorEnbOrGnbId,
                    anchorLcid = snapshot.anchorLcid,
                    anchorPci = snapshot.anchorPci,
                    anchorTac = snapshot.anchorTac,
                    anchorBandNumber = snapshot.anchorBandNumber,
                    anchorEarfcn = snapshot.anchorEarfcn,
                    anchorBandwidthKhz = snapshot.anchorBandwidthKhz,
                    anchorRsrp = snapshot.anchorRsrp,
                    anchorRsrq = snapshot.anchorRsrq,
                    anchorSinr = snapshot.anchorSinr,
                    anchorRssi = snapshot.anchorRssi,
                    anchorCqi = snapshot.anchorCqi,
                    anchorTimingAdvance = snapshot.anchorTimingAdvance
                ))
                caBandsByRecord.add(caEntities)
            } catch (e: Exception) {
                continue
            }
        }

        if (records.isNotEmpty()) {
            cellRecordRepository.insertRecordBatch(records, caBandsByRecord)
        }

        sessionRepository.incrementPointCount(sessionId)
        totalPointCount++

        synchronized(this) {
            _recordedPath.addLast(location.latitude to location.longitude)
            if (_recordedPath.size > MAX_PATH_SIZE) {
                _recordedPath.removeFirst()
            }
        }
        lastRecordedLocation = location
        lastRecordedTime = System.currentTimeMillis()

        updateLiveState(pingAvg, location, isExtrapolatingParam = isEstimated)
    }

    fun updateLiveState(
        avgLatencyMs: Double?,
        location: LocationUpdate,
        isExtrapolatingParam: Boolean = false
    ) {
        stateManager.update { it?.copy(
            pointCount = totalPointCount,
            currentLatency = avgLatencyMs?.let { String.format(Locale.US, "%.1f", it) } ?: "---",
            currentLatitude = location.latitude,
            currentLongitude = location.longitude,
            currentAltitude = location.altitude,
            recordedPath = recordedPathSnapshot,
            gpsStatus = if (isExtrapolatingParam) "EXTRAPOLATING" else "OK",
            isExtrapolatingGps = isExtrapolatingParam
        ) }
    }

    suspend fun recordIndoorPoint(
        indoorUpdate: IndoorPositionUpdate,
        sessionId: Long,
        config: AppConfigEntity,
        activeSubs: Map<Int, SubscriptionInfo>,
        pingWindow: PingSlidingWindow,
        originResetCount: Int = 0
    ) {
        val snapshots = cellInfoCollector.snapshots(config)
        val pingAvg = pingWindow.avgLatencyMs()
        val pingLoss = pingWindow.packetLossPct()

        val records = mutableListOf<CellRecordEntity>()
        val caBandsByRecord = mutableListOf<List<CellRecordCaBandEntity>>()

        for (snapshot in snapshots) {
            try {
                val caEntities = if (snapshot.caBands.isNotEmpty()) {
                    snapshot.caBands.map { ca ->
                        CellRecordCaBandEntity(
                            cellRecordId = 0,
                            bandNumber = ca.bandNumber,
                            earfcn = ca.earfcn,
                            pci = ca.pci,
                            rsrp = ca.rsrp,
                            rsrq = ca.rsrq,
                            sinr = ca.sinr,
                            rssi = ca.rssi,
                            cqi = ca.cqi,
                            timingAdvance = ca.timingAdvance
                        )
                    }
                } else emptyList()

                records.add(CellRecordEntity(
                    sessionId = sessionId,
                    timestamp = System.currentTimeMillis(),
                    latitude = 0.0,
                    longitude = 0.0,
                    altitude = 0.0,
                    accuracy = 0f,
                    relativeX = indoorUpdate.relativeX,
                    relativeY = indoorUpdate.relativeY,
                    rat = snapshot.rat,
                    networkTypeCode = snapshot.networkTypeCode,
                    fullCellIdentity = snapshot.fullCellIdentity,
                    enbOrGnbId = snapshot.enbOrGnbId,
                    lcid = snapshot.lcid,
                    cellIdBitLength = snapshot.cellIdBitLength,
                    pci = snapshot.pci,
                    tac = snapshot.tac,
                    bandNumber = snapshot.bandNumber,
                    earfcn = snapshot.earfcn,
                    bandwidthKhz = snapshot.bandwidthKhz,
                    rsrp = snapshot.rsrp,
                    rsrq = snapshot.rsrq,
                    sinr = snapshot.sinr,
                    rssi = snapshot.rssi,
                    cqi = snapshot.cqi,
                    timingAdvance = snapshot.timingAdvance,
                    mcc = snapshot.mcc,
                    mnc = snapshot.mnc,
                    subscriptionId = snapshot.subscriptionId,
                    simSlotIndex = activeSubs[snapshot.subscriptionId]?.simSlotIndex,
                    avgLatencyMs = pingAvg,
                    packetLossPct = pingLoss,
                    isLocationEstimated = false,
                    locationSource = "INDOOR_IMU",
                    anchorEnbOrGnbId = snapshot.anchorEnbOrGnbId,
                    anchorLcid = snapshot.anchorLcid,
                    anchorPci = snapshot.anchorPci,
                    anchorTac = snapshot.anchorTac,
                    anchorBandNumber = snapshot.anchorBandNumber,
                    anchorEarfcn = snapshot.anchorEarfcn,
                    anchorBandwidthKhz = snapshot.anchorBandwidthKhz,
                    anchorRsrp = snapshot.anchorRsrp,
                    anchorRsrq = snapshot.anchorRsrq,
                    anchorSinr = snapshot.anchorSinr,
                    anchorRssi = snapshot.anchorRssi,
                    anchorCqi = snapshot.anchorCqi,
                    anchorTimingAdvance = snapshot.anchorTimingAdvance
                ))
                caBandsByRecord.add(caEntities)
            } catch (e: Exception) {
                continue
            }
        }

        if (records.isNotEmpty()) {
            cellRecordRepository.insertRecordBatch(records, caBandsByRecord)
        }

        sessionRepository.incrementPointCount(sessionId)
        totalPointCount++

        synchronized(this) {
            val pathIndex = _recordedPath.size
            _recordedPath.addLast(indoorUpdate.relativeX to indoorUpdate.relativeY)
            if (originResetCount > lastOriginResetCount && pathIndex > 0) {
                _discontinuities.addLast(pathIndex - 1)
            }
            if (_recordedPath.size > MAX_PATH_SIZE) {
                _recordedPath.removeFirst()
                if (_discontinuities.isNotEmpty() && _discontinuities.first() == 0) {
                    _discontinuities.removeFirst()
                }
                val shifted = ArrayDeque<Int>(_discontinuities.size)
                for (idx in _discontinuities) {
                    shifted.addLast(idx - 1)
                }
                _discontinuities.clear()
                _discontinuities.addAll(shifted)
            }
            lastOriginResetCount = originResetCount
        }
        lastRecordedTime = System.currentTimeMillis()

        stateManager.update { it?.copy(
            pointCount = totalPointCount,
            currentLatency = pingAvg?.let { String.format(Locale.US, "%.1f", it) } ?: "---",
            recordedPath = recordedPathSnapshot,
            recordedDiscontinuities = recordedDiscontinuitiesSnapshot,
            currentRelativeX = indoorUpdate.relativeX,
            currentRelativeY = indoorUpdate.relativeY,
            currentHeading = indoorUpdate.headingRad,
            currentStepCount = indoorUpdate.stepCount,
            estimatedDriftM = indoorUpdate.estimatedDriftM
        ) }
    }

    fun updateState(sessionId: Long, isRecording: Boolean, error: String? = null, recordingMode: String = "OUTDOOR") {
        stateManager.currentState = RecordingState(
            sessionId = sessionId,
            isRecording = isRecording,
            errorMessage = error,
            recordingMode = recordingMode
        )
    }
}