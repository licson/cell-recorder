package com.cellrecorder.app.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import com.cellrecorder.app.data.local.entity.AppConfigEntity
import com.cellrecorder.app.data.local.entity.SpeedTestRecordEntity
import com.cellrecorder.app.data.repository.CellRecordRepository
import com.cellrecorder.app.data.repository.ConfigRepository
import com.cellrecorder.app.data.repository.SessionMarkerRepository
import com.cellrecorder.app.data.repository.SessionRepository
import com.cellrecorder.app.data.repository.SpeedTestRecordRepository
import com.cellrecorder.app.domain.model.MarkerType
import com.cellrecorder.app.domain.model.PingResult
import com.cellrecorder.app.domain.ping.PingEngine
import com.cellrecorder.app.domain.ping.PingSlidingWindow
import com.cellrecorder.app.domain.speedtest.SpeedTestEngine
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.first
import kotlin.math.*
import javax.inject.Inject

@AndroidEntryPoint
class RecordingService : Service() {

    @Inject lateinit var sessionRepository: SessionRepository
    @Inject lateinit var cellRecordRepository: CellRecordRepository
    @Inject lateinit var configRepository: ConfigRepository
    @Inject lateinit var sessionMarkerRepository: SessionMarkerRepository
    @Inject lateinit var locationCollector: LocationCollector
    @Inject lateinit var indoorPositionCollector: IndoorPositionCollector
    @Inject lateinit var cellInfoCollector: CellInfoCollector
    @Inject lateinit var pingEngine: PingEngine
    @Inject lateinit var sensorFusion: SensorFusionCollector
    @Inject lateinit var stateManager: RecordingStateManager
    @Inject lateinit var recordingMutex: RecordingMutex
    @Inject lateinit var notificationHelper: RecordingNotificationHelper
    @Inject lateinit var pointRecorder: PointRecorder
    @Inject lateinit var speedTestEngine: SpeedTestEngine
    @Inject lateinit var speedTestRecordRepository: SpeedTestRecordRepository

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val shutdownScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val markerCount = MutableStateFlow(0)
    private var recordingJob: Job? = null
    private var pingJob: Job? = null
    private var speedTestJob: Job? = null
    private var markerCountJob: Job? = null
    private val pingWindow = PingSlidingWindow()
    private val gpsState = GpsStateMachine()

    private var sessionId: Long = -1L
    private var recordingMode: String = "OUTDOOR"
    private var config: AppConfigEntity = AppConfigEntity()
    private var startTime: Long = 0L
    private var activeSubs: Map<Int, SubscriptionInfo> = emptyMap()
    private var defaultDataSubId: Int = -1
    private var fallbackRecordingJob: Job? = null
    private var lastLocation: LocationUpdate? = null
    private var stateUpdateJob: Job? = null
    private var isStopped: Boolean = false

    override fun onCreate() {
        super.onCreate()
        notificationHelper.createChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                sessionId = intent.getLongExtra(EXTRA_SESSION_ID, -1L)
                recordingMode = intent.getStringExtra(EXTRA_RECORDING_MODE) ?: "OUTDOOR"
                if (sessionId > 0 && recordingJob?.isActive != true) {
                    startRecording()
                }
            }
            ACTION_STOP -> {
                stopRecording()
            }
            ACTION_MARK_NOTE -> {
                markNote()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stateUpdateJob?.cancel()
        stopRecording()
        serviceScope.cancel()
        shutdownScope.launch {
            delay(5000)
            shutdownScope.cancel()
        }
        super.onDestroy()
    }

    private fun startRecording() {
        isStopped = false
        recordingJob?.cancel()
        recordingJob = null
        fallbackRecordingJob?.cancel()
        fallbackRecordingJob = null
        pingJob?.cancel()
        pingJob = null
        speedTestJob?.cancel()
        speedTestJob = null
        stateUpdateJob?.cancel()
        stateUpdateJob = null
        markerCountJob?.cancel()
        markerCountJob = null

        startTime = System.currentTimeMillis()
        pointRecorder.reset()
        gpsState.reset()
        gpsState.lastAccurateFixTime = System.currentTimeMillis()

        try {
            val subManager = getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
            activeSubs = subManager?.activeSubscriptionInfoList?.associateBy { it.subscriptionId } ?: emptyMap()
            defaultDataSubId = SubscriptionManager.getDefaultDataSubscriptionId()
        } catch (e: SecurityException) {
            activeSubs = emptyMap()
            defaultDataSubId = -1
        }

        pointRecorder.updateState(sessionId, isRecording = true, recordingMode = recordingMode)

        try {
            val notif = when (recordingMode) {
                "INDOOR" -> notificationHelper.buildIndoorNotification(
                    this, sessionId,
                    elapsedMs = 0, pointCount = 0,
                    trackingConfidence = "Confident"
                )
                "TUNNEL" -> notificationHelper.buildTunnelNotification(
                    this, sessionId,
                    elapsedMs = 0, pointCount = 0, markerCount = 0
                )
                else -> notificationHelper.buildNotification(
                    this, sessionId,
                    elapsedMs = 0, pointCount = 0,
                    isExtrapolating = false, hasGpsFix = false
                )
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFICATION_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            } else {
                startForeground(NOTIFICATION_ID, notif)
            }
        } catch (e: Exception) {
            pointRecorder.updateState(sessionId, isRecording = false, error = "Foreground service failed: ${e.message}")
            stopSelf()
            return
        }

        serviceScope.launch {
            try {
                config = configRepository.getConfig().first()
            } catch (e: Exception) {
                pointRecorder.updateState(sessionId, isRecording = false, error = "Config load failed: ${e.message}")
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return@launch
            }

recordingJob = launch {
                try {
                    when (recordingMode) {
                        "INDOOR" -> {
                            indoorPositionCollector.start(stepLength = config.indoorStepLengthM)
                            if (!indoorPositionCollector.isAnyStepDetectionActive()) {
                                pointRecorder.updateState(sessionId, isRecording = false, error = "No step detection sensor available")
                                stopForeground(STOP_FOREGROUND_REMOVE)
                                stopSelf()
                                return@launch
                            }
                            indoorPositionCollector.positionUpdate.collect { position ->
                                recordingMutex.mutex.withLock {
                                    val now = System.currentTimeMillis()
                                    val elapsedSinceLast = now - pointRecorder.lastRecordedTime
                                    if (elapsedSinceLast < config.indoorRecordingIntervalMs) return@withLock
                                    pointRecorder.recordIndoorPoint(
                                        position, sessionId, config, activeSubs, pingWindow,
                                        originResetCount = indoorPositionCollector.originResetCount
                                    )
                                }
                                val elapsed = System.currentTimeMillis() - startTime
                                if (elapsed >= config.maxRecordingDurationMin * 60_000L) {
                                    stopRecording()
                                }
                            }
                        }
                        "TUNNEL" -> {
                            while (isActive) {
                                recordingMutex.mutex.withLock {
                                    pointRecorder.recordTunnelPoint(
                                        sessionId, config, activeSubs, pingWindow
                                    )
                                }
                                val elapsed = System.currentTimeMillis() - startTime
                                if (elapsed >= config.maxRecordingDurationMin * 60_000L) {
                                    stopRecording()
                                    return@launch
                                }
                                delay(config.recordingIntervalMs)
                            }
                        }
                        else -> {
                            locationCollector.locationFlow().collect { location ->
                                recordingMutex.mutex.withLock {
                                    gpsState.updateMotion(location.speed, location.bearing)
                                    lastLocation = location

                                    if (gpsState.isExtrapolating) {
                                        if (location.accuracy > config.gpsAccuracyThresholdM) {
                                            return@withLock
                                        }

                                        val distanceFromLast = pointRecorder.lastRecordedLocation?.let {
                                            calculateDistance(it.latitude, it.longitude, location.latitude, location.longitude)
                                        } ?: Float.MAX_VALUE

                                        val extrapolationAgeMs = System.currentTimeMillis() - gpsState.gpsLostAtMs
                                        if (extrapolationAgeMs > 5_000 && distanceFromLast < config.locationChangeThresholdM) {
                                            return@withLock
                                        }

                                        gpsState.stopExtrapolating()
                                        sensorFusion.stop()
                                        val now = System.currentTimeMillis()
                                        gpsState.recordAccurateFix(location, now)
                                        gpsState.setSettlingUntil(now + GPS_SETTLING_DELAY_MS)
                                        lastLocation = location
                                        pointRecorder.recordPoint(
                                            location, isEstimated = false, source = "GPS",
                                            sessionId, config, activeSubs, pingWindow
                                        )
                                        return@collect
                                    }

                                    if (location.accuracy > config.gpsAccuracyThresholdM) return@withLock

                                    gpsState.recordAccurateFix(location, System.currentTimeMillis())
                                    lastLocation = location

                                    val elapsedSinceLast = System.currentTimeMillis() - pointRecorder.lastRecordedTime
                                    val distance = pointRecorder.lastRecordedLocation?.let { last ->
                                        calculateDistance(last.latitude, last.longitude, location.latitude, location.longitude)
                                    } ?: Float.MAX_VALUE

                                    val shouldRecord = distance >= config.locationChangeThresholdM ||
                                            elapsedSinceLast >= config.recordingIntervalMs

                                    if (shouldRecord) {
                                        pointRecorder.recordPoint(
                                            location, isEstimated = false, source = "GPS",
                                            sessionId, config, activeSubs, pingWindow
                                        )
                                    }
                                }

                                val elapsed = System.currentTimeMillis() - startTime
                                if (elapsed >= config.maxRecordingDurationMin * 60_000L) {
                                    stopRecording()
                                }
                            }
                        }
                    }
                } catch (e: CancellationException) {
                    // normal stop
                } catch (e: SecurityException) {
                    pointRecorder.updateState(sessionId, isRecording = false, error = "Location permission revoked")
                    stopRecording()
                } catch (e: Exception) {
                    pointRecorder.updateState(sessionId, isRecording = false, error = "Recording error: ${e.message}")
                    stopRecording()
                }
            }

            if (recordingMode != "INDOOR") {
                fallbackRecordingJob = launch {
                    while (isActive) {
                        delay(1000L)
                        val now = System.currentTimeMillis()

                        recordingMutex.mutex.withLock {
                            if (gpsState.isFixLost(now, 3_000L)) {
                                gpsState.startExtrapolating(now)
                                if (sensorFusion.isAvailable) sensorFusion.start(
                                    bearing = gpsState.lastKnownBearing,
                                    speedMps = gpsState.lastKnownSpeedMps
                                )
                            }

                            if (gpsState.isExtrapolating) {
                                val extrapolationAgeSec = gpsState.extrapolationAgeSec(now)
                                if (extrapolationAgeSec > config.maxGpsLossExtrapolationSec) {
                                    gpsState.stopExtrapolating()
                                    sensorFusion.stop()
                                    return@withLock
                                }

                                if (gpsState.lastValidLocation == null) return@withLock
                                val timeSinceLastRecorded = now - pointRecorder.lastRecordedTime
                                if (timeSinceLastRecorded < config.recordingIntervalMs) return@withLock

                                val elapsedSec = (now - pointRecorder.lastRecordedTime) / 1000f
                                val speedAdjust = if (sensorFusion.isAvailable) sensorFusion.speedDeltaMps.value else 0f
                                val effectiveSpeed = (gpsState.lastKnownSpeedMps + speedAdjust).coerceAtLeast(0f)
                                val distanceM = effectiveSpeed * elapsedSec
                                val headingDelta = if (sensorFusion.isAvailable) sensorFusion.headingDelta.value else 0f
                                val estimatedHeading = (gpsState.lastKnownBearing + headingDelta) % 360f

                                val origin = pointRecorder.lastRecordedLocation ?: gpsState.lastValidLocation ?: return@withLock
                                val (estLat, estLon) = movePoint(
                                    origin.latitude, origin.longitude,
                                    estimatedHeading, distanceM
                                )

                                val estimatedLocation = LocationUpdate(
                                    latitude = estLat, longitude = estLon,
                                    altitude = gpsState.lastValidLocation?.altitude ?: 0.0,
                                    accuracy = gpsState.estimatedAccuracy(extrapolationAgeSec),
                                    timestamp = now, speed = effectiveSpeed,
                                    bearing = estimatedHeading
                                )

                                pointRecorder.recordPoint(
                                    estimatedLocation, isEstimated = true, source = "SENSOR_FUSION",
                                    sessionId, config, activeSubs, pingWindow
                                )
                            }
                        }

                        val elapsed = now - startTime
                        if (elapsed >= config.maxRecordingDurationMin * 60_000L) {
                            stopRecording()
                            return@launch
                        }
                    }
                }
            }

            pingJob = launch {
                pingEngine.pingFlow(
                    host = config.pingDestination,
                    intervalSec = config.pingIntervalMs / 1000f,
                    timeoutMs = config.pingTimeoutMs
                ).collect { result ->
                    pingWindow.add(result)
                }
            }

            markerCountJob = launch {
                sessionMarkerRepository.getMarkersForSession(sessionId)
                    .map { it.size }
                    .collect { markerCount.value = it }
            }

            if (config.speedTestEnabled) {
                speedTestJob = launch {
                    speedTestEngine.invalidateCache()
                    while (isActive) {
                        val testStart = System.currentTimeMillis()
                        stateManager.update { it?.copy(speedTestStatus = "Discovering") }

                        val snapshots = try {
                            cellInfoCollector.snapshots(config)
                        } catch (_: Exception) { emptyList() }
                        val dataSnapshot = snapshots.firstOrNull { s ->
                            s.subscriptionId == defaultDataSubId
                        } ?: snapshots.firstOrNull()
                        val dataSimSlot = activeSubs[defaultDataSubId]?.simSlotIndex
                            ?: dataSnapshot?.simSlotIndex

                        val result = speedTestEngine.runTest(
                            preferredServerId = config.speedTestServerId?.toIntOrNull(),
                            uploadEnabled = config.speedTestUploadEnabled,
                            onStatus = { status -> stateManager.update { it?.copy(speedTestStatus = status) } }
                        )

                        if (!result.succeeded && result.errorMessage == "SKIPPED_WIFI") {
                            stateManager.update { it?.copy(
                                speedTestStatus = "SkippedWiFi",
                                lastSpeedTestDownloadBps = null,
                                lastSpeedTestUploadBps = null
                            ) }
                        } else if (!result.succeeded) {
                            // Engine may have already invalidated for measurement failures;
                            // this covers config/selection exceptions
                            speedTestEngine.invalidateCache()
                            stateManager.update { it?.copy(
                                speedTestStatus = "Failed",
                                lastSpeedTestDownloadBps = null,
                                lastSpeedTestUploadBps = null
                            ) }
                        } else {
                            stateManager.update { it?.copy(
                                speedTestStatus = "Completed",
                                lastSpeedTestDownloadBps = result.downloadBps,
                                lastSpeedTestUploadBps = result.uploadBps
                            ) }
                        }

                        try {
                            speedTestRecordRepository.insert(SpeedTestRecordEntity(
                                sessionId = sessionId,
                                timestamp = testStart,
                                downloadBps = result.downloadBps,
                                uploadBps = result.uploadBps,
                                serverName = result.serverName,
                                serverHost = result.serverHost,
                                serverLocation = result.serverLocation,
                                serverId = result.serverId?.toLong(),
                                dataSimSlotIndex = dataSimSlot,
                                ratAtTest = dataSnapshot?.rat,
                                rsrpAtTest = dataSnapshot?.rsrp,
                                bandAtTest = dataSnapshot?.bandNumber,
                                succeeded = result.succeeded,
                                errorMessage = result.errorMessage,
                                networkType = if (result.errorMessage == "SKIPPED_WIFI") "WIFI" else "CELLULAR"
                            ))
                        } catch (_: Exception) { }

                        val elapsed = System.currentTimeMillis() - testStart
                        val delayMs = (config.speedTestIntervalMs - elapsed).coerceAtLeast(0L)
                        if (delayMs > 0) delay(delayMs)
                    }
                }
            }

            stateUpdateJob = launch {
                while (isActive) {
                    delay(1000)
                    val elapsed = System.currentTimeMillis() - startTime
                    val indoorPos = indoorPositionCollector.positionUpdate.value
                    val gpsSnap = gpsState.snapshot()
                    val count = markerCount.value

                    when (recordingMode) {
                        "INDOOR" -> {
                            val timeSinceStep = indoorPositionCollector.secondsSinceLastStep()
                            val noStepWarn = timeSinceStep >= 10

                            val drift = indoorPos.estimatedDriftM
                            val trackingConfidence = when {
                                noStepWarn -> "No steps"
                                drift < 3.0 -> "Confident"
                                drift < 10.0 -> "Degrading"
                                else -> "High drift"
                            }
                            val timeSinceOriginReset = System.currentTimeMillis() - indoorPositionCollector.originResetTimestampMs
                            stateManager.update { it?.copy(
                                elapsedMs = elapsed,
                                pointCount = pointRecorder.totalPointCount,
                                markerCount = count,
                                recordedPath = pointRecorder.recordedPathSnapshot,
                                recordedDiscontinuities = pointRecorder.recordedDiscontinuitiesSnapshot,
                                recordingMode = recordingMode,
                                currentRelativeX = indoorPos.relativeX,
                                currentRelativeY = indoorPos.relativeY,
                                currentHeading = indoorPos.headingRad,
                                currentStepCount = indoorPos.stepCount,
                                estimatedDriftM = indoorPos.estimatedDriftM,
                                timeSinceOriginResetMs = timeSinceOriginReset,
                                noStepWarning = noStepWarn
                            ) }
                            notificationHelper.notify(this@RecordingService, notificationHelper.buildIndoorNotification(
                                this@RecordingService, sessionId,
                                elapsedMs = elapsed,
                                pointCount = pointRecorder.totalPointCount,
                                trackingConfidence = trackingConfidence
                            ))
                        }
                        "TUNNEL" -> {
                            stateManager.update { it?.copy(
                                elapsedMs = elapsed,
                                pointCount = pointRecorder.totalPointCount,
                                markerCount = count,
                                recordingMode = recordingMode,
                                currentRelativeX = null,
                                currentRelativeY = null,
                                currentHeading = null,
                                currentStepCount = null,
                                estimatedDriftM = null,
                                noStepWarning = false
                            ) }
                            notificationHelper.notify(this@RecordingService, notificationHelper.buildTunnelNotification(
                                this@RecordingService, sessionId,
                                elapsedMs = elapsed,
                                pointCount = pointRecorder.totalPointCount,
                                markerCount = count
                            ))
                        }
                        else -> {
                            val loc = pointRecorder.lastRecordedLocation ?: lastLocation
                            val currentStatus = when {
                                gpsSnap.isExtrapolating -> "EXTRAPOLATING"
                                gpsSnap.hasGpsFix -> "OK"
                                else -> "Searching..."
                            }
                            stateManager.update { it?.copy(
                                elapsedMs = elapsed,
                                pointCount = pointRecorder.totalPointCount,
                                markerCount = count,
                                gpsStatus = currentStatus,
                                isExtrapolatingGps = gpsSnap.isExtrapolating,
                                recordedPath = pointRecorder.recordedPathSnapshot,
                                currentLatitude = loc?.latitude ?: 0.0,
                                currentLongitude = loc?.longitude ?: 0.0,
                                currentAltitude = loc?.altitude ?: 0.0
                            ) }
                            notificationHelper.notify(this@RecordingService, notificationHelper.buildNotification(
                                this@RecordingService, sessionId,
                                elapsedMs = elapsed,
                                pointCount = pointRecorder.totalPointCount,
                                isExtrapolating = gpsSnap.isExtrapolating,
                                hasGpsFix = gpsSnap.hasGpsFix
                            ))
                        }
                    }
                }
            }
        }
    }

    private fun stopRecording() {
        if (isStopped) return
        isStopped = true
        recordingJob?.cancel()
        recordingJob = null
        fallbackRecordingJob?.cancel()
        fallbackRecordingJob = null
        pingJob?.cancel()
        pingJob = null
        speedTestJob?.cancel()
        speedTestJob = null
        stateUpdateJob?.cancel()
        stateUpdateJob = null
        markerCountJob?.cancel()
        markerCountJob = null
        sensorFusion.stop()
        indoorPositionCollector.stop()
        gpsState.stopExtrapolating()
        pingWindow.reset()
        pointRecorder.reset()

        val endedSessionId = sessionId
        val primarySlot = activeSubs[defaultDataSubId]?.simSlotIndex
        activeSubs = emptyMap()
        defaultDataSubId = -1

        shutdownScope.launch {
            withTimeoutOrNull(5000) {
                try {
                    sessionRepository.updateEndedAt(endedSessionId, System.currentTimeMillis())
                    sessionRepository.updatePrimarySimSlot(endedSessionId, primarySlot)
                } catch (e: Exception) {
                }
            }
        }

        if (stateManager.currentState?.isRecording != false) {
            pointRecorder.updateState(sessionId, isRecording = false)
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun markNote() {
        val currentSessionId = sessionId
        if (currentSessionId <= 0) return
        serviceScope.launch {
            try {
                recordingMutex.mutex.withLock {
                    sessionMarkerRepository.insertMarkerWithAutoLabel(currentSessionId, MarkerType.NOTE)
                }
            } catch (e: Exception) {
            }
        }
    }

    private fun movePoint(
        lat: Double, lon: Double,
        bearingDeg: Float, distanceM: Float
    ): Pair<Double, Double> = GeoExtrapolation.movePoint(
        lat = lat, lon = lon,
        bearingDeg = bearingDeg.toDouble(),
        distanceM = distanceM.toDouble()
    )

    private fun calculateDistance(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Float = GeoExtrapolation.calculateDistance(lat1, lon1, lat2, lon2).toFloat()

    companion object {
        private const val GPS_SETTLING_DELAY_MS = 5000L
        const val CHANNEL_ID = "cell_recorder_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.cellrecorder.app.START_RECORDING"
        const val ACTION_STOP = "com.cellrecorder.app.STOP_RECORDING"
        const val ACTION_MARK_NOTE = "com.cellrecorder.app.MARK_NOTE"
        const val EXTRA_SESSION_ID = "session_id"
        const val EXTRA_RECORDING_MODE = "recording_mode"

        fun start(context: Context, sessionId: Long, recordingMode: String = "OUTDOOR") {
            val intent = Intent(context, RecordingService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_SESSION_ID, sessionId)
                putExtra(EXTRA_RECORDING_MODE, recordingMode)
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, RecordingService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}