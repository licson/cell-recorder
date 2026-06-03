package com.cellrecorder.app.service

import android.content.Context
import android.telephony.SubscriptionInfo
import com.cellrecorder.app.data.local.entity.AppConfigEntity
import com.cellrecorder.app.data.local.entity.CellRecordCaBandEntity
import com.cellrecorder.app.data.local.entity.CellRecordEntity
import com.cellrecorder.app.data.repository.CellRecordRepository
import com.cellrecorder.app.data.repository.SessionRepository
import com.cellrecorder.app.domain.ping.PingSlidingWindow
import javax.inject.Inject

private const val MAX_PATH_SIZE = 2000

class PointRecorder @Inject constructor(
    private val cellRecordRepository: CellRecordRepository,
    private val sessionRepository: SessionRepository,
    private val cellInfoCollector: CellInfoCollector,
    private val stateManager: RecordingStateManager
) {
    var totalPointCount: Int = 0
        private set
    var lastRecordedLocation: LocationUpdate? = null
        internal set
    var lastRecordedTime: Long = 0L
        internal set

    private val _recordedPath = mutableListOf<Pair<Double, Double>>()
    val recordedPathSnapshot: List<Pair<Double, Double>> get() = _recordedPath.toList()

    fun reset() {
        totalPointCount = 0
        lastRecordedLocation = null
        lastRecordedTime = 0L
        _recordedPath.clear()
    }

    suspend fun recordPoint(
        location: LocationUpdate,
        isEstimated: Boolean,
        source: String,
        sessionId: Long,
        config: AppConfigEntity,
        activeSubs: Map<Int, SubscriptionInfo>,
        pingWindow: PingSlidingWindow,
        notificationHelper: RecordingNotificationHelper,
        notificationContext: Context
    ) {
        val snapshots = cellInfoCollector.snapshots(config)
        val pingAvg = pingWindow.avgLatencyMs()
        val pingLoss = pingWindow.packetLossPct()

        for (snapshot in snapshots) {
            val record = CellRecordEntity(
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
                locationSource = source
            )
            val recordId = cellRecordRepository.insert(record)
            if (snapshot.caBands.isNotEmpty()) {
                val caEntities = snapshot.caBands.map { ca ->
                    CellRecordCaBandEntity(
                        cellRecordId = recordId,
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
                cellRecordRepository.insertCaBands(caEntities)
            }
        }
        sessionRepository.incrementPointCount(sessionId)
        totalPointCount++

        _recordedPath.add(location.latitude to location.longitude)
        if (_recordedPath.size > MAX_PATH_SIZE) {
            _recordedPath.removeAt(0)
        }
        lastRecordedLocation = location
        lastRecordedTime = System.currentTimeMillis()

        updateLiveState(pingAvg, location, isExtrapolatingParam = isEstimated)
        notificationHelper.notify(notificationContext, notificationHelper.buildNotification(
            context = notificationContext,
            sessionId = sessionId,
            elapsedMs = lastRecordedTime,
            pointCount = totalPointCount,
            isExtrapolating = isEstimated,
            hasGpsFix = true
        ))
    }

    fun updateLiveState(
        avgLatencyMs: Double?,
        location: LocationUpdate,
        isExtrapolatingParam: Boolean = false
    ) {
        stateManager.update { it?.copy(
            pointCount = totalPointCount,
            currentLatency = avgLatencyMs?.let { String.format("%.1f", it) } ?: "---",
            currentLatitude = location.latitude,
            currentLongitude = location.longitude,
            currentAltitude = location.altitude,
            recordedPath = _recordedPath.toList(),
            gpsStatus = if (isExtrapolatingParam) "EXTRAPOLATING" else "OK",
            isExtrapolatingGps = isExtrapolatingParam
        ) }
    }

    fun updateState(sessionId: Long, isRecording: Boolean, error: String? = null) {
        stateManager.currentState = RecordingState(
            sessionId = sessionId,
            isRecording = isRecording,
            errorMessage = error
        )
    }
}