package com.cellrecorder.app.domain.usecase

import com.cellrecorder.app.data.local.entity.CellRecordCaBandEntity
import com.cellrecorder.app.data.local.entity.CellRecordEntity
import com.cellrecorder.app.data.local.entity.CellRecordWithCaBands
import com.cellrecorder.app.data.local.entity.SessionEntity
import com.cellrecorder.app.data.local.entity.SessionMarkerEntity
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class ExportSessionUseCaseTest {

    private lateinit var useCase: ExportSessionUseCase
    private lateinit var session: SessionEntity
    private lateinit var records: List<CellRecordWithCaBands>
    private lateinit var record: CellRecordEntity

    @BeforeEach
    fun setup() {
        useCase = ExportSessionUseCase()
        session = SessionEntity(
            id = 1,
            name = "Test Session",
            createdAt = 1000L,
            endedAt = 2000L,
            pointCount = 1
        )
        record = CellRecordEntity(
            id = 1,
            sessionId = 1,
            timestamp = 1500L,
            latitude = 40.7128,
            longitude = -74.0060,
            altitude = 10.0,
            accuracy = 5f,
            rat = "4G",
            networkTypeCode = 13,
            fullCellIdentity = 123456L,
            enbOrGnbId = 482L,
            lcid = 64,
            pci = 301,
            tac = 1234,
            bandNumber = 4,
            earfcn = 2000,
            rsrp = -110,
            rsrq = -12,
            sinr = 15,
            avgLatencyMs = 25.3,
            packetLossPct = 0.0,
            mcc = "310",
            mnc = "410"
        )
        records = listOf(
            CellRecordWithCaBands(
                record = record,
                caBands = listOf(
                    CellRecordCaBandEntity(
                        cellRecordId = 1,
                        bandNumber = 7,
                        earfcn = 3100,
                        pci = 456,
                        rsrp = -105,
                        rsrq = -10,
                        sinr = 12
                    )
                )
            )
        )
    }

    @Test
    fun `exportCsv produces correct header`() {
        val data = useCase.exportCsv(session, records)
        assertTrue(data.content.startsWith("timestamp,lat,lon,alt,accuracy,relative_x,relative_y,subscription_id,sim_slot_index,rat,pci,rsrp,rsrq,sinr,enb_gnb_id,lcid,avg_latency_ms,packet_loss_pct,mcc,mnc,band,bandwidth,earfcn,tac"))
        assertTrue(data.content.contains("ca_bands"))
    }

    @Test
    fun `exportCsv contains correct values`() {
        val data = useCase.exportCsv(session, records)
        assertTrue(data.content.contains("1500"))
        assertTrue(data.content.contains("40.7128"))
        assertTrue(data.content.contains("-74.006"))
        assertTrue(data.content.contains("4G"))
        assertTrue(data.content.contains("301"))
        assertTrue(data.content.contains("25.3"))
        assertTrue(data.content.contains("310"))
    }

    @Test
    fun `exportCsv contains CA bands`() {
        val data = useCase.exportCsv(session, records)
        assertTrue(data.content.contains("ca_bands"))
        assertTrue(data.content.contains("band"))
        assertTrue(data.content.contains("7"))
        assertTrue(data.content.contains("456"))
    }

    @Test
    fun `exportGeoJson produces valid structure`() {
        val data = useCase.exportGeoJson(session, records)
        assertTrue(data.content.contains("\"type\":\"FeatureCollection\""))
        assertTrue(data.content.contains("\"type\":\"Point\""))
        assertTrue(data.content.contains("-74.006"))
        assertTrue(data.content.contains("4G"))
    }

    @Test
    fun `exportGeoJson contains properties`() {
        val data = useCase.exportGeoJson(session, records)
        assertTrue(data.content.contains("\"rsrp\":-110"))
        assertTrue(data.content.contains("\"pci\":301"))
        assertTrue(data.content.contains("\"mcc\":\"310\""))
        assertTrue(data.content.contains("\"mnc\":\"410\""))
    }

    @Test
    fun `exportGeoJson contains CA bands`() {
        val data = useCase.exportGeoJson(session, records)
        assertTrue(data.content.contains("\"caBands\""))
        assertTrue(data.content.contains("\"band\":7"))
        assertTrue(data.content.contains("\"pci\":456"))
    }

    @Test
    fun `suggested filenames are correct`() {
        val csv = useCase.exportCsv(session, records)
        val geojson = useCase.exportGeoJson(session, records)
        assertTrue(csv.suggestedFilename.endsWith(".csv"))
        assertTrue(geojson.suggestedFilename.endsWith(".geojson"))
        assertTrue(csv.suggestedFilename.contains("Test_Session"))
        assertTrue(geojson.suggestedFilename.contains("Test_Session"))
    }

    @Test
    fun `empty records produces valid export`() {
        val csv = useCase.exportCsv(session, emptyList())
        val geojson = useCase.exportGeoJson(session, emptyList())
        val lines = csv.content.trimEnd().lines()
        assertEquals(1, lines.size) // just header
        assertTrue(geojson.content.contains("\"type\":\"FeatureCollection\""))
        assertTrue(geojson.content.startsWith("{"))
    }

    @Nested
    inner class IndoorMode {

        private val indoorSession = SessionEntity(
            id = 2,
            name = "Indoor",
            createdAt = 1000L,
            endedAt = 2000L,
            pointCount = 1,
            recordingMode = "INDOOR"
        )

        private val indoorRecord = CellRecordEntity(
            id = 10,
            sessionId = 2,
            timestamp = 1500L,
            latitude = 0.0,
            longitude = 0.0,
            altitude = 0.0,
            accuracy = 0f,
            relativeX = 5.5,
            relativeY = 7.2,
            rat = "UNKNOWN",
            pci = 1,
            rsrp = -90,
            rsrq = -10,
            sinr = 5
        )

        private val indoorRecords = listOf(CellRecordWithCaBands(record = indoorRecord, caBands = emptyList()))

        @Test
        fun `indoor CSV writes relative_x and relative_y values`() {
            val data = useCase.exportCsv(indoorSession, indoorRecords)
            assertTrue(data.content.contains("5.5"), "relative_x value should appear in CSV")
            assertTrue(data.content.contains("7.2"), "relative_y value should appear in CSV")
        }

        @Test
        fun `indoor GeoJSON adds indoorMode and coordinateReference flags`() {
            val data = useCase.exportGeoJson(indoorSession, indoorRecords)
            assertTrue(data.content.contains("\"indoorMode\":true"))
            assertTrue(data.content.contains("\"coordinateReference\":\"relative\""))
        }

        @Test
        fun `indoor GeoJSON uses fake lat and lon derived from relative coordinates`() {
            val data = useCase.exportGeoJson(indoorSession, indoorRecords)
            val expectedLon = (5.5 / 111320.0).toString()
            val expectedLat = (7.2 / 111320.0).toString()
            assertTrue(
                data.content.contains(expectedLon) || data.content.contains(trimDouble(expectedLon)),
                "Expected fake longitude from relativeX; expected ~$expectedLon"
            )
            assertTrue(
                data.content.contains(expectedLat) || data.content.contains(trimDouble(expectedLat)),
                "Expected fake latitude from relativeY; expected ~$expectedLat"
            )
        }

        @Test
        fun `indoor GeoJSON includes relativeX and relativeY properties`() {
            val data = useCase.exportGeoJson(indoorSession, indoorRecords)
            assertTrue(data.content.contains("\"relativeX\":5.5"))
            assertTrue(data.content.contains("\"relativeY\":7.2"))
        }

        @Test
        fun `outdoor session does not set indoorMode flag`() {
            val data = useCase.exportGeoJson(session, records)
            assertFalse(data.content.contains("\"indoorMode\""), "Outdoor session should not set indoorMode")
        }

        private fun trimDouble(s: String): String {
            return if (s.contains(".")) s.trimEnd('0').trimEnd('.') else s
        }
    }

    @Nested
    inner class MarkerExport {

        private val marker = SessionMarkerEntity(
            id = 1,
            sessionId = 1,
            timestamp = 1600L,
            seq = 1,
            type = "NOTE",
            label = "poor signal"
        )

        @Test
        fun `exportMarkersCsv produces header and row`() {
            val data = useCase.exportMarkersCsv(session, listOf(marker)) ?: fail("expected data")
            assertTrue(data.content.startsWith("timestamp,seq,type,label"))
            assertTrue(data.content.contains("1600,1,NOTE,poor signal"))
            assertTrue(data.suggestedFilename.endsWith("_markers.csv"))
        }

        @Test
        fun `exportMarkersCsv escapes embedded commas in label`() {
            val m = marker.copy(label = "bad, area")
            val data = useCase.exportMarkersCsv(session, listOf(m)) ?: fail("expected data")
            assertTrue(data.content.contains("\"bad, area\""))
        }

        @Test
        fun `exportMarkersCsv returns null for empty markers`() {
            assertNull(useCase.exportMarkersCsv(session, emptyList()))
        }

        @Test
        fun `exportGeoJson includes marker Point feature`() {
            val data = useCase.exportGeoJson(session, records, listOf(marker))
            assertTrue(data.content.contains("\"markerType\":\"NOTE\""))
            assertTrue(data.content.contains("\"label\":\"poor signal\""))
            assertTrue(data.content.contains("\"seq\":1"))
        }
    }

    @Nested
    inner class TunnelMode {

        private val tunnelSession = SessionEntity(
            id = 3,
            name = "Tunnel",
            createdAt = 1000L,
            endedAt = 2000L,
            pointCount = 1,
            recordingMode = "TUNNEL"
        )

        private val tunnelRecord = CellRecordEntity(
            id = 20,
            sessionId = 3,
            timestamp = 1500L,
            latitude = 0.0,
            longitude = 0.0,
            altitude = 0.0,
            accuracy = 0f,
            rat = "UNKNOWN",
            locationSource = "TUNNEL",
            pci = 1,
            rsrp = -90,
            rsrq = -10,
            sinr = 5
        )

        private val tunnelRecords = listOf(CellRecordWithCaBands(record = tunnelRecord, caBands = emptyList()))

        @Test
        fun `tunnel GeoJSON sets tunnelMode flag`() {
            val data = useCase.exportGeoJson(tunnelSession, tunnelRecords)
            assertTrue(data.content.contains("\"tunnelMode\":true"))
        }

        @Test
        fun `tunnel GeoJSON does not set indoorMode flag`() {
            val data = useCase.exportGeoJson(tunnelSession, tunnelRecords)
            assertFalse(data.content.contains("\"indoorMode\""), "Tunnel session should not set indoorMode")
        }
    }

    @Nested
    inner class MarkerExportComprehensive {

        private val tunnelSession = SessionEntity(
            id = 3, name = "Tunnel", createdAt = 1000L, endedAt = 2000L, pointCount = 3, recordingMode = "TUNNEL"
        )

        private val outdoorSession = SessionEntity(
            id = 1, name = "Test Session", createdAt = 1000L, endedAt = 2000L, pointCount = 1
        )

        private val indoorSession = SessionEntity(
            id = 2, name = "Indoor", createdAt = 1000L, endedAt = 2000L, pointCount = 1, recordingMode = "INDOOR"
        )

        private val tunnelRecord = CellRecordEntity(
            id = 20, sessionId = 3, timestamp = 1500L,
            latitude = 0.0, longitude = 0.0, altitude = 0.0, accuracy = 0f,
            rat = "UNKNOWN", locationSource = "TUNNEL", pci = 1, rsrp = -90, rsrq = -10, sinr = 5
        )
        private val tunnelRecords = listOf(CellRecordWithCaBands(record = tunnelRecord, caBands = emptyList()))

        // 3 markers with seq out of insertion order; export must sort by seq ascending.
        private val threeMarkers = listOf(
            SessionMarkerEntity(id = 31, sessionId = 3, timestamp = 3000, seq = 3, type = "STOP", label = "end"),
            SessionMarkerEntity(id = 10, sessionId = 3, timestamp = 1000, seq = 1, type = "NOTE", label = "first"),
            SessionMarkerEntity(id = 20, sessionId = 3, timestamp = 2000, seq = 2, type = "WAYPOINT", label = "mid")
        )

        @Test
        fun `tunnel session with 3 markers produces markers CSV with header and 3 rows sorted by seq`() {
            val data = useCase.exportMarkersCsv(tunnelSession, threeMarkers)
                ?: fail("exportMarkersCsv should return data for non-empty markers")
            val lines = data.content.trimEnd().lines()
            assertEquals("timestamp,seq,type,label", lines[0])
            assertEquals(4, lines.size, "header + 3 rows")
            // rows must be sorted by seq ascending
            assertTrue(lines[1].contains(",1,NOTE,first"), "row 1 should be seq=1")
            assertTrue(lines[2].contains(",2,WAYPOINT,mid"), "row 2 should be seq=2")
            assertTrue(lines[3].contains(",3,STOP,end"), "row 3 should be seq=3")
            assertTrue(data.suggestedFilename.endsWith("_markers.csv"))
        }

        @Test
        fun `outdoor session with markers also produces a markers file (universal markers)`() {
            val m = listOf(SessionMarkerEntity(id = 1, sessionId = 1, timestamp = 100, seq = 1, type = "NOTE", label = "x"))
            val data = useCase.exportMarkersCsv(outdoorSession, m)
            assertNotNull(data, "outdoor sessions must also emit a markers CSV (universal markers per D12)")
        }

        @Test
        fun `indoor session with markers also produces a markers file (universal markers)`() {
            val m = listOf(SessionMarkerEntity(id = 1, sessionId = 2, timestamp = 100, seq = 1, type = "NOTE", label = "y"))
            val data = useCase.exportMarkersCsv(indoorSession, m)
            assertNotNull(data, "indoor sessions must also emit a markers CSV (universal markers per D12)")
        }

        @Test
        fun `session with zero markers produces no markers file`() {
            assertNull(useCase.exportMarkersCsv(tunnelSession, emptyList()))
        }

        @Test
        fun `marker label with embedded double quote is escaped as doubled quotes`() {
            val m = SessionMarkerEntity(id = 1, sessionId = 3, timestamp = 1, seq = 1, type = "NOTE", label = "a\"b")
            val data = useCase.exportMarkersCsv(tunnelSession, listOf(m)) ?: fail("expected data")
            assertTrue(data.content.contains("\"a\"\"b\""), "embedded double quote in label should be escaped as doubled quotes")
        }

        @Test
        fun `marker label with embedded newline is quoted`() {
            val m = SessionMarkerEntity(id = 1, sessionId = 3, timestamp = 1, seq = 1, type = "NOTE", label = "line1\nline2")
            val data = useCase.exportMarkersCsv(tunnelSession, listOf(m)) ?: fail("expected data")
            assertTrue(data.content.contains("\"line1\nline2\""), "embedded newline in label should be quoted")
        }

        @Test
        fun `export contains no recent_marker_labels data in CSV`() {
            val csv = useCase.exportCsv(tunnelSession, tunnelRecords).content
            val markersCsv = useCase.exportMarkersCsv(tunnelSession, threeMarkers)?.content ?: ""
            assertFalse(csv.contains("recent_marker_labels", ignoreCase = true),
                "cell records CSV must not contain recent_marker_labels data")
            assertFalse(markersCsv.contains("recent_marker_labels", ignoreCase = true),
                "markers CSV must not contain recent_marker_labels data")
        }

        @Test
        fun `tunnel cell-record GeoJSON features have zero-zero coordinates`() {
            val data = useCase.exportGeoJson(tunnelSession, tunnelRecords)
            // tunnel records store 0.0/0.0 in DB and export reads them as-is
            assertTrue(data.content.contains("\"coordinates\":[0.0,0.0,0.0]"),
                "tunnel cell-record features should have [0.0, 0.0, 0.0] coordinates")
        }

        @Test
        fun `outdoor session with markers emits marker Point features with zero-zero coordinates and no tunnelMode`() {
            val m = SessionMarkerEntity(id = 1, sessionId = 1, timestamp = 100, seq = 1, type = "NOTE", label = "outdoor mark")
            val data = useCase.exportGeoJson(outdoorSession, records, listOf(m))
            assertFalse(data.content.contains("\"tunnelMode\""), "outdoor session must not set tunnelMode")
            assertTrue(data.content.contains("\"markerType\":\"NOTE\""))
            // marker features are the only features with 2-element [0.0,0.0] coordinates (cell records have 3-element [lon,lat,alt])
            assertTrue(data.content.contains("\"coordinates\":[0.0,0.0]"),
                "outdoor marker feature should have [0.0, 0.0] coordinates (2-element)")
        }

        @Test
        fun `indoor session with markers emits marker Point features with zero-zero coordinates preserving indoorMode and no tunnelMode`() {
            val m = SessionMarkerEntity(id = 1, sessionId = 2, timestamp = 100, seq = 1, type = "NOTE", label = "indoor mark")
            val indoorRecord = CellRecordEntity(
                id = 10, sessionId = 2, timestamp = 1500L,
                latitude = 0.0, longitude = 0.0, altitude = 0.0, accuracy = 0f,
                relativeX = 5.5, relativeY = 7.2, rat = "UNKNOWN", pci = 1, rsrp = -90, rsrq = -10, sinr = 5
            )
            val indoorRecords = listOf(CellRecordWithCaBands(record = indoorRecord, caBands = emptyList()))
            val data = useCase.exportGeoJson(indoorSession, indoorRecords, listOf(m))
            assertTrue(data.content.contains("\"indoorMode\":true"), "indoor session must preserve indoorMode")
            assertFalse(data.content.contains("\"tunnelMode\""), "indoor session must not set tunnelMode")
            assertTrue(data.content.contains("\"markerType\":\"NOTE\""))
            assertTrue(data.content.contains("\"coordinates\":[0.0,0.0]"),
                "indoor marker feature should have [0.0, 0.0] coordinates (2-element)")
        }

        @Test
        fun `export contains no recent_marker_labels data in GeoJSON`() {
            val data = useCase.exportGeoJson(tunnelSession, tunnelRecords, threeMarkers)
            assertFalse(data.content.contains("recent_marker_labels", ignoreCase = true),
                "GeoJSON must not contain recent_marker_labels data")
        }

        @Test
        fun `marker feature has Point geometry type`() {
            val m = SessionMarkerEntity(id = 1, sessionId = 3, timestamp = 100, seq = 1, type = "NOTE", label = "x")
            val data = useCase.exportGeoJson(tunnelSession, tunnelRecords, listOf(m))
            // marker features have 2-element [0.0,0.0] coordinates with Point geometry
            assertTrue(data.content.contains("\"type\":\"Point\",\"coordinates\":[0.0,0.0]"),
                "marker feature should have Point geometry with [0.0, 0.0] coordinates")
        }
    }

    @Nested
    inner class CsvFieldEscaping {

        @Test
        fun `mcc with embedded comma is quoted and preserved`() {
            val rec = record.copy(mcc = "3,1,0")
            val data = useCase.exportCsv(session, listOf(CellRecordWithCaBands(rec, emptyList())))
            assertTrue(data.content.contains("\"3,1,0\""), "MCC with commas should be quoted")
        }

        @Test
        fun `mnc with embedded double quote is escaped as doubled quotes`() {
            val rec = record.copy(mnc = "ab\"cd")
            val data = useCase.exportCsv(session, listOf(CellRecordWithCaBands(rec, emptyList())))
            assertTrue(data.content.contains("\"ab\"\"cd\""), "Embedded double quotes should be doubled")
        }

        @Test
        fun `rat with embedded newline is quoted`() {
            val rec = record.copy(rat = "4G\nLTE")
            val data = useCase.exportCsv(session, listOf(CellRecordWithCaBands(rec, emptyList())))
            assertTrue(data.content.contains("\"4G\nLTE\""), "Embedded newline should be quoted")
        }

        @Test
        fun `rat with embedded carriage return is quoted`() {
            val rec = record.copy(rat = "4G\rLTE")
            val data = useCase.exportCsv(session, listOf(CellRecordWithCaBands(rec, emptyList())))
            assertTrue(data.content.contains("\"4G\rLTE\""), "Embedded CR should be quoted")
        }

        @Test
        fun `plain numeric field is not quoted`() {
            val data = useCase.exportCsv(session, records)
            val lines = data.content.lines()
            val dataLine = lines.drop(1).first()
            assertTrue(dataLine.contains(",301,"), "PCI value should appear unquoted in data row")
        }
    }

    @Nested
    inner class AnchorAndLocationFields {

        @Test
        fun `CSV includes is_location_estimated and location_source header`() {
            val data = useCase.exportCsv(session, records)
            assertTrue(data.content.contains("is_location_estimated"))
            assertTrue(data.content.contains("location_source"))
        }

        @Test
        fun `CSV includes anchor field headers`() {
            val data = useCase.exportCsv(session, records)
            assertTrue(data.content.contains("anchor_enb_gnb_id"))
            assertTrue(data.content.contains("anchor_lcid"))
            assertTrue(data.content.contains("anchor_pci"))
            assertTrue(data.content.contains("anchor_tac"))
            assertTrue(data.content.contains("anchor_band"))
            assertTrue(data.content.contains("anchor_earfcn"))
            assertTrue(data.content.contains("anchor_rsrp"))
            assertTrue(data.content.contains("anchor_rsrq"))
            assertTrue(data.content.contains("anchor_sinr"))
            assertTrue(data.content.contains("anchor_rssi"))
            assertTrue(data.content.contains("anchor_cqi"))
            assertTrue(data.content.contains("anchor_timing_advance"))
        }

        @Test
        fun `GeoJSON includes anchor fields in properties when present`() {
            val recWithAnchor = record.copy(
                anchorEnbOrGnbId = 555L,
                anchorLcid = 99,
                anchorPci = 42,
                anchorTac = 1234,
                anchorBandNumber = 3,
                anchorEarfcn = 1800,
                anchorRsrp = -85,
                anchorRsrq = -90,
                anchorSinr = 8,
                anchorRssi = -65,
                anchorCqi = 7,
                anchorTimingAdvance = 1
            )
            val data = useCase.exportGeoJson(session, listOf(CellRecordWithCaBands(recWithAnchor, emptyList())))
            assertTrue(data.content.contains("\"anchorEnbGnbId\":555"))
            assertTrue(data.content.contains("\"anchorLcid\":99"))
            assertTrue(data.content.contains("\"anchorPci\":42"))
            assertTrue(data.content.contains("\"anchorBand\":3"))
            assertTrue(data.content.contains("\"anchorRsrp\":-85"))
            assertTrue(data.content.contains("\"anchorRssi\":-65"))
        }

        @Test
        fun `GeoJSON includes isLocationEstimated and locationSource fields`() {
            val recWithLocation = record.copy(
                isLocationEstimated = true,
                locationSource = "GPS"
            )
            val data = useCase.exportGeoJson(session, listOf(CellRecordWithCaBands(recWithLocation, emptyList())))
            assertTrue(data.content.contains("\"isLocationEstimated\":true"))
            assertTrue(data.content.contains("\"locationSource\":\"GPS\""))
        }
    }
}