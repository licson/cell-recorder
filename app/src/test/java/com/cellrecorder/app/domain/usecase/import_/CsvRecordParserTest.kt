package com.cellrecorder.app.domain.usecase.import_

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Unit tests for [CsvRecordParser].
 *
 * KNOWN BUG (characterized, not fixed here — out of scope):
 * The CSV column `band` is not parsed into `CellRecordEntity.bandNumber` because
 * `parseRow` calls `int("band")` but the column index is stored under the
 * mapped key `"bandNumber"` (per `columnMap["band"] = "bandNumber"`). The lookup
 * under the original key `"band"` returns null. This is a latent production bug
 * worth a separate fix; tests below document the current (buggy) behavior.
 */
class CsvRecordParserTest {

    private val parser = CsvRecordParser()

    @Nested
    inner class EmptyAndHeaderOnly {

        @Test
        fun `empty content produces error and no records`() {
            val result = parser.parse("", sessionId = 1L)
            assertTrue(result.records.isEmpty())
            assertEquals(1, result.errors.size)
            assertEquals(0, result.errors[0].line)
            assertTrue(result.errors[0].message.contains("no data rows", ignoreCase = true))
        }

        @Test
        fun `header only produces error and no records`() {
            val result = parser.parse("timestamp,lat,lon", sessionId = 1L)
            assertTrue(result.records.isEmpty())
            assertEquals(1, result.errors.size)
            assertEquals(0, result.errors[0].line)
        }

        @Test
        fun `blank lines are filtered before parsing`() {
            val result = parser.parse("   \n\n", sessionId = 1L)
            assertTrue(result.records.isEmpty())
            assertEquals(1, result.errors.size)
        }
    }

    @Nested
    inner class RequiredColumns {

        @Test
        fun `missing timestamp column produces error`() {
            val csv = "lat,lon\n40.0,-74.0"
            val result = parser.parse(csv, sessionId = 1L)
            assertTrue(result.records.isEmpty())
            assertEquals(1, result.errors.size)
            assertTrue(result.errors[0].message.contains("Missing required columns"))
        }

        @Test
        fun `missing lat and lon without relative coords produces error`() {
            val csv = "timestamp\n1000"
            val result = parser.parse(csv, sessionId = 1L)
            assertTrue(result.records.isEmpty())
            assertEquals(1, result.errors.size)
            assertTrue(result.errors[0].message.contains("Missing required columns"))
        }

        @Test
        fun `missing lat but has relative_x_y parses successfully`() {
            val csv = "timestamp,relative_x,relative_y\n1000,1.5,2.5"
            val result = parser.parse(csv, sessionId = 7L)
            assertEquals(1, result.records.size)
            assertEquals(0, result.errors.size)
            assertEquals(7L, result.records[0].sessionId)
            assertEquals(1000L, result.records[0].timestamp)
            assertEquals(1.5, result.records[0].relativeX ?: 0.0, 1e-9)
            assertEquals(2.5, result.records[0].relativeY ?: 0.0, 1e-9)
        }

        @Test
        fun `header names are case-insensitive`() {
            val csv = "TIMESTAMP,LAT,LON\n1000,40.0,-74.0"
            val result = parser.parse(csv, sessionId = 1L)
            assertEquals(1, result.records.size)
            assertEquals(0, result.errors.size)
        }

        @Test
        fun `header names have whitespace trimmed`() {
            val csv = "timestamp , lat , lon\n1000,40.0,-74.0"
            val result = parser.parse(csv, sessionId = 1L)
            assertEquals(1, result.records.size)
            assertEquals(0, result.errors.size)
        }
    }

    @Nested
    inner class FieldMapping {

        @Test
        fun `all primary columns map correctly to CellRecordEntity fields`() {
            val csv = "timestamp,lat,lon,alt,accuracy,subscription_id,sim_slot_index,rat,pci,rsrp,rsrq,sinr,enb_gnb_id,lcid,avg_latency_ms,packet_loss_pct,mcc,mnc,band,earfcn,tac\n" +
                "1000,40.0,-74.0,10.0,5.0,123,0,4G_LTE,42,-85,-90,8,555,99,12.5,1.5,310,260,3,1800,1234"
            val result = parser.parse(csv, sessionId = 1L)
            assertEquals(1, result.records.size)
            assertEquals(0, result.errors.size)
            val r = result.records[0]
            assertEquals(1000L, r.timestamp)
            assertEquals(40.0, r.latitude, 1e-9)
            assertEquals(-74.0, r.longitude, 1e-9)
            assertEquals(10.0, r.altitude, 1e-9)
            assertEquals(5.0f, r.accuracy, 1e-9f)
            assertEquals(123, r.subscriptionId)
            assertEquals(0, r.simSlotIndex)
            assertEquals("4G_LTE", r.rat)
            assertEquals(42, r.pci)
            assertEquals(-85, r.rsrp)
            assertEquals(-90, r.rsrq)
            assertEquals(8, r.sinr)
            assertEquals(555L, r.enbOrGnbId)
            assertEquals(99, r.lcid)
            assertEquals(12.5, r.avgLatencyMs ?: 0.0, 1e-9)
            assertEquals(1.5, r.packetLossPct ?: 0.0, 1e-9)
            assertEquals("310", r.mcc)
            assertEquals("260", r.mnc)
            assertEquals(1800, r.earfcn)
            assertEquals(1234, r.tac)
            assertNull(r.bandNumber, "Known bug: band column is not mapped (see KDoc)")
        }

        @Test
        fun `all anchor columns map correctly to anchor fields`() {
            val csv = "timestamp,lat,lon,anchor_enb_gnb_id,anchor_lcid,anchor_pci,anchor_tac,anchor_band,anchor_earfcn,anchor_bandwidth,anchor_rsrp,anchor_rsrq,anchor_sinr,anchor_rssi,anchor_cqi,anchor_timing_advance\n" +
                "1000,40.0,-74.0,555,99,42,1234,3,1800,20000,-85,-90,8,5,7,1"
            val result = parser.parse(csv, sessionId = 1L)
            assertEquals(1, result.records.size)
            assertEquals(0, result.errors.size)
            val r = result.records[0]
            assertEquals(555L, r.anchorEnbOrGnbId)
            assertEquals(99, r.anchorLcid)
            assertEquals(42, r.anchorPci)
            assertEquals(1234, r.anchorTac)
            assertEquals(3, r.anchorBandNumber)
            assertEquals(1800, r.anchorEarfcn)
            assertEquals(20000, r.anchorBandwidthKhz)
            assertEquals(-85, r.anchorRsrp)
            assertEquals(-90, r.anchorRsrq)
            assertEquals(8, r.anchorSinr)
            assertEquals(5, r.anchorRssi)
            assertEquals(7, r.anchorCqi)
            assertEquals(1, r.anchorTimingAdvance)
        }

        @Test
        fun `missing optional fields default to null or sensible defaults`() {
            val csv = "timestamp,lat,lon\n1000,40.0,-74.0"
            val result = parser.parse(csv, sessionId = 1L)
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
        }
    }

    @Nested
    inner class QuoteAwareLineSplitter {

        @Test
        fun `unquoted commas are field separators`() {
            val csv = "timestamp,lat,lon\n1000,40.0,-74.0"
            val result = parser.parse(csv, sessionId = 1L)
            assertEquals(1, result.records.size)
            assertEquals(40.0, result.records[0].latitude, 1e-9)
            assertEquals(-74.0, result.records[0].longitude, 1e-9)
        }

        @Test
        fun `quoted commas in a string field are preserved within that field`() {
            val csv = "timestamp,lat,lon,mcc\n1000,40.0,-74.0,\"3,1,0\""
            val result = parser.parse(csv, sessionId = 1L)
            assertEquals(1, result.records.size)
            assertEquals(0, result.errors.size)
            assertEquals("3,1,0", result.records[0].mcc)
        }

        @Test
        fun `multiple quoted commas are preserved in string fields`() {
            val csv = "timestamp,lat,lon,mcc,mnc\n1000,40.0,-74.0,\"3,1,0\",\"2,6,0\""
            val result = parser.parse(csv, sessionId = 1L)
            assertEquals(1, result.records.size)
            assertEquals("3,1,0", result.records[0].mcc)
            assertEquals("2,6,0", result.records[0].mnc)
        }

        @Test
        fun `empty quoted field becomes empty string then filtered by str helper to null`() {
            val csv = "timestamp,lat,lon,mcc\n1000,40.0,-74.0,\"\""
            val result = parser.parse(csv, sessionId = 1L)
            assertEquals(1, result.records.size)
            assertNull(result.records[0].mcc, "Empty string is filtered by takeIf { it.isNotEmpty() } in str helper")
        }

        @Test
        fun `unquoted numeric field with comma would fail to parse as numeric (documented limitation)`() {
            val csv = "timestamp,lat,lon\n1000,\"40,0\",-74.0"
            val result = parser.parse(csv, sessionId = 1L)
            assertEquals(0, result.records.size, "Comma in numeric field renders it unparseable as Double; row is rejected")
            assertEquals(1, result.errors.size)
        }
    }

    @Nested
    inner class CaBandJsonParsing {

        @Test
        fun `absent ca_bands column yields one empty entry per successful row`() {
            val csv = "timestamp,lat,lon\n1000,40.0,-74.0"
            val result = parser.parse(csv, sessionId = 1L)
            assertEquals(1, result.records.size)
            assertEquals(1, result.caBands.size, "caBands has one entry per successful record (empty if no CA bands)")
            assertEquals(0, result.caBands[0].size)
        }

        @Test
        fun `empty ca_bands column is treated as no CA bands`() {
            val csv = "timestamp,lat,lon,ca_bands\n1000,40.0,-74.0,"
            val result = parser.parse(csv, sessionId = 1L)
            assertEquals(1, result.records.size)
            assertEquals(1, result.caBands.size)
            assertEquals(0, result.caBands[0].size)
        }

        @Test
        fun `valid CA band JSON (CSV-quoted multi-key) parses to CellRecordCaBandEntity list`() {
            val csv = "timestamp,lat,lon,ca_bands\n" +
                "1000,40.0,-74.0," +
                "\"[{\"\"band\"\":3,\"\"earfcn\"\":1800,\"\"pci\"\":42,\"\"rsrp\"\":-85,\"\"rsrq\"\":-90,\"\"sinr\"\":8,\"\"rssi\"\":-65,\"\"cqi\"\":7,\"\"timingAdvance\"\":1}]\""
            val result = parser.parse(csv, sessionId = 1L)
            assertEquals(1, result.records.size)
            assertEquals(1, result.caBands.size)
            val ca = result.caBands[0]
            assertEquals(1, ca.size)
            assertEquals(3, ca[0].bandNumber)
            assertEquals(1800, ca[0].earfcn)
            assertEquals(42, ca[0].pci)
            assertEquals(-85, ca[0].rsrp)
            assertEquals(-90, ca[0].rsrq)
            assertEquals(8, ca[0].sinr)
            assertEquals(-65, ca[0].rssi)
            assertEquals(7, ca[0].cqi)
            assertEquals(1, ca[0].timingAdvance)
        }

        @Test
        fun `single-key JSON without internal commas parses without CSV-quoting (lenient unquoted keys)`() {
            val csv = "timestamp,lat,lon,ca_bands\n1000,40.0,-74.0,[{\"band\":3}]"
            val result = parser.parse(csv, sessionId = 1L)
            assertEquals(1, result.records.size)
            assertEquals(1, result.caBands[0].size)
            assertEquals(3, result.caBands[0][0].bandNumber)
            assertNull(result.caBands[0][0].pci)
        }

        @Test
        fun `multi-key JSON without CSV-quoting fails to parse (internal commas split the row)`() {
            val csv = "timestamp,lat,lon,ca_bands\n1000,40.0,-74.0,[{\"band\":3,\"pci\":42}]"
            val result = parser.parse(csv, sessionId = 1L)
            assertEquals(1, result.records.size)
            assertEquals(0, result.caBands[0].size, "Internal commas split the row before JSON parsing")
        }

        @Test
        fun `multiple CA bands parse when CSV-quoted`() {
            val csv = "timestamp,lat,lon,ca_bands\n" +
                "1000,40.0,-74.0," +
                "\"[{\"\"band\"\":3,\"\"pci\"\":42},{\"\"band\"\":7,\"\"pci\"\":50}]\""
            val result = parser.parse(csv, sessionId = 1L)
            assertEquals(1, result.records.size)
            assertEquals(2, result.caBands[0].size)
            assertEquals(3, result.caBands[0][0].bandNumber)
            assertEquals(42, result.caBands[0][0].pci)
            assertEquals(7, result.caBands[0][1].bandNumber)
            assertEquals(50, result.caBands[0][1].pci)
        }

        @Test
        fun `malformed CA band JSON yields empty CA band list but record still parses`() {
            val csv = "timestamp,lat,lon,ca_bands\n" +
                "1000,40.0,-74.0,not-valid-json"
            val result = parser.parse(csv, sessionId = 1L)
            assertEquals(1, result.records.size)
            assertEquals(1, result.caBands.size)
            assertEquals(0, result.caBands[0].size, "Malformed JSON: empty CA band list returned")
        }

        @Test
        fun `missing keys in CA band JSON produce null fields`() {
            val csv = "timestamp,lat,lon,ca_bands\n" +
                "1000,40.0,-74.0," +
                "\"[{\"\"band\"\":3}]\""
            val result = parser.parse(csv, sessionId = 1L)
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
    inner class RowErrorCollection {

        @Test
        fun `row with invalid timestamp collects error but other rows still parse`() {
            val csv = "timestamp,lat,lon\n" +
                "1000,40.0,-74.0\n" +
                "invalid,50.0,5.0\n" +
                "2000,51.0,0.5"
            val result = parser.parse(csv, sessionId = 1L)
            assertEquals(2, result.records.size)
            assertEquals(1, result.errors.size)
            assertEquals(3, result.errors[0].line, "Error line number is 1-based from line 1 being the header, so row 2 is line 3")
            assertEquals(1000L, result.records[0].timestamp)
            assertEquals(2000L, result.records[1].timestamp)
        }

        @Test
        fun `row with missing lat and lon and no relative collects error`() {
            val csv = "timestamp,lat,lon\n" +
                "1000,,"
            val result = parser.parse(csv, sessionId = 1L)
            assertEquals(0, result.records.size)
            assertEquals(1, result.errors.size)
            assertEquals(2, result.errors[0].line)
        }

        @Test
        fun `multiple malformed rows collect errors per row`() {
            val csv = "timestamp,lat,lon\n" +
                "abc,40.0,-74.0\n" +
                "1000,,\n" +
                "2000,50.0,5.0"
            val result = parser.parse(csv, sessionId = 1L)
            assertEquals(1, result.records.size)
            assertEquals(2, result.errors.size)
            assertEquals(2000L, result.records[0].timestamp)
        }
    }

    @Nested
    inner class MultipleRows {

        @Test
        fun `multiple valid rows all parse`() {
            val csv = "timestamp,lat,lon\n" +
                "1000,40.0,-74.0\n" +
                "2000,51.0,5.0\n" +
                "3000,-33.0,151.0"
            val result = parser.parse(csv, sessionId = 1L)
            assertEquals(3, result.records.size)
            assertEquals(0, result.errors.size)
            assertEquals(1000L, result.records[0].timestamp)
            assertEquals(2000L, result.records[1].timestamp)
            assertEquals(3000L, result.records[2].timestamp)
        }

        @Test
        fun `sessionId is assigned to all records`() {
            val csv = "timestamp,lat,lon\n" +
                "1000,40.0,-74.0\n" +
                "2000,51.0,5.0"
            val result = parser.parse(csv, sessionId = 42L)
            assertEquals(2, result.records.size)
            assertEquals(42L, result.records[0].sessionId)
            assertEquals(42L, result.records[1].sessionId)
        }

        @Test
        fun `caBands list size matches records list size`() {
            val csv = "timestamp,lat,lon\n" +
                "1000,40.0,-74.0\n" +
                "2000,51.0,5.0"
            val result = parser.parse(csv, sessionId = 1L)
            assertEquals(result.records.size, result.caBands.size)
        }
    }
}
