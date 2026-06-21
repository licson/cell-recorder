package com.cellrecorder.app.domain.speedtest

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Unit tests for the pure derived properties of [SpeedTestProtocolConfig].
 *
 * The XML parsing logic in [SpeedTestConfigParser.parse] uses `android.util.Xml.newPullParser()`,
 * which is not available in plain JVM unit tests (the Android stub throws
 * "Method newInstance not mocked"). `org.xmlpull.v1.XmlPullParserFactory` is likewise stubbed
 * out by the Android SDK. The parsing flow is therefore deferred to `androidTest` or a
 * future Robolectric-enabled change; the high-value logic under test here is the pure
 * derivation of `downloadThreads`, `uploadSizes`, `uploadCount`, and `uploadMax` from
 * `ratio`/`maxChunkCount`/`threadCount` — these are the computations whose regression
 * risk is highest.
 */
class SpeedTestConfigParserTest {

    private fun config(
        threadCount: Int = 4,
        ratio: Int = 4,
        maxChunkCount: Int = 4
    ): SpeedTestProtocolConfig = SpeedTestProtocolConfig(
        client = SpeedTestClientConfig(ip = "", lat = 0.0, lon = 0.0, isp = "", hasValidLocation = false),
        download = SpeedTestDownloadConfig(threadsPerUrl = 4, testLengthSec = 10),
        upload = SpeedTestUploadConfig(threads = 4, ratio = ratio, testLengthSec = 10, maxChunkCount = maxChunkCount),
        server = SpeedTestServerConfig(ignoreIds = emptyList(), threadCount = threadCount)
    )

    @Nested
    inner class DownloadThreads {

        @Test
        fun `downloadThreads is server threadCount multiplied by 2`() {
            assertEquals(8, config(threadCount = 4).downloadThreads)
            assertEquals(2, config(threadCount = 1).downloadThreads)
            assertEquals(16, config(threadCount = 8).downloadThreads)
            assertEquals(0, config(threadCount = 0).downloadThreads)
        }
    }

    @Nested
    inner class UploadSizes {

        private val base = listOf(32768, 65536, 131072, 262144, 524288, 1048576, 7340032)

        @Test
        fun `ratio 1 returns full 7-element list`() {
            assertEquals(base, config(ratio = 1).uploadSizes)
            assertEquals(7, config(ratio = 1).uploadSizes.size)
        }

        @Test
        fun `ratio 4 drops first 3 elements leaving 4`() {
            assertEquals(base.drop(3), config(ratio = 4).uploadSizes)
            assertEquals(4, config(ratio = 4).uploadSizes.size)
        }

        @Test
        fun `ratio 7 leaves single largest size`() {
            assertEquals(listOf(7340032), config(ratio = 7).uploadSizes)
            assertEquals(1, config(ratio = 7).uploadSizes.size)
        }

        @Test
        fun `ratio 8 yields empty upload sizes (edge case — all sizes dropped)`() {
            assertTrue(config(ratio = 8).uploadSizes.isEmpty())
        }

        @Test
        fun `ratio beyond base list length yields empty upload sizes`() {
            assertTrue(config(ratio = 100).uploadSizes.isEmpty())
        }

        @Test
        fun `first six sizes double incrementally and seventh is a special large size`() {
            val sizes = config(ratio = 1).uploadSizes
            assertEquals(7, sizes.size)
            for (i in 0 until sizes.size - 2) {
                assertEquals(sizes[i] * 2, sizes[i + 1], "Size at index $i should double next index")
            }
            assertEquals(7340032, sizes.last(), "Last size is a special large upload size (not 2x of 1MiB)")
        }
    }

    @Nested
    inner class UploadCount {

        @Test
        fun `exact division yields integer quotient`() {
            val cfg = config(ratio = 4, maxChunkCount = 4)
            assertEquals(1, cfg.uploadCount)
        }

        @Test
        fun `non-exact division rounds up via ceil`() {
            val cfg = config(ratio = 4, maxChunkCount = 10)
            val sizeCount = cfg.uploadSizes.size
            assertEquals(kotlin.math.ceil(10.0 / sizeCount).toInt(), cfg.uploadCount)
            assertEquals(3, cfg.uploadCount)
        }

        @Test
        fun `maxChunkCount 1 with 4 sizes yields 1`() {
            assertEquals(1, config(ratio = 4, maxChunkCount = 1).uploadCount)
        }

        @Test
        fun `large maxChunkCount produces proportionally large count`() {
            val cfg = config(ratio = 4, maxChunkCount = 100)
            val sizeCount = cfg.uploadSizes.size
            assertEquals(kotlin.math.ceil(100.0 / sizeCount).toInt(), cfg.uploadCount)
            assertEquals(25, cfg.uploadCount)
        }

        @Test
        fun `uploadCount uses ceil to avoid dropping remainder`() {
            val cfg = config(ratio = 4, maxChunkCount = 7)
            val sizeCount = cfg.uploadSizes.size
            assertEquals(kotlin.math.ceil(7.0 / sizeCount).toInt(), cfg.uploadCount)
        }
    }

    @Nested
    inner class UploadMax {

        @Test
        fun `uploadMax equals uploadCount times number of upload sizes`() {
            val cfg = config(ratio = 4, maxChunkCount = 10)
            assertEquals(cfg.uploadCount * cfg.uploadSizes.size, cfg.uploadMax)
            assertEquals(3 * 4, cfg.uploadMax)
        }

        @Test
        fun `uploadMax is at least uploadCount when only one size remains`() {
            val cfg = config(ratio = 7, maxChunkCount = 5)
            assertEquals(cfg.uploadCount * 1, cfg.uploadMax)
        }

        @Test
        fun `uploadMax is zero when uploadSizes is empty`() {
            val cfg = config(ratio = 8, maxChunkCount = 5)
            assertEquals(0, cfg.uploadMax)
        }
    }
}
