package com.cellrecorder.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_config")
data class AppConfigEntity(
    @PrimaryKey
    val id: Int = 1,
    val pingDestination: String = "8.8.8.8",
    val pingIntervalMs: Long = 1000,
    val pingTimeoutMs: Long = 3000,
    val recordingIntervalMs: Long = 5000,
    val locationChangeThresholdM: Float = 10f,
    val gpsAccuracyThresholdM: Float = 50f,
    val maxRecordingDurationMin: Int = 120,
    val nrGnbBitLength: Int = 24,
    val cellInfoRefreshIntervalSec: Int = 5,
    val maxGpsLossExtrapolationSec: Int = 120,
    val handoffTimeWindowMs: Long = 5000,
    val rsrpDropThresholdDbm: Int = 15,
    val rsrpDropTimeWindowMs: Long = 10000,
    val latencySpikeSigma: Double = 3.0,
    val pciFlapWindowMs: Long = 30000,
    val pciFlapCountThreshold: Int = 3,
    val coverageGapThresholdMs: Long = 30000,
    val mobilityStationaryKmh: Float = 5f,
    val mobilityWalkingKmh: Float = 15f,
    val indoorAccuracyThresholdM: Float = 30f,
    val tunnelSignalLossThresholdMs: Long = 10000
)