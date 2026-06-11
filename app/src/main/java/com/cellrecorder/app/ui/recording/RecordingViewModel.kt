package com.cellrecorder.app.ui.recording

import android.content.Context
import android.telephony.SubscriptionManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cellrecorder.app.data.local.entity.SessionEntity
import com.cellrecorder.app.data.repository.ConfigRepository
import com.cellrecorder.app.data.repository.SessionRepository
import com.cellrecorder.app.domain.model.BandResolver
import com.cellrecorder.app.domain.model.CellRecordSnapshot
import com.cellrecorder.app.service.CellInfoCollector
import com.cellrecorder.app.service.IndoorPositionCollector
import com.cellrecorder.app.service.RecordingState
import com.cellrecorder.app.service.RecordingStateManager
import com.cellrecorder.app.service.SimLiveState
import com.cellrecorder.app.ui.shared.formatPlmn
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
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class RecordingViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
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

    private var pollingJob: Job? = null

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
                            SimLiveState(
                                subscriptionId = s.subscriptionId,
                                simSlotIndex = info?.simSlotIndex ?: 0,
                                plmn = formatPlmn(s.mcc, s.mnc),
                                rat = s.rat,
                                tac = s.tac?.toString() ?: "---",
                                bandNumber = BandResolver.formatBand(s.bandNumber, s.earfcn, s.rat),
                                earfcn = s.earfcn?.toString() ?: "---",
                                cellId = formatCellId(s),
                                pci = s.pci?.toString() ?: "---",
                                rsrp = s.rsrp?.toString() ?: "---",
                                rsrq = s.rsrq?.toString() ?: "---",
                                sinr = s.sinr?.toString() ?: "---",
                                caBands = s.caBands.map { ca ->
                                    "B${ca.bandNumber ?: "?"} (PCI ${ca.pci ?: "?"})"
                                },
                                anchorInfo = if (s.rat.startsWith("5G_NSA") && s.anchorPci != null) {
                                    "B${s.anchorBandNumber ?: "?"} PCI ${s.anchorPci} RSRP ${s.anchorRsrp ?: "---"}"
                                } else ""
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
    }

    fun resetOrigin() {
        indoorPositionCollector.resetOrigin()
    }

    fun trackingConfidenceText(driftM: Double): String = when {
        driftM < 3.0 -> "Confident"
        driftM < 10.0 -> "Degrading"
        else -> "High drift"
    }

    private fun formatCellId(snapshot: CellRecordSnapshot): String {
        if (snapshot.enbOrGnbId != null && snapshot.lcid != null) {
            return "${snapshot.enbOrGnbId}:${snapshot.lcid}"
        }
        return snapshot.fullCellIdentity?.toString() ?: "---"
    }
}