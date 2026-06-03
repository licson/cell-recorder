package com.cellrecorder.app.domain.analytics

import com.cellrecorder.app.data.local.entity.AppConfigEntity
import com.cellrecorder.app.data.local.entity.CellRecordEntity
import com.cellrecorder.app.domain.analytics.model.AnomalyType
import com.cellrecorder.app.domain.analytics.model.MobilityType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
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
            record(ts = 1000, rat = "4G", rsrp = -85)
        )
        val result = engine.analyze(records, defaultConfig)
        assertEquals(1, result.ratCoverage.size)
        assertEquals("4G", result.ratCoverage[0].rat)
        assertEquals(100.0, result.ratCoverage[0].percentage, 0.01)
    }

    @Test
    fun `rat coverage has correct percentage`() {
        val records = listOf(
            record(ts = 1000, rat = "4G", rsrp = -85),
            record(ts = 2000, rat = "4G", rsrp = -90),
            record(ts = 3000, rat = "5G_SA", rsrp = -75),
            record(ts = 4000, rat = "5G_SA", rsrp = -70),
            record(ts = 5000, rat = "UNKNOWN", rsrp = null)
        )
        val result = engine.analyze(records, defaultConfig)
        val coverage4g = result.ratCoverage.find { it.rat == "4G" }
        val coverage5g = result.ratCoverage.find { it.rat == "5G_SA" }
        val coverageUnknown = result.ratCoverage.find { it.rat == "UNKNOWN" }
        assertEquals(40.0, coverage4g?.percentage ?: 0.0, 0.1)
        assertEquals(40.0, coverage5g?.percentage ?: 0.0, 0.1)
        assertEquals(20.0, coverageUnknown?.percentage ?: 0.0, 0.1)
    }

    @Test
    fun `rsrp drop anomaly detected`() {
        val records = listOf(
            record(ts = 1000, rsrp = -70, simSlot = 0),
            record(ts = 2000, rsrp = -72, simSlot = 0),
            record(ts = 3000, rsrp = -90, simSlot = 0),
            record(ts = 4000, rsrp = -92, simSlot = 0)
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
            record(ts = 1000, rsrp = -70, simSlot = 0),
            record(ts = 2000, rsrp = -75, simSlot = 0),
            record(ts = 3000, rsrp = -80, simSlot = 0)
        )
        val config = defaultConfig.copy(rsrpDropThresholdDbm = 15, rsrpDropTimeWindowMs = 5000)
        val result = engine.analyze(records, config)
        val drops = result.anomalyFlags.filter { it.type == AnomalyType.RSRP_DROP }
        assertTrue(drops.isEmpty(), "No RSRP drop expected")
    }

    @Test
    fun `latency spike detected`() {
        val records = (1..20).map { i ->
            record(ts = i * 1000L, rsrp = -80, latency = if (i == 15) 150.0 else 10.0)
        }
        val result = engine.analyze(records, defaultConfig)
        val spikes = result.anomalyFlags.filter { it.type == AnomalyType.LATENCY_SPIKE }
        assertTrue(spikes.isNotEmpty(), "Expected latency spike")
        assertEquals(1, spikes.size)
    }

    @Test
    fun `pci flapping detected`() {
        val records = listOf(
            record(ts = 1000, pci = 101, simSlot = 0),
            record(ts = 2000, pci = 102, simSlot = 0),
            record(ts = 3000, pci = 101, simSlot = 0),
            record(ts = 4000, pci = 103, simSlot = 0),
            record(ts = 5000, pci = 102, simSlot = 0)
        )
        val config = defaultConfig.copy(pciFlapWindowMs = 10000, pciFlapCountThreshold = 3)
        val result = engine.analyze(records, config)
        val flaps = result.anomalyFlags.filter { it.type == AnomalyType.PCI_FLAP}
        assertTrue(flaps.isNotEmpty(), "Expected PCI flapping anomaly")
    }

    @Test
    fun `missing ping cluster detected`() {
        val records = listOf(
            record(ts = 1000, latency = 10.0),
            record(ts = 2000, latency = null),
            record(ts = 3000, latency = null),
            record(ts = 4000, latency = null),
            record(ts = 5000, latency = 20.0)
        )
        val result = engine.analyze(records, defaultConfig)
        val clusters = result.anomalyFlags.filter { it.type == AnomalyType.MISSING_PING_CLUSTER }
        assertTrue(clusters.isNotEmpty(), "Expected missing ping cluster")
        assertEquals(1, clusters.size)
    }

    @Test
    fun `consecutive latency spikes grouped into one anomaly with duration`() {
        val records = (1..30).map { i ->
            record(ts = i * 1000L, rsrp = -80, latency = if (i in 13..17) 300.0 else 10.0)
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
            record(ts = 1000, pci = 101, simSlot = 0),
            record(ts = 2000, pci = 102, simSlot = 0),
            record(ts = 3000, pci = 101, simSlot = 0),
            record(ts = 4000, pci = 103, simSlot = 0),
            record(ts = 5000, pci = 102, simSlot = 0)
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
            record(ts = 1000, enb = 100L, pci = 1, simSlot = 0, rat = "4G", rsrp = -80),
            record(ts = 2000, enb = 100L, pci = 1, simSlot = 0, rat = "4G", rsrp = -82),
            record(ts = 3000, enb = 200L, pci = 2, simSlot = 0, rat = "4G", rsrp = -90),
            record(ts = 4000, enb = 200L, pci = 2, simSlot = 0, rat = "4G", rsrp = -88)
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
            record(ts = 1000, rat = "4G", rsrp = -80, lat = 10.0, lng = 20.0),
            record(ts = 40000, rat = "UNKNOWN", rsrp = null, lat = 10.1, lng = 20.1),
            record(ts = 80000, rat = "UNKNOWN", rsrp = null, lat = 10.1, lng = 20.1),
            record(ts = 120000, rat = "4G", rsrp = -85, lat = 10.2, lng = 20.2)
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
            record(ts = 1000, lat = 10.0, lng = 20.0, rat = "4G", rsrp = -80),
            record(ts = 2000, lat = 10.1, lng = 20.1, rat = "4G", rsrp = -82),
            record(ts = 3000, lat = 10.5, lng = 20.5, rat = "4G", rsrp = -85)
        )
        val result = engine.analyze(records, defaultConfig)
        assertTrue(result.mobilitySegments.isNotEmpty(), "Expected mobility segments")
    }

    @Test
    fun `latency stats computed correctly`() {
        val records = listOf(
            record(ts = 1000, latency = 10.0),
            record(ts = 2000, latency = 20.0),
            record(ts = 3000, latency = 30.0),
            record(ts = 4000, latency = 40.0),
            record(ts = 5000, latency = 50.0)
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
            record(ts = 1000, rat = "4G"),
            record(ts = 2000, rat = "4G"),
            record(ts = 3000, rat = "5G_SA"),
            record(ts = 4000, rat = "4G"),
            record(ts = 5000, rat = "UNKNOWN")
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
            record(ts = 1000, band = 78, simSlot = 0),
            record(ts = 2000, band = null, simSlot = 0),
            record(ts = 3000, band = 78, simSlot = 0),
            record(ts = 4000, band = null, simSlot = 0),
            record(ts = 5000, band = 1, simSlot = 0)
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
    fun `single record analytics basic fields populated`() {
        val records = listOf(
            record(ts = 1000, rat = "5G_SA", rsrp = -75, sinr = 25, band = 78, simSlot = 0, mcc = "310", mnc = "260")
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
            record(ts = 1000, rat = "4G", rsrp = -75),
            record(ts = 2000, rat = "4G", rsrp = -85),
            record(ts = 3000, rat = "4G", rsrp = -95),
            record(ts = 4000, rat = "4G", rsrp = -105),
            record(ts = 5000, rat = "4G", rsrp = -70)
        )
        val result = engine.analyze(records, defaultConfig)
        val coverage = result.ratCoverage.find { it.rat == "4G" }
        assertNotNull(coverage)
        assertEquals(2, coverage!!.excellent) // -75 and -70 > -80
        assertEquals(1, coverage.good)        // -85 in -80~-90
        assertEquals(1, coverage.fair)        // -95 in -90~-100
        assertEquals(1, coverage.poor)        // -105 < -100
    }

    companion object {
        private var idCounter = 1L

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
            enbOrGnbId = enb,
            simSlotIndex = simSlot,
            avgLatencyMs = latency,
            bandNumber = band,
            mcc = mcc,
            mnc = mnc
        )
    }
}