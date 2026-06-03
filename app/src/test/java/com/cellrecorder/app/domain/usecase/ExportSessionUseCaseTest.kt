package com.cellrecorder.app.domain.usecase

import com.cellrecorder.app.data.local.entity.CellRecordEntity
import com.cellrecorder.app.data.local.entity.SessionEntity
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ExportSessionUseCaseTest {

    private lateinit var useCase: ExportSessionUseCase
    private lateinit var session: SessionEntity
    private lateinit var records: List<CellRecordEntity>

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
        records = listOf(
            CellRecordEntity(
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
        )
    }

    @Test
    fun `exportCsv produces correct header`() {
        val data = useCase.exportCsv(session, records)
        assertTrue(data.content.startsWith("timestamp,lat,lon,alt,accuracy,subscription_id,sim_slot_index,rat,pci,rsrp,rsrq,sinr,enb_gnb_id,lcid,avg_latency_ms,packet_loss_pct,mcc,mnc,band,earfcn,tac"))
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
}