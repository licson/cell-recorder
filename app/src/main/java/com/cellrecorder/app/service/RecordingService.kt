package com.cellrecorder.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import androidx.core.app.NotificationCompat
import com.cellrecorder.app.R
import com.cellrecorder.app.data.local.entity.AppConfigEntity
import com.cellrecorder.app.data.local.entity.CellRecordEntity
import com.cellrecorder.app.data.repository.CellRecordRepository
import com.cellrecorder.app.data.repository.ConfigRepository
import com.cellrecorder.app.data.repository.SessionRepository
import com.cellrecorder.app.domain.model.PingResult
import com.cellrecorder.app.domain.ping.PingEngine
import com.cellrecorder.app.domain.ping.PingSlidingWindow
import com.cellrecorder.app.ui.MainActivity
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

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val recordingMutex = Mutex()
    private var recordingJob: Job? = null
    private var pingJob: Job? = null
    private val pingWindow = PingSlidingWindow()

    private var sessionId: Long = -1L
    private var config: AppConfigEntity = AppConfigEntity()
    private var lastRecordedLocation: LocationUpdate? = null
    private var lastRecordedTime: Long = 0L
    private var startTime: Long = 0L
    private var totalPointCount: Int = 0
    private var activeSubs: Map<Int, SubscriptionInfo> = emptyMap()
    private var defaultDataSubId: Int = -1
    private var hasGpsFix: Boolean = false
    private var lastKnownSpeedMps: Float = 0f
    private var lastKnownBearing: Float = 0f
    private var lastValidLocation: LocationUpdate? = null
    private var lastAccurateFixTime: Long = 0L
    private var isExtrapolating: Boolean = false
    private var gpsLostAtMs: Long = 0L
    private var gpsSettlingUntilMs: Long = 0L
    private var fallbackRecordingJob: Job? = null
    private var lastLocation: LocationUpdate? = null
    private val recordedPath = mutableListOf<Pair<Double, Double>>()
    private var stateUpdateJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                sessionId = intent.getLongExtra(EXTRA_SESSION_ID, -1L)
                if (sessionId != -1L) {
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
        startTime = System.currentTimeMillis()
        lastRecordedTime = System.currentTimeMillis()
        lastAccurateFixTime = System.currentTimeMillis()
        hasGpsFix = false
        isExtrapolating = false
        gpsSettlingUntilMs = 0L
        recordedPath.clear()

        try {
            val subManager = getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
            activeSubs = subManager?.activeSubscriptionInfoList?.associateBy { it.subscriptionId } ?: emptyMap()
            defaultDataSubId = SubscriptionManager.getDefaultDataSubscriptionId()
        } catch (e: SecurityException) {
            activeSubs = emptyMap()
            defaultDataSubId = -1
        }

        updateState(isRecording = true)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    buildNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                )
            } else {
                startForeground(NOTIFICATION_ID, buildNotification())
            }
        } catch (e: Exception) {
            updateState(isRecording = false, error = "Foreground service failed: ${e.message}")
            stopSelf()
            return
        }

        serviceScope.launch {
            try {
                config = configRepository.getConfig().first()
            } catch (e: Exception) {
                updateState(isRecording = false, error = "Config load failed: ${e.message}")
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return@launch
            }

            recordingJob = launch {
                try {
                    locationCollector.locationFlow().collect { location ->
                        recordingMutex.withLock {
                            lastKnownSpeedMps = location.speed ?: lastKnownSpeedMps
                            location.bearing?.let { lastKnownBearing = it }

                            if (isExtrapolating) {
                                if (location.accuracy > config.gpsAccuracyThresholdM) {
                                    return@withLock
                                }

                                val distanceFromLast = lastRecordedLocation?.let {
                                    calculateDistance(it.latitude, it.longitude, location.latitude, location.longitude)
                                } ?: Float.MAX_VALUE

                                val extrapolationAgeMs = System.currentTimeMillis() - gpsLostAtMs
                                if (extrapolationAgeMs > 5_000 && distanceFromLast < config.locationChangeThresholdM) {
                                    return@withLock
                                }

                                isExtrapolating = false
                                sensorFusion.stop()
                                val now = System.currentTimeMillis()
                                lastAccurateFixTime = now
                                lastValidLocation = location
                                lastLocation = location
                                gpsSettlingUntilMs = now + GPS_SETTLING_DELAY_MS
                                recordPoint(location, isEstimated = false, source = "GPS")
                                if (!hasGpsFix) hasGpsFix = true
                                return@collect
                            }

                            if (location.accuracy > config.gpsAccuracyThresholdM) return@withLock

                            lastAccurateFixTime = System.currentTimeMillis()
                            lastValidLocation = location
                            lastLocation = location
                            val elapsedSinceLast = System.currentTimeMillis() - lastRecordedTime
                            val distance = lastRecordedLocation?.let { last ->
                                calculateDistance(last.latitude, last.longitude, location.latitude, location.longitude)
                            } ?: Float.MAX_VALUE

                            val shouldRecord = distance >= config.locationChangeThresholdM ||
                                    elapsedSinceLast >= config.recordingIntervalMs

                            if (!hasGpsFix && location.accuracy < config.gpsAccuracyThresholdM) {
                                hasGpsFix = true
                            }

                            if (shouldRecord) {
                                recordPoint(location, isEstimated = false, source = "GPS")
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
                    updateState(isRecording = false, error = "Location permission revoked")
                    stopRecording()
                } catch (e: Exception) {
                    updateState(isRecording = false, error = "Recording error: ${e.message}")
                    stopRecording()
                }
            }

            fallbackRecordingJob = launch {
                while (isActive) {
                    delay(1000L)
                    val now = System.currentTimeMillis()

                    recordingMutex.withLock {
                        val timeSinceAccurateFix = now - lastAccurateFixTime

                        if (timeSinceAccurateFix > 3_000 && hasGpsFix && !isExtrapolating && now >= gpsSettlingUntilMs) {
                            isExtrapolating = true
                            gpsLostAtMs = now
                            if (sensorFusion.isAvailable) sensorFusion.start()
                        }

                        if (isExtrapolating) {
                            val extrapolationAgeSec = (now - gpsLostAtMs) / 1000f
                            if (extrapolationAgeSec > config.maxGpsLossExtrapolationSec) {
                                isExtrapolating = false
                                sensorFusion.stop()
                                return@withLock
                            }

                            if (lastValidLocation == null) return@withLock
                            val timeSinceLastRecorded = now - lastRecordedTime
                            if (timeSinceLastRecorded < config.recordingIntervalMs) return@withLock

                            val elapsedSec = (now - lastRecordedTime) / 1000f
                            val distanceM = lastKnownSpeedMps * elapsedSec
                            val headingDelta = if (sensorFusion.isAvailable) sensorFusion.headingDelta.value else 0f
                            val estimatedHeading = (lastKnownBearing + headingDelta) % 360f

                            val origin = lastRecordedLocation ?: lastValidLocation
                            val (estLat, estLon) = movePoint(
                                origin!!.latitude,
                                origin!!.longitude,
                                estimatedHeading,
                                distanceM
                            )

                            val estimatedLocation = LocationUpdate(
                                latitude = estLat,
                                longitude = estLon,
                                altitude = lastValidLocation!!.altitude,
                                accuracy = 50f + extrapolationAgeSec * 3f,
                                timestamp = now,
                                speed = lastKnownSpeedMps,
                                bearing = estimatedHeading
                            )

                            recordPoint(estimatedLocation, isEstimated = true, source = "SENSOR_FUSION")
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
                while (isActive) {
                    try {
                        val result = pingEngine.ping(config.pingDestination, config.pingTimeoutMs)
                        pingWindow.add(result)
                    } catch (_: Exception) {
                        pingWindow.add(PingResult(latencyMs = null, timestamp = System.currentTimeMillis()))
                    }
                    delay(config.pingIntervalMs)
                }
            }

            stateUpdateJob = launch {
                while (isActive) {
                    delay(1000)
                    val elapsed = System.currentTimeMillis() - startTime

                    recordingMutex.withLock {
                        val loc = lastRecordedLocation ?: lastLocation
                        val currentStatus = when {
                            isExtrapolating -> "EXTRAPOLATING"
                            hasGpsFix -> "OK"
                            else -> "Searching..."
                        }
                        stateManager.update { it?.copy(
                            elapsedMs = elapsed,
                            pointCount = totalPointCount,
                            gpsStatus = currentStatus,
                            isExtrapolatingGps = isExtrapolating,
                            recordedPath = recordedPath.toList(),
                            currentLatitude = loc?.latitude ?: 0.0,
                            currentLongitude = loc?.longitude ?: 0.0,
                            currentAltitude = loc?.altitude ?: 0.0
                        ) }
                        updateNotification()
                    }
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
        stateUpdateJob?.cancel()
        stateUpdateJob = null
        sensorFusion.stop()
        isExtrapolating = false
        pingWindow.reset()
        recordedPath.clear()

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
            updateState(isRecording = false)
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.recording_notification_channel),
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val stopIntent = PendingIntent.getService(
            this, 0, Intent(this, RecordingService::class.java).apply {
                action = ACTION_STOP
                putExtra(EXTRA_SESSION_ID, sessionId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).apply {
                putExtra(EXTRA_SESSION_ID, sessionId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val elapsed = if (stateManager.currentState?.elapsedMs != null) {
            val totalSec = stateManager.currentState!!.elapsedMs / 1000
            String.format("%02d:%02d", totalSec / 60, totalSec % 60)
        } else "00:00"
        val gps = when {
            isExtrapolating -> "GPS ! Hold phone steady"
            hasGpsFix -> "GPS OK"
            else -> "GPS ..."
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Cell Recorder")
            .setContentText("$elapsed — $totalPointCount pts — $gps")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(openIntent)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stopIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun updateLiveState(
        avgLatencyMs: Double?,
        location: LocationUpdate,
        isExtrapolatingParam: Boolean = false
    ) {
        stateManager.update { it?.copy(
            pointCount = totalPointCount,
            dataSubId = defaultDataSubId,
            currentLatency = avgLatencyMs?.let { String.format("%.1f", it) } ?: "---",
            currentLatitude = location.latitude,
            currentLongitude = location.longitude,
            currentAltitude = location.altitude,
            recordedPath = recordedPath.toList(),
            gpsStatus = if (isExtrapolatingParam) "EXTRAPOLATING" else "OK",
            isExtrapolatingGps = isExtrapolatingParam
        ) }
    }

    private fun updateState(isRecording: Boolean, error: String? = null) {
        stateManager.currentState = RecordingState(
            sessionId = sessionId,
            isRecording = isRecording,
            errorMessage = error
        )
    }

    private suspend fun recordPoint(
        location: LocationUpdate,
        isEstimated: Boolean,
        source: String
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
            cellRecordRepository.insert(record)
        }
        sessionRepository.incrementPointCount(sessionId)
        totalPointCount++

        recordedPath.add(location.latitude to location.longitude)
        lastRecordedLocation = location
        lastRecordedTime = System.currentTimeMillis()

        updateLiveState(pingAvg, location, isExtrapolatingParam = isEstimated)
        updateNotification()
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
