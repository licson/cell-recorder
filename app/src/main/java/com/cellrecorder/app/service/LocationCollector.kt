package com.cellrecorder.app.service

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import dagger.hilt.android.qualifiers.ApplicationContext
import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

data class LocationUpdate(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val accuracy: Float,
    val timestamp: Long,
    val speed: Float? = null,
    val bearing: Float? = null
)

@SuppressLint("MissingPermission")
@Singleton
open class LocationCollector @Inject constructor(
    private val fusedLocationClient: FusedLocationProviderClient,
    @ApplicationContext private val context: Context,
    private val callbackHandler: CallbackHandlerThread
) {
    private val locationManager: LocationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    @VisibleForTesting
    @Volatile
    internal var locationFlowFailure: Throwable? = null

    private val locationRequest: LocationRequest = LocationRequest.Builder(1000L)
        .setMinUpdateIntervalMillis(1000L)
        .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
        .build()

    fun locationFlow(): Flow<LocationUpdate> = callbackFlow {
        locationFlowFailure?.let { throw it }
        if (isGooglePlayServicesAvailable()) {
            val callback = object : com.google.android.gms.location.LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    result.lastLocation?.let { location ->
                        trySend(location.toUpdate())
                    }
                }
            }
            fusedLocationClient.requestLocationUpdates(locationRequest, callback, getMainLooper())
            awaitClose {
                fusedLocationClient.removeLocationUpdates(callback)
            }
        } else {
            val listener = object : android.location.LocationListener {
                override fun onLocationChanged(location: Location) {
                    trySend(location.toUpdate())
                }
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
            }
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                1000L,
                0f,
                listener,
                getMainLooper()
            )
            awaitClose {
                locationManager.removeUpdates(listener)
            }
        }
    }

    private fun Location.toUpdate() = LocationUpdate(
        latitude = latitude,
        longitude = longitude,
        altitude = if (hasAltitude()) altitude else 0.0,
        accuracy = if (hasAccuracy()) accuracy else Float.MAX_VALUE,
        timestamp = time,
        speed = if (hasSpeed()) speed else null,
        bearing = if (hasBearing()) bearing else null
    )

    suspend fun getCurrentLocation(): LocationUpdate? {
        if (isGooglePlayServicesAvailable()) {
            try {
                return fusedLocationClient.lastLocation.await()?.toUpdate()
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { }
        }
        return try {
            locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.toUpdate()
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) {
            null
        }
    }

    protected open fun getMainLooper(): Looper = callbackHandler.looper

    internal open fun isGooglePlayServicesAvailable(): Boolean {
        return try {
            GoogleApiAvailability.getInstance()
                .isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) {
            false
        }
    }
}