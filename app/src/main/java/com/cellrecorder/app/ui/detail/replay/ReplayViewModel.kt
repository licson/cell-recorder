package com.cellrecorder.app.ui.detail.replay

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cellrecorder.app.data.local.entity.CellRecordWithCaBands
import com.cellrecorder.app.data.local.entity.SessionEntity
import com.cellrecorder.app.data.local.entity.SessionMarkerEntity
import com.cellrecorder.app.data.local.entity.SpeedTestRecordEntity
import com.cellrecorder.app.data.repository.RecentMarkerLabelRepository
import com.cellrecorder.app.data.repository.SessionMarkerRepository
import com.cellrecorder.app.data.repository.SessionRepository
import com.cellrecorder.app.data.repository.SpeedTestRecordRepository
import com.cellrecorder.app.domain.model.MarkerType
import com.cellrecorder.app.domain.usecase.GetSessionPointsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SpeedTestMarker(
    val record: SpeedTestRecordEntity,
    val timelineIndex: Int
)

@HiltViewModel
class ReplayViewModel @Inject constructor(
    private val getSessionPointsUseCase: GetSessionPointsUseCase,
    private val speedTestRecordRepository: SpeedTestRecordRepository,
    private val sessionRepository: SessionRepository,
    private val sessionMarkerRepository: SessionMarkerRepository,
    private val recentMarkerLabelRepository: RecentMarkerLabelRepository
) : ViewModel() {

    private val _session = MutableStateFlow<SessionEntity?>(null)
    val session: StateFlow<SessionEntity?> = _session

    private val _records = MutableStateFlow<List<CellRecordWithCaBands>>(emptyList())
    val records: StateFlow<List<CellRecordWithCaBands>> = _records

    private val _filteredRecords = MutableStateFlow<List<CellRecordWithCaBands>>(emptyList())
    val filteredRecords: StateFlow<List<CellRecordWithCaBands>> = _filteredRecords

    private val _currentIndex = MutableStateFlow(0)
    val currentIndex: StateFlow<Int> = _currentIndex

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _speed = MutableStateFlow(1f)
    val speed: StateFlow<Float> = _speed

    private val _selectedSim = MutableStateFlow<Int?>(null)
    val selectedSim: StateFlow<Int?> = _selectedSim

    private val _availableSimSlots = MutableStateFlow<List<Int>>(emptyList())
    val availableSimSlots: StateFlow<List<Int>> = _availableSimSlots

    private val _speedTestRecords = MutableStateFlow<List<SpeedTestRecordEntity>>(emptyList())
    val speedTestRecords: StateFlow<List<SpeedTestRecordEntity>> = _speedTestRecords

    private val _speedTestMarkers = MutableStateFlow<List<SpeedTestMarker>>(emptyList())
    val speedTestMarkers: StateFlow<List<SpeedTestMarker>> = _speedTestMarkers

    private val _selectedSpeedTestMarker = MutableStateFlow<SpeedTestRecordEntity?>(null)
    val selectedSpeedTestMarker: StateFlow<SpeedTestRecordEntity?> = _selectedSpeedTestMarker

    private val _markers = MutableStateFlow<List<SessionMarkerEntity>>(emptyList())
    val markers: StateFlow<List<SessionMarkerEntity>> = _markers

    private val _selectedMarker = MutableStateFlow<SessionMarkerEntity?>(null)
    val selectedMarker: StateFlow<SessionMarkerEntity?> = _selectedMarker

    private var playbackJob: Job? = null

    fun loadSession(sessionId: Long) {
        viewModelScope.launch {
            sessionRepository.getById(sessionId).collect { entity ->
                _session.value = entity
            }
        }
        viewModelScope.launch {
            getSessionPointsUseCase(sessionId).collect { list ->
                _records.value = list
                val slots = list.mapNotNull { it.record.simSlotIndex }.distinct().sorted()
                _availableSimSlots.value = slots
                _selectedSim.value = slots.firstOrNull()
                applyFilter()
            }
        }
        viewModelScope.launch {
            speedTestRecordRepository.getBySessionId(sessionId).collect { records ->
                _speedTestRecords.value = records
                recomputeMarkers()
            }
        }
        viewModelScope.launch {
            sessionMarkerRepository.getMarkersForSession(sessionId).collect { list ->
                _markers.value = list
            }
        }
    }

    fun selectMarker(marker: SessionMarkerEntity?) {
        _selectedMarker.value = marker
    }

    fun editMarker(id: Long, type: MarkerType, label: String?) {
        viewModelScope.launch {
            sessionMarkerRepository.updateMarker(id, type, label)
        }
    }

    fun deleteMarker(id: Long) {
        viewModelScope.launch {
            sessionMarkerRepository.deleteMarker(id)
        }
    }

    suspend fun getRecentLabels(type: MarkerType): List<com.cellrecorder.app.data.local.entity.RecentMarkerLabelEntity> =
        recentMarkerLabelRepository.getByTypeOrdered(type)

    private fun recomputeMarkers() {
        val speedRecords = _speedTestRecords.value
        if (speedRecords.isEmpty()) {
            _speedTestMarkers.value = emptyList()
            return
        }
        val cellRecords = _filteredRecords.value
        val markers = speedRecords.map { speedRec ->
            val index = cellRecords.indexOfLast { it.record.timestamp <= speedRec.timestamp }
                .coerceAtLeast(0)
            SpeedTestMarker(record = speedRec, timelineIndex = index)
        }
        _speedTestMarkers.value = markers
    }

    fun selectSpeedTestMarker(record: SpeedTestRecordEntity?) {
        _selectedSpeedTestMarker.value = record
    }

    fun setSimFilter(simSlotIndex: Int?) {
        _selectedSim.value = simSlotIndex
        applyFilter()
    }

    private fun applyFilter() {
        val sim = _selectedSim.value
        _filteredRecords.value = if (sim == null) {
            _records.value
        } else {
            _records.value.filter { it.record.simSlotIndex == sim }
        }
        _currentIndex.value = 0
        recomputeMarkers()
        if (_isPlaying.value) {
            pause()
        }
    }

    fun togglePlayPause() {
        if (_isPlaying.value) pause() else play()
    }

    fun play() {
        val records = _filteredRecords.value
        if (records.isEmpty()) return
        _isPlaying.value = true
        playbackJob?.cancel()
        playbackJob = viewModelScope.launch {
            while (_currentIndex.value < records.lastIndex) {
                val current = records[_currentIndex.value]
                val next = records[_currentIndex.value + 1]
                val delta = next.record.timestamp - current.record.timestamp
                val adjustedDelta = (delta.toFloat() / _speed.value).toLong().coerceAtLeast(16L)
                delay(adjustedDelta)
                _currentIndex.value = _currentIndex.value + 1
            }
            _isPlaying.value = false
        }
    }

    fun pause() {
        _isPlaying.value = false
        playbackJob?.cancel()
    }

    fun seekTo(index: Int) {
        val records = _filteredRecords.value
        if (index in records.indices) {
            _currentIndex.value = index
        }
    }

    fun setSpeed(speed: Float) {
        _speed.value = speed
    }

    override fun onCleared() {
        playbackJob?.cancel()
    }
}