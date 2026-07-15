package com.cellrecorder.app.domain.usecase.import_

import com.cellrecorder.app.data.local.entity.CellRecordEntity
import com.cellrecorder.app.data.local.entity.SessionMarkerEntity
import com.cellrecorder.app.data.repository.CellRecordRepository
import com.cellrecorder.app.data.repository.SessionMarkerRepository
import com.cellrecorder.app.data.repository.SessionRepository
import com.cellrecorder.app.domain.usecase.ExportSessionUseCase
import com.cellrecorder.app.data.local.entity.CellRecordWithCaBands
import com.cellrecorder.app.data.local.entity.SessionEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ImportSessionUseCaseTest {

    private lateinit var useCase: ImportSessionUseCase
    private val sessionRepository: SessionRepository = mockk(relaxed = true)
    private val cellRecordRepository: CellRecordRepository = mockk(relaxed = true)
    private val sessionMarkerRepository: SessionMarkerRepository = mockk(relaxed = true)
    private val csvParser = CsvRecordParser()
    private val geoJsonParser = GeoJsonRecordParser()

    @BeforeEach
    fun setup() {
        useCase = ImportSessionUseCase(
            sessionRepository = sessionRepository,
            cellRecordRepository = cellRecordRepository,
            sessionMarkerRepository = sessionMarkerRepository,
            csvRecordParser = csvParser,
            geoJsonRecordParser = geoJsonParser
        )
        coEvery { sessionRepository.create(any(), any()) } returns 1L
    }

    @Test
    fun `importCsv without markers creates outdoor session`() = runTest {
        val csv = "timestamp,lat,lon\n1000,40.0,-74.0"
        val summary = useCase.importCsv(csv, "Test")
        assertEquals("OUTDOOR", summary.recordingMode)
        assertEquals(1, summary.importedCount)
        coVerify(exactly = 0) { sessionMarkerRepository.insertAll(any()) }
    }

    @Test
    fun `importCsv with markers creates tunnel session and persists markers`() = runTest {
        val csv = "timestamp,lat,lon\n1000,40.0,-74.0"
        val markers = "timestamp,seq,type,label\n2000,1,NOTE,drop"
        coEvery { cellRecordRepository.insertAll(any<List<CellRecordEntity>>()) } returns listOf(10L)

        val summary = useCase.importCsv(csv, "Test", markers)
        assertEquals("TUNNEL", summary.recordingMode)
        assertEquals(1, summary.importedCount)
        coVerify { sessionMarkerRepository.insertAll(match { it.size == 1 && it[0].type == "NOTE" }) }
    }

    @Test
    fun `importCsv with locationSource tunnel creates tunnel session`() = runTest {
        val csv = "timestamp,lat,lon,location_source\n1000,40.0,-74.0,TUNNEL"
        coEvery { cellRecordRepository.insertAll(any<List<CellRecordEntity>>()) } returns listOf(10L)

        val summary = useCase.importCsv(csv, "Test")
        assertEquals("TUNNEL", summary.recordingMode)
    }

    @Test
    fun `importCsv with relative coords creates indoor session`() = runTest {
        val csv = "timestamp,relative_x,relative_y\n1000,1.0,2.0"
        coEvery { cellRecordRepository.insertAll(any<List<CellRecordEntity>>()) } returns listOf(10L)

        val summary = useCase.importCsv(csv, "Test")
        assertEquals("INDOOR", summary.recordingMode)
    }

    @Test
    fun `importGeoJson with tunnelMode flag creates tunnel session and markers`() = runTest {
        val geojson = """
            {"type":"FeatureCollection","tunnelMode":true,"features":[
                {"type":"Feature","geometry":{"type":"Point","coordinates":[-74.0,40.0]},"properties":{"timestamp":1000,"rat":"4G"}},
                {"type":"Feature","geometry":{"type":"Point","coordinates":[0.0,0.0]},"properties":{"timestamp":2000,"markerType":"NOTE","seq":1,"label":"drop"}}
            ]}
        """.trimIndent()
        coEvery { cellRecordRepository.insertAll(any<List<CellRecordEntity>>()) } returns listOf(10L)

        val summary = useCase.importGeoJson(geojson, "Test")
        assertEquals("TUNNEL", summary.recordingMode)
        assertEquals(1, summary.importedCount)
        coVerify { sessionMarkerRepository.insertAll(match { markers -> markers.any { it.type == "NOTE" } }) }
    }

    @Test
    fun `importGeoJson with indoorMode flag creates indoor session`() = runTest {
        val geojson = """
            {"type":"FeatureCollection","indoorMode":true,"features":[
                {"type":"Feature","geometry":{"type":"Point","coordinates":[-74.0,40.0]},"properties":{"timestamp":1000,"rat":"4G"}}
            ]}
        """.trimIndent()
        coEvery { cellRecordRepository.insertAll(any<List<CellRecordEntity>>()) } returns listOf(10L)

        val summary = useCase.importGeoJson(geojson, "Test")
        assertEquals("INDOOR", summary.recordingMode)
    }

    @Test
    fun `importGeoJson without flags creates outdoor session`() = runTest {
        val geojson = """
            {"type":"FeatureCollection","features":[
                {"type":"Feature","geometry":{"type":"Point","coordinates":[-74.0,40.0]},"properties":{"timestamp":1000,"rat":"4G"}}
            ]}
        """.trimIndent()
        coEvery { cellRecordRepository.insertAll(any<List<CellRecordEntity>>()) } returns listOf(10L)

        val summary = useCase.importGeoJson(geojson, "Test")
        assertEquals("OUTDOOR", summary.recordingMode)
    }

    @Test
    fun `importCsv with markers companion preserves original seq values`() = runTest {
        val csv = "timestamp,lat,lon\n1000,40.0,-74.0"
        val markers = "timestamp,seq,type,label\n3000,3,STOP,end\n1000,1,NOTE,start\n2000,2,WAYPOINT,mid"
        coEvery { cellRecordRepository.insertAll(any<List<CellRecordEntity>>()) } returns listOf(10L)

        val summary = useCase.importCsv(csv, "Test", markers)
        assertEquals("TUNNEL", summary.recordingMode)
        coVerify {
            sessionMarkerRepository.insertAll(match { list ->
                // import preserves CSV file order (3,1,2), not sorted; all 3 seq values present
                list.size == 3 &&
                    list.map { it.seq }.toSet() == setOf(1, 2, 3) &&
                    list.any { it.seq == 1 && it.type == "NOTE" && it.label == "start" } &&
                    list.any { it.seq == 2 && it.type == "WAYPOINT" && it.label == "mid" } &&
                    list.any { it.seq == 3 && it.type == "STOP" && it.label == "end" }
            })
        }
    }

    @Test
    fun `importGeoJson with tunnelMode and marker features preserves original seq`() = runTest {
        val geojson = """
            {"type":"FeatureCollection","tunnelMode":true,"features":[
                {"type":"Feature","geometry":{"type":"Point","coordinates":[-74.0,40.0]},"properties":{"timestamp":1000,"rat":"4G"}},
                {"type":"Feature","geometry":{"type":"Point","coordinates":[0.0,0.0]},"properties":{"timestamp":2000,"markerType":"NOTE","seq":7,"label":"kept"}}
            ]}
        """.trimIndent()
        coEvery { cellRecordRepository.insertAll(any<List<CellRecordEntity>>()) } returns listOf(10L)

        useCase.importGeoJson(geojson, "Test")
        coVerify {
            sessionMarkerRepository.insertAll(match { list ->
                list.size == 1 && list[0].seq == 7 && list[0].type == "NOTE" && list[0].label == "kept"
            })
        }
    }

    @Test
    fun `importGeoJson outdoor session with marker features inserts markers with original seq and OUTDOOR mode`() = runTest {
        val geojson = """
            {"type":"FeatureCollection","features":[
                {"type":"Feature","geometry":{"type":"Point","coordinates":[-74.0,40.0]},"properties":{"timestamp":1000,"rat":"4G"}},
                {"type":"Feature","geometry":{"type":"Point","coordinates":[0.0,0.0]},"properties":{"timestamp":2000,"markerType":"WAYPOINT","seq":4,"label":"out"}}
            ]}
        """.trimIndent()
        coEvery { cellRecordRepository.insertAll(any<List<CellRecordEntity>>()) } returns listOf(10L)

        val summary = useCase.importGeoJson(geojson, "Outdoor With Marks")
        assertEquals("OUTDOOR", summary.recordingMode)
        coVerify {
            sessionMarkerRepository.insertAll(match { list ->
                list.size == 1 && list[0].seq == 4 && list[0].type == "WAYPOINT" && list[0].label == "out"
            })
        }
    }

    @Test
    fun `importGeoJson indoor session with marker features inserts markers and sets INDOOR mode`() = runTest {
        val geojson = """
            {"type":"FeatureCollection","indoorMode":true,"features":[
                {"type":"Feature","geometry":{"type":"Point","coordinates":[-74.0,40.0]},"properties":{"timestamp":1000,"rat":"4G","relativeX":1.0,"relativeY":2.0}},
                {"type":"Feature","geometry":{"type":"Point","coordinates":[0.0,0.0]},"properties":{"timestamp":2000,"markerType":"STOP","seq":2,"label":"stop"}}
            ]}
        """.trimIndent()
        coEvery { cellRecordRepository.insertAll(any<List<CellRecordEntity>>()) } returns listOf(10L)

        val summary = useCase.importGeoJson(geojson, "Indoor With Marks")
        assertEquals("INDOOR", summary.recordingMode)
        coVerify {
            sessionMarkerRepository.insertAll(match { list ->
                list.size == 1 && list[0].seq == 2 && list[0].type == "STOP" && list[0].label == "stop"
            })
        }
    }

    @Test
    fun `round-trip tunnel session preserves cell-record count, marker count, type, label, seq, and locationSource`() = runTest {
        val exportUseCase = ExportSessionUseCase()
        val originalSession = SessionEntity(id = 5, name = "Tunnel Trip", createdAt = 1000L, endedAt = 2000L, pointCount = 2, recordingMode = "TUNNEL")
        val originalRecords = listOf(
            CellRecordWithCaBands(
                record = CellRecordEntity(
                    id = 1, sessionId = 5, timestamp = 1000L,
                    latitude = 0.0, longitude = 0.0, altitude = 0.0, accuracy = 0f,
                    rat = "UNKNOWN", locationSource = "TUNNEL", pci = 1, rsrp = -90, rsrq = -10, sinr = 5
                ),
                caBands = emptyList()
            ),
            CellRecordWithCaBands(
                record = CellRecordEntity(
                    id = 2, sessionId = 5, timestamp = 2000L,
                    latitude = 0.0, longitude = 0.0, altitude = 0.0, accuracy = 0f,
                    rat = "UNKNOWN", locationSource = "TUNNEL", pci = 2, rsrp = -85, rsrq = -8, sinr = 6
                ),
                caBands = emptyList()
            )
        )
        val originalMarkers = listOf(
            SessionMarkerEntity(id = 1, sessionId = 5, timestamp = 1000, seq = 1, type = "NOTE", label = "entry"),
            SessionMarkerEntity(id = 2, sessionId = 5, timestamp = 2000, seq = 2, type = "STOP", label = "exit")
        )

        val exportedCsv = exportUseCase.exportCsv(originalSession, originalRecords).content
        val exportedMarkersCsv = exportUseCase.exportMarkersCsv(originalSession, originalMarkers)?.content
        assertTrue(exportedMarkersCsv != null && exportedMarkersCsv.isNotBlank())

        var capturedCellRecords: List<CellRecordEntity> = emptyList()
        var capturedMarkers: List<SessionMarkerEntity> = emptyList()
        coEvery { cellRecordRepository.insertAll(any<List<CellRecordEntity>>()) } answers {
            capturedCellRecords = firstArg()
            listOf(1L, 2L)
        }
        coEvery { sessionRepository.create(any<String>(), any<Long>(), eq("TUNNEL")) } returns 99L

        val summary = useCase.importCsv(exportedCsv, "Reimported", exportedMarkersCsv)

        assertEquals("TUNNEL", summary.recordingMode)
        assertEquals(2, summary.importedCount, "cell-record count should round-trip")
        assertEquals(2, capturedCellRecords.size)
        assertEquals("TUNNEL", capturedCellRecords[0].locationSource, "locationSource should round-trip")
        assertEquals("TUNNEL", capturedCellRecords[1].locationSource)
        coVerify {
            sessionMarkerRepository.insertAll(match { list ->
                list.size == 2 &&
                    list.map { it.seq }.sorted() == listOf(1, 2) &&
                    list.any { it.type == "NOTE" && it.label == "entry" } &&
                    list.any { it.type == "STOP" && it.label == "exit" }
            })
        }
    }

    @Test
    fun `import does not touch recent_marker_labels table`() = runTest {
        val csv = "timestamp,lat,lon\n1000,40.0,-74.0"
        val markers = "timestamp,seq,type,label\n1000,1,NOTE,x"
        coEvery { cellRecordRepository.insertAll(any<List<CellRecordEntity>>()) } returns listOf(10L)

        useCase.importCsv(csv, "Test", markers)

        // ImportSessionUseCase has no RecentMarkerLabelRepository dependency; verify it is never invoked.
        // The use case only depends on sessionRepository, cellRecordRepository, sessionMarkerRepository.
        // To assert recents are untouched, verify sessionMarkerRepository.insertAll was called (markers persisted)
        // but no recent-labels upsert occurred through the use case (it calls insertAll, not insertMarker which would upsert recents).
        coVerify(exactly = 1) { sessionMarkerRepository.insertAll(any()) }
        coVerify(exactly = 0) { sessionMarkerRepository.insertMarker(any(), any(), any()) }
        coVerify(exactly = 0) { sessionMarkerRepository.insertMarkerWithAutoLabel(any(), any()) }
        coVerify(exactly = 0) { sessionMarkerRepository.updateMarker(any(), any(), any()) }
    }
}
