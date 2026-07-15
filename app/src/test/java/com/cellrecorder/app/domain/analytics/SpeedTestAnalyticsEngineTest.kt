package com.cellrecorder.app.domain.analytics

import com.cellrecorder.app.data.local.entity.SpeedTestRecordEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class SpeedTestAnalyticsEngineTest {

    private fun record(
        downloadBps: Long? = null,
        uploadBps: Long? = null,
        downloadSucceeded: Boolean = downloadBps != null,
        uploadSucceeded: Boolean? = if (uploadBps != null) true else null,
        rsrpAtTest: Int? = null,
        ratAtTest: String? = null,
        dataSimSlotIndex: Int? = null,
        serverName: String? = "Server",
        timestamp: Long = 0,
        finishedAt: Long = 0L,
        errorMessage: String? = null,
        networkType: String? = null
    ): SpeedTestRecordEntity = SpeedTestRecordEntity(
        id = 0,
        sessionId = 1L,
        timestamp = timestamp,
        finishedAt = finishedAt,
        downloadBps = downloadBps,
        uploadBps = uploadBps,
        serverName = serverName,
        serverHost = null,
        serverLocation = null,
        serverId = null,
        dataSimSlotIndex = dataSimSlotIndex,
        ratAtTest = ratAtTest,
        rsrpAtTest = rsrpAtTest,
        bandAtTest = null,
        downloadSucceeded = downloadSucceeded,
        uploadSucceeded = uploadSucceeded,
        errorMessage = errorMessage,
        networkType = networkType
    )

    @Nested
    inner class AnalyzeEmpty {

        @Test
        fun `empty records returns null`() {
            assertNull(SpeedTestAnalyticsEngine.analyze(emptyList()))
        }
    }

    @Nested
    inner class AnalyzeAverages {

        @Test
        fun `avg download is null when no records have downloadBps`() {
            val result = SpeedTestAnalyticsEngine.analyze(
                listOf(record(downloadBps = null, downloadSucceeded = true))
            )
            assertNull(result?.avgDownloadBps)
        }

        @Test
        fun `avg download is mean of records with downloadBps`() {
            val result = SpeedTestAnalyticsEngine.analyze(
                listOf(
                    record(downloadBps = 10_000_000L),
                    record(downloadBps = 20_000_000L),
                    record(downloadBps = 30_000_000L)
                )
            )
            assertEquals(20_000_000L, result?.avgDownloadBps)
        }

        @Test
        fun `avg upload is mean of records with uploadBps`() {
            val result = SpeedTestAnalyticsEngine.analyze(
                listOf(
                    record(uploadBps = 1_000_000L),
                    record(uploadBps = 3_000_000L)
                )
            )
            assertEquals(2_000_000L, result?.avgUploadBps)
        }

        @Test
        fun `download-failed rows (downloadBps=null) are excluded from download average`() {
            val result = SpeedTestAnalyticsEngine.analyze(
                listOf(
                    record(downloadBps = 10_000_000L, downloadSucceeded = true),
                    record(downloadBps = null, downloadSucceeded = false)
                )
            )
            assertEquals(10_000_000L, result?.avgDownloadBps)
        }

        @Test
        fun `legacy partial-success rows (downloadSucceeded=true, uploadSucceeded=false) are retroactively included in download average`() {
            val result = SpeedTestAnalyticsEngine.analyze(
                listOf(
                    record(downloadBps = 10_000_000L, uploadBps = null, downloadSucceeded = true, uploadSucceeded = false, errorMessage = "No data transferred: upload measurement failed"),
                    record(downloadBps = 20_000_000L, uploadBps = null, downloadSucceeded = true, uploadSucceeded = false, errorMessage = "No data transferred: upload measurement failed")
                )
            )
            assertEquals(15_000_000L, result?.avgDownloadBps)
            assertNull(result?.avgUploadBps)
        }

        @Test
        fun `partial-success rows contribute to download stats but not upload stats`() {
            val result = SpeedTestAnalyticsEngine.analyze(
                listOf(
                    record(downloadBps = 10_000_000L, uploadBps = null, downloadSucceeded = true, uploadSucceeded = false, errorMessage = "Upload probe failed: HTTP 500"),
                    record(downloadBps = 20_000_000L, uploadBps = 5_000_000L, downloadSucceeded = true, uploadSucceeded = true)
                )
            )
            assertEquals(15_000_000L, result?.avgDownloadBps)
            assertEquals(5_000_000L, result?.avgUploadBps)
        }

        @Test
        fun `upload-disabled rows contribute to download stats only`() {
            val result = SpeedTestAnalyticsEngine.analyze(
                listOf(
                    record(downloadBps = 10_000_000L, uploadBps = null, downloadSucceeded = true, uploadSucceeded = null),
                    record(downloadBps = 20_000_000L, uploadBps = null, downloadSucceeded = true, uploadSucceeded = null)
                )
            )
            assertEquals(15_000_000L, result?.avgDownloadBps)
            assertNull(result?.avgUploadBps)
        }
    }

    @Nested
    inner class AnalyzeSuccessRate {

        @Test
        fun `successRate is fraction of records with downloadSucceeded=true`() {
            val result = SpeedTestAnalyticsEngine.analyze(
                listOf(
                    record(downloadSucceeded = true),
                    record(downloadSucceeded = true),
                    record(downloadBps = null, downloadSucceeded = false),
                    record(downloadBps = null, downloadSucceeded = false)
                )
            )
            assertEquals(0.5, result?.successRate ?: 0.0, 1e-9)
        }

        @Test
        fun `partial-success rows count as success for successRate (download is headline)`() {
            val result = SpeedTestAnalyticsEngine.analyze(
                listOf(
                    record(downloadBps = 10_000_000L, downloadSucceeded = true, uploadSucceeded = false, errorMessage = "Upload probe failed: HTTP 500"),
                    record(downloadBps = 20_000_000L, downloadSucceeded = true, uploadSucceeded = true)
                )
            )
            assertEquals(1.0, result?.successRate ?: 0.0, 1e-9)
        }

        @Test
        fun `sampleCount includes all records and failureCount only download-failed`() {
            val result = SpeedTestAnalyticsEngine.analyze(
                listOf(
                    record(downloadSucceeded = true),
                    record(downloadBps = null, downloadSucceeded = false)
                )
            )
            assertEquals(2, result?.sampleCount)
            assertEquals(1, result?.failureCount)
        }

        @Test
        fun `serverName is from first record with downloadSucceeded=true`() {
            val result = SpeedTestAnalyticsEngine.analyze(
                listOf(
                    record(downloadBps = null, downloadSucceeded = false, serverName = "Failed"),
                    record(downloadBps = 1L, downloadSucceeded = true, serverName = "Server1"),
                    record(downloadBps = 2L, downloadSucceeded = true, serverName = "Server2")
                )
            )
            assertEquals("Server1", result?.serverName)
        }
    }

    @Nested
    inner class AnalyzeWifiSkipExclusion {

        @Test
        fun `SKIPPED_WIFI records are excluded from sampleCount`() {
            val result = SpeedTestAnalyticsEngine.analyze(
                listOf(
                    record(downloadSucceeded = true, downloadBps = 10_000_000L),
                    record(downloadSucceeded = false, errorMessage = "SKIPPED_WIFI", networkType = "WIFI"),
                    record(downloadSucceeded = false, errorMessage = "SKIPPED_WIFI", networkType = "WIFI")
                )
            )
            assertEquals(1, result?.sampleCount)
            assertEquals(0, result?.failureCount)
            assertEquals(1.0, result?.successRate ?: 0.0, 1e-9)
        }

        @Test
        fun `only SKIPPED_WIFI records returns empty analytics not null`() {
            val result = SpeedTestAnalyticsEngine.analyze(
                listOf(
                    record(downloadSucceeded = false, errorMessage = "SKIPPED_WIFI", networkType = "WIFI")
                )
            )
            assertNotNull(result)
            assertEquals(0, result?.sampleCount)
            assertEquals(0, result?.failureCount)
            assertEquals(0.0, result?.successRate ?: -1.0, 1e-9)
        }

        @Test
        fun `SKIPPED_WIFI mixed with real download failures counts only real failures`() {
            val result = SpeedTestAnalyticsEngine.analyze(
                listOf(
                    record(downloadSucceeded = true, downloadBps = 5_000_000L),
                    record(downloadSucceeded = false, errorMessage = "SKIPPED_WIFI", networkType = "WIFI"),
                    record(downloadBps = null, downloadSucceeded = false, errorMessage = "No data transferred: download measurement failed")
                )
            )
            assertEquals(2, result?.sampleCount)
            assertEquals(1, result?.failureCount)
            assertEquals(0.5, result?.successRate ?: 0.0, 1e-9)
        }
    }

    @Nested
    inner class Percentile {

        @Test
        fun `empty list returns null`() {
            assertNull(SpeedTestAnalyticsEngine.percentile(emptyList(), 0.95))
        }

        @Test
        fun `single value returns that value for both p50 and p95`() {
            assertEquals(42L, SpeedTestAnalyticsEngine.percentile(listOf(42L), 0.5))
            assertEquals(42L, SpeedTestAnalyticsEngine.percentile(listOf(42L), 0.95))
        }

        @Test
        fun `p95 of 10-element sorted list picks the maximum (ceil 9 5 equals 10)`() {
            val values = (1..10L).toList()
            assertEquals(10L, SpeedTestAnalyticsEngine.percentile(values, 0.95))
        }

        @Test
        fun `p50 of 10-element list picks the 5th smallest (ceil 5 0 equals 5)`() {
            val values = (1..10L).toList()
            assertEquals(5L, SpeedTestAnalyticsEngine.percentile(values, 0.5))
        }

        @Test
        fun `p50 of 9-element list picks the 5th smallest (ceil 4 5 equals 5)`() {
            val values = (1..9L).toList()
            assertEquals(5L, SpeedTestAnalyticsEngine.percentile(values, 0.5))
        }

        @Test
        fun `p95 of 9-element list picks the 9th smallest (ceil 8 55 equals 9)`() {
            val values = (1..9L).toList()
            assertEquals(9L, SpeedTestAnalyticsEngine.percentile(values, 0.95))
        }

        @Test
        fun `percentile works on unsorted input (sorts internally)`() {
            val values = listOf(30L, 10L, 20L, 50L, 40L)
            assertEquals(30L, SpeedTestAnalyticsEngine.percentile(values, 0.5))
            assertEquals(50L, SpeedTestAnalyticsEngine.percentile(values, 0.95))
        }

        @Test
        fun `p0 coerced to minimum rank 1`() {
            assertEquals(1L, SpeedTestAnalyticsEngine.percentile((1..10L).toList(), 0.0))
        }

        @Test
        fun `p100 coerced to maximum rank`() {
            assertEquals(10L, SpeedTestAnalyticsEngine.percentile((1..10L).toList(), 1.0))
        }
    }

    @Nested
    inner class AnalyzePercentileIntegration {

        @Test
        fun `p95DownloadBps matches percentile of records with downloadBps`() {
            val result = SpeedTestAnalyticsEngine.analyze(
                (1..10L).map { record(downloadBps = it * 1_000_000L) }
            )
            assertEquals(10_000_000L, result?.p95DownloadBps)
        }

        @Test
        fun `p95UploadBps matches percentile of records with uploadBps`() {
            val result = SpeedTestAnalyticsEngine.analyze(
                (1..9L).map { record(uploadBps = it * 100_000L) }
            )
            assertEquals(900_000L, result?.p95UploadBps)
        }

        @Test
        fun `p95DownloadBps is null when no records have downloadBps`() {
            val result = SpeedTestAnalyticsEngine.analyze(
                listOf(record(downloadBps = null, downloadSucceeded = true))
            )
            assertNull(result?.p95DownloadBps)
        }
    }

    @Nested
    inner class CorrelationBins {

        @Test
        fun `RSRP correlation groups records by RSRP bucket`() {
            val result = SpeedTestAnalyticsEngine.analyze(
                listOf(
                    record(downloadBps = 50_000_000L, rsrpAtTest = -70),
                    record(downloadBps = 20_000_000L, rsrpAtTest = -85),
                    record(downloadBps = 5_000_000L, rsrpAtTest = -95),
                    record(downloadBps = 1_000_000L, rsrpAtTest = -110)
                )
            )
            val bins = result?.downloadByRsrp
            assertNotNull(bins)
            assertEquals(4, bins?.size)
            assertEquals(">-80", bins?.get(0)?.label)
            assertEquals("-80~-90", bins?.get(1)?.label)
            assertEquals("-90~-100", bins?.get(2)?.label)
            assertEquals("<-100", bins?.get(3)?.label)
            assertEquals(50_000_000.0, bins?.get(0)?.values?.firstOrNull()?.value ?: 0.0, 1.0)
            assertEquals(20_000_000.0, bins?.get(1)?.values?.firstOrNull()?.value ?: 0.0, 1.0)
            assertEquals(5_000_000.0, bins?.get(2)?.values?.firstOrNull()?.value ?: 0.0, 1.0)
            assertEquals(1_000_000.0, bins?.get(3)?.values?.firstOrNull()?.value ?: 0.0, 1.0)
        }

        @Test
        fun `RSRP bin for empty bucket has null value`() {
            val result = SpeedTestAnalyticsEngine.analyze(
                listOf(record(downloadBps = 50_000_000L, rsrpAtTest = -70))
            )
            val bins = result?.downloadByRsrp
            assertEquals(">-80", bins?.get(0)?.label)
            assertNotNull(bins?.get(0)?.values?.firstOrNull()?.value)
            assertNull(bins?.get(1)?.values?.firstOrNull()?.value)
            assertNull(bins?.get(2)?.values?.firstOrNull()?.value)
            assertNull(bins?.get(3)?.values?.firstOrNull()?.value)
        }

        @Test
        fun `RAT correlation groups records by RAT label`() {
            val result = SpeedTestAnalyticsEngine.analyze(
                listOf(
                    record(downloadBps = 50_000_000L, ratAtTest = "4G_LTE"),
                    record(downloadBps = 20_000_000L, ratAtTest = "4G_LTE"),
                    record(downloadBps = 200_000_000L, ratAtTest = "5G_NSA")
                )
            )
            val bins = result?.downloadByRat ?: emptyList()
            assertEquals(2, bins.size)
            assertEquals(true, bins.any { it.label == "4G_LTE" && it.values.firstOrNull()?.value == 35_000_000.0 })
            assertEquals(true, bins.any { it.label == "5G_NSA" && it.values.firstOrNull()?.value == 200_000_000.0 })
        }

        @Test
        fun `RAT correlation excludes records with null RAT`() {
            val result = SpeedTestAnalyticsEngine.analyze(
                listOf(
                    record(downloadBps = 50_000_000L, ratAtTest = "4G_LTE"),
                    record(downloadBps = 20_000_000L, ratAtTest = null)
                )
            )
            assertEquals(1, result?.downloadByRat?.size)
            assertEquals("4G_LTE", result?.downloadByRat?.first()?.label)
        }

        @Test
        fun `SIM correlation groups records by SIM slot index and labels as SIM1 SIM2`() {
            val result = SpeedTestAnalyticsEngine.analyze(
                listOf(
                    record(downloadBps = 50_000_000L, dataSimSlotIndex = 0),
                    record(downloadBps = 10_000_000L, dataSimSlotIndex = 1)
                )
            )
            val bins = result?.downloadBySim ?: emptyList()
            assertEquals(2, bins.size)
            assertEquals("SIM 1", bins.first { it.label == "SIM 1" }.label)
            assertEquals(50_000_000.0, bins.first { it.label == "SIM 1" }.values.first().value ?: 0.0, 1.0)
            assertEquals("SIM 2", bins.first { it.label == "SIM 2" }.label)
            assertEquals(10_000_000.0, bins.first { it.label == "SIM 2" }.values.first().value ?: 0.0, 1.0)
        }

        @Test
        fun `SIM correlation excludes records with null dataSimSlotIndex`() {
            val result = SpeedTestAnalyticsEngine.analyze(
                listOf(
                    record(downloadBps = 50_000_000L, dataSimSlotIndex = null),
                    record(downloadBps = 10_000_000L, dataSimSlotIndex = 0)
                )
            )
            assertEquals(1, result?.downloadBySim?.size)
            assertEquals("SIM 1", result?.downloadBySim?.first()?.label)
        }

        @Test
        fun `uploadByRsrp is non-null with null SimValue values when no records have uploadBps`() {
            val result = SpeedTestAnalyticsEngine.analyze(
                listOf(record(downloadBps = 50_000_000L, uploadBps = null, rsrpAtTest = -70))
            )
            val bins = result?.uploadByRsrp
            assertNotNull(bins)
            assertEquals(4, bins?.size, "Engine returns 4 RSRP bins regardless of input; takeIf is dead code")
            bins?.forEach { bin ->
                assertTrue(bin.values.all { it.value == null }, "All SimValue.value fields are null when no uploadBps")
            }
        }

        @Test
        fun `uploadByRsrp is populated when records have uploadBps`() {
            val result = SpeedTestAnalyticsEngine.analyze(
                listOf(
                    record(downloadBps = 50_000_000L, uploadBps = 1_000_000L, rsrpAtTest = -70)
                )
            )
            assertNotNull(result?.uploadByRsrp)
            assertEquals(1_000_000.0, result?.uploadByRsrp?.first()?.values?.firstOrNull()?.value ?: 0.0, 1.0)
        }
    }

    @Nested
    inner class DownloadHistogram {

        @Test
        fun `empty download values returns empty histogram`() {
            val result = SpeedTestAnalyticsEngine.analyze(
                listOf(record(downloadBps = null, downloadSucceeded = true))
            )
            assertTrue((result?.downloadHistogram ?: emptyList()).isEmpty())
        }

        @Test
        fun `values below 5Mbps go into the first bin (0bps~5Mbps)`() {
            val result = SpeedTestAnalyticsEngine.analyze(
                listOf(record(downloadBps = 1_000_000L))
            )
            val bins = result?.downloadHistogram ?: emptyList()
            assertEquals(true, bins.isNotEmpty())
            val firstBin = bins.first()
            assertEquals("0bps~5Mbps", firstBin.label)
            assertEquals(1, firstBin.count)
        }

        @Test
        fun `values above the highest bin threshold go into the above-bin`() {
            val result = SpeedTestAnalyticsEngine.analyze(
                listOf(record(downloadBps = 1_000_000_000L))
            )
            val bins = result?.downloadHistogram ?: emptyList()
            assertEquals(true, bins.any { it.label.startsWith(">") })
            val aboveBin = bins.first { it.label.startsWith(">") }
            assertEquals(1, aboveBin.count)
        }

        @Test
        fun `histogram includes count and percentage label`() {
            val result = SpeedTestAnalyticsEngine.analyze(
                listOf(
                    record(downloadBps = 1_000_000L),
                    record(downloadBps = 50_000_000L),
                    record(downloadBps = 50_000_000L)
                )
            )
            val bins = result?.downloadHistogram ?: emptyList()
            val binWith50Mbps = bins.first { it.label.contains("50") }
            assertEquals(true, binWith50Mbps.countLabel.contains("66.7%") || binWith50Mbps.countLabel.contains("66.7"))
        }

        @Test
        fun `histogram with all values in one bin`() {
            val result = SpeedTestAnalyticsEngine.analyze(
                (1..5L).map { record(downloadBps = 1_000_000L) }
            )
            val bins = result?.downloadHistogram ?: emptyList()
            val firstBin = bins.first()
            assertEquals(5, firstBin.count)
        }

        @Test
        fun `histogram values spanning multiple bins`() {
            val result = SpeedTestAnalyticsEngine.analyze(
                listOf(
                    record(downloadBps = 1_000_000L),
                    record(downloadBps = 7_000_000L),
                    record(downloadBps = 30_000_000L),
                    record(downloadBps = 75_000_000L),
                    record(downloadBps = 150_000_000L),
                    record(downloadBps = 1_000_000_000L)
                )
            )
            val bins = result?.downloadHistogram ?: emptyList()
            assertEquals(true, bins.size >= 2)
        }
    }

    @Nested
    inner class AnalyzeDuration {

        @Test
        fun `avgDurationMs is null when all records are legacy (finishedAt = 0)`() {
            val result = SpeedTestAnalyticsEngine.analyze(
                listOf(
                    record(downloadBps = 10_000_000L, timestamp = 1000L, finishedAt = 0L),
                    record(downloadBps = 20_000_000L, timestamp = 2000L, finishedAt = 0L)
                )
            )
            assertNull(result?.avgDurationMs)
        }

        @Test
        fun `avgDurationMs is null when all records are instant bail-outs (finishedAt = timestamp)`() {
            val result = SpeedTestAnalyticsEngine.analyze(
                listOf(
                    record(downloadBps = null, downloadSucceeded = false, timestamp = 5000L, finishedAt = 5000L, errorMessage = "SKIPPED_WIFI"),
                    record(downloadBps = null, downloadSucceeded = false, timestamp = 6000L, finishedAt = 6000L, errorMessage = "Server selection failed")
                )
            )
            assertNull(result?.avgDurationMs)
        }

        @Test
        fun `avgDurationMs computed from records with positive duration`() {
            val result = SpeedTestAnalyticsEngine.analyze(
                listOf(
                    record(downloadBps = 10_000_000L, timestamp = 1000L, finishedAt = 4000L), // 3s
                    record(downloadBps = 20_000_000L, timestamp = 5000L, finishedAt = 12000L)  // 7s
                )
            )
            assertNotNull(result?.avgDurationMs)
            assertEquals(5000L, result?.avgDurationMs) // avg(3000, 7000) = 5000
        }

        @Test
        fun `avgDurationMs ignores legacy and instant rows in mixed set`() {
            val result = SpeedTestAnalyticsEngine.analyze(
                listOf(
                    record(downloadBps = 10_000_000L, timestamp = 1000L, finishedAt = 4000L),   // 3s, counted
                    record(downloadBps = 20_000_000L, timestamp = 5000L, finishedAt = 0L),      // legacy, ignored
                    record(downloadBps = null, downloadSucceeded = false, timestamp = 6000L, finishedAt = 6000L, errorMessage = "SKIPPED_WIFI") // instant, ignored
                )
            )
            assertNotNull(result?.avgDurationMs)
            assertEquals(3000L, result?.avgDurationMs) // only the 3s record counts
        }
    }
}
