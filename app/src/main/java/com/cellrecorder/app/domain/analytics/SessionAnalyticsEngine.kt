package com.cellrecorder.app.domain.analytics

import com.cellrecorder.app.data.local.entity.AppConfigEntity
import com.cellrecorder.app.data.local.entity.CellRecordEntity
import com.cellrecorder.app.data.local.entity.CellRecordWithCaBands
import com.cellrecorder.app.domain.analytics.model.*
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.sqrt

class SessionAnalyticsEngine {

    companion object {
        private const val WEAK_SIGNAL_RSRP_THRESHOLD_DBM = -110
    }

    fun analyze(records: List<CellRecordWithCaBands>, config: AppConfigEntity, recordingMode: String = "OUTDOOR"): SessionAnalytics {
        if (records.isEmpty()) return SessionAnalytics()

        val entities = records.map { it.record }.sortedBy { it.timestamp }
        val recordsSorted = records.sortedBy { it.record.timestamp }
        val bySim = entities.groupBy { it.simSlotIndex ?: 0 }.toSortedMap()
        val isIndoor = recordingMode == "INDOOR"
        val handoffs = if (isIndoor) emptyList() else detectHandoffs(entities, config)

        return SessionAnalytics(
            ratCoverage = computeRatCoverage(entities),
            bandDistributionPerSim = computeBandDistribution(recordsSorted, bySim),
            rsrpHistogram = computeHistogram(entities, RSRP_BINS, CellRecordEntity::rsrp),
            sinrHistogram = computeHistogram(entities, SINR_BINS, CellRecordEntity::sinr),
            pingHistogram = computeHistogram(entities, PING_BINS) { it.avgLatencyMs?.toInt() },
            correlationBins = CorrelationBins(
                rsrpPing = computeCorrelation(entities, RSRP_BINS, { it.rsrp }, { it.avgLatencyMs }),
                rsrpLoss = computeCorrelation(entities, RSRP_BINS, { it.rsrp }, { it.packetLossPct }),
                sinrPing = computeCorrelation(entities, SINR_BINS, { it.sinr }, { it.avgLatencyMs }),
                sinrLoss = computeCorrelation(entities, SINR_BINS, { it.sinr }, { it.packetLossPct })
            ),
            latencyStats = computeLatencyStats(entities),
            handoffEvents = handoffs,
            anomalyFlags = detectAnomalies(entities, config),
            mobilitySegments = classifyMobility(entities, config, isIndoor),
            coverageGaps = detectCoverageGaps(entities, config),
            timelineSegments = buildTimeline(entities),
            insightCards = generatePciInsights(handoffs)
        )
    }

    // ── RAT Coverage ──────────────────────────────────────────────

    private fun computeRatCoverage(records: List<CellRecordEntity>): List<RatCoverage> {
        if (records.size < 2) {
            return records.groupBy { it.rat }.map { (rat, group) ->
                RatCoverage(
                    rat = rat,
                    percentage = 100.0,
                    durationMs = 0L,
                    excellent = group.count { it.rsrp != null && it.rsrp >= -80 },
                    good = group.count { it.rsrp != null && it.rsrp in -90 until -80 },
                    fair = group.count { it.rsrp != null && it.rsrp in -100 until -90 },
                    poor = group.count { it.rsrp != null && it.rsrp < -100 }
                )
            }
        }

        val intervals = records.zipWithNext { current, next ->
            val delta = (next.timestamp - current.timestamp).coerceAtLeast(0)
            current.rat to delta
        }
        val totalDuration = intervals.sumOf { it.second }.toDouble()

        return intervals.groupBy { it.first }.map { (rat, ratIntervals) ->
            val ratDuration = ratIntervals.sumOf { it.second }
            val ratRecords = records.filter { it.rat == rat }
            RatCoverage(
                rat = rat,
                percentage = if (totalDuration > 0) ratDuration / totalDuration * 100.0 else 0.0,
                durationMs = ratDuration,
                excellent = ratRecords.count { it.rsrp != null && it.rsrp >= -80 },
                good = ratRecords.count { it.rsrp != null && it.rsrp in -90 until -80 },
                fair = ratRecords.count { it.rsrp != null && it.rsrp in -100 until -90 },
                poor = ratRecords.count { it.rsrp != null && it.rsrp < -100 }
            )
        }.sortedByDescending { it.percentage }
    }

    // ── Band Distribution per SIM (includes CA bands) ─────────────

    private fun computeBandDistribution(
        recordsWithCa: List<CellRecordWithCaBands>,
        bySim: Map<Int, List<CellRecordEntity>>
    ): Map<Int, List<BandDistItem>> {
        val caBySim = mutableMapOf<Int, MutableMap<Int, Pair<Int, String>>>()
        for ((sim, recs) in bySim) {
            caBySim.getOrPut(sim) { mutableMapOf() }
        }
        for (wrapper in recordsWithCa) {
            val sim = wrapper.record.simSlotIndex ?: 0
            val rat = wrapper.record.rat
            for (caBand in wrapper.caBands) {
                val band = caBand.bandNumber ?: continue
                val map = caBySim.getOrPut(sim) { mutableMapOf() }
                val current = map[band]
                map[band] = (current?.first ?: 0) + 1 to rat
            }
        }

        return bySim.mapValues { (sim, recs) ->
            val primaryCounts = mutableMapOf<Int, Pair<Int, String>>()
            for (rec in recs) {
                val band = rec.bandNumber ?: continue
                val current = primaryCounts[band]
                primaryCounts[band] = (current?.first ?: 0) + 1 to rec.rat
            }

            val caCounts = caBySim[sim] ?: emptyMap()
            caCounts.forEach { (band, pair) ->
                val current = primaryCounts[band]
                primaryCounts[band] = (current?.first ?: 0) + pair.first to (current?.second ?: pair.second)
            }

            primaryCounts.map { (band, pair) -> BandDistItem(band!!, pair.first, pair.second) }
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
        Bin.Above(-80, "\u2265-80"),
        Bin.Range(-90, -80, "-90~-80"),
        Bin.Range(-100, -90, "-100~-90"),
        Bin.Below(-100, "<-100")
    )

    private val SINR_BINS = listOf(
        Bin.Above(20, "\u226520"),
        Bin.Range(10, 20, "10~20"),
        Bin.Range(0, 10, "0~10"),
        Bin.Below(0, "<0")
    )

    private val PING_BINS = listOf(
        Bin.Range(0, 30, "0~30"),
        Bin.Range(30, 60, "30~60"),
        Bin.Range(60, 100, "60~100"),
        Bin.Above(100, "\u2265100")
    )

    private fun inBin(value: Int, bin: Bin): Boolean = when (bin) {
        is Bin.Exact -> value == bin.value
        is Bin.Range -> value in bin.min until bin.max
        is Bin.Above -> value >= bin.min
        is Bin.Below -> value < bin.max
    }

    private fun computeHistogram(
        records: List<CellRecordEntity>,
        bins: List<Bin>,
        selector: (CellRecordEntity) -> Int?
    ): List<HistogramBin> {
        val validCount = records.count { selector(it) != null }
        val total = validCount
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

        val sorted = values.sorted()
        val n = sorted.size

        fun percentile(p: Double): Double {
            val rank = ceil(p * n).toInt().coerceIn(1, n)
            return sorted[rank - 1]
        }

        val mean = values.average()
        val variance = values.sumOf { (it - mean) * (it - mean) } / n
        val stddev = sqrt(variance)

        val jitterSamples = values.zipWithNext { a, b -> abs(b - a) }
        val jitter = jitterSamples.average()

        return LatencyStats(
            mean = mean,
            p50 = percentile(0.50),
            p95 = percentile(0.95),
            p99 = percentile(0.99),
            jitterMs = stddev,
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

                val cellChanged = cur.enbOrGnbId != null && next.enbOrGnbId != null && cur.enbOrGnbId != next.enbOrGnbId
                val pciChanged = cur.pci != null && next.pci != null && cur.pci != next.pci
                val ratChanged = cur.rat != next.rat
                val bandChanged = cur.bandNumber != null && next.bandNumber != null && cur.bandNumber != next.bandNumber

                if (!cellChanged && !pciChanged && !ratChanged && !bandChanged) continue

                val latDelta = next.avgLatencyMs?.let { n ->
                    cur.avgLatencyMs?.let { c -> n - c }
                }
                val lossDelta = next.packetLossPct?.let { n ->
                    cur.packetLossPct?.let { c -> n - c }
                }

                val type = when {
                    ratChanged -> HandoffType.RAT_CHANGE
                    cellChanged && cur.enbOrGnbId == next.enbOrGnbId -> HandoffType.INTRA_SITE_PCI_CHANGE
                    cellChanged && cur.enbOrGnbId != next.enbOrGnbId -> HandoffType.INTER_SITE
                    bandChanged -> HandoffType.BAND_CHANGE
                    pciChanged -> HandoffType.UNKNOWN_CELL_CHANGE
                    else -> HandoffType.UNKNOWN_CELL_CHANGE
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
                        rat = next.rat,
                        fromRat = cur.rat,
                        toRat = next.rat,
                        fromBand = cur.bandNumber,
                        toBand = next.bandNumber,
                        fromCellId = cur.lcid,
                        toCellId = next.lcid
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
        val bySim = records.groupBy { it.simSlotIndex ?: 0 }.toSortedMap()

        for ((_, simRecords) in bySim) {
            result.addAll(detectRsrpDrops(simRecords, config))
            result.addAll(detectLatencySpikes(simRecords, config))
            result.addAll(detectMissingPingClusters(simRecords))
        }
        result.addAll(detectPciFlapping(records, config))

        return result.sortedBy { it.timestamp }
    }

    private fun detectRsrpDrops(
        records: List<CellRecordEntity>,
        config: AppConfigEntity
    ): List<AnomalyFlag> {
        val result = mutableListOf<AnomalyFlag>()
        val withRsrp = records.filter { it.rsrp != null }
        for (i in 0 until withRsrp.size - 1) {
            val cur = withRsrp[i]
            val next = withRsrp[i + 1]
            val timeDelta = next.timestamp - cur.timestamp
            if (timeDelta > config.rsrpDropTimeWindowMs) continue
            val drop = cur.rsrp!! - next.rsrp!!
            if (drop >= config.rsrpDropThresholdDbm) {
                result.add(
                    AnomalyFlag(
                        timestamp = cur.timestamp,
                        endTimestamp = next.timestamp,
                        simSlot = next.simSlotIndex ?: 0,
                        type = AnomalyType.RSRP_DROP,
                        severity = if (drop >= config.rsrpDropThresholdDbm * 1.5) Severity.CRITICAL else Severity.WARNING,
                        description = "RSRP dropped ${drop}dBm (${cur.rsrp} \u2192 ${next.rsrp})"
                    )
                )
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

        val sorted = values.sorted()
        val median = if (sorted.size % 2 == 0) {
            (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2.0
        } else sorted[sorted.size / 2]

        val deviations = sorted.map { abs(it - median) }.sorted()
        val mad = if (deviations.size % 2 == 0) {
            (deviations[deviations.size / 2 - 1] + deviations[deviations.size / 2]) / 2.0
        } else deviations[deviations.size / 2]

        val threshold = maxOf(median + config.latencySpikeSigma * mad, median + 80.0)

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
                        severity = if (peak > median + 2 * config.latencySpikeSigma * mad) Severity.CRITICAL else Severity.WARNING,
                        description = "Latency spike: ${"%.0f".format(peak)}ms (median ${"%.0f".format(median)}ms)"
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
            if (sorted.size < config.pciFlapCountThreshold) continue

            val window = ArrayDeque<Pair<Long, Int>>()
            val pciCounts = mutableMapOf<Int, Int>()
            val siteIds = mutableSetOf<Long>()

            for (record in sorted) {
                val ts = record.timestamp
                val pci = record.pci!!

                while (window.isNotEmpty() && window.first().first + config.pciFlapWindowMs < ts) {
                    val (_, expiredPci) = window.removeFirst()
                    val count = pciCounts.getValue(expiredPci)
                    if (count == 1) pciCounts.remove(expiredPci)
                    else pciCounts[expiredPci] = count - 1
                }

                window.addLast(ts to pci)
                pciCounts[pci] = (pciCounts[pci] ?: 0) + 1
                record.enbOrGnbId?.let { siteIds.add(it) }

                if (pciCounts.size >= config.pciFlapCountThreshold) {
                    val isIntraSite = siteIds.size <= 1
                    result.add(
                        AnomalyFlag(
                            timestamp = window.first().first,
                            endTimestamp = ts,
                            simSlot = sim,
                            type = AnomalyType.PCI_FLAP,
                            severity = if (isIntraSite) Severity.INFO else Severity.WARNING,
                            description = if (isIntraSite) {
                                "Intra-site PCI changes: ${pciCounts.size} distinct PCIs — likely engineered"
                            } else {
                                "PCI flapping: ${pciCounts.size} distinct PCI values across ${siteIds.size} sites"
                            }
                        )
                    )
                    window.clear()
                    pciCounts.clear()
                    siteIds.clear()
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
        config: AppConfigEntity,
        isIndoor: Boolean = false
    ): List<MobilitySegment> {
        if (records.size < 2) return emptyList()
        if (isIndoor) {
            return listOf(MobilitySegment(
                startTime = records.first().timestamp,
                endTime = records.last().timestamp,
                type = MobilityType.INDOOR
            ))
        }

        val segments = mutableListOf<MobilitySegment>()
        var segStart = records.first().timestamp
        var segType = classifyRecordMobility(records[0], records.getOrNull(1), config)

        for (i in 1 until records.size) {
            val type = classifyRecordMobility(records[i], records.getOrNull(i + 1), config)
            if (type != segType) {
                segments.add(MobilitySegment(startTime = segStart, endTime = records[i].timestamp, type = segType))
                segStart = records[i].timestamp
                segType = type
            }
        }
        segments.add(MobilitySegment(startTime = segStart, endTime = records.last().timestamp, type = segType))

        return segments
    }

    private fun classifyRecordMobility(
        record: CellRecordEntity,
        next: CellRecordEntity?,
        config: AppConfigEntity
    ): MobilityType {
        if (record.rat == "UNKNOWN" && next != null && next.rat == "UNKNOWN") {
            return MobilityType.TUNNEL
        }

        if (record.accuracy > config.indoorAccuracyThresholdM && (record.rsrp == null || record.rsrp < -100)) {
            return MobilityType.INDOOR
        }

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
        val dt = (next.timestamp - cur.timestamp) / 1000.0
        if (dt <= 0) return null

        val dlat = Math.toRadians(next.latitude - cur.latitude)
        val dlon = Math.toRadians(next.longitude - cur.longitude)
        val a = Math.sin(dlat / 2) * Math.sin(dlat / 2) +
                Math.cos(Math.toRadians(cur.latitude)) * Math.cos(Math.toRadians(next.latitude)) *
                Math.sin(dlon / 2) * Math.sin(dlon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        val distKm = 6371.0 * c

        return (distKm / dt * 3600).toFloat()
    }

    // ── Coverage Gaps ─────────────────────────────────────────────

    private fun detectCoverageGaps(
        records: List<CellRecordEntity>,
        config: AppConfigEntity
    ): List<CoverageGap> {
        val result = mutableListOf<CoverageGap>()
        var gapStart = -1L
        var gapType: GapType? = null
        var lastKnownLat: Double? = null
        var lastKnownLng: Double? = null

        fun classifyGap(record: CellRecordEntity): GapType? = when {
            record.rat == "UNKNOWN" -> GapType.NO_RAT
            record.enbOrGnbId == null && record.pci == null -> GapType.NO_SERVING_CELL
            record.rsrp == null -> GapType.NO_SIGNAL_METRIC
            record.rsrp < WEAK_SIGNAL_RSRP_THRESHOLD_DBM -> GapType.WEAK_SIGNAL
            else -> null
        }

        for (record in records) {
            val currentGapType = classifyGap(record)
            if (currentGapType != null) {
                if (gapStart == -1L) {
                    gapStart = record.timestamp
                    gapType = currentGapType
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
                                lastKnownLng = lastKnownLng,
                                type = gapType ?: GapType.NO_RAT
                            )
                        )
                    }
                    gapStart = -1L
                    gapType = null
                }
                lastKnownLat = record.latitude
                lastKnownLng = record.longitude
            }
        }

        if (gapStart != -1L) {
            val duration = records.last().timestamp - gapStart
            if (duration >= config.coverageGapThresholdMs) {
                result.add(
                    CoverageGap(
                        startTime = gapStart,
                        endTime = records.last().timestamp,
                        durationMs = duration,
                        lastKnownLat = lastKnownLat,
                        lastKnownLng = lastKnownLng,
                        type = gapType ?: GapType.NO_RAT
                    )
                )
            }
        }

        return result
    }

    // ── Timeline Segments ─────────────────────────────────────────

    private fun buildTimeline(records: List<CellRecordEntity>): List<TimelineSegment> {
        if (records.isEmpty()) return emptyList()

        val segments = mutableListOf<TimelineSegment>()
        var segStart = records.first().timestamp
        var segRat = records.first().rat
        var count = 0

        for (record in records) {
            if (record.rat != segRat) {
                segments.add(TimelineSegment(startTime = segStart, endTime = record.timestamp, rat = segRat, recordCount = count))
                segStart = record.timestamp
                segRat = record.rat
                count = 0
            }
            count++
        }
        segments.add(TimelineSegment(startTime = segStart, endTime = records.last().timestamp, rat = segRat, recordCount = count))

        return segments
    }

    // ── PCI Insights ──────────────────────────────────────────────

    private fun generatePciInsights(handoffEvents: List<HandoffEvent>): List<InsightCard> {
        val cards = mutableListOf<InsightCard>()

        val intraSite5g = handoffEvents.count {
            it.type == HandoffType.INTRA_SITE_PCI_CHANGE && (it.rat.startsWith("5G") || it.toRat?.startsWith("5G") == true)
        }
        val intraSite4g = handoffEvents.count {
            it.type == HandoffType.INTRA_SITE_PCI_CHANGE && (it.rat == "4G" || it.rat == "4G_CA" || it.toRat == "4G" || it.toRat == "4G_CA")
        }
        val interSiteWithLatency = handoffEvents.count {
            it.type == HandoffType.INTER_SITE && it.latencyDeltaMs != null && it.latencyDeltaMs!! > 0
        }

        if (intraSite5g >= 3) {
            cards.add(
                InsightCard(
                    title = "Massive MIMO Candidate",
                    body = "Frequent intra-site PCI changes in 5G ($intraSite5g events) — possible Massive MIMO or load-balanced deployment"
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