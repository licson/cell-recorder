package com.cellrecorder.app.domain.analytics.model

data class SessionAnalytics(
    val ratCoverage: List<RatCoverage> = emptyList(),
    val bandDistributionPerSim: Map<Int, List<BandDistItem>> = emptyMap(),
    val rsrpHistogram: List<HistogramBin> = emptyList(),
    val sinrHistogram: List<HistogramBin> = emptyList(),
    val pingHistogram: List<HistogramBin> = emptyList(),
    val correlationBins: CorrelationBins = CorrelationBins(),
    val latencyStats: LatencyStats? = null,
    val handoffEvents: List<HandoffEvent> = emptyList(),
    val anomalyFlags: List<AnomalyFlag> = emptyList(),
    val mobilitySegments: List<MobilitySegment> = emptyList(),
    val coverageGaps: List<CoverageGap> = emptyList(),
    val timelineSegments: List<TimelineSegment> = emptyList(),
    val insightCards: List<InsightCard> = emptyList()
)