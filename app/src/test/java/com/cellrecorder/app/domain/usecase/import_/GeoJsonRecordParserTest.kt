package com.cellrecorder.app.domain.usecase.import_

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class GeoJsonRecordParserTest {

    private val parser = GeoJsonRecordParser()

    /**
     * Builds a properties JSON object string from a map of key-value pairs.
     * Numeric values are emitted unquoted; String values are quoted.
     */
    private fun props(vararg pairs: Pair<String, Any?>): String {
        return pairs.joinToString(separator = ", ", prefix = "", postfix = "") { (k, v) ->
            when (v) {
                null -> "\"$k\": null"
                is Number -> "\"$k\": $v"
                is Boolean -> "\"$k\": $v"
                is String -> "\"$k\": \"$v\""
                is List<*> -> "\"$k\": [" + v.joinToString(separator = ", ") { el ->
                    when (el) {
                        is Number -> el.toString()
                        is String -> "\"$el\""
                        is Map<*, *> -> {
                            @Suppress("UNCHECKED_CAST")
                            val mapPairs = el.entries.map { e -> e.key.toString() to e.value }.toTypedArray<Pair<String, Any?>>()
                            "{" + props(*mapPairs) + "}"
                        }
                        else -> el.toString()
                    }
                } + "]"
                is Map<*, *> -> {
                    @Suppress("UNCHECKED_CAST")
                    val mapPairs = v.entries.map { e -> e.key.toString() to e.value }.toTypedArray<Pair<String, Any?>>()
                    "\"$k\": {" + props(*mapPairs) + "}"
                }
                else -> "\"$k\": \"$v\""
            }
        }
    }

    private fun feature(
        coordinates: String = "[0.0, 0.0, 0.0]",
        properties: String = props("timestamp" to 1000L, "rat" to "4G_LTE"),
        type: String = "Point",
        includeGeometry: Boolean = true,
        includeProperties: Boolean = true,
        includeCoordinates: Boolean = true
    ): String {
        val geom = if (includeGeometry) {
            val coordPart = if (includeCoordinates) ", \"coordinates\": $coordinates" else ""
            ", \"geometry\": {\"type\": \"$type\"$coordPart}"
        } else ""
        val propsPart = if (includeProperties) ", \"properties\": {$properties}" else ""
        return "{\"type\": \"Feature\"$geom$propsPart}"
    }

    private fun featureCollection(features: String): String =
        "{\"type\": \"FeatureCollection\", \"features\": [$features]}"

    @Nested
    inner class InvalidRoot {

        @Test
        fun `invalid JSON returns one error`() {
            val result = parser.parse("not valid json", sessionId = 1L)
            assertTrue(result.records.isEmpty())
            assertEquals(1, result.errors.size)
            assertEquals(0, result.errors[0].line)
            assertTrue(result.errors[0].message.contains("Invalid GeoJSON"))
        }

        @Test
        fun `non-FeatureCollection root returns error`() {
            val result = parser.parse("""{"type": "Point", "coordinates": [0, 0]}""", sessionId = 1L)
            assertTrue(result.records.isEmpty())
            assertEquals(1, result.errors.size)
            assertTrue(result.errors[0].message.contains("Expected a FeatureCollection"))
        }

        @Test
        fun `missing features array returns error`() {
            val result = parser.parse("""{"type": "FeatureCollection"}""", sessionId = 1L)
            assertTrue(result.records.isEmpty())
            assertEquals(1, result.errors.size)
            assertTrue(result.errors[0].message.contains("Missing features"))
        }

        @Test
        fun `empty features array returns empty result`() {
            val result = parser.parse(featureCollection(""), sessionId = 1L)
            assertTrue(result.records.isEmpty())
            assertTrue(result.errors.isEmpty())
        }
    }

    @Nested
    inner class GeometryValidation {

        @Test
        fun `feature without geometry is skipped with error`() {
            val fc = featureCollection(feature(includeGeometry = false))
            val result = parser.parse(fc, sessionId = 1L)
            assertTrue(result.records.isEmpty())
            assertEquals(1, result.errors.size)
            assertEquals(1, result.errors[0].line)
            assertTrue(result.errors[0].message.contains("no geometry"))
        }

        @Test
        fun `non-Point geometry is skipped with error`() {
            val fc = featureCollection(feature(type = "LineString", coordinates = "[[0,0],[1,1]]"))
            val result = parser.parse(fc, sessionId = 1L)
            assertTrue(result.records.isEmpty())
            assertEquals(1, result.errors.size)
            assertTrue(result.errors[0].message.contains("not a Point"))
        }

        @Test
        fun `missing coordinates is skipped with error`() {
            val fc = featureCollection(feature(includeCoordinates = false))
            val result = parser.parse(fc, sessionId = 1L)
            assertTrue(result.records.isEmpty())
            assertEquals(1, result.errors.size)
            assertTrue(result.errors[0].message.contains("no coordinates"))
        }

        @Test
        fun `invalid latitude in coordinates is skipped with error`() {
            val fc = featureCollection(feature(coordinates = "[\"invalid\", 0.0]"))
            val result = parser.parse(fc, sessionId = 1L)
            assertTrue(result.records.isEmpty())
            assertEquals(1, result.errors.size)
            assertTrue(result.errors[0].message.contains("invalid coordinates"))
        }

        @Test
        fun `invalid longitude in coordinates is skipped with error`() {
            val fc = featureCollection(feature(coordinates = "[0.0, \"invalid\"]"))
            val result = parser.parse(fc, sessionId = 1L)
            assertTrue(result.records.isEmpty())
            assertEquals(1, result.errors.size)
            assertTrue(result.errors[0].message.contains("invalid coordinates"))
        }
    }

    @Nested
    inner class TimestampValidation {

        @Test
        fun `missing timestamp is skipped with error`() {
            val fc = featureCollection(feature(properties = props("rat" to "4G_LTE")))
            val result = parser.parse(fc, sessionId = 1L)
            assertTrue(result.records.isEmpty())
            assertEquals(1, result.errors.size)
            assertEquals(1, result.errors[0].line)
            assertTrue(result.errors[0].message.contains("missing timestamp"))
        }

        @Test
        fun `non-numeric timestamp is skipped with error`() {
            val fc = featureCollection(feature(properties = props("timestamp" to "not-a-number")))
            val result = parser.parse(fc, sessionId = 1L)
            assertTrue(result.records.isEmpty())
            assertEquals(1, result.errors.size)
        }
    }

    @Nested
    inner class DualKeySupport {

        @Test
        fun `enbGnbId camelCase populates enbOrGnbId`() {
            val fc = featureCollection(feature(properties = props("timestamp" to 1000L, "enbGnbId" to 555L)))
            val result = parser.parse(fc, sessionId = 1L)
            assertEquals(1, result.records.size)
            assertEquals(555L, result.records[0].enbOrGnbId)
        }

        @Test
        fun `enb_gnb_id snake_case populates enbOrGnbId when camelCase is absent`() {
            val fc = featureCollection(feature(properties = props("timestamp" to 1000L, "enb_gnb_id" to 777L)))
            val result = parser.parse(fc, sessionId = 1L)
            assertEquals(1, result.records.size)
            assertEquals(777L, result.records[0].enbOrGnbId)
        }

        @Test
        fun `camelCase takes precedence over snake_case for enb id`() {
            val fc = featureCollection(feature(properties = props("timestamp" to 1000L, "enbGnbId" to 555L, "enb_gnb_id" to 777L)))
            val result = parser.parse(fc, sessionId = 1L)
            assertEquals(555L, result.records[0].enbOrGnbId)
        }

        @Test
        fun `anchorEnbGnbId camelCase populates anchorEnbOrGnbId`() {
            val fc = featureCollection(feature(properties = props("timestamp" to 1000L, "anchorEnbGnbId" to 999L)))
            val result = parser.parse(fc, sessionId = 1L)
            assertEquals(999L, result.records[0].anchorEnbOrGnbId)
        }

        @Test
        fun `anchor_enb_gnb_id snake_case populates anchorEnbOrGnbId when camelCase is absent`() {
            val fc = featureCollection(feature(properties = props("timestamp" to 1000L, "anchor_enb_gnb_id" to 888L)))
            val result = parser.parse(fc, sessionId = 1L)
            assertEquals(888L, result.records[0].anchorEnbOrGnbId)
        }

        @Test
        fun `anchorBand camelCase populates anchorBandNumber`() {
            val fc = featureCollection(feature(properties = props("timestamp" to 1000L, "anchorBand" to 3)))
            val result = parser.parse(fc, sessionId = 1L)
            assertEquals(3, result.records[0].anchorBandNumber)
        }

        @Test
        fun `anchor_band snake_case populates anchorBandNumber when camelCase is absent`() {
            val fc = featureCollection(feature(properties = props("timestamp" to 1000L, "anchor_band" to 7)))
            val result = parser.parse(fc, sessionId = 1L)
            assertEquals(7, result.records[0].anchorBandNumber)
        }

        @Test
        fun `anchorBandwidth camelCase populates anchorBandwidthKhz`() {
            val fc = featureCollection(feature(properties = props("timestamp" to 1000L, "anchorBandwidth" to 20000)))
            val result = parser.parse(fc, sessionId = 1L)
            assertEquals(20000, result.records[0].anchorBandwidthKhz)
        }

        @Test
        fun `anchor_bandwidth snake_case populates anchorBandwidthKhz when camelCase is absent`() {
            val fc = featureCollection(feature(properties = props("timestamp" to 1000L, "anchor_bandwidth" to 15000)))
            val result = parser.parse(fc, sessionId = 1L)
            assertEquals(15000, result.records[0].anchorBandwidthKhz)
        }

        @Test
        fun `avgLatencyMs camelCase populates avgLatencyMs`() {
            val fc = featureCollection(feature(properties = props("timestamp" to 1000L, "avgLatencyMs" to 12.5)))
            val result = parser.parse(fc, sessionId = 1L)
            assertEquals(12.5, result.records[0].avgLatencyMs ?: 0.0, 1e-9)
        }

        @Test
        fun `avg_latency_ms snake_case populates avgLatencyMs when camelCase is absent`() {
            val fc = featureCollection(feature(properties = props("timestamp" to 1000L, "avg_latency_ms" to 9.5)))
            val result = parser.parse(fc, sessionId = 1L)
            assertEquals(9.5, result.records[0].avgLatencyMs ?: 0.0, 1e-9)
        }

        @Test
        fun `packetLossPct camelCase populates packetLossPct`() {
            val fc = featureCollection(feature(properties = props("timestamp" to 1000L, "packetLossPct" to 1.5)))
            val result = parser.parse(fc, sessionId = 1L)
            assertEquals(1.5, result.records[0].packetLossPct ?: 0.0, 1e-9)
        }

        @Test
        fun `packet_loss_pct snake_case populates packetLossPct when camelCase is absent`() {
            val fc = featureCollection(feature(properties = props("timestamp" to 1000L, "packet_loss_pct" to 2.0)))
            val result = parser.parse(fc, sessionId = 1L)
            assertEquals(2.0, result.records[0].packetLossPct ?: 0.0, 1e-9)
        }
    }

    @Nested
    inner class FieldMapping {

        @Test
        fun `minimal valid feature with all primary fields maps correctly`() {
            val fc = featureCollection(feature(
                coordinates = "[-74.0, 40.0, 10.0]",
                properties = props(
                    "timestamp" to 1000L, "accuracy" to 5.0, "relativeX" to 1.5, "relativeY" to 2.5,
                    "subscriptionId" to 123, "simSlotIndex" to 0, "rat" to "4G_LTE", "pci" to 42,
                    "rsrp" to -85, "rsrq" to -90, "sinr" to 8, "enbGnbId" to 555L, "lcid" to 99,
                    "mcc" to "310", "mnc" to "260", "band" to 3, "earfcn" to 1800, "tac" to 1234
                )
            ))
            val result = parser.parse(fc, sessionId = 7L)
            assertEquals(1, result.records.size)
            assertEquals(0, result.errors.size)
            val r = result.records[0]
            assertEquals(7L, r.sessionId)
            assertEquals(1000L, r.timestamp)
            assertEquals(40.0, r.latitude, 1e-9)
            assertEquals(-74.0, r.longitude, 1e-9)
            assertEquals(10.0, r.altitude, 1e-9)
            assertEquals(5.0f, r.accuracy, 1e-9f)
            assertEquals(1.5, r.relativeX ?: 0.0, 1e-9)
            assertEquals(2.5, r.relativeY ?: 0.0, 1e-9)
            assertEquals(123, r.subscriptionId)
            assertEquals(0, r.simSlotIndex)
            assertEquals("4G_LTE", r.rat)
            assertEquals(42, r.pci)
            assertEquals(-85, r.rsrp)
            assertEquals(-90, r.rsrq)
            assertEquals(8, r.sinr)
            assertEquals(555L, r.enbOrGnbId)
            assertEquals(99, r.lcid)
            assertEquals("310", r.mcc)
            assertEquals("260", r.mnc)
            assertEquals(3, r.bandNumber)
            assertEquals(1800, r.earfcn)
            assertEquals(1234, r.tac)
        }

        @Test
        fun `missing optional fields default to null or sensible defaults`() {
            val fc = featureCollection(feature(properties = props("timestamp" to 1000L)))
            val result = parser.parse(fc, sessionId = 1L)
            assertEquals(1, result.records.size)
            val r = result.records[0]
            assertEquals(0.0, r.altitude, 1e-9)
            assertEquals(0f, r.accuracy, 1e-9f)
            assertEquals("UNKNOWN", r.rat)
            assertNull(r.subscriptionId)
            assertNull(r.simSlotIndex)
            assertNull(r.pci)
            assertNull(r.rsrp)
            assertNull(r.enbOrGnbId)
            assertNull(r.lcid)
            assertNull(r.relativeX)
            assertNull(r.relativeY)
            assertNull(r.mcc)
            assertNull(r.mnc)
            assertNull(r.bandNumber)
        }
    }

    @Nested
    inner class CaBandsParsing {

        @Test
        fun `absent caBands in properties yields empty CA band list`() {
            val fc = featureCollection(feature(properties = props("timestamp" to 1000L)))
            val result = parser.parse(fc, sessionId = 1L)
            assertEquals(1, result.records.size)
            assertEquals(1, result.caBands.size)
            assertEquals(0, result.caBands[0].size)
        }

        @Test
        fun `valid caBands array parses to CellRecordCaBandEntity list`() {
            val properties = props(
                "timestamp" to 1000L,
                "caBands" to listOf(
                    mapOf("band" to 3, "earfcn" to 1800, "pci" to 42, "rsrp" to -85, "rsrq" to -90, "sinr" to 8, "rssi" to -65, "cqi" to 7, "timingAdvance" to 1)
                )
            )
            val fc = featureCollection(feature(properties = properties))
            val result = parser.parse(fc, sessionId = 1L)
            assertEquals(1, result.records.size)
            assertEquals(1, result.caBands.size)
            assertEquals(1, result.caBands[0].size)
            val ca = result.caBands[0][0]
            assertEquals(3, ca.bandNumber)
            assertEquals(1800, ca.earfcn)
            assertEquals(42, ca.pci)
            assertEquals(-85, ca.rsrp)
            assertEquals(-90, ca.rsrq)
            assertEquals(8, ca.sinr)
            assertEquals(-65, ca.rssi)
            assertEquals(7, ca.cqi)
            assertEquals(1, ca.timingAdvance)
        }

        @Test
        fun `multiple CA bands parse correctly`() {
            val props = props(
                "timestamp" to 1000L,
                "caBands" to listOf(
                    mapOf("band" to 3, "pci" to 42),
                    mapOf("band" to 7, "pci" to 50)
                )
            )
            val fc = featureCollection(feature(properties = props))
            val result = parser.parse(fc, sessionId = 1L)
            assertEquals(1, result.records.size)
            assertEquals(2, result.caBands[0].size)
            assertEquals(3, result.caBands[0][0].bandNumber)
            assertEquals(42, result.caBands[0][0].pci)
            assertEquals(7, result.caBands[0][1].bandNumber)
            assertEquals(50, result.caBands[0][1].pci)
        }

        @Test
        fun `missing keys in CA band JSON produce null fields`() {
            val properties = props(
                "timestamp" to 1000L,
                "caBands" to listOf(mapOf("band" to 3))
            )
            val fc = featureCollection(feature(properties = properties))
            val result = parser.parse(fc, sessionId = 1L)
            assertEquals(1, result.records.size)
            val ca = result.caBands[0]
            assertEquals(1, ca.size)
            assertEquals(3, ca[0].bandNumber)
            assertNull(ca[0].earfcn)
            assertNull(ca[0].pci)
            assertNull(ca[0].rsrp)
        }
    }

    @Nested
    inner class MarkerParsing {

        @Test
        fun `marker feature is parsed into markers list`() {
            val markerProps = props("timestamp" to 2000L, "markerType" to "NOTE", "seq" to 1, "label" to "drop")
            val fc = featureCollection(feature(properties = markerProps))
            val result = parser.parse(fc, sessionId = 1L)
            assertEquals(0, result.records.size)
            assertEquals(1, result.markers.size)
            val m = result.markers[0]
            assertEquals(1L, m.sessionId)
            assertEquals(2000L, m.timestamp)
            assertEquals("NOTE", m.type)
            assertEquals(1, m.seq)
            assertEquals("drop", m.label)
        }

        @Test
        fun `marker feature without label has null label`() {
            val markerProps = props("timestamp" to 2000L, "markerType" to "WAYPOINT", "seq" to 2)
            val fc = featureCollection(feature(properties = markerProps))
            val result = parser.parse(fc, sessionId = 1L)
            assertEquals(1, result.markers.size)
            assertNull(result.markers[0].label)
        }

        @Test
        fun `marker feature missing timestamp is skipped`() {
            val markerProps = props("markerType" to "NOTE", "seq" to 1)
            val fc = featureCollection(feature(properties = markerProps))
            val result = parser.parse(fc, sessionId = 1L)
            assertEquals(0, result.markers.size)
            assertEquals(1, result.errors.size)
        }

        @Test
        fun `mixed record and marker features both parse`() {
            val recordFeature = feature(properties = props("timestamp" to 1000L, "rat" to "4G"))
            val markerProps = props("timestamp" to 2000L, "markerType" to "STOP", "seq" to 1)
            val markerFeature = feature(properties = markerProps)
            val fc = featureCollection("$recordFeature, $markerFeature")
            val result = parser.parse(fc, sessionId = 1L)
            assertEquals(1, result.records.size)
            assertEquals(1, result.markers.size)
            assertEquals(0, result.errors.size)
        }
    }

    @Nested
    inner class MixedValidInvalidFeatures {

        @Test
        fun `valid feature after invalid feature still parses`() {
            val fc = featureCollection(
                feature(properties = props("timestamp" to "invalid")) + ", " +
                feature(properties = props("timestamp" to 1000L))
            )
            val result = parser.parse(fc, sessionId = 1L)
            assertEquals(1, result.records.size)
            assertEquals(1, result.errors.size)
            assertEquals(1000L, result.records[0].timestamp)
        }

        @Test
        fun `valid feature before invalid feature still parses`() {
            val fc = featureCollection(
                feature(properties = props("timestamp" to 1000L)) + ", " +
                feature(properties = props("timestamp" to "invalid"))
            )
            val result = parser.parse(fc, sessionId = 1L)
            assertEquals(1, result.records.size)
            assertEquals(1, result.errors.size)
            assertEquals(2, result.errors[0].line, "Invalid feature is the second one (featureNum=2)")
            assertEquals(1000L, result.records[0].timestamp)
        }

        @Test
        fun `multiple valid features all parse`() {
            val fc = featureCollection(
                feature(properties = props("timestamp" to 1000L, "rat" to "4G_LTE")) + ", " +
                feature(properties = props("timestamp" to 2000L, "rat" to "5G_NSA")) + ", " +
                feature(properties = props("timestamp" to 3000L, "rat" to "3G"))
            )
            val result = parser.parse(fc, sessionId = 1L)
            assertEquals(3, result.records.size)
            assertEquals(0, result.errors.size)
            assertEquals("4G_LTE", result.records[0].rat)
            assertEquals("5G_NSA", result.records[1].rat)
            assertEquals("3G", result.records[2].rat)
        }

        @Test
        fun `sessionId is assigned to all records`() {
            val fc = featureCollection(
                feature(properties = props("timestamp" to 1000L)) + ", " +
                feature(properties = props("timestamp" to 2000L))
            )
            val result = parser.parse(fc, sessionId = 42L)
            assertEquals(2, result.records.size)
            assertEquals(42L, result.records[0].sessionId)
            assertEquals(42L, result.records[1].sessionId)
        }

        @Test
        fun `caBands list size matches records list size`() {
            val fc = featureCollection(
                feature(properties = props("timestamp" to 1000L)) + ", " +
                feature(properties = props("timestamp" to 2000L))
            )
            val result = parser.parse(fc, sessionId = 1L)
            assertEquals(result.records.size, result.caBands.size)
        }
    }
}
