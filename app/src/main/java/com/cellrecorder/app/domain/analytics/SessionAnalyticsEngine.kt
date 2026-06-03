package com.cellrecorder.app.domain.analytics

import com.cellrecorder.app.data.local.entity.AppConfigEntity
import com.cellrecorder.app.data.local.entity.CellRecordEntity
import com.cellrecorder.app.domain.analytics.model.*
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.sqrt

class SessionAnalyticsEngine {

    fun analyze(records: List<CellRecordEntity>, config: AppConfigEntity): SessionAnalytics {
        if (records.isEmpty()) return SessionAnalytics()

        val bySim = records.groupBy { it.simSlotIndex ?: 0 }.toSortedMap()
        val handoffs = detectHandoffs(records, config)

        return SessionAnalytics(
            ratCoverage = computeRatCoverage(records),
            bandDistributionPerSim = computeBandDistribution(bySim),
            rsrpHistogram = computeHistogram(records, RSRP_BINS, CellRecordEntity::rsrp),
            sinrHistogram = computeHistogram(records, SINR_BINS, CellRecordEntity::sinr),
            pingHistogram = computeHistogram(records, PING_BINS) { it.avgLatencyMs?.toInt() },
            correlationBins = CorrelationBins(
                rsrpPing = computeCorrelation(records, RSRP_BINS, { it.rsrp }, { it.avgLatencyMs }),
                rsrpLoss = computeCorrelation(records, RSRP_BINS, { it.rsrp }, { it.packetLossPct }),
                sinrPing = computeCorrelation(records, SINR_BINS, { it.sinr }, { it.avgLatencyMs }),
                sinrLoss = computeCorrelation(records, SINR_BINS, { it.sinr }, { it.packetLossPct })
            ),
            latencyStats = computeLatencyStats(records),
            handoffEvents = handoffs,
            anomalyFlags = detectAnomalies(records, config),
            mobilitySegments = classifyMobility(records, config),
            coverageGaps = detectCoverageGaps(records, config),
            timelineSegments = buildTimeline(records),
            insightCards = generatePciInsights(handoffs)
        )
    }

    // ── Rat Coverage ──────────────────────────────────────────────

    private fun computeRatCoverage(records: List<CellRecordEntity>): List<RatCoverage> {
        val total = records.size.toDouble()
        return records.groupBy { it.rat }.map { (rat, group) ->
            val count = group.size
            val excellent = group.count { it.rsrp != null && it.rsrp > -80 }
            val good = group.count { it.rsrp != null && it.rsrp in -90 until -80 }
            val fair = group.count { it.rsrp != null && it.rsrp in -100 until -90 }
            val poor = group.count { it.rsrp != null && it.rsrp < -100 }
            RatCoverage(
                rat = rat,
                percentage = count / total * 100.0,
                durationMs = estimateDuration(group),
                excellent = excellent,
                good = good,
                fair = fair,
                poor = poor
            )
        }.sortedByDescending { it.percentage }
    }

    private fun estimateDuration(records: List<CellRecordEntity>): Long {
        if (records.size < 2) return 0L
        return records.last().timestamp - records.first().timestamp
    }

    // ── Band Distribution per SIM ─────────────────────────────────

    private fun computeBandDistribution(
        bySim: Map<Int, List<CellRecordEntity>>
    ): Map<Int, List<BandDistItem>> {
        return bySim.mapValues { (_, recs) ->
            recs.groupBy { it.bandNumber }
                .map { (band, group) -> BandDistItem(band ?: -1, group.size) }
                .sortedByDescending { it.count }
        }
    }

    // ── Histogram ─────────────────────────────────────────────────

    private sealed class Bin(val label: String) {
        class Exact(val value: Int, label: String) : Bin(label)
        class Range(val min: Int, val max: Int, label: String) : Bin(label)
        class Above(val min: Int, label: String) : Bin(label)
        class Below(val max: Int, label: String) : Bin(label)
    }

    private val RSRP_BINS = listOf(
        Bin.Above(-80, ">-80"),
        Bin.Range(-90, -80, "-80~-90"),
        Bin.Range(-100, -90, "-90~-100"),
        Bin.Below(-100, "<-100")
    )

    private val SINR_BINS = listOf(
        Bin.Above(20, ">20"),
        Bin.Range(10, 20, "10~20"),
        Bin.Range(0, 10, "0~10"),
        Bin.Below(0, "<0")
    )

    private val PING_BINS = listOf(
        Bin.Range(0, 30, "0~30"),
        Bin.Range(30, 60, "30~60"),
        Bin.Range(60, 100, "60~100"),
        Bin.Above(100, ">100")
    )

    private fun inBin(value: Int, bin: Bin): Boolean = when (bin) {
        is Bin.Exact -> value == bin.value
        is Bin.Range -> value in bin.min until bin.max
        is Bin.Above -> value > bin.min
        is Bin.Below -> value < bin.max
    }

    private fun computeHistogram(
        records: List<CellRecordEntity>,
        bins: List<Bin>,
        selector: (CellRecordEntity) -> Int?
    ): List<HistogramBin> {
        val total = records.size
        return bins.map { bin ->
            val count = records.count { r ->
                val value = selector(r)
                value != null && inBin(value, bin)
            }
            HistogramBin(
                label = bin.label,
                count = count,
                countLabel = if (total > 0) {
                    "${count} (${"%.1f".format(count * 100.0 / total)}%)"
                } else "$count"
            )
        }
    }

    private fun computeCorrelation(
        records: List<CellRecordEntity>,
        bins: List<Bin>,
        binSelector: (CellRecordEntity) -> Int?,
        valueSelector: (CellRecordEntity) -> Double?
    ): List<CorrelationBin> {
        val bySim = records.groupBy { it.simSlotIndex ?: 0 }
        val allSims = bySim.keys.sorted()
        return bins.map { bin ->
            val values = allSims.map { sim ->
                val binRecords = bySim[sim].orEmpty().filter { r ->
                    val v = binSelector(r)
                    v != null && inBin(v, bin)
                }
                val avg = if (binRecords.isNotEmpty()) {
                    binRecords.mapNotNull(valueSelector).average().takeIf { !it.isNaN() }
                } else null
                SimValue(simSlotIndex = sim, value = avg)
            }
            CorrelationBin(label = bin.label, values = values)
        }
    }

    // ── Latency Stats ─────────────────────────────────────────────

    private fun computeLatencyStats(records: List<CellRecordEntity>): LatencyStats? {
        val values = records.mapNotNull { it.avgLatencyMs }
        if (values.isEmpty()) return null

        val mean = values.average()
        val sorted = values.sorted()
        val n = sorted.size

        fun percentile(p: Double): Double {
            val rank = ceil(p * n).toInt().coerceIn(1, n)
            return sorted[rank - 1]
        }

        val variance = values.sumOf { (it - mean) * (it - mean) } / n
        val jitter = sqrt(variance)

        return LatencyStats(
            mean = mean,
            p50 = percentile(0.50),
            p95 = percentile(0.95),
            p99 = percentile(0.99),
            jitterMs = jitter,
            sampleCount = n
        )
    }

    // ── Handoff Detection ─────────────────────────────────────────

    private fun detectHandoffs(
        records: List<CellRecordEntity>,
        config: AppConfigEntity
    ): List<HandoffEvent> {
        val bySim = records.groupBy { it.simSlotIndex ?: 0 }
        val result = mutableListOf<HandoffEvent>()

        for ((sim, recs) in bySim) {
            val sorted = recs.sortedBy { it.timestamp }
            for (i in 0 until sorted.size - 1) {
                val cur = sorted[i]
                val next = sorted[i + 1]
                val timeDelta = next.timestamp - cur.timestamp
                if (timeDelta > config.handoffTimeWindowMs) continue

                val cellChanged = (cur.enbOrGnbId != null && next.enbOrGnbId != null && cur.enbOrGnbId != next.enbOrGnbId)
                val pciChanged = (cur.pci != null && next.pci != null && cur.pci != next.pci)
                if (!cellChanged && !pciChanged) continue

                val latDelta = next.avgLatencyMs?.let { n ->
                    cur.avgLatencyMs?.let { c -> n - c }
                }
                val lossDelta = next.packetLossPct?.let { n ->
                    cur.packetLossPct?.let { c -> n - c }
                }

                val type = when {
                    cur.enbOrGnbId != null && next.enbOrGnbId != null && cur.enbOrGnbId == next.enbOrGnbId -> HandoffType.INTRA_SITE_PCI_CHANGE
                    else -> HandoffType.INTER_SITE
                }

                result.add(
                    HandoffEvent(
                        timestamp = next.timestamp,
                        simSlot = sim,
                        fromEnbOrGnbId = cur.enbOrGnbId,
                        toEnbOrGnbId = next.enbOrGnbId,
                        fromPci = cur.pci,
                        toPci = next.pci,
                        latencyDeltaMs = latDelta,
                        packetLossDeltaPct = lossDelta,
                        type = type,
                        rat = next.rat
                    )
                )
            }
        }

        return result.sortedBy { it.timestamp }
    }

    // ── Anomaly Detection ─────────────────────────────────────────

    private fun detectAnomalies(
        records: List<CellRecordEntity>,
        config: AppConfigEntity
    ): List<AnomalyFlag> {
        val result = mutableListOf<AnomalyFlag>()

        // RSRP drops
        result.addAll(detectRsrpDrops(records, config))
        // Latency spikes
        result.addAll(detectLatencySpikes(records, config))
        // PCI flapping
        result.addAll(detectPciFlapping(records, config))
        // Missing ping clusters
        result.addAll(detectMissingPingClusters(records))

        return result.sortedBy { it.timestamp }
    }

    private fun detectRsrpDrops(
        records: List<CellRecordEntity>,
        config: AppConfigEntity
    ): List<AnomalyFlag> {
        val result = mutableListOf<AnomalyFlag>()
        val withRsrp = records.filter { it.rsrp != null }
        for (i in withRsrp.indices) {
            val cur = withRsrp[i]
            val windowEnd = cur.timestamp + config.rsrpDropTimeWindowMs
            for (j in i + 1 until withRsrp.size) {
                val next = withRsrp[j]
                if (next.timestamp > windowEnd) break
                val drop = cur.rsrp!! - next.rsrp!!
                if (drop >= config.rsrpDropThresholdDbm) {
                    result.add(
                        AnomalyFlag(
                            timestamp = cur.timestamp,
                            endTimestamp = next.timestamp,
                            simSlot = next.simSlotIndex ?: 0,
                            type = AnomalyType.RSRP_DROP,
                            severity = if (drop >= config.rsrpDropThresholdDbm * 1.5) Severity.CRITICAL else Severity.WARNING,
                            description = "RSRP dropped ${drop}dBm (${cur.rsrp} → ${next.rsrp})"
                        )
                    )
                    break
                }
            }
        }
        return result
    }

    private fun detectLatencySpikes(
        records: List<CellRecordEntity>,
        config: AppConfigEntity
    ): List<AnomalyFlag> {
        val values = records.mapNotNull { it.avgLatencyMs }
        if (values.isEmpty()) return emptyList()

        val mean = values.average()
        val variance = values.sumOf { (it - mean) * (it - mean) } / values.size
        val stddev = sqrt(variance)
        val threshold = mean + config.latencySpikeSigma * stddev

        val result = mutableListOf<AnomalyFlag>()
        var i = 0
        while (i < records.size) {
            val r = records[i]
            if (r.avgLatencyMs != null && r.avgLatencyMs!! > threshold) {
                val runStart = i
                var peak = r.avgLatencyMs!!
                while (i + 1 < records.size &&
                    records[i + 1].avgLatencyMs != null &&
                    records[i + 1].avgLatencyMs!! > threshold
                ) {
                    i++
                    if (records[i].avgLatencyMs!! > peak) {
                        peak = records[i].avgLatencyMs!!
                    }
                }
                result.add(
                    AnomalyFlag(
                        timestamp = records[runStart].timestamp,
                        endTimestamp = records[i].timestamp,
                        simSlot = records[runStart].simSlotIndex ?: 0,
                        type = AnomalyType.LATENCY_SPIKE,
                        severity = if (peak > mean + 2 * config.latencySpikeSigma * stddev) Severity.CRITICAL else Severity.WARNING,
                        description = "Latency spike: ${"%.0f".format(peak)}ms (mean ${"%.0f".format(mean)}ms)"
                    )
                )
            }
            i++
        }
        return result
    }

    private fun detectPciFlapping(
        records: List<CellRecordEntity>,
        config: AppConfigEntity
    ): List<AnomalyFlag> {
        val result = mutableListOf<AnomalyFlag>()
        val bySim = records.groupBy { it.simSlotIndex ?: 0 }

        for ((sim, recs) in bySim) {
            val sorted = recs.filter { it.pci != null }.sortedBy { it.timestamp }
            var i = 0
            while (i < sorted.size) {
                val windowEnd = sorted[i].timestamp + config.pciFlapWindowMs
                val distinctPcis = mutableSetOf<Int>()
                val distinctSiteIds = mutableSetOf<Long>()
                var j = i
                while (j < sorted.size && sorted[j].timestamp <= windowEnd) {
                    distinctPcis.add(sorted[j].pci!!)
                    sorted[j].enbOrGnbId?.let { distinctSiteIds.add(it) }
                    j++
                }
                if (distinctPcis.size >= config.pciFlapCountThreshold) {
                    val isIntraSite = distinctSiteIds.size <= 1
                    result.add(
                        AnomalyFlag(
                            timestamp = sorted[i].timestamp,
                            endTimestamp = windowEnd,
                            simSlot = sim,
                            type = AnomalyType.PCI_FLAP,
                            severity = if (isIntraSite) Severity.INFO else Severity.WARNING,
                            description = if (isIntraSite) {
                                "Intra-site PCI changes: ${distinctPcis.size} distinct PCIs — likely engineered"
                            } else {
                                "PCI flapping: ${distinctPcis.size} distinct PCI values across ${distinctSiteIds.size} sites"
                            }
                        )
                    )
                    i = j
                } else {
                    i++
                }
            }
        }
        return result
    }

    private fun detectMissingPingClusters(records: List<CellRecordEntity>): List<AnomalyFlag> {
        val result = mutableListOf<AnomalyFlag>()
        var runStart = -1
        for (i in records.indices) {
            if (records[i].avgLatencyMs == null) {
                if (runStart == -1) runStart = i
            } else {
                if (runStart != -1) {
                    val runLength = i - runStart
                    if (runLength >= 3) {
                        result.add(
                            AnomalyFlag(
                                timestamp = records[runStart].timestamp,
                                endTimestamp = records[i - 1].timestamp,
                                simSlot = records[runStart].simSlotIndex ?: 0,
                                type = AnomalyType.MISSING_PING_CLUSTER,
                                severity = if (runLength >= 10) Severity.CRITICAL else Severity.INFO,
                                description = "Missing ping data for $runLength consecutive samples"
                            )
                        )
                    }
                    runStart = -1
                }
            }
        }
        // trailing run
        if (runStart != -1) {
            val runLength = records.size - runStart
            if (runLength >= 3) {
                result.add(
                    AnomalyFlag(
                        timestamp = records[runStart].timestamp,
                        endTimestamp = records.last().timestamp,
                        simSlot = records[runStart].simSlotIndex ?: 0,
                        type = AnomalyType.MISSING_PING_CLUSTER,
                        severity = if (runLength >= 10) Severity.CRITICAL else Severity.INFO,
                        description = "Missing ping data for $runLength consecutive samples"
                    )
                )
            }
        }
        return result
    }

    // ── Mobility Classification ───────────────────────────────────

    private fun classifyMobility(
        records: List<CellRecordEntity>,
        config: AppConfigEntity
    ): List<MobilitySegment> {
        if (records.size < 2) return emptyList()

        val segments = mutableListOf<MobilitySegment>()
        val sorted = records.sortedBy { it.timestamp }
        var segStart = sorted.first().timestamp
        var segType = classifyRecordMobility(sorted[0], sorted.getOrNull(1), config)

        for (i in 1 until sorted.size) {
            val type = classifyRecordMobility(sorted[i], sorted.getOrNull(i + 1), config)
            if (type != segType) {
                segments.add(MobilitySegment(startTime = segStart, endTime = sorted[i].timestamp, type = segType))
                segStart = sorted[i].timestamp
                segType = type
            }
        }
        segments.add(MobilitySegment(startTime = segStart, endTime = sorted.last().timestamp, type = segType))

        return segments
    }

    private fun classifyRecordMobility(
        record: CellRecordEntity,
        next: CellRecordEntity?,
        config: AppConfigEntity
    ): MobilityType {
        // Tunnel: unknown RAT + motion
        if (record.rat == "UNKNOWN" && next != null && next.rat == "UNKNOWN") {
            return MobilityType.TUNNEL
        }

        // Indoor: poor accuracy + weak signal
        if (record.accuracy > config.indoorAccuracyThresholdM && (record.rsrp == null || record.rsrp < -100)) {
            return MobilityType.INDOOR
        }

        // Speed-based classification
        val speed = estimateSpeed(record, next)
        return when {
            speed == null || speed < config.mobilityStationaryKmh -> MobilityType.STATIONARY
            speed < config.mobilityWalkingKmh -> MobilityType.WALKING
            else -> MobilityType.DRIVING
        }
    }

    private fun estimateSpeed(
        cur: CellRecordEntity,
        next: CellRecordEntity?
    ): Float? {
        if (next == null) return null
        val dt = (next.timestamp - cur.timestamp) / 1000.0 // seconds
        if (dt <= 0) return null

        val dlat = Math.toRadians(next.latitude - cur.latitude)
        val dlon = Math.toRadians(next.longitude - cur.longitude)
        val a = Math.sin(dlat / 2) * Math.sin(dlat / 2) +
                Math.cos(Math.toRadians(cur.latitude)) * Math.cos(Math.toRadians(next.latitude)) *
                Math.sin(dlon / 2) * Math.sin(dlon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        val distKm = 6371.0 * c // Earth radius in km

        return (distKm / dt * 3600).toFloat() // km/h
    }

    // ── Coverage Gaps ─────────────────────────────────────────────

    private fun detectCoverageGaps(
        records: List<CellRecordEntity>,
        config: AppConfigEntity
    ): List<CoverageGap> {
        val result = mutableListOf<CoverageGap>()
        var gapStart = -1L
        var lastKnownLat: Double? = null
        var lastKnownLng: Double? = null

        for (record in records) {
            if (record.rat == "UNKNOWN") {
                if (gapStart == -1L) {
                    gapStart = record.timestamp
                }
            } else {
                if (gapStart != -1L) {
                    val duration = record.timestamp - gapStart
                    if (duration >= config.coverageGapThresholdMs) {
                        result.add(
                            CoverageGap(
                                startTime = gapStart,
                                endTime = record.timestamp,
                                durationMs = duration,
                                lastKnownLat = lastKnownLat,
                                lastKnownLng = lastKnownLng
                            )
                        )
                    }
                    gapStart = -1L
                }
                lastKnownLat = record.latitude
                lastKnownLng = record.longitude
            }
        }

        // trailing gap
        if (gapStart != -1L) {
            val duration = records.last().timestamp - gapStart
            if (duration >= config.coverageGapThresholdMs) {
                result.add(
                    CoverageGap(
                        startTime = gapStart,
                        endTime = records.last().timestamp,
                        durationMs = duration,
                        lastKnownLat = lastKnownLat,
                        lastKnownLng = lastKnownLng
                    )
                )
            }
        }

        return result
    }

    // ── Timeline Segments ─────────────────────────────────────────

    private fun buildTimeline(records: List<CellRecordEntity>): List<TimelineSegment> {
        if (records.isEmpty()) return emptyList()

        val sorted = records.sortedBy { it.timestamp }
        val segments = mutableListOf<TimelineSegment>()
        var segStart = sorted.first().timestamp
        var segRat = sorted.first().rat
        var count = 0

        for (record in sorted) {
            if (record.rat != segRat) {
                segments.add(TimelineSegment(startTime = segStart, endTime = record.timestamp, rat = segRat, recordCount = count))
                segStart = record.timestamp
                segRat = record.rat
                count = 0
            }
            count++
        }
        segments.add(TimelineSegment(startTime = segStart, endTime = sorted.last().timestamp, rat = segRat, recordCount = count))

        return segments
    }

    // ── PCI Insights ──────────────────────────────────────────────

    private fun generatePciInsights(handoffEvents: List<HandoffEvent>): List<InsightCard> {
        val cards = mutableListOf<InsightCard>()

        val intraSite5g = handoffEvents.count {
            it.type == HandoffType.INTRA_SITE_PCI_CHANGE && it.rat == "5G_SA"
        }
        val intraSite4g = handoffEvents.count {
            it.type == HandoffType.INTRA_SITE_PCI_CHANGE && (it.rat == "4G" || it.rat == "4G_CA")
        }
        val interSiteWithLatency = handoffEvents.count {
            it.type == HandoffType.INTER_SITE && it.latencyDeltaMs != null && it.latencyDeltaMs!! > 0
        }

        if (intraSite5g >= 3) {
            cards.add(
                InsightCard(
                    title = "Massive MIMO Candidate",
                    body = "Frequent intra-site PCI changes in 5G SA ($intraSite5g events) — possible Massive MIMO or load-balanced deployment"
                )
            )
        }
        if (intraSite4g >= 3) {
            cards.add(
                InsightCard(
                    title = "Load Balancing Detected",
                    body = "Frequent PCI changes within same site in 4G ($intraSite4g events) — network likely uses load balancing"
                )
            )
        }
        if (interSiteWithLatency >= 3) {
            cards.add(
                InsightCard(
                    title = "Cross-Site Handoff Impact",
                    body = "$interSiteWithLatency cross-site handoffs coincided with latency increases"
                )
            )
        }

        return cards
    }
}