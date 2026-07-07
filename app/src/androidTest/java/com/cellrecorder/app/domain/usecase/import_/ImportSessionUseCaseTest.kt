package com.cellrecorder.app.domain.usecase.import_

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.cellrecorder.app.data.local.AppDatabase
import com.cellrecorder.app.data.repository.CellRecordRepository
import com.cellrecorder.app.data.repository.SessionMarkerRepository
import com.cellrecorder.app.data.repository.SessionRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@MediumTest
class ImportSessionUseCaseTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var sessionRepository: SessionRepository

    @Inject
    lateinit var cellRecordRepository: CellRecordRepository

    @Inject
    lateinit var db: AppDatabase

    @Inject
    lateinit var sessionMarkerRepository: SessionMarkerRepository

    private lateinit var useCase: ImportSessionUseCase

    @Before
    fun setUp() {
        hiltRule.inject()
        useCase = ImportSessionUseCase(
            sessionRepository,
            cellRecordRepository,
            sessionMarkerRepository,
            CsvRecordParser(),
            GeoJsonRecordParser()
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun importCsv_createsSessionAndPersistsRecords() = runBlocking {
        val csv = csvWithCaBands()

        useCase.importCsv(csv, "Test CSV Import")

        val sessions = sessionRepository.getAll().first()
        assertEquals(1, sessions.size)
        val sessionId = sessions[0].id

        val records = cellRecordRepository.getBySessionIdOnce(sessionId)
        assertEquals(3, records.size)

        val withCaBands = cellRecordRepository.getBySessionIdOnceWithCaBands(sessionId)
        assertEquals(3, withCaBands.size)
        for (wrapper in withCaBands) {
            assertEquals(1, wrapper.caBands.size)
            assertEquals(3, wrapper.caBands[0].bandNumber)
            assertEquals(1800, wrapper.caBands[0].earfcn)
            assertEquals(42, wrapper.caBands[0].pci)
        }

        val session = sessionRepository.getById(sessionId).first()
        assertNotNull(session)
        assertEquals(3, session!!.pointCount)
        assertNotNull(session.endedAt)
        assertEquals("OUTDOOR", session.recordingMode)
    }

    @Test
    fun importCsv_returnsImportSummaryWithCorrectCounts() = runBlocking {
        val csv = csvWithCaBands()

        val summary = useCase.importCsv(csv, "Test CSV Import")

        assertEquals("Test CSV Import", summary.sessionName)
        assertEquals(3, summary.importedCount)
        assertEquals(0, summary.errorCount)
        assertEquals("OUTDOOR", summary.recordingMode)
        assertTrue(summary.errors.isEmpty())
    }

    @Test
    fun importCsv_malformedLinesSkipped() = runBlocking {
        val csv = "timestamp,lat,lon\n" +
            "1000,37.0,-122.0\n" +
            "2000,37.1,-122.1\n" +
            "3000,37.2,-122.2\n" +
            "invalid,37.3,-122.3\n"

        val summary = useCase.importCsv(csv, "Malformed Test")

        assertEquals(3, summary.importedCount)
        assertEquals(1, summary.errorCount)

        val sessions = sessionRepository.getAll().first()
        assertEquals(1, sessions.size)
        val sessionId = sessions[0].id
        val records = cellRecordRepository.getBySessionIdOnce(sessionId)
        assertEquals(3, records.size)

        val session = sessionRepository.getById(sessionId).first()
        assertNotNull(session)
        assertEquals(3, session!!.pointCount)
    }

    @Test
    fun importCsv_emptyFile() = runBlocking {
        val summary = useCase.importCsv("", "Empty Test")

        assertEquals(0, summary.importedCount)
        assertEquals(1, summary.errorCount)
        assertTrue(summary.errors.isNotEmpty())

        val sessions = sessionRepository.getAll().first()
        assertEquals(1, sessions.size)
        val sessionId = sessions[0].id

        val records = cellRecordRepository.getBySessionIdOnce(sessionId)
        assertEquals(0, records.size)

        val session = sessionRepository.getById(sessionId).first()
        assertNotNull(session)
        assertEquals(0, session!!.pointCount)
        assertNull(session.endedAt)
    }

    @Test
    fun importGeoJson_createsSessionAndPersistsRecords() = runBlocking {
        val geoJson = geoJsonFeatureCollection()

        useCase.importGeoJson(geoJson, "Test GeoJSON Import")

        val sessions = sessionRepository.getAll().first()
        assertEquals(1, sessions.size)
        val sessionId = sessions[0].id

        val records = cellRecordRepository.getBySessionIdOnce(sessionId).sortedBy { it.timestamp }
        assertEquals(3, records.size)
        assertEquals(1000L, records[0].timestamp)
        assertEquals(2000L, records[1].timestamp)
        assertEquals(3000L, records[2].timestamp)
        assertEquals(37.0, records[0].latitude, 1e-9)
        assertEquals(-122.0, records[0].longitude, 1e-9)
        assertEquals(3, records[0].bandNumber)

        val withCaBands = cellRecordRepository.getBySessionIdOnceWithCaBands(sessionId)
        assertEquals(3, withCaBands.size)
        for (wrapper in withCaBands) {
            assertEquals(1, wrapper.caBands.size)
            assertEquals(3, wrapper.caBands[0].bandNumber)
            assertEquals(1800, wrapper.caBands[0].earfcn)
            assertEquals(42, wrapper.caBands[0].pci)
        }

        val session = sessionRepository.getById(sessionId).first()
        assertNotNull(session)
        assertEquals(3, session!!.pointCount)
        assertNotNull(session.endedAt)
        assertEquals("OUTDOOR", session.recordingMode)
    }

    @Test
    fun importGeoJson_returnsImportSummaryWithCorrectCounts() = runBlocking {
        val geoJson = geoJsonFeatureCollection()

        val summary = useCase.importGeoJson(geoJson, "Test GeoJSON Import")

        assertEquals("Test GeoJSON Import", summary.sessionName)
        assertEquals(3, summary.importedCount)
        assertEquals(0, summary.errorCount)
        assertEquals("OUTDOOR", summary.recordingMode)
        assertTrue(summary.errors.isEmpty())
    }

    @Test
    fun importGeoJson_invalidJson() = runBlocking {
        val summary = useCase.importGeoJson("not valid json", "Invalid GeoJSON")

        assertEquals(0, summary.importedCount)
        assertEquals(1, summary.errorCount)
        assertTrue(summary.errors.isNotEmpty())

        val sessions = sessionRepository.getAll().first()
        assertEquals(1, sessions.size)
        val sessionId = sessions[0].id

        val records = cellRecordRepository.getBySessionIdOnce(sessionId)
        assertEquals(0, records.size)

        val session = sessionRepository.getById(sessionId).first()
        assertNotNull(session)
        assertEquals(0, session!!.pointCount)
        assertNull(session.endedAt)
    }

    @Test
    fun importGeoJson_emptyFeatureCollection() = runBlocking {
        val geoJson = """{"type":"FeatureCollection","features":[]}"""

        val summary = useCase.importGeoJson(geoJson, "Empty FC")

        assertEquals(0, summary.importedCount)
        assertEquals(0, summary.errorCount)
        assertEquals("OUTDOOR", summary.recordingMode)

        val sessions = sessionRepository.getAll().first()
        assertEquals(1, sessions.size)
        val sessionId = sessions[0].id

        val records = cellRecordRepository.getBySessionIdOnce(sessionId)
        assertEquals(0, records.size)

        val session = sessionRepository.getById(sessionId).first()
        assertNotNull(session)
        assertEquals(0, session!!.pointCount)
        assertNull(session.endedAt)
    }

    @Test
    fun importCsv_indoorMode_setsRecordingModeAndRelativeCoordinates() = runBlocking {
        val csv = "timestamp,relative_x,relative_y\n" +
            "1000,1.5,2.5\n" +
            "2000,3.0,4.0\n" +
            "3000,5.5,6.5\n"

        val summary = useCase.importCsv(csv, "Indoor Test")

        assertEquals("INDOOR", summary.recordingMode)

        val sessions = sessionRepository.getAll().first()
        assertEquals(1, sessions.size)
        val session = sessions[0]
        assertEquals("INDOOR", session.recordingMode)
        val sessionId = session.id

        val records = cellRecordRepository.getBySessionIdOnce(sessionId).sortedBy { it.timestamp }
        assertEquals(3, records.size)

        assertEquals(0.0, records[0].latitude, 1e-9)
        assertEquals(0.0, records[0].longitude, 1e-9)
        assertEquals(1.5, records[0].relativeX ?: 0.0, 1e-9)
        assertEquals(2.5, records[0].relativeY ?: 0.0, 1e-9)

        assertEquals(0.0, records[1].latitude, 1e-9)
        assertEquals(0.0, records[1].longitude, 1e-9)
        assertEquals(3.0, records[1].relativeX ?: 0.0, 1e-9)
        assertEquals(4.0, records[1].relativeY ?: 0.0, 1e-9)

        assertEquals(0.0, records[2].latitude, 1e-9)
        assertEquals(0.0, records[2].longitude, 1e-9)
        assertEquals(5.5, records[2].relativeX ?: 0.0, 1e-9)
        assertEquals(6.5, records[2].relativeY ?: 0.0, 1e-9)

        val refreshedSession = sessionRepository.getById(sessionId).first()
        assertNotNull(refreshedSession)
        assertEquals(3, refreshedSession!!.pointCount)
        assertNotNull(refreshedSession.endedAt)
    }

    private fun csvWithCaBands(): String {
        val caBands = "\"[{\"\"band\"\":3,\"\"earfcn\"\":1800,\"\"pci\"\":42,\"\"rsrp\"\":-95,\"\"rsrq\"\":-10,\"\"sinr\"\":15}]\""
        return "timestamp,lat,lon,alt,accuracy,rat,pci,rsrp,rsrq,sinr,mcc,mnc,band,earfcn,tac,ca_bands\n" +
            "1000,37.0,-122.0,10.0,5.0,4G,100,-90,-10,5,310,410,3,1800,1,$caBands\n" +
            "2000,37.1,-122.1,11.0,5.0,4G,100,-91,-11,6,310,410,3,1800,1,$caBands\n" +
            "3000,37.2,-122.2,12.0,5.0,4G,100,-92,-12,7,310,410,3,1800,1,$caBands"
    }

    private fun geoJsonFeatureCollection(): String = """
        {
          "type": "FeatureCollection",
          "features": [
            {
              "type": "Feature",
              "geometry": {"type": "Point", "coordinates": [-122.0, 37.0, 10.0]},
              "properties": {
                "timestamp": 1000, "rat": "4G", "pci": 100, "rsrp": -90, "rsrq": -10, "sinr": 5,
                "mcc": "310", "mnc": "410", "band": 3, "earfcn": 1800,
                "caBands": [{"band": 3, "earfcn": 1800, "pci": 42, "rsrp": -95, "rsrq": -10, "sinr": 15}]
              }
            },
            {
              "type": "Feature",
              "geometry": {"type": "Point", "coordinates": [-122.1, 37.1, 11.0]},
              "properties": {
                "timestamp": 2000, "rat": "4G", "pci": 100, "rsrp": -91, "rsrq": -11, "sinr": 6,
                "mcc": "310", "mnc": "410", "band": 3, "earfcn": 1800,
                "caBands": [{"band": 3, "earfcn": 1800, "pci": 42, "rsrp": -95, "rsrq": -10, "sinr": 15}]
              }
            },
            {
              "type": "Feature",
              "geometry": {"type": "Point", "coordinates": [-122.2, 37.2, 12.0]},
              "properties": {
                "timestamp": 3000, "rat": "4G", "pci": 100, "rsrp": -92, "rsrq": -12, "sinr": 7,
                "mcc": "310", "mnc": "410", "band": 3, "earfcn": 1800,
                "caBands": [{"band": 3, "earfcn": 1800, "pci": 42, "rsrp": -95, "rsrq": -10, "sinr": 15}]
              }
            }
          ]
        }
    """.trimIndent()
}
