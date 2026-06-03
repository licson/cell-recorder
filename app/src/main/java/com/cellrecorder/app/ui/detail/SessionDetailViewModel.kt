package com.cellrecorder.app.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cellrecorder.app.data.local.entity.AppConfigEntity
import com.cellrecorder.app.data.local.entity.CellRecordEntity
import com.cellrecorder.app.data.local.entity.SessionEntity
import com.cellrecorder.app.data.repository.SessionRepository
import com.cellrecorder.app.domain.analytics.SessionAnalyticsEngine
import com.cellrecorder.app.domain.analytics.model.SessionAnalytics
import com.cellrecorder.app.domain.usecase.BatchResplitUseCase
import com.cellrecorder.app.domain.usecase.ExportData
import com.cellrecorder.app.domain.usecase.ExportSessionUseCase
import com.cellrecorder.app.domain.usecase.GetConfigUseCase
import com.cellrecorder.app.domain.usecase.GetSessionPointsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class TimestampGroup(
    val serialNumber: Int,
    val timestamp: Long,
    val records: List<CellRecordEntity>
)

@HiltViewModel
class SessionDetailViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val getSessionPointsUseCase: GetSessionPointsUseCase,
    private val exportSessionUseCase: ExportSessionUseCase,
    private val batchResplitUseCase: BatchResplitUseCase,
    private val getConfigUseCase: GetConfigUseCase
) : ViewModel() {

    private val _session = MutableStateFlow<SessionEntity?>(null)
    val session: StateFlow<SessionEntity?> = _session

    private val _records = MutableStateFlow<List<CellRecordEntity>>(emptyList())
    val records: StateFlow<List<CellRecordEntity>> = _records

    private val _filteredRecords = MutableStateFlow<List<CellRecordEntity>>(emptyList())
    val filteredRecords: StateFlow<List<CellRecordEntity>> = _filteredRecords

    private val _selectedRecord = MutableStateFlow<CellRecordEntity?>(null)
    val selectedRecord: StateFlow<CellRecordEntity?> = _selectedRecord

    private val _exportData = MutableStateFlow<ExportData?>(null)
    val exportData: StateFlow<ExportData?> = _exportData

    private val _allGrouped = MutableStateFlow<List<TimestampGroup>>(emptyList())
    val allGrouped: StateFlow<List<TimestampGroup>> = _allGrouped

    private val _visibleWindow = MutableStateFlow(0..300)
    val visibleWindow: StateFlow<IntRange> = _visibleWindow

    private val _analytics = MutableStateFlow(SessionAnalytics())
    val analytics: StateFlow<SessionAnalytics> = _analytics

    private val _mapDisplayMode = MutableStateFlow(MapDisplayMode.SIGNAL_TRAILS)
    val mapDisplayMode: StateFlow<MapDisplayMode> = _mapDisplayMode

    private val _showAnalytics = MutableStateFlow(false)
    val showAnalytics: StateFlow<Boolean> = _showAnalytics

    private val _selectedSim = MutableStateFlow<Int?>(null)
    val selectedSim: StateFlow<Int?> = _selectedSim

    private val _availableSimSlots = MutableStateFlow<List<Int>>(emptyList())
    val availableSimSlots: StateFlow<List<Int>> = _availableSimSlots

    private val engine = SessionAnalyticsEngine()
    private var config: AppConfigEntity = AppConfigEntity()

    fun updateVisibleWindow(firstVisible: Int, lastVisible: Int, buffer: Int = 150) {
        val total = _allGrouped.value.size
        if (total == 0) return
        val start = (firstVisible - buffer).coerceAtLeast(0)
        val end = (lastVisible + buffer).coerceAtMost(total - 1)
        val newRange = start..end
        if (newRange != _visibleWindow.value) {
            _visibleWindow.value = newRange
        }
    }

    fun loadSession(sessionId: Long) {
        viewModelScope.launch {
            sessionRepository.getById(sessionId).collect { _session.value = it }
        }
        viewModelScope.launch {
            getSessionPointsUseCase(sessionId).collect { list ->
                _records.value = list
                _availableSimSlots.value = list.mapNotNull { it.simSlotIndex }.distinct().sorted()
                val grouped = withContext(Dispatchers.Default) {
                    list.groupBy { it.timestamp }
                        .entries
                        .sortedBy { it.key }
                        .mapIndexed { index, (ts, recs) ->
                            TimestampGroup(
                                serialNumber = index + 1,
                                timestamp = ts,
                                records = recs.sortedBy { it.simSlotIndex ?: -1 }
                            )
                        }
                }
                _allGrouped.value = grouped
                _visibleWindow.value = 0..minOf(300, maxOf(0, grouped.lastIndex))
                updateFilteredAndAnalytics()
            }
        }
        viewModelScope.launch {
            getConfigUseCase().collect { config = it }
        }
    }

    fun selectRecord(record: CellRecordEntity?) {
        _selectedRecord.value = record
    }

    fun exportCsv() {
        val session = _session.value ?: return
        val records = _records.value
        if (records.isEmpty()) return
        viewModelScope.launch {
            _exportData.value = withContext(Dispatchers.IO) {
                exportSessionUseCase.exportCsv(session, records)
            }
        }
    }

    fun exportGeoJson() {
        val session = _session.value ?: return
        val records = _records.value
        if (records.isEmpty()) return
        viewModelScope.launch {
            _exportData.value = withContext(Dispatchers.IO) {
                exportSessionUseCase.exportGeoJson(session, records)
            }
        }
    }

    fun clearExportData() {
        _exportData.value = null
    }

    fun batchResplit(sessionId: Long) {
        viewModelScope.launch {
            batchResplitUseCase(
                sessionId = sessionId,
                nrBitLen = config.nrGnbBitLength
            )
        }
    }

    fun deleteSession(sessionId: Long) {
        viewModelScope.launch {
            sessionRepository.deleteById(sessionId)
        }
    }

    fun setMapDisplayMode(mode: MapDisplayMode) {
        _mapDisplayMode.value = mode
    }

    fun toggleAnalytics() {
        _showAnalytics.value = !_showAnalytics.value
    }

    fun setSimFilter(sim: Int?) {
        _selectedSim.value = sim
        viewModelScope.launch { updateFilteredAndAnalytics() }
    }

    private suspend fun updateFilteredAndAnalytics() {
        val records = _records.value
        val sim = _selectedSim.value
        val filtered = if (sim == null) records else records.filter { it.simSlotIndex == sim }
        _filteredRecords.value = filtered
        _analytics.value = withContext(Dispatchers.Default) {
            engine.analyze(filtered, config)
        }
    }
}