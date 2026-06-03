package com.cellrecorder.app.domain.model

data class RatDistribution(
    val rat: String,
    val count: Int
)

data class BandDistribution(
    val bandNumber: Int,
    val count: Int
)

data class SimSlotDistribution(
    val simSlotIndex: Int,
    val count: Int
)

data class Sim5GTime(
    val simSlotIndex: Int,
    val saCount: Int,
    val nsaCount: Int
)

data class RatDistributionPerSim(
    val simSlotIndex: Int,
    val rat: String,
    val count: Int
)

data class BandDistributionPerSim(
    val simSlotIndex: Int,
    val bandNumber: Int,
    val count: Int
)