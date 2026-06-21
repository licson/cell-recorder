package com.cellrecorder.app.domain.speedtest

import com.cellrecorder.app.domain.speedtest.model.SpeedTestServerInfo
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.xmlpull.v1.XmlPullParser
import kotlin.math.PI
import kotlin.math.abs

class SpeedTestServerSelectorTest {

    @Nested
    inner class HaversineKm {

        @Test
        fun `zero distance returns zero`() {
            assertEquals(0.0, SpeedTestServerSelector.haversineKm(0.0, 0.0, 0.0, 0.0), 1e-9)
            assertEquals(0.0, SpeedTestServerSelector.haversineKm(45.0, 90.0, 45.0, 90.0), 1e-9)
            assertEquals(0.0, SpeedTestServerSelector.haversineKm(-33.0, -70.0, -33.0, -70.0), 1e-9)
        }

        @Test
        fun `prime meridian distance 1 degree latitude at equator is about 111 km`() {
            val result = SpeedTestServerSelector.haversineKm(0.0, 0.0, 1.0, 0.0)
            assertEquals(111.19, result, 0.5)
        }

        @Test
        fun `one degree longitude at equator is about 111 km`() {
            val result = SpeedTestServerSelector.haversineKm(0.0, 0.0, 0.0, 1.0)
            assertEquals(111.19, result, 0.5)
        }

        @Test
        fun `one degree longitude at 60 degrees latitude is about 55 km (cos 60)`() {
            val result = SpeedTestServerSelector.haversineKm(60.0, 0.0, 60.0, 1.0)
            assertEquals(55.59, result, 0.5)
        }

        @Test
        fun `antipodal points are about 20015 km apart (half earth circumference)`() {
            val result = SpeedTestServerSelector.haversineKm(0.0, 0.0, 0.0, 180.0)
            assertEquals(20015.09, result, 5.0)
        }

        @Test
        fun `north pole to south pole is about 20015 km`() {
            val result = SpeedTestServerSelector.haversineKm(90.0, 0.0, -90.0, 0.0)
            assertEquals(20015.09, result, 5.0)
        }

        @Test
        fun `symmetric — distance from A to B equals distance from B to A`() {
            val a = SpeedTestServerSelector.haversineKm(40.7128, -74.0060, 51.5074, -0.1278)
            val b = SpeedTestServerSelector.haversineKm(51.5074, -0.1278, 40.7128, -74.0060)
            assertEquals(a, b, 1e-9)
        }

        @Test
        fun `London to New York is approximately 5570 km`() {
            val result = SpeedTestServerSelector.haversineKm(51.5074, -0.1278, 40.7128, -74.0060)
            assertEquals(5570.0, result, 50.0)
        }

        @Test
        fun `negative latitudes work (southern hemisphere)`() {
            val result = SpeedTestServerSelector.haversineKm(-33.8688, 151.2093, -41.2865, 174.7762)
            assertEquals(2222.0, result, 50.0)
        }

        @Test
        fun `360 degree longitude wraps to same as 0`() {
            val result0 = SpeedTestServerSelector.haversineKm(0.0, 0.0, 0.0, 90.0)
            val result360 = SpeedTestServerSelector.haversineKm(0.0, 0.0, 0.0, -270.0)
            assertEquals(result0, result360, 1e-6)
        }
    }

    @Nested
    inner class ParseServerElement {

        private fun parserWithAttributes(attrs: Map<String, String?>): XmlPullParser {
            val parser = mockk<XmlPullParser>()
            attrs.forEach { (name, value) ->
                every { parser.getAttributeValue(null, name) } returns value
            }
            every { parser.next() } returns XmlPullParser.END_TAG
            return parser
        }

        @Test
        fun `valid server element parses to SpeedTestServerInfo`() {
            val parser = parserWithAttributes(
                mapOf(
                    "id" to "1234",
                    "url" to "http://server.example.com/speedtest/upload.php",
                    "lat" to "40.7128",
                    "lon" to "-74.0060",
                    "name" to "New York",
                    "sponsor" to "Verizon",
                    "host" to "server.example.com:8080"
                )
            )

            val server = SpeedTestServerSelector.parseServerElement(parser)
            assertEquals(1234, server?.id)
            assertEquals("http://server.example.com/speedtest/upload.php", server?.url)
            assertEquals(40.7128, server?.lat ?: 0.0)
            assertEquals(-74.0060, server?.lon ?: 0.0)
            assertEquals("New York", server?.name)
            assertEquals("Verizon", server?.sponsor)
            assertEquals("server.example.com:8080", server?.host)
        }

        @Test
        fun `missing id attribute returns null`() {
            val parser = parserWithAttributes(
                mapOf(
                    "id" to null,
                    "url" to "http://example.com/upload.php",
                    "lat" to "40.0",
                    "lon" to "-74.0"
                )
            )
            assertNull(SpeedTestServerSelector.parseServerElement(parser))
        }

        @Test
        fun `non-integer id returns null`() {
            val parser = parserWithAttributes(
                mapOf(
                    "id" to "abc",
                    "url" to "http://example.com/upload.php",
                    "lat" to "40.0",
                    "lon" to "-74.0"
                )
            )
            assertNull(SpeedTestServerSelector.parseServerElement(parser))
        }

        @Test
        fun `missing url returns null`() {
            val parser = parserWithAttributes(
                mapOf(
                    "id" to "1234",
                    "url" to null,
                    "lat" to "40.0",
                    "lon" to "-74.0"
                )
            )
            assertNull(SpeedTestServerSelector.parseServerElement(parser))
        }

        @Test
        fun `missing lat returns null`() {
            val parser = parserWithAttributes(
                mapOf(
                    "id" to "1234",
                    "url" to "http://example.com/upload.php",
                    "lat" to null,
                    "lon" to "-74.0"
                )
            )
            assertNull(SpeedTestServerSelector.parseServerElement(parser))
        }

        @Test
        fun `missing lon returns null`() {
            val parser = parserWithAttributes(
                mapOf(
                    "id" to "1234",
                    "url" to "http://example.com/upload.php",
                    "lat" to "40.0",
                    "lon" to null
                )
            )
            assertNull(SpeedTestServerSelector.parseServerElement(parser))
        }

        @Test
        fun `malformed latitude returns null`() {
            val parser = parserWithAttributes(
                mapOf(
                    "id" to "1234",
                    "url" to "http://example.com/upload.php",
                    "lat" to "not-a-number",
                    "lon" to "-74.0"
                )
            )
            assertNull(SpeedTestServerSelector.parseServerElement(parser))
        }

        @Test
        fun `malformed longitude returns null`() {
            val parser = parserWithAttributes(
                mapOf(
                    "id" to "1234",
                    "url" to "http://example.com/upload.php",
                    "lat" to "40.0",
                    "lon" to "NaN-but-actually-not"
                )
            )
            assertNull(SpeedTestServerSelector.parseServerElement(parser))
        }

        @Test
        fun `missing name sponsor and host default to empty strings`() {
            val parser = parserWithAttributes(
                mapOf(
                    "id" to "1234",
                    "url" to "http://example.com/upload.php",
                    "lat" to "40.0",
                    "lon" to "-74.0",
                    "name" to null,
                    "sponsor" to null,
                    "host" to null
                )
            )

            val server = SpeedTestServerSelector.parseServerElement(parser)
            assertEquals("", server?.name)
            assertEquals("", server?.sponsor)
            assertEquals("", server?.host)
        }

        @Test
        fun `parser throwing returns null via catch block`() {
            val parser = mockk<XmlPullParser>()
            every { parser.getAttributeValue(null, any()) } throws RuntimeException("boom")
            assertNull(SpeedTestServerSelector.parseServerElement(parser))
        }

        @Test
        fun `parser next throwing after attributes returns null`() {
            val parser = mockk<XmlPullParser>()
            every { parser.getAttributeValue(null, "id") } returns "1234"
            every { parser.getAttributeValue(null, "url") } returns "http://example.com/upload.php"
            every { parser.getAttributeValue(null, "lat") } returns "40.0"
            every { parser.getAttributeValue(null, "lon") } returns "-74.0"
            every { parser.getAttributeValue(null, "name") } returns "Test"
            every { parser.getAttributeValue(null, "sponsor") } returns "Sponsor"
            every { parser.getAttributeValue(null, "host") } returns "host.example.com"
            every { parser.next() } throws RuntimeException("next failed")
            assertNull(SpeedTestServerSelector.parseServerElement(parser))
        }
    }
}
