package com.cellrecorder.app.ui.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cellrecorder.app.data.repository.CellRecordRepository
import com.cellrecorder.app.data.repository.SessionRepository
import com.cellrecorder.app.domain.model.BandDistribution
import com.cellrecorder.app.domain.model.RatDistribution
import com.cellrecorder.app.domain.model.Sim5GTime
import com.cellrecorder.app.domain.model.SimSlotDistribution
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class GlobalStats(
    val totalSessions: Int = 0,
    val totalPoints: Int = 0,
    val totalDurationMs: Long = 0,
    val onNetworkCount: Int = 0
) {
    val onNetworkPct: Float
        get() = if (totalPoints > 0) onNetworkCount.toFloat() / totalPoints * 100f else 0f
}

data class Sim5GPercent(
    val simSlotIndex: Int,
    val saCount: Int,
    val nsaCount: Int,
    val totalRecords: Int
) {
    val fiveGPct: Float
        get() = if (totalRecords > 0) (saCount + nsaCount).toFloat() / totalRecords * 100f else 0f
    val saPct: Float
        get() = if (totalRecords > 0) saCount.toFloat() / totalRecords * 100f else 0f
    val nsaPct: Float
        get() = if (totalRecords > 0) nsaCount.toFloat() / totalRecords * 100f else 0f
}

data class SimOnNetwork(
    val simSlotIndex: Int,
    val onNetworkCount: Int,
    val totalRecords: Int
) {
    val pct: Float
        get() = if (totalRecords > 0) onNetworkCount.toFloat() / totalRecords * 100f else 0f
}

@HiltViewModel
class StatisticsViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val cellRecordRepository: CellRecordRepository
) : ViewModel() {

    val stats: StateFlow<GlobalStats> = combine(
        sessionRepository.getTotalSessionCount(),
        cellRecordRepository.getTotalRecordCount(),
        sessionRepository.getTotalDurationMs(),
        cellRecordRepository.getOnNetworkCount()
    ) { sessions, points, durationMs, onNetwork ->
        GlobalStats(
            totalSessions = sessions,
            totalPoints = points,
            totalDurationMs = durationMs ?: 0L,
            onNetworkCount = onNetwork
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GlobalStats())

    val ratDistributionPerSim: StateFlow<Map<Int, List<RatDistribution>>> =
        cellRecordRepository.getRatDistributionPerSim()
            .map { list -> list.groupBy { it.simSlotIndex }.mapValues { (_, items) -> items.map { RatDistribution(it.rat, it.count) } } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val bandDistributionPerSim: StateFlow<Map<Int, List<BandDistribution>>> =
        cellRecordRepository.getBandDistributionPerSim()
            .map { list -> list.groupBy { it.simSlotIndex }.mapValues { (_, items) -> items.map { BandDistribution(it.bandNumber, it.count) } } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val simSlotDistribution: StateFlow<List<SimSlotDistribution>> =
        cellRecordRepository.getSimSlotDistribution()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val fiveGTimePerSim: StateFlow<List<Sim5GTime>> =
        cellRecordRepository.get5GTimePerSim()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val onNetworkPerSim: StateFlow<List<SimOnNetwork>> = combine(
        cellRecordRepository.getOnNetworkPerSim(),
        cellRecordRepository.getSimSlotDistribution()
    ) { onNet, totals ->
        val totalMap = totals.associateBy { it.simSlotIndex }
        onNet.map { on ->
            SimOnNetwork(
                simSlotIndex = on.simSlotIndex,
                onNetworkCount = on.count,
                totalRecords = totalMap[on.simSlotIndex]?.count ?: on.count
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val fiveGPercentPerSim: StateFlow<List<Sim5GPercent>> = combine(
        cellRecordRepository.get5GTimePerSim(),
        cellRecordRepository.getSimSlotDistribution()
    ) { fiveG, totals ->
        val totalMap = totals.associateBy { it.simSlotIndex }
        fiveG.map { fg ->
            Sim5GPercent(
                simSlotIndex = fg.simSlotIndex,
                saCount = fg.saCount,
                nsaCount = fg.nsaCount,
                totalRecords = totalMap[fg.simSlotIndex]?.count ?: (fg.saCount + fg.nsaCount)
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}