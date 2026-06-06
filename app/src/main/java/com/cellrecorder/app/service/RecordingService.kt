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
import com.cellrecorder.app.data.repository.SessionRepository
import com.cellrecorder.app.data.repository.SpeedTestRecordRepository
import com.cellrecorder.app.domain.model.PingResult
import com.cellrecorder.app.domain.ping.PingEngine
import com.cellrecorder.app.domain.ping.PingSlidingWindow
import com.cellrecorder.app.domain.speedtest.SpeedTestEngine
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
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
    @Inject lateinit var locationCollector: LocationCollector
    @Inject lateinit var cellInfoCollector: CellInfoCollector
    @Inject lateinit var pingEngine: PingEngine
    @Inject lateinit var sensorFusion: SensorFusionCollector
    @Inject lateinit var stateManager: RecordingStateManager
    @Inject lateinit var notificationHelper: RecordingNotificationHelper
    @Inject lateinit var pointRecorder: PointRecorder
    @Inject lateinit var speedTestEngine: SpeedTestEngine
    @Inject lateinit var speedTestRecordRepository: SpeedTestRecordRepository

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val recordingMutex = Mutex()
    private var recordingJob: Job? = null
    private var pingJob: Job? = null
    private var speedTestJob: Job? = null
    private val pingWindow = PingSlidingWindow()
    private val gpsState = GpsStateMachine()

    private var sessionId: Long = -1L
    private var config: AppConfigEntity = AppConfigEntity()
    private var startTime: Long = 0L
    private var activeSubs: Map<Int, SubscriptionInfo> = emptyMap()
    private var defaultDataSubId: Int = -1
    private var fallbackRecordingJob: Job? = null
    private var lastLocation: LocationUpdate? = null
    private var stateUpdateJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        notificationHelper.createChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                sessionId = intent.getLongExtra(EXTRA_SESSION_ID, -1L)
                if (sessionId > 0 && recordingJob?.isActive != true) {
                    startRecording()
                }
            }
            ACTION_STOP -> {
                stopRecording()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stateUpdateJob?.cancel()
        stopRecording()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startRecording() {
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

        pointRecorder.updateState(sessionId, isRecording = true)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notificationHelper.buildNotification(
                        this, sessionId,
                        elapsedMs = 0, pointCount = 0,
                        isExtrapolating = false, hasGpsFix = false
                    ),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                )
            } else {
                startForeground(NOTIFICATION_ID, notificationHelper.buildNotification(
                    this, sessionId,
                    elapsedMs = 0, pointCount = 0,
                    isExtrapolating = false, hasGpsFix = false
                ))
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
                    locationCollector.locationFlow().collect { location ->
                        recordingMutex.withLock {
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

            fallbackRecordingJob = launch {
                while (isActive) {
                    delay(1000L)
                    val now = System.currentTimeMillis()

                    recordingMutex.withLock {
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

            pingJob = launch {
                pingEngine.pingFlow(
                    host = config.pingDestination,
                    intervalSec = config.pingIntervalMs / 1000f,
                    timeoutMs = config.pingTimeoutMs
                ).collect { result ->
                    pingWindow.add(result)
                }
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
                            uploadEnabled = config.speedTestUploadEnabled
                        )

                        if (!result.succeeded && result.errorMessage == "SKIPPED_WIFI") {
                            stateManager.update { it?.copy(speedTestStatus = "SkippedWiFi") }
                        } else if (!result.succeeded) {
                            stateManager.update { it?.copy(speedTestStatus = "Failed") }
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

                    val loc = pointRecorder.lastRecordedLocation ?: lastLocation
                    val currentStatus = when {
                        gpsState.isExtrapolating -> "EXTRAPOLATING"
                        gpsState.hasGpsFix -> "OK"
                        else -> "Searching..."
                    }
                    stateManager.update { it?.copy(
                        elapsedMs = elapsed,
                        pointCount = pointRecorder.totalPointCount,
                        gpsStatus = currentStatus,
                        isExtrapolatingGps = gpsState.isExtrapolating,
                        recordedPath = pointRecorder.recordedPathSnapshot,
                        currentLatitude = loc?.latitude ?: 0.0,
                        currentLongitude = loc?.longitude ?: 0.0,
                        currentAltitude = loc?.altitude ?: 0.0
                    ) }
                    notificationHelper.notify(this@RecordingService, notificationHelper.buildNotification(
                        this@RecordingService, sessionId,
                        elapsedMs = elapsed,
                        pointCount = pointRecorder.totalPointCount,
                        isExtrapolating = gpsState.isExtrapolating,
                        hasGpsFix = gpsState.hasGpsFix
                    ))
                }
            }
        }
    }

    private fun stopRecording() {
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
        sensorFusion.stop()
        gpsState.stopExtrapolating()
        pingWindow.reset()
        pointRecorder.reset()

        val endedSessionId = sessionId
        val primarySlot = activeSubs[defaultDataSubId]?.simSlotIndex
        activeSubs = emptyMap()
        defaultDataSubId = -1

        serviceScope.launch {
            try {
                sessionRepository.updateEndedAt(endedSessionId, System.currentTimeMillis())
                sessionRepository.updatePrimarySimSlot(endedSessionId, primarySlot)
            } catch (_: Exception) { }
        }

        if (stateManager.currentState?.isRecording != false) {
            pointRecorder.updateState(sessionId, isRecording = false)
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun movePoint(
        lat: Double, lon: Double,
        bearingDeg: Float, distanceM: Float
    ): Pair<Double, Double> {
        val R = 6371000.0
        val latRad = Math.toRadians(lat)
        val lonRad = Math.toRadians(lon)
        val bRad = Math.toRadians(bearingDeg.toDouble())
        val dR = distanceM / R
        val newLatRad = asin(
            sin(latRad) * cos(dR) + cos(latRad) * sin(dR) * cos(bRad)
        )
        val newLonRad = lonRad + atan2(
            sin(bRad) * sin(dR) * cos(latRad),
            cos(dR) - sin(latRad) * sin(newLatRad)
        )
        return Math.toDegrees(newLatRad) to Math.toDegrees(newLonRad)
    }

    private fun calculateDistance(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Float {
        val results = FloatArray(1)
        android.location.Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return results[0]
    }

    companion object {
        private const val GPS_SETTLING_DELAY_MS = 5000L
        const val CHANNEL_ID = "cell_recorder_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.cellrecorder.app.START_RECORDING"
        const val ACTION_STOP = "com.cellrecorder.app.STOP_RECORDING"
        const val EXTRA_SESSION_ID = "session_id"

        fun start(context: Context, sessionId: Long) {
            val intent = Intent(context, RecordingService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_SESSION_ID, sessionId)
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