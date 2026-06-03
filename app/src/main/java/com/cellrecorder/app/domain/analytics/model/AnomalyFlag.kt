package com.cellrecorder.app.domain.analytics.model

enum class AnomalyType {
    RSRP_DROP,
    LATENCY_SPIKE,
    PCI_FLAP,
    MISSING_PING_CLUSTER
}

enum class Severity {
    INFO,
    WARNING,
    CRITICAL
}

data class AnomalyFlag(
    val timestamp: Long,
    val simSlot: Int,
    val type: AnomalyType,
    val severity: Severity,
    val description: String
)