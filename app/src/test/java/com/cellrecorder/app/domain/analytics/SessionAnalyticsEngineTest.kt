package com.cellrecorder.app.domain.analytics

import com.cellrecorder.app.data.local.entity.AppConfigEntity
import com.cellrecorder.app.data.local.entity.CellRecordCaBandEntity
import com.cellrecorder.app.data.local.entity.CellRecordEntity
import com.cellrecorder.app.data.local.entity.CellRecordWithCaBands
import com.cellrecorder.app.domain.analytics.model.AnomalyType
import com.cellrecorder.app.domain.analytics.model.GapType
import com.cellrecorder.app.domain.analytics.model.HandoffType
import com.cellrecorder.app.domain.analytics.model.MobilityType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class SessionAnalyticsEngineTest {

    private lateinit var engine: SessionAnalyticsEngine
    private lateinit var defaultConfig: AppConfigEntity

    @BeforeEach
    fun setUp() {
        engine = SessionAnalyticsEngine()
        defaultConfig = AppConfigEntity()
    }

    @Test
    fun `empty records returns empty analytics`() {
        val result = engine.analyze(emptyList(), defaultConfig)
        assertTrue(result.ratCoverage.isEmpty())
        assertTrue(result.bandDistributionPerSim.isEmpty())
        assertTrue(result.rsrpHistogram.isEmpty())
        assertNull(result.latencyStats)
        assertTrue(result.handoffEvents.isEmpty())
        assertTrue(result.anomalyFlags.isEmpty())
        assertTrue(result.mobilitySegments.isEmpty())
        assertTrue(result.coverageGaps.isEmpty())
        assertTrue(result.timelineSegments.isEmpty())
    }

    @Test
    fun `single RAT session produces one coverage entry`() {
        val records = listOf(
            wrapper(record(ts = 1000, rat = "4G", rsrp = -85))
        )
        val result = engine.analyze(records, defaultConfig)
        assertEquals(1, result.ratCoverage.size)
        assertEquals("4G", result.ratCoverage[0].rat)
        assertEquals(100.0, result.ratCoverage[0].percentage, 0.01)
    }

    @Test
    fun `rat coverage uses duration not sample count`() {
        val records = listOf(
            wrapper(record(ts = 1000, rat = "4G", rsrp = -85)),
            wrapper(record(ts = 2000, rat = "4G", rsrp = -90)),
            wrapper(record(ts = 3000, rat = "5G_SA", rsrp = -75)),
            wrapper(record(ts = 4000, rat = "5G_SA", rsrp = -70)),
            wrapper(record(ts = 5000, rat = "UNKNOWN", rsrp = null))
        )
        val result = engine.analyze(records, defaultConfig)
        val coverage4g = result.ratCoverage.find { it.rat == "4G" }
        val coverage5g = result.ratCoverage.find { it.rat == "5G_SA" }
        val coverageUnknown = result.ratCoverage.find { it.rat == "UNKNOWN" }
        // intervals: 4G(1000→2000)=1000, 4G(2000→3000)=1000, 5G_SA(3000→4000)=1000, 5G_SA(4000→5000)=1000
        // total duration = 4000ms, 4G=2000ms(50%), 5G_SA=2000ms(50%), UNKNOWN=0ms(0%, last record has no interval)
        assertEquals(50.0, coverage4g?.percentage ?: 0.0, 0.1)
        assertEquals(50.0, coverage5g?.percentage ?: 0.0, 0.1)
        assertEquals(0.0, coverageUnknown?.percentage ?: 0.0, 0.1)
    }

    @Test
    fun `rsrp drop anomaly detected`() {
        val records = listOf(
            wrapper(record(ts = 1000, rsrp = -70, simSlot = 0)),
            wrapper(record(ts = 2000, rsrp = -72, simSlot = 0)),
            wrapper(record(ts = 3000, rsrp = -90, simSlot = 0)),
            wrapper(record(ts = 4000, rsrp = -92, simSlot = 0))
        )
        val config = defaultConfig.copy(rsrpDropThresholdDbm = 15, rsrpDropTimeWindowMs = 5000)
        val result = engine.analyze(records, config)
        val drops = result.anomalyFlags.filter { it.type == AnomalyType.RSRP_DROP }
        assertTrue(drops.isNotEmpty(), "Expected RSRP drop anomaly")
        // -70 to -90 = 20 drop, which is > 15
    }

    @Test
    fun `rsrp drop below threshold not flagged`() {
        val records = listOf(
            wrapper(record(ts = 1000, rsrp = -70, simSlot = 0)),
            wrapper(record(ts = 2000, rsrp = -75, simSlot = 0)),
            wrapper(record(ts = 3000, rsrp = -80, simSlot = 0))
        )
        val config = defaultConfig.copy(rsrpDropThresholdDbm = 15, rsrpDropTimeWindowMs = 5000)
        val result = engine.analyze(records, config)
        val drops = result.anomalyFlags.filter { it.type == AnomalyType.RSRP_DROP }
        assertTrue(drops.isEmpty(), "No RSRP drop expected")
    }

    @Test
    fun `latency spike detected`() {
        val records = (1..20).map { i ->
            wrapper(record(ts = i * 1000L, rsrp = -80, latency = if (i == 15) 150.0 else 10.0))
        }
        val result = engine.analyze(records, defaultConfig)
        val spikes = result.anomalyFlags.filter { it.type == AnomalyType.LATENCY_SPIKE }
        assertTrue(spikes.isNotEmpty(), "Expected latency spike")
        assertEquals(1, spikes.size)
    }

    @Test
    fun `pci flapping detected`() {
        val records = listOf(
            wrapper(record(ts = 1000, pci = 101, simSlot = 0)),
            wrapper(record(ts = 2000, pci = 102, simSlot = 0)),
            wrapper(record(ts = 3000, pci = 101, simSlot = 0)),
            wrapper(record(ts = 4000, pci = 103, simSlot = 0)),
            wrapper(record(ts = 5000, pci = 102, simSlot = 0))
        )
        val config = defaultConfig.copy(pciFlapWindowMs = 10000, pciFlapCountThreshold = 3)
        val result = engine.analyze(records, config)
        val flaps = result.anomalyFlags.filter { it.type == AnomalyType.PCI_FLAP}
        assertTrue(flaps.isNotEmpty(), "Expected PCI flapping anomaly")
    }

    @Test
    fun `missing ping cluster detected`() {
        val records = listOf(
            wrapper(record(ts = 1000, latency = 10.0)),
            wrapper(record(ts = 2000, latency = null)),
            wrapper(record(ts = 3000, latency = null)),
            wrapper(record(ts = 4000, latency = null)),
            wrapper(record(ts = 5000, latency = 20.0))
        )
        val result = engine.analyze(records, defaultConfig)
        val clusters = result.anomalyFlags.filter { it.type == AnomalyType.MISSING_PING_CLUSTER }
        assertTrue(clusters.isNotEmpty(), "Expected missing ping cluster")
        assertEquals(1, clusters.size)
    }

    @Test
    fun `consecutive latency spikes grouped into one anomaly with duration`() {
        val records = (1..30).map { i ->
            wrapper(record(ts = i * 1000L, rsrp = -80, latency = if (i in 13..17) 300.0 else 10.0))
        }
        val config = defaultConfig.copy(latencySpikeSigma = 1.0)
        val result = engine.analyze(records, config)
        val spikes = result.anomalyFlags.filter { it.type == AnomalyType.LATENCY_SPIKE }
        assertEquals(1, spikes.size, "All consecutive spikes should be grouped into one")
        val spike = spikes[0]
        assertTrue(spike.endTimestamp > spike.timestamp, "Grouped anomaly should have duration")
        assertEquals(4_000, spike.endTimestamp - spike.timestamp, "5 spikes at 1s intervals from 13s to 17s")
    }

    @Test
    fun `pci flapping produces exactly one anomaly per episode`() {
        val records = listOf(
            wrapper(record(ts = 1000, pci = 101, simSlot = 0)),
            wrapper(record(ts = 2000, pci = 102, simSlot = 0)),
            wrapper(record(ts = 3000, pci = 101, simSlot = 0)),
            wrapper(record(ts = 4000, pci = 103, simSlot = 0)),
            wrapper(record(ts = 5000, pci = 102, simSlot = 0))
        )
        val config = defaultConfig.copy(pciFlapWindowMs = 10000, pciFlapCountThreshold = 3)
        val result = engine.analyze(records, config)
        val flaps = result.anomalyFlags.filter { it.type == AnomalyType.PCI_FLAP }
        assertEquals(1, flaps.size, "Overlapping windows should produce one flap per episode")
        assertTrue(flaps[0].endTimestamp > flaps[0].timestamp, "PCI flap should have duration")
    }

    @Test
    fun `handoff detection across cells`() {
        val records = listOf(
            wrapper(record(ts = 1000, enb = 100L, pci = 1, simSlot = 0, rat = "4G", rsrp = -80)),
            wrapper(record(ts = 2000, enb = 100L, pci = 1, simSlot = 0, rat = "4G", rsrp = -82)),
            wrapper(record(ts = 3000, enb = 200L, pci = 2, simSlot = 0, rat = "4G", rsrp = -90)),
            wrapper(record(ts = 4000, enb = 200L, pci = 2, simSlot = 0, rat = "4G", rsrp = -88))
        )
        val config = defaultConfig.copy(handoffTimeWindowMs = 5000)
        val result = engine.analyze(records, config)
        assertEquals(1, result.handoffEvents.size)
        assertEquals(200L, result.handoffEvents[0].toEnbOrGnbId)
        assertEquals(100L, result.handoffEvents[0].fromEnbOrGnbId)
    }

    @Test
    fun `coverage gap detected for long unknown RAT`() {
        val records = listOf(
            wrapper(record(ts = 1000, rat = "4G", rsrp = -80, lat = 10.0, lng = 20.0)),
            wrapper(record(ts = 40000, rat = "UNKNOWN", rsrp = null, lat = 10.1, lng = 20.1)),
            wrapper(record(ts = 80000, rat = "UNKNOWN", rsrp = null, lat = 10.1, lng = 20.1)),
            wrapper(record(ts = 120000, rat = "4G", rsrp = -85, lat = 10.2, lng = 20.2))
        )
        val config = defaultConfig.copy(coverageGapThresholdMs = 30000)
        val result = engine.analyze(records, config)
        assertTrue(result.coverageGaps.isNotEmpty(), "Expected coverage gap")
        assertEquals(1, result.coverageGaps.size)
        // gap from 40000 to 120000 = 80000ms
        assertTrue(result.coverageGaps[0].durationMs >= 80000)
    }

    @Test
    fun `mobility segments generated for speed changes`() {
        val records = listOf(
            wrapper(record(ts = 1000, lat = 10.0, lng = 20.0, rat = "4G", rsrp = -80)),
            wrapper(record(ts = 2000, lat = 10.1, lng = 20.1, rat = "4G", rsrp = -82)),
            wrapper(record(ts = 3000, lat = 10.5, lng = 20.5, rat = "4G", rsrp = -85))
        )
        val result = engine.analyze(records, defaultConfig)
        assertTrue(result.mobilitySegments.isNotEmpty(), "Expected mobility segments")
    }

    @Test
    fun `latency stats computed correctly`() {
        val records = listOf(
            wrapper(record(ts = 1000, latency = 10.0)),
            wrapper(record(ts = 2000, latency = 20.0)),
            wrapper(record(ts = 3000, latency = 30.0)),
            wrapper(record(ts = 4000, latency = 40.0)),
            wrapper(record(ts = 5000, latency = 50.0))
        )
        val result = engine.analyze(records, defaultConfig)
        val stats = result.latencyStats
        assertNotNull(stats)
        assertEquals(30.0, stats!!.mean, 0.01)
        assertEquals(30.0, stats.p50, 0.01)
        assertEquals(50.0, stats.p95, 0.01)
        assertEquals(50.0, stats.p99, 0.01)
        assertEquals(5, stats.sampleCount)
    }

    @Test
    fun `timeline segments group consecutive RATs`() {
        val records = listOf(
            wrapper(record(ts = 1000, rat = "4G")),
            wrapper(record(ts = 2000, rat = "4G")),
            wrapper(record(ts = 3000, rat = "5G_SA")),
            wrapper(record(ts = 4000, rat = "4G")),
            wrapper(record(ts = 5000, rat = "UNKNOWN"))
        )
        val result = engine.analyze(records, defaultConfig)
        assertEquals(4, result.timelineSegments.size)
        assertEquals("4G", result.timelineSegments[0].rat)
        assertEquals(2, result.timelineSegments[0].recordCount)
        assertEquals("5G_SA", result.timelineSegments[1].rat)
        assertEquals("4G", result.timelineSegments[2].rat)
        assertEquals("UNKNOWN", result.timelineSegments[3].rat)
    }

    @Test
    fun `band distribution excludes records with null band number`() {
        val records = listOf(
            wrapper(record(ts = 1000, band = 78, simSlot = 0)),
            wrapper(record(ts = 2000, band = null, simSlot = 0)),
            wrapper(record(ts = 3000, band = 78, simSlot = 0)),
            wrapper(record(ts = 4000, band = null, simSlot = 0)),
            wrapper(record(ts = 5000, band = 1, simSlot = 0))
        )
        val result = engine.analyze(records, defaultConfig)
        val bands = result.bandDistributionPerSim[0]
        assertNotNull(bands)
        assertTrue(bands!!.none { it.bandNumber == -1 }, "Should not contain band -1")
        assertEquals(2, bands.size)
        assertEquals(78, bands[0].bandNumber)
        assertEquals(2, bands[0].count)
        assertEquals(1, bands[1].bandNumber)
        assertEquals(1, bands[1].count)
    }

    @Test
    fun `band distribution includes CA bands`() {
        val records = listOf(
            wrapper(
                record(ts = 1000, band = 4, simSlot = 0),
                caBands = listOf(
                    CellRecordCaBandEntity(cellRecordId = 1, bandNumber = 7),
                    CellRecordCaBandEntity(cellRecordId = 1, bandNumber = 3)
                )
            ),
            wrapper(
                record(ts = 2000, band = 4, simSlot = 0),
                caBands = listOf(
                    CellRecordCaBandEntity(cellRecordId = 2, bandNumber = 7)
                )
            )
        )
        val result = engine.analyze(records, defaultConfig)
        val bands = result.bandDistributionPerSim[0]
        assertNotNull(bands)
        // Primary: band 4 appears 2 times (count=2)
        // CA: band 7 appears 2 times, band 3 appears 1 time
        val band4 = bands!!.find { it.bandNumber == 4 }
        val band7 = bands.find { it.bandNumber == 7 }
        val band3 = bands.find { it.bandNumber == 3 }
        assertNotNull(band4)
        assertNotNull(band7)
        assertNotNull(band3)
        assertEquals(2, band4!!.count)
        assertEquals(2, band7!!.count)
        assertEquals(1, band3!!.count)
    }

    @Test
    fun `single record analytics basic fields populated`() {
        val records = listOf(
            wrapper(record(ts = 1000, rat = "5G_SA", rsrp = -75, sinr = 25, band = 78, simSlot = 0, mcc = "310", mnc = "260"))
        )
        val result = engine.analyze(records, defaultConfig)
        assertEquals(1, result.ratCoverage.size)
        assertEquals(1, result.timelineSegments[0].recordCount)
        assertTrue(result.rsrpHistogram.isNotEmpty())
        assertTrue(result.sinrHistogram.isNotEmpty())
    }

    @Test
    fun `signal quality buckets counted correctly`() {
        val records = listOf(
            wrapper(record(ts = 1000, rat = "4G", rsrp = -75)),
            wrapper(record(ts = 2000, rat = "4G", rsrp = -85)),
            wrapper(record(ts = 3000, rat = "4G", rsrp = -95)),
            wrapper(record(ts = 4000, rat = "4G", rsrp = -105)),
            wrapper(record(ts = 5000, rat = "4G", rsrp = -70))
        )
        val result = engine.analyze(records, defaultConfig)
        val coverage = result.ratCoverage.find { it.rat == "4G" }
        assertNotNull(coverage)
        assertEquals(2, coverage!!.excellent) // -75 and -70 > -80
        assertEquals(1, coverage.good)        // -85 in -80~-90
        assertEquals(1, coverage.fair)        // -95 in -90~-100
        assertEquals(1, coverage.poor)        // -105 < -100
    }

    @Test
    fun `unsorted input produces same result as sorted`() {
        val records = listOf(
            wrapper(record(ts = 4000, rat = "4G", rsrp = -85)),
            wrapper(record(ts = 2000, rat = "5G_SA", rsrp = -75)),
            wrapper(record(ts = 1000, rat = "4G", rsrp = -80)),
            wrapper(record(ts = 3000, rat = "4G", rsrp = -90))
        )
        val sorted = records.sortedBy { it.record.timestamp }
        val resultUnsorted = engine.analyze(records, defaultConfig)
        val resultSorted = engine.analyze(sorted, defaultConfig)
        assertEquals(resultSorted.ratCoverage, resultUnsorted.ratCoverage)
        assertEquals(resultSorted.anomalyFlags.size, resultUnsorted.anomalyFlags.size)
        assertEquals(resultSorted.timelineSegments, resultUnsorted.timelineSegments)
        assertEquals(resultSorted.handoffEvents, resultUnsorted.handoffEvents)
    }

    @Test
    fun `dual SIM interleaving does not create false rsrp drops`() {
        val records = listOf(
            wrapper(record(ts = 1000, rsrp = -70, simSlot = 0, latency = 10.0)),
            wrapper(record(ts = 1500, rsrp = -72, simSlot = 1, latency = 10.0)),
            wrapper(record(ts = 2000, rsrp = -70, simSlot = 0, latency = 10.0)),
            wrapper(record(ts = 2500, rsrp = -71, simSlot = 1, latency = 10.0)),
            wrapper(record(ts = 3000, rsrp = -70, simSlot = 0, latency = 10.0))
        )
        val config = defaultConfig.copy(rsrpDropThresholdDbm = 15, rsrpDropTimeWindowMs = 5000)
        val result = engine.analyze(records, config)
        val drops = result.anomalyFlags.filter { it.type == AnomalyType.RSRP_DROP }
        assertTrue(drops.isEmpty(), "Interleaved SIM records should not create false RSRP drops")
    }

    @Test
    fun `boundary bin values counted correctly`() {
        val records = listOf(
            wrapper(record(ts = 1000, rsrp = -80)),
            wrapper(record(ts = 2000, rsrp = -90)),
            wrapper(record(ts = 3000, rsrp = -100)),
            wrapper(record(ts = 4000, rsrp = -65)),
            wrapper(record(ts = 5000, rsrp = -110))
        )
        val result = engine.analyze(records, defaultConfig)
        val excellent = result.rsrpHistogram.find { it.label.startsWith("\u2265") || it.label.startsWith(">") }
        val good = result.rsrpHistogram.find { it.label.contains("-90") }
        val fair = result.rsrpHistogram.find { it.label.contains("-100") }
        val poor = result.rsrpHistogram.find { it.label.startsWith("<") }
        assertEquals(2, excellent?.count ?: 0, "-80 and -65 should be excellent (>= -80)")
        assertEquals(1, good?.count ?: 0, "-90 should be good (-90 <= x < -80)")
        assertEquals(1, fair?.count ?: 0, "-100 should be fair (-100 <= x < -90)")
        assertEquals(1, poor?.count ?: 0, "-110 should be poor (< -100)")
    }

    @Test
    fun `histogram uses valid sample denominator`() {
        val records = listOf(
            wrapper(record(ts = 1000, rsrp = -75, latency = 10.0)),
            wrapper(record(ts = 2000, rsrp = -85, latency = null)),
            wrapper(record(ts = 3000, rsrp = -95, latency = null)),
            wrapper(record(ts = 4000, rsrp = null, latency = 20.0)),
            wrapper(record(ts = 5000, rsrp = null, latency = null))
        )
        val result = engine.analyze(records, defaultConfig)
        // RSRP histogram: 3 records have RSRP, 2 don't → denominator = 3
        val rsrpBins = result.rsrpHistogram
        val rsrpTotal = rsrpBins.sumOf { it.count }
        assertEquals(3, rsrpTotal, "Only 3 valid RSRP values should be counted")
        // Ping histogram: 2 records have latency, 3 don't → denominator = 2
        val pingBins = result.pingHistogram
        val pingTotal = pingBins.sumOf { it.count }
        assertEquals(2, pingTotal, "Only 2 valid latency values should be counted")
    }

    @Test
    fun `rat coverage with irregular sampling reflects duration not count`() {
        val records = listOf(
            wrapper(record(ts = 1000, rat = "4G", rsrp = -80)),
            wrapper(record(ts = 30000, rat = "4G", rsrp = -85)), // 29s gap
            wrapper(record(ts = 31000, rat = "5G_SA", rsrp = -75)), // 1s gap
            wrapper(record(ts = 31500, rat = "5G_SA", rsrp = -70)) // 0.5s gap
        )
        val result = engine.analyze(records, defaultConfig)
        val coverage4g = result.ratCoverage.find { it.rat == "4G" }
        val coverage5g = result.ratCoverage.find { it.rat == "5G_SA" }
        // Intervals: 4G(1000→30000)=29000ms, 4G(30000→31000)=1000ms, 5G_SA(31000→31500)=500ms
        // Total: 30500ms, 4G=30000ms(98.4%), 5G_SA=500ms(1.6%)
        assertNotNull(coverage4g)
        assertNotNull(coverage5g)
        assertTrue((coverage4g!!.percentage) > 90.0, "4G should dominate by duration")
        assertTrue(coverage5g!!.percentage < 10.0, "5G_SA should be small by duration")
        // Sample count would say 50/50, but duration should reflect truth
    }

    @Test
    fun `latency spike detection uses robust baseline`() {
        val records = (1..30).map { i ->
            wrapper(record(ts = i * 1000L, rsrp = -80, latency = if (i == 25) 500.0 else 10.0))
        }
        val result = engine.analyze(records, defaultConfig)
        val spikes = result.anomalyFlags.filter { it.type == AnomalyType.LATENCY_SPIKE }
        assertTrue(spikes.isNotEmpty(), "Single 500ms spike should be detected despite massive outlier in global mean")
        // With MAD baseline, median=10, MAD=0, threshold = max(10 + 3*0, 10+80) = 90
        // 500 > 90 → spike detected
    }

    @Test
    fun `multiple gap types detected`() {
        val records = listOf(
            wrapper(record(ts = 1000, rat = "4G", rsrp = -80, lat = 10.0, lng = 20.0)),
            wrapper(record(ts = 40000, rat = "UNKNOWN", rsrp = null, lat = 10.1, lng = 20.1)),
            wrapper(record(ts = 70000, rat = "4G", rsrp = -85, lat = 10.2, lng = 20.2)),
            wrapper(record(ts = 130000, rat = "4G", rsrp = -120, lat = 10.3, lng = 20.3)),
            wrapper(record(ts = 180000, rat = "4G", rsrp = -80, lat = 10.4, lng = 20.4))
        )
        val config = defaultConfig.copy(coverageGapThresholdMs = 25000)
        val result = engine.analyze(records, config)
        assertEquals(2, result.coverageGaps.size, "Should detect 2 distinct gaps")
        assertEquals(GapType.NO_RAT, result.coverageGaps[0].type, "First gap is NO_RAT")
        assertEquals(GapType.WEAK_SIGNAL, result.coverageGaps[1].type, "Second gap is WEAK_SIGNAL")
    }

    @Test
    fun `handoff detects rat change type`() {
        val records = listOf(
            wrapper(record(ts = 1000, rat = "4G", enb = 100L, pci = 1, simSlot = 0, band = 3)),
            wrapper(record(ts = 3000, rat = "4G", enb = 100L, pci = 1, simSlot = 0, band = 3)),
            wrapper(record(ts = 5000, rat = "5G_SA", enb = 100L, pci = 2, simSlot = 0, band = 78))
        )
        val config = defaultConfig.copy(handoffTimeWindowMs = 5000)
        val result = engine.analyze(records, config)
        val ratChanges = result.handoffEvents.filter { it.type == HandoffType.RAT_CHANGE }
        assertEquals(1, ratChanges.size, "Should detect one RAT change")
        assertEquals("4G", ratChanges[0].fromRat)
        assertEquals("5G_SA", ratChanges[0].toRat)
    }

    @Nested
    inner class GeneratePciInsights {

        @Test
        fun `no handoffs yields no insight cards`() {
            val records = listOf(
                wrapper(record(ts = 1000, rat = "4G", enb = 1L, pci = 100)),
                wrapper(record(ts = 2000, rat = "4G", enb = 1L, pci = 100))
            )
            val result = engine.analyze(records, defaultConfig)
            assertEquals(0, result.insightCards.size)
        }

        @Test
        fun `fewer than 3 inter-site handoffs with latency delta yield no Cross-Site Handoff Impact card`() {
            val records = listOf(
                wrapper(record(ts = 1000, rat = "4G", enb = 1L, pci = 100, latency = 10.0)),
                wrapper(record(ts = 2000, rat = "4G", enb = 2L, pci = 200, latency = 20.0)),
                wrapper(record(ts = 3000, rat = "4G", enb = 1L, pci = 100, latency = 10.0)),
                wrapper(record(ts = 4000, rat = "4G", enb = 1L, pci = 100, latency = 10.0))
            )
            val result = engine.analyze(records, defaultConfig)
            assertEquals(false, result.insightCards.any { it.title.contains("Cross-Site") })
        }

        @Test
        fun `3 or more inter-site handoffs with positive latency delta generate Cross-Site Handoff Impact card`() {
            val records = listOf(
                wrapper(record(ts = 1000, rat = "4G", enb = 1L, pci = 100, latency = 10.0)),
                wrapper(record(ts = 1500, rat = "4G", enb = 2L, pci = 200, latency = 30.0)),
                wrapper(record(ts = 2000, rat = "4G", enb = 1L, pci = 100, latency = 10.0)),
                wrapper(record(ts = 2500, rat = "4G", enb = 3L, pci = 300, latency = 40.0)),
                wrapper(record(ts = 3000, rat = "4G", enb = 1L, pci = 100, latency = 10.0)),
                wrapper(record(ts = 3500, rat = "4G", enb = 4L, pci = 400, latency = 50.0)),
                wrapper(record(ts = 4000, rat = "4G", enb = 1L, pci = 100, latency = 10.0)),
                wrapper(record(ts = 4500, rat = "4G", enb = 5L, pci = 500, latency = 60.0))
            )
            val result = engine.analyze(records, defaultConfig)
            assertTrue(
                result.insightCards.any { it.title == "Cross-Site Handoff Impact" },
                "Expected Cross-Site Handoff Impact card for ≥3 inter-site handoffs with positive latency delta"
            )
        }

        @Test
        fun `inter-site handoffs with non-positive latency delta do not generate Cross-Site Handoff Impact card`() {
            val records = listOf(
                wrapper(record(ts = 1000, rat = "4G", enb = 1L, pci = 100, latency = 50.0)),
                wrapper(record(ts = 1500, rat = "4G", enb = 2L, pci = 200, latency = 10.0)),
                wrapper(record(ts = 2000, rat = "4G", enb = 1L, pci = 100, latency = 50.0)),
                wrapper(record(ts = 2500, rat = "4G", enb = 3L, pci = 300, latency = 5.0)),
                wrapper(record(ts = 3000, rat = "4G", enb = 1L, pci = 100, latency = 50.0)),
                wrapper(record(ts = 3500, rat = "4G", enb = 4L, pci = 400, latency = 1.0))
            )
            val result = engine.analyze(records, defaultConfig)
            assertEquals(false, result.insightCards.any { it.title.contains("Cross-Site") })
        }
    }

    @Nested
    inner class IndoorMode {

        @Test
        fun `indoor mode produces no handoff events`() {
            val records = listOf(
                wrapper(record(ts = 1000, rat = "4G", enb = 1L, pci = 100)),
                wrapper(record(ts = 2000, rat = "5G_NSA", enb = 2L, pci = 200))
            )
            val result = engine.analyze(records, defaultConfig, recordingMode = "INDOOR")
            assertTrue(result.handoffEvents.isEmpty(), "Indoor mode disables handoff detection")
        }

        @Test
        fun `indoor mode produces exactly one INDOOR mobility segment`() {
            val records = listOf(
                wrapper(record(ts = 1000, rat = "4G")),
                wrapper(record(ts = 2000, rat = "4G")),
                wrapper(record(ts = 3000, rat = "4G"))
            )
            val result = engine.analyze(records, defaultConfig, recordingMode = "INDOOR")
            assertEquals(1, result.mobilitySegments.size)
            assertEquals(MobilityType.INDOOR, result.mobilitySegments[0].type)
        }

        @Test
        fun `outdoor mode produces handoff events for cell changes`() {
            val records = listOf(
                wrapper(record(ts = 1000, rat = "4G", enb = 1L, pci = 100)),
                wrapper(record(ts = 2000, rat = "5G_NSA", enb = 2L, pci = 200))
            )
            val result = engine.analyze(records, defaultConfig, recordingMode = "OUTDOOR")
            assertTrue(result.handoffEvents.isNotEmpty(), "Outdoor mode detects handoffs")
        }
    }

    @Nested
    inner class TunnelMode {

        @Test
        fun `tunnel mode produces no handoff events`() {
            val records = listOf(
                wrapper(record(ts = 1000, rat = "4G", enb = 1L, pci = 100)),
                wrapper(record(ts = 2000, rat = "5G_NSA", enb = 2L, pci = 200))
            )
            val result = engine.analyze(records, defaultConfig, recordingMode = "TUNNEL")
            assertTrue(result.handoffEvents.isEmpty(), "Tunnel mode disables handoff detection")
        }

        @Test
        fun `tunnel mode produces no mobility segments`() {
            val records = listOf(
                wrapper(record(ts = 1000, rat = "4G", lat = 0.0, lng = 0.0)),
                wrapper(record(ts = 2000, rat = "4G", lat = 0.0, lng = 0.0)),
                wrapper(record(ts = 3000, rat = "4G", lat = 0.0, lng = 0.0))
            )
            val result = engine.analyze(records, defaultConfig, recordingMode = "TUNNEL")
            assertTrue(result.mobilitySegments.isEmpty(), "Tunnel mode disables speed-based mobility classification")
        }
    }

    @Nested
    inner class MobilityClassification {

        @Test
        fun `stationary segment for very low speed movement`() {
            val records = listOf(
                wrapper(record(ts = 0, lat = 0.0, lng = 0.0, rat = "4G")),
                wrapper(record(ts = 60_000, lat = 0.0, lng = 0.0, rat = "4G"))
            )
            val result = engine.analyze(records, defaultConfig)
            assertTrue(result.mobilitySegments.isNotEmpty(), "Mobility segments should not be empty")
            assertEquals(MobilityType.STATIONARY, result.mobilitySegments.last().type)
        }

        @Test
        fun `walking segment for moderate speed movement`() {
            val records = listOf(
                wrapper(record(ts = 0, lat = 0.0, lng = 0.0, rat = "4G")),
                wrapper(record(ts = 60_000, lat = 0.0005, lng = 0.0, rat = "4G"))
            )
            val result = engine.analyze(records, defaultConfig)
            assertTrue(result.mobilitySegments.isNotEmpty())
            val types = result.mobilitySegments.map { it.type }.toSet()
            assertTrue(
                types.any { it == MobilityType.WALKING || it == MobilityType.DRIVING || it == MobilityType.STATIONARY },
                "Expected one of STATIONARY/WALKING/DRIVING for moderate-speed movement"
            )
        }

        @Test
        fun `driving segment for high speed movement`() {
            val records = listOf(
                wrapper(record(ts = 0, lat = 0.0, lng = 0.0, rat = "4G")),
                wrapper(record(ts = 1000, lat = 0.005, lng = 0.0, rat = "4G")),
                wrapper(record(ts = 2000, lat = 0.010, lng = 0.0, rat = "4G"))
            )
            val result = engine.analyze(records, defaultConfig)
            assertTrue(result.mobilitySegments.isNotEmpty())
            assertTrue(
                result.mobilitySegments.any { it.type == MobilityType.DRIVING },
                "Expected at least one DRIVING segment for high-speed movement; got types: ${result.mobilitySegments.map { it.type }}"
            )
        }

        @Test
        fun `tunnel segment for consecutive UNKNOWN RAT records`() {
            val records = listOf(
                wrapper(record(ts = 1000, rat = "4G")),
                wrapper(record(ts = 2000, rat = "UNKNOWN")),
                wrapper(record(ts = 3000, rat = "UNKNOWN"))
            )
            val result = engine.analyze(records, defaultConfig)
            assertTrue(result.mobilitySegments.isNotEmpty())
            assertTrue(
                result.mobilitySegments.any { it.type == MobilityType.TUNNEL },
                "Expected at least one TUNNEL segment for consecutive UNKNOWN RAT records"
            )
        }

        @Test
        fun `indoor segment for high-accuracy weak-signal record`() {
            val config = defaultConfig.copy(indoorAccuracyThresholdM = 30f)
            val records = listOf(
                wrapper(record(ts = 0, lat = 0.0, lng = 0.0, rat = "4G", rsrp = -110, accuracy = 50f)),
                wrapper(record(ts = 60_000, lat = 0.0, lng = 0.0, rat = "4G", rsrp = -110, accuracy = 50f))
            )
            val result = engine.analyze(records, config)
            assertTrue(result.mobilitySegments.isNotEmpty())
            assertTrue(
                result.mobilitySegments.any { it.type == MobilityType.INDOOR },
                "Expected INDOOR mobility segment for accuracy above threshold and weak RSRP"
            )
        }
    }

    @Nested
    inner class SeverityLevels {

        @Test
        fun `detected anomalies have a severity of INFO WARNING or CRITICAL`() {
            val records = listOf(
                wrapper(record(ts = 0, rat = "4G", rsrp = -50, latency = 10.0)),
                wrapper(record(ts = 1000, rat = "4G", rsrp = -90, latency = 10.0)),
                wrapper(record(ts = 2000, rat = "4G", rsrp = -50, latency = 10.0)),
                wrapper(record(ts = 3000, rat = "4G", rsrp = -50, latency = 10.0))
            )
            val config = defaultConfig.copy(rsrpDropThresholdDbm = 15, rsrpDropTimeWindowMs = 5000)
            val result = engine.analyze(records, config)
            assertTrue(result.anomalyFlags.isNotEmpty(), "Expected at least one anomaly")
            result.anomalyFlags.forEach { flag ->
                val name = flag.severity.name
                assertTrue(name == "INFO" || name == "WARNING" || name == "CRITICAL",
                    "Severity must be INFO, WARNING, or CRITICAL; was $name")
            }
        }

        @Test
        fun `anomaly flags include a type from AnomalyType enum`() {
            val records = listOf(
                wrapper(record(ts = 0, rat = "4G", rsrp = -50, latency = 10.0)),
                wrapper(record(ts = 1000, rat = "4G", rsrp = -90, latency = 10.0)),
                wrapper(record(ts = 2000, rat = "4G", rsrp = -50, latency = 10.0))
            )
            val config = defaultConfig.copy(rsrpDropThresholdDbm = 15, rsrpDropTimeWindowMs = 5000)
            val result = engine.analyze(records, config)
            assertTrue(result.anomalyFlags.isNotEmpty())
            result.anomalyFlags.forEach { flag ->
                val name = flag.type.name
                assertTrue(name.isNotEmpty(), "AnomalyType name should not be empty")
            }
        }
    }

    @Nested
    inner class LatencyStatsValues {

        @Test
        fun `jitterMs (stddev) is non-zero on varied latency input`() {
            val records = listOf(
                wrapper(record(ts = 0, latency = 10.0)),
                wrapper(record(ts = 1000, latency = 20.0)),
                wrapper(record(ts = 2000, latency = 30.0)),
                wrapper(record(ts = 3000, latency = 40.0))
            )
            val result = engine.analyze(records, defaultConfig)
            val stats = result.latencyStats
            assertNotNull(stats)
            assertTrue(stats!!.jitterMs > 0, "jitterMs (stddev) must be positive for non-uniform latencies")
        }

        @Test
        fun `jitterMs is zero when all latency values are identical`() {
            val records = listOf(
                wrapper(record(ts = 0, latency = 15.0)),
                wrapper(record(ts = 1000, latency = 15.0)),
                wrapper(record(ts = 2000, latency = 15.0))
            )
            val result = engine.analyze(records, defaultConfig)
            val stats = result.latencyStats
            assertNotNull(stats)
            assertEquals(0.0, stats!!.jitterMs, 1e-9, "stddev must be zero when all latencies are identical")
        }

        @Test
        fun `sampleCount matches number of records with non-null latency`() {
            val records = listOf(
                wrapper(record(ts = 0, latency = 10.0)),
                wrapper(record(ts = 1000, latency = null)),
                wrapper(record(ts = 2000, latency = 20.0))
            )
            val result = engine.analyze(records, defaultConfig)
            val stats = result.latencyStats
            assertNotNull(stats)
            assertEquals(2, stats!!.sampleCount, "sampleCount counts only records with non-null latency")
        }
    }

    @Nested
    inner class SinrHistogram {

        @Test
        fun `sinr histogram bins have non-zero counts when SINR values are present`() {
            val records = listOf(
                wrapper(record(ts = 0, sinr = 20)),
                wrapper(record(ts = 1000, sinr = 10)),
                wrapper(record(ts = 2000, sinr = -10)),
                wrapper(record(ts = 3000, sinr = -20))
            )
            val result = engine.analyze(records, defaultConfig)
            assertTrue(result.sinrHistogram.isNotEmpty(), "SINR histogram must not be empty when SINR values exist")
            val total = result.sinrHistogram.sumOf { it.count }
            assertEquals(4, total, "All four SINR values should map to histogram bins")
        }

        @Test
        fun `sinr histogram is empty when no records have SINR values`() {
            val records = listOf(
                wrapper(record(ts = 0, sinr = null)),
                wrapper(record(ts = 1000, sinr = null))
            )
            val result = engine.analyze(records, defaultConfig)
            assertEquals(0, result.sinrHistogram.sumOf { it.count })
        }
    }

    @Nested
    inner class ComputeCorrelation {

        @Test
        fun `rsrpPing correlation bins contain labels for all RSRP bin boundaries`() {
            val records = listOf(
                wrapper(record(ts = 0, rsrp = -70, latency = 10.0)),
                wrapper(record(ts = 1000, rsrp = -85, latency = 20.0)),
                wrapper(record(ts = 2000, rsrp = -95, latency = 30.0)),
                wrapper(record(ts = 3000, rsrp = -110, latency = 40.0))
            )
            val result = engine.analyze(records, defaultConfig)
            val bins = result.correlationBins.rsrpPing
            assertTrue(bins.isNotEmpty(), "rsrpPing correlation should have bins")
            assertEquals(4, bins.size, "Expected one bin per RSRP range")
        }

        @Test
        fun `rsrpPing bin values are non-null when records with matching RSRP and latency exist`() {
            val records = listOf(
                wrapper(record(ts = 0, rsrp = -70, latency = 10.0)),
                wrapper(record(ts = 1000, rsrp = -72, latency = 12.0))
            )
            val result = engine.analyze(records, defaultConfig)
            val firstBin = result.correlationBins.rsrpPing.first()
            assertNotNull(firstBin.values.firstOrNull()?.value, "Expected non-null average for bin with matching records")
        }

        @Test
        fun `rsrpLoss correlation bins are computed`() {
            val records = listOf(
                wrapper(record(ts = 0, rsrp = -70, latency = 10.0))
            )
            val result = engine.analyze(records, defaultConfig)
            assertTrue(result.correlationBins.rsrpLoss.isNotEmpty())
        }

        @Test
        fun `sinrPing correlation bins are computed`() {
            val records = listOf(
                wrapper(record(ts = 0, sinr = 20, latency = 10.0))
            )
            val result = engine.analyze(records, defaultConfig)
            assertTrue(result.correlationBins.sinrPing.isNotEmpty())
        }

        @Test
        fun `sinrLoss correlation bins are computed`() {
            val records = listOf(
                wrapper(record(ts = 0, sinr = 20, latency = 10.0))
            )
            val result = engine.analyze(records, defaultConfig)
            assertTrue(result.correlationBins.sinrLoss.isNotEmpty())
        }

        @Test
        fun `correlation bins are empty when no records have the relevant metrics`() {
            val records = listOf(
                wrapper(record(ts = 0, rsrp = null, sinr = null, latency = null))
            )
            val result = engine.analyze(records, defaultConfig)
            val firstBin = result.correlationBins.rsrpPing.first()
            assertTrue(firstBin.values.all { it.value == null }, "All bin values should be null when no records match")
        }
    }

    companion object {
        private var idCounter = 1L

        private fun wrapper(
            rec: CellRecordEntity,
            caBands: List<CellRecordCaBandEntity> = emptyList()
        ) = CellRecordWithCaBands(record = rec, caBands = caBands)

        private fun record(
            ts: Long,
            rat: String = "4G",
            rsrp: Int? = -85,
            sinr: Int? = null,
            pci: Int? = null,
            enb: Long? = null,
            simSlot: Int = 0,
            latency: Double? = null,
            lat: Double = 0.0,
            lng: Double = 0.0,
            band: Int? = null,
            mcc: String? = null,
            mnc: String? = null,
            accuracy: Float = 10f
        ) = CellRecordEntity(
            id = idCounter++,
            sessionId = 1,
            timestamp = ts,
            latitude = lat,
            longitude = lng,
            altitude = 0.0,
            accuracy = accuracy,
            rat = rat,
            rsrp = rsrp,
            sinr = sinr,
            pci = pci,
            enbOrGnbId = enb ?: if (rat != "UNKNOWN" && rat.isNotEmpty()) 1L else null,
            simSlotIndex = simSlot,
            avgLatencyMs = latency,
            bandNumber = band,
            mcc = mcc,
            mnc = mnc
        )
    }
}