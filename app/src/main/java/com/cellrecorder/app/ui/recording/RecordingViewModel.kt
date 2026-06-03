package com.cellrecorder.app.ui.recording

import android.content.Context
import android.telephony.SubscriptionManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cellrecorder.app.data.local.entity.SessionEntity
import com.cellrecorder.app.data.repository.ConfigRepository
import com.cellrecorder.app.data.repository.SessionRepository
import com.cellrecorder.app.domain.model.CellRecordSnapshot
import com.cellrecorder.app.service.CellInfoCollector
import com.cellrecorder.app.service.RecordingService
import com.cellrecorder.app.service.RecordingState
import com.cellrecorder.app.service.SimLiveState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RecordingViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val cellInfoCollector: CellInfoCollector,
    private val configRepository: ConfigRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _session = MutableStateFlow<SessionEntity?>(null)
    val session: StateFlow<SessionEntity?> = _session

    val serviceState: StateFlow<RecordingState?> = RecordingService.currentState

    private val _liveSimStates = MutableStateFlow<List<SimLiveState>>(emptyList())
    val liveSimStates: StateFlow<List<SimLiveState>> = _liveSimStates

    init {
        viewModelScope.launch {
            val config = configRepository.getConfig().first()
            while (isActive) {
                val snapshots = cellInfoCollector.snapshots(config)
                val subManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
                val activeSubs = subManager?.activeSubscriptionInfoList?.associateBy { it.subscriptionId } ?: emptyMap()

                _liveSimStates.value = snapshots.map { s ->
                    val info = activeSubs[s.subscriptionId]
                    SimLiveState(
                        subscriptionId = s.subscriptionId,
                        simSlotIndex = info?.simSlotIndex ?: 0,
                        plmn = formatPlmn(s.mcc, s.mnc),
                        rat = s.rat,
                        tac = s.tac?.toString() ?: "---",
                        bandNumber = s.bandNumber?.let { "B$it" } ?: "---",
                        earfcn = s.earfcn?.toString() ?: "---",
                        cellId = formatCellId(s),
                        pci = s.pci?.toString() ?: "---",
                        rsrp = s.rsrp?.toString() ?: "---",
                        rsrq = s.rsrq?.toString() ?: "---",
                        sinr = s.sinr?.toString() ?: "---"
                    )
                }
                delay(config.cellInfoRefreshIntervalSec * 1000L)
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

    private fun formatPlmn(mcc: String?, mnc: String?): String {
        if (mcc != null && mnc != null) return "$mcc-$mnc"
        if (mcc != null) return mcc
        return "---"
    }

    private fun formatCellId(snapshot: CellRecordSnapshot): String {
        if (snapshot.enbOrGnbId != null && snapshot.lcid != null) {
            return "${snapshot.enbOrGnbId}:${snapshot.lcid}"
        }
        return snapshot.fullCellIdentity?.toString() ?: "---"
    }
}