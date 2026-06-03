package com.cellrecorder.app.service

import android.content.Context
import android.location.Location
import android.location.LocationManager
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

private class TestLocationCollector(
    fusedLocationClient: FusedLocationProviderClient,
    context: Context,
    private val gmsAvailable: Boolean
) : LocationCollector(fusedLocationClient, context) {
    override fun isGooglePlayServicesAvailable(): Boolean = gmsAvailable
    override fun getMainLooper(): android.os.Looper = mockk(relaxed = true)
}

class LocationCollectorTest {

    private lateinit var context: Context
    private lateinit var locationManager: LocationManager
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    @BeforeEach
    fun setUp() {
        context = mockk(relaxed = true)
        locationManager = mockk(relaxed = true)
        fusedLocationClient = mockk(relaxed = true)
        every { context.getSystemService(Context.LOCATION_SERVICE) } returns locationManager
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    private fun collector(gmsAvailable: Boolean): LocationCollector =
        TestLocationCollector(fusedLocationClient, context, gmsAvailable)

    @Test
    fun `locationFlow uses fused provider when GMS is available`() = runBlocking {
        val location = mockLocation(
            lat = 37.7749, lon = -122.4194, alt = 10.0, acc = 5f,
            time = 1000L, speedVal = 1.5f, bearingVal = 90f
        )

        every {
            fusedLocationClient.requestLocationUpdates(
                any<LocationRequest>(),
                any<LocationCallback>(),
                any()
            )
        } answers {
            arg<LocationCallback>(1).onLocationResult(LocationResult.create(listOf(location)))
            mockk(relaxed = true)
        }

        val result = withTimeout(5000) { collector(gmsAvailable = true).locationFlow().first() }

        assertEquals(37.7749, result.latitude)
        assertEquals(-122.4194, result.longitude)
        assertEquals(10.0, result.altitude)
        assertEquals(5f, result.accuracy)
        assertEquals(1.5f, result.speed)
        assertEquals(90f, result.bearing)
    }

    @Test
    fun `locationFlow uses GPS provider when GMS is unavailable`() = runBlocking {
        val location = mockLocation(
            lat = 47.6062, lon = -122.3321, alt = 50.0, acc = 8f,
            time = 2000L, speedVal = 0f, bearingVal = 0f
        )

        every {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                1000L,
                0f,
                any<android.location.LocationListener>(),
                any<android.os.Looper>()
            )
        } answers {
            arg<android.location.LocationListener>(3).onLocationChanged(location)
        }

        val result = withTimeout(5000) { collector(gmsAvailable = false).locationFlow().first() }

        assertEquals(47.6062, result.latitude)
        assertEquals(-122.3321, result.longitude)
        assertEquals(50.0, result.altitude)
        assertEquals(8f, result.accuracy)
        assertEquals(0f, result.speed)
        assertEquals(0f, result.bearing)
    }

    @Test
    fun `getCurrentLocation falls back to GPS when GMS is unavailable`() = runBlocking {
        val location = mockLocation(lat = 47.6062, lon = -122.3321)
        every { locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER) } returns location

        val result = collector(gmsAvailable = false).getCurrentLocation()

        assertNotNull(result)
        assertEquals(47.6062, result!!.latitude)
        assertEquals(-122.3321, result.longitude)
    }

    @Test
    fun `getCurrentLocation returns null when both providers fail`() = runBlocking {
        every { locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER) } returns null

        val result = collector(gmsAvailable = false).getCurrentLocation()

        assertNull(result)
    }

    @Test
    fun `locationFlow cleans up fused on completion`() = runBlocking {
        val location = mockLocation(lat = 37.7749, lon = -122.4194)

        every {
            fusedLocationClient.requestLocationUpdates(
                any<LocationRequest>(),
                any<LocationCallback>(),
                any()
            )
        } answers {
            arg<LocationCallback>(1).onLocationResult(LocationResult.create(listOf(location)))
            mockk(relaxed = true)
        }

        every { fusedLocationClient.removeLocationUpdates(any<LocationCallback>()) } returns mockk(
            relaxed = true
        )

        withTimeout(5000) { collector(gmsAvailable = true).locationFlow().first() }

        verify { fusedLocationClient.removeLocationUpdates(any<LocationCallback>()) }
    }

    @Test
    fun `locationFlow cleans up GPS on completion`() = runBlocking {
        val location = mockLocation(lat = 47.6062, lon = -122.3321)

        every {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                1000L,
                0f,
                any<android.location.LocationListener>(),
                any<android.os.Looper>()
            )
        } answers {
            arg<android.location.LocationListener>(3).onLocationChanged(location)
        }

        withTimeout(5000) { collector(gmsAvailable = false).locationFlow().first() }

        verify { locationManager.removeUpdates(any<android.location.LocationListener>()) }
    }

    @Test
    fun `toUpdate maps full Location to LocationUpdate correctly`() = runBlocking {
        val location = mockLocation(
            lat = 40.7128, lon = -74.0060, alt = 15.0, acc = 3f,
            time = 5000L, speedVal = 2.0f, bearingVal = 180f
        )

        every {
            fusedLocationClient.requestLocationUpdates(
                any<LocationRequest>(),
                any<LocationCallback>(),
                any()
            )
        } answers {
            arg<LocationCallback>(1).onLocationResult(LocationResult.create(listOf(location)))
            mockk(relaxed = true)
        }

        val result = withTimeout(5000) { collector(gmsAvailable = true).locationFlow().first() }

        assertEquals(40.7128, result.latitude)
        assertEquals(-74.0060, result.longitude)
        assertEquals(15.0, result.altitude)
        assertEquals(3f, result.accuracy)
        assertEquals(5000L, result.timestamp)
        assertEquals(2.0f, result.speed)
        assertEquals(180f, result.bearing)
    }

    @Test
    fun `toUpdate handles minimal Location without optional fields`() = runBlocking {
        val location = mockk<Location>(relaxed = true)
        every { location.latitude } returns 51.5074
        every { location.longitude } returns -0.1278
        every { location.hasAltitude() } returns false
        every { location.hasAccuracy() } returns false
        every { location.time } returns 3000L
        every { location.hasSpeed() } returns false
        every { location.hasBearing() } returns false

        every {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                1000L,
                0f,
                any<android.location.LocationListener>(),
                any<android.os.Looper>()
            )
        } answers {
            arg<android.location.LocationListener>(3).onLocationChanged(location)
        }

        val result = withTimeout(5000) { collector(gmsAvailable = false).locationFlow().first() }

        assertEquals(51.5074, result.latitude)
        assertEquals(-0.1278, result.longitude)
        assertEquals(0.0, result.altitude)
        assertEquals(Float.MAX_VALUE, result.accuracy)
        assertEquals(3000L, result.timestamp)
        assertNull(result.speed)
        assertNull(result.bearing)
    }

    private fun mockLocation(
        lat: Double = 0.0,
        lon: Double = 0.0,
        alt: Double = 0.0,
        acc: Float = 0f,
        time: Long = 0L,
        speedVal: Float? = null,
        bearingVal: Float? = null
    ): Location {
        val loc = mockk<Location>(relaxed = true)
        every { loc.latitude } returns lat
        every { loc.longitude } returns lon
        every { loc.hasAltitude() } returns true
        every { loc.altitude } returns alt
        every { loc.hasAccuracy() } returns true
        every { loc.accuracy } returns acc
        every { loc.time } returns time
        val hasSpeed = speedVal != null
        every { loc.hasSpeed() } returns hasSpeed
        every { loc.speed } returns (speedVal ?: 0f)
        val hasBearing = bearingVal != null
        every { loc.hasBearing() } returns hasBearing
        every { loc.bearing } returns (bearingVal ?: 0f)
        return loc
    }
}