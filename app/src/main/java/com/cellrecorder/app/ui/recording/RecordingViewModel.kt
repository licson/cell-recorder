package com.cellrecorder.app.ui.recording

import android.annotation.SuppressLint
import android.content.Context
import android.telephony.SubscriptionManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cellrecorder.app.data.local.entity.RecentMarkerLabelEntity
import com.cellrecorder.app.data.local.entity.SessionEntity
import com.cellrecorder.app.data.local.entity.SessionMarkerEntity
import com.cellrecorder.app.data.repository.ConfigRepository
import com.cellrecorder.app.data.repository.RecentMarkerLabelRepository
import com.cellrecorder.app.data.repository.SessionMarkerRepository
import com.cellrecorder.app.data.repository.SessionRepository
import com.cellrecorder.app.domain.model.MarkerType
import com.cellrecorder.app.service.CellInfoCollector
import com.cellrecorder.app.service.IndoorPositionCollector
import com.cellrecorder.app.service.RecordingMutex
import com.cellrecorder.app.service.RecordingState
import com.cellrecorder.app.service.RecordingStateManager
import com.cellrecorder.app.service.SimLiveState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject

@SuppressLint("MissingPermission")
@HiltViewModel
class RecordingViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val sessionMarkerRepository: SessionMarkerRepository,
    private val recentMarkerLabelRepository: RecentMarkerLabelRepository,
    private val recordingMutex: RecordingMutex,
    private val cellInfoCollector: CellInfoCollector,
    private val configRepository: ConfigRepository,
    private val stateManager: RecordingStateManager,
    private val indoorPositionCollector: IndoorPositionCollector,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _session = MutableStateFlow<SessionEntity?>(null)
    val session: StateFlow<SessionEntity?> = _session

    val serviceState: StateFlow<RecordingState?> = stateManager.state

    private val _liveSimStates = MutableStateFlow<List<SimLiveState>>(emptyList())
    val liveSimStates: StateFlow<List<SimLiveState>> = _liveSimStates

    private val _markers = MutableStateFlow<List<SessionMarkerEntity>>(emptyList())
    val markers: StateFlow<List<SessionMarkerEntity>> = _markers

    private var pollingJob: Job? = null
    private var markerCollectionJob: Job? = null

    init {
        viewModelScope.launch {
            configRepository.getConfig().collect { config ->
                pollingJob?.cancel()
                pollingJob = launch {
                    while (isActive) {
                        val snapshots = withContext(Dispatchers.IO) {
                            cellInfoCollector.snapshots(config)
                        }
                        val subManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
                        val activeSubs = withContext(Dispatchers.IO) {
                            subManager?.activeSubscriptionInfoList?.associateBy { it.subscriptionId } ?: emptyMap()
                        }

                        _liveSimStates.value = snapshots.map { s ->
                            val info = activeSubs[s.subscriptionId]
                            SimLiveStateMapper.map(
                                snapshot = s,
                                simSlotIndex = info?.simSlotIndex ?: 0
                            )
                        }
                        delay(config.cellInfoRefreshIntervalSec * 1000L)
                    }
                }
            }
        }
    }

    fun loadSession(sessionId: Long) {
        viewModelScope.launch {
            sessionRepository.getById(sessionId).collect { entity ->
                _session.value = entity
            }
        }
        markerCollectionJob?.cancel()
        markerCollectionJob = viewModelScope.launch {
            sessionMarkerRepository.getMarkersForSession(sessionId).collect { list ->
                _markers.value = list
            }
        }
    }

    fun quickMark(): Job = viewModelScope.launch(Dispatchers.IO) {
        val sessionId = _session.value?.id ?: return@launch
        recordingMutex.mutex.withLock {
            sessionMarkerRepository.insertMarkerWithAutoLabel(sessionId, MarkerType.NOTE)
        }
    }

    fun createMarker(type: MarkerType, label: String?): Job = viewModelScope.launch(Dispatchers.IO) {
        val sessionId = _session.value?.id ?: return@launch
        recordingMutex.mutex.withLock {
            sessionMarkerRepository.insertMarker(sessionId, type, label)
        }
    }

    fun editMarker(id: Long, type: MarkerType, label: String?): Job = viewModelScope.launch(Dispatchers.IO) {
        recordingMutex.mutex.withLock {
            sessionMarkerRepository.updateMarker(id, type, label)
        }
    }

    fun deleteMarker(id: Long): Job = viewModelScope.launch(Dispatchers.IO) {
        recordingMutex.mutex.withLock {
            sessionMarkerRepository.deleteMarker(id)
        }
    }

    suspend fun getRecentLabels(type: MarkerType): List<RecentMarkerLabelEntity> =
        recentMarkerLabelRepository.getByTypeOrdered(type)

    fun resetOrigin() {
        indoorPositionCollector.resetOrigin()
    }

    fun trackingConfidenceText(driftM: Double, noStepWarning: Boolean = false): String = when {
        noStepWarning -> "No steps"
        driftM < 3.0 -> "Confident"
        driftM < 10.0 -> "Degrading"
        else -> "High drift"
    }
}