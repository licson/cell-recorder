package com.cellrecorder.app.domain.usecase

import com.cellrecorder.app.data.local.entity.SpeedTestRecordEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ExportSpeedTestUseCaseTest {

    private val useCase = ExportSpeedTestUseCase()

    private fun record(
        timestamp: Long = 1000L,
        finishedAt: Long = 0L,
        downloadBps: Long? = 50_000_000L,
        uploadBps: Long? = 10_000_000L,
        serverName: String? = "TestServer",
        serverHost: String? = "host.example.com",
        serverLocation: String? = "City",
        serverId: Long? = 42L,
        succeeded: Boolean = true,
        errorMessage: String? = null,
        networkType: String? = "CELLULAR"
    ): SpeedTestRecordEntity = SpeedTestRecordEntity(
        id = 1,
        sessionId = 1,
        timestamp = timestamp,
        finishedAt = finishedAt,
        downloadBps = downloadBps,
        uploadBps = uploadBps,
        serverName = serverName,
        serverHost = serverHost,
        serverLocation = serverLocation,
        serverId = serverId,
        dataSimSlotIndex = 0,
        ratAtTest = "4G",
        rsrpAtTest = -90,
        bandAtTest = 3,
        succeeded = succeeded,
        errorMessage = errorMessage,
        networkType = networkType
    )

    @Test
    fun `exportCsv returns null for empty records`() {
        assertNull(useCase.exportCsv("test", emptyList()))
    }

    @Test
    fun `header contains finished_at immediately after timestamp`() {
        val data = useCase.exportCsv("test", listOf(record()))!!
        val header = data.content.lineSequence().first()
        val columns = header.split(",")
        assertEquals("timestamp", columns[0])
        assertEquals("finished_at", columns[1])
        assertEquals("download_bps", columns[2])
    }

    @Test
    fun `row contains finishedAt value as second field`() {
        val data = useCase.exportCsv("test", listOf(record(timestamp = 1000L, finishedAt = 5000L)))!!
        val row = data.content.lineSequence().drop(1).first()
        val fields = row.split(",")
        assertEquals("1000", fields[0])
        assertEquals("5000", fields[1])
    }

    @Test
    fun `legacy row with finishedAt zero exports zero`() {
        val data = useCase.exportCsv("test", listOf(record(timestamp = 1000L, finishedAt = 0L)))!!
        val row = data.content.lineSequence().drop(1).first()
        val fields = row.split(",")
        assertEquals("0", fields[1])
    }

    @Test
    fun `instant bail-out row exports finishedAt equal to timestamp`() {
        val data = useCase.exportCsv("test", listOf(record(timestamp = 7000L, finishedAt = 7000L, succeeded = false, errorMessage = "SKIPPED_WIFI")))!!
        val row = data.content.lineSequence().drop(1).first()
        val fields = row.split(",")
        assertEquals("7000", fields[0])
        assertEquals("7000", fields[1])
    }

    @Test
    fun `suggestedFilename replaces spaces with underscores`() {
        val data = useCase.exportCsv("My Session", listOf(record()))!!
        assertEquals("My_Session_speedtest.csv", data.suggestedFilename)
    }

    @Test
    fun `csv fields with commas are quoted`() {
        val r = record(serverName = "Server, With Comma")
        val data = useCase.exportCsv("test", listOf(r))!!
        assertTrue(data.content.contains("\"Server, With Comma\""))
    }
}
