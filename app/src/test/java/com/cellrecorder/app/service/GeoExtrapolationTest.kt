package com.cellrecorder.app.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class GeoExtrapolationTest {

    @Nested
    inner class MovePoint {

        @Test
        fun `zero distance returns the same point`() {
            val (lat, lon) = GeoExtrapolation.movePoint(40.0, -74.0, bearingDeg = 0.0, distanceM = 0.0)
            assertEquals(40.0, lat, 1e-9)
            assertEquals(-74.0, lon, 1e-9)
        }

        @Test
        fun `north bearing moves latitude north`() {
            val (lat, lon) = GeoExtrapolation.movePoint(0.0, 0.0, bearingDeg = 0.0, distanceM = 1000.0)
            assertEquals(0.008993, lat, 1e-5, "1000m north at equator moves ~0.009° latitude")
            assertEquals(0.0, lon, 1e-9, "Longitude unchanged when bearing is due north")
        }

        @Test
        fun `east bearing moves longitude east`() {
            val (lat, lon) = GeoExtrapolation.movePoint(0.0, 0.0, bearingDeg = 90.0, distanceM = 1000.0)
            assertEquals(0.0, lat, 1e-9, "Latitude unchanged when bearing is due east at equator")
            assertEquals(0.008993, lon, 1e-5, "1000m east at equator moves ~0.009° longitude")
        }

        @Test
        fun `south bearing moves latitude south`() {
            val (lat, lon) = GeoExtrapolation.movePoint(0.0, 0.0, bearingDeg = 180.0, distanceM = 1000.0)
            assertEquals(-0.008993, lat, 1e-5)
            assertEquals(0.0, lon, 1e-9)
        }

        @Test
        fun `west bearing moves longitude west`() {
            val (lat, lon) = GeoExtrapolation.movePoint(0.0, 0.0, bearingDeg = 270.0, distanceM = 1000.0)
            assertEquals(0.0, lat, 1e-9)
            assertEquals(-0.008993, lon, 1e-5)
        }

        @Test
        fun `360 degrees bearing behaves same as 0 (north)`() {
            val (lat0, lon0) = GeoExtrapolation.movePoint(45.0, 90.0, bearingDeg = 0.0, distanceM = 1000.0)
            val (lat360, lon360) = GeoExtrapolation.movePoint(45.0, 90.0, bearingDeg = 360.0, distanceM = 1000.0)
            assertEquals(lat0, lat360, 1e-9)
            assertEquals(lon0, lon360, 1e-9)
        }

        @Test
        fun `negative bearing wraps to opposite direction`() {
            val (lat, lon) = GeoExtrapolation.movePoint(0.0, 0.0, bearingDeg = -90.0, distanceM = 1000.0)
            assertEquals(0.0, lat, 1e-9)
            assertEquals(-0.008993, lon, 1e-5, "-90 bearing should move west")
        }

        @Test
        fun `diagonal bearing moves both lat and lon`() {
            val (lat, lon) = GeoExtrapolation.movePoint(0.0, 0.0, bearingDeg = 45.0, distanceM = 1000.0)
            assertTrue(lat > 0, "Latitude should increase for NE bearing")
            assertTrue(lon > 0, "Longitude should increase for NE bearing")
        }

        @Test
        fun `long distance across equator preserves approximate correctness`() {
            val (lat, lon) = GeoExtrapolation.movePoint(0.0, 0.0, bearingDeg = 0.0, distanceM = 1_000_000.0)
            // 1000 km north from equator is about 9 degrees latitude
            assertEquals(9.0, lat, 0.5)
            assertEquals(0.0, lon, 1e-9)
        }
    }

    @Nested
    inner class CalculateDistance {

        @Test
        fun `same point returns zero distance`() {
            assertEquals(0.0, GeoExtrapolation.calculateDistance(40.0, -74.0, 40.0, -74.0), 1e-9)
        }

        @Test
        fun `1 degree latitude at equator is about 111 km`() {
            val result = GeoExtrapolation.calculateDistance(0.0, 0.0, 1.0, 0.0)
            assertEquals(111_195.0, result, 100.0)
        }

        @Test
        fun `1 degree longitude at equator is about 111 km`() {
            val result = GeoExtrapolation.calculateDistance(0.0, 0.0, 0.0, 1.0)
            assertEquals(111_195.0, result, 100.0)
        }

        @Test
        fun `antipodal points are about 20015 km apart`() {
            val result = GeoExtrapolation.calculateDistance(0.0, 0.0, 0.0, 180.0)
            assertEquals(20_015_085.0, result, 1000.0)
        }

        @Test
        fun `north pole to south pole is about 20015 km`() {
            val result = GeoExtrapolation.calculateDistance(90.0, 0.0, -90.0, 0.0)
            assertEquals(20_015_085.0, result, 1000.0)
        }

        @Test
        fun `distance is symmetric`() {
            val a = GeoExtrapolation.calculateDistance(40.7128, -74.0060, 51.5074, -0.1278)
            val b = GeoExtrapolation.calculateDistance(51.5074, -0.1278, 40.7128, -74.0060)
            assertEquals(a, b, 1e-9)
        }

        @Test
        fun `short distance at high latitude is about same as at equator`() {
            // Same lat-lon delta should give approximately same distance for haversine (spherical)
            val equator = GeoExtrapolation.calculateDistance(0.0, 0.0, 0.0, 0.001)
            val arctic = GeoExtrapolation.calculateDistance(80.0, 0.0, 80.0, 0.001)
            // Both should be ~111 meters per 0.001 degree of longitude at the equator.
            // At 80° latitude, longitude distance shrinks by cos(80°)≈0.17, so arctic distance
            // should be much smaller than equator distance for the same longitude delta.
            assertTrue(arctic < equator / 5, "Arctic longitude distance should be much smaller than equatorial")
        }
    }
}
