package com.cellrecorder.app.domain.usecase.import_

import com.cellrecorder.app.data.local.entity.CellRecordEntity
import com.cellrecorder.app.data.local.entity.SessionMarkerEntity
import com.cellrecorder.app.data.repository.CellRecordRepository
import com.cellrecorder.app.data.repository.SessionMarkerRepository
import com.cellrecorder.app.data.repository.SessionRepository
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
}
