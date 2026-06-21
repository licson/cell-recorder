package com.cellrecorder.app.service

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure haversine-based geographic helpers extracted from [RecordingService] so the
 * extrapolation and distance logic is unit-testable without instantiating the
 * Hilt-injected service.
 *
 * - [movePoint] extrapolates a lat/lon coordinate by [distanceM] along [bearingDeg]
 *   using a spherical-Earth approximation (R = 6,371,000 m).
 * - [calculateDistance] returns the great-circle distance (meters) between two
 *   coordinates via the haversine formula.
 *
 * Note: [calculateDistance] uses haversine rather than `android.location.Location.distanceBetween`
 * (Vincenty). Differences are typically <0.5% for short distances (meters to hundreds of meters).
 * If higher accuracy is required in the future, this helper can be updated to use Vincenty
 * or the Android call can be restored for production callers while the pure haversine remains
 * for testing.
 */
object GeoExtrapolation {

    private const val EARTH_RADIUS_M = 6_371_000.0

    fun movePoint(
        lat: Double, lon: Double,
        bearingDeg: Double, distanceM: Double
    ): Pair<Double, Double> {
        val latRad = Math.toRadians(lat)
        val lonRad = Math.toRadians(lon)
        val bRad = Math.toRadians(bearingDeg)
        val dR = distanceM / EARTH_RADIUS_M
        val newLatRad = kotlin.math.asin(
            sin(latRad) * cos(dR) + cos(latRad) * sin(dR) * cos(bRad)
        )
        val newLonRad = lonRad + atan2(
            sin(bRad) * sin(dR) * cos(latRad),
            cos(dR) - sin(latRad) * sin(newLatRad)
        )
        return Math.toDegrees(newLatRad) to Math.toDegrees(newLonRad)
    }

    fun calculateDistance(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_M * c
    }
}
