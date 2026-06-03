package com.cellrecorder.app.ui.liveinfo

import android.content.Context
import android.telephony.SubscriptionManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cellrecorder.app.data.repository.ConfigRepository
import com.cellrecorder.app.domain.model.CellRecordSnapshot
import com.cellrecorder.app.service.CellInfoCollector
import com.cellrecorder.app.service.SimLiveState
import cz.mroczis.netmonster.core.db.BandTableLte
import cz.mroczis.netmonster.core.db.BandTableNr
import cz.mroczis.netmonster.core.db.BandTableWcdma
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class LiveInfoViewModel @Inject constructor(
    private val cellInfoCollector: CellInfoCollector,
    private val configRepository: ConfigRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _liveSimStates = MutableStateFlow<List<SimLiveState>>(emptyList())
    val liveSimStates: StateFlow<List<SimLiveState>> = _liveSimStates

    private val _rsrpHistory = MutableStateFlow<Map<Int, List<Int?>>>(emptyMap())
    val rsrpHistory: StateFlow<Map<Int, List<Int?>>> = _rsrpHistory

    private val _sinrHistory = MutableStateFlow<Map<Int, List<Int?>>>(emptyMap())
    val sinrHistory: StateFlow<Map<Int, List<Int?>>> = _sinrHistory

    companion object {
        private const val MAX_HISTORY = 60
    }

    init {
        viewModelScope.launch {
            val config = configRepository.getConfig().first()
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
                        plmn = if (s.mcc != null && s.mnc != null) "${s.mcc}-${s.mnc}" else "---",
                        rat = s.rat,
                        tac = s.tac?.toString() ?: "---",
                        bandNumber = resolveBandNumber(s),
                        earfcn = s.earfcn?.toString() ?: "---",
                        cellId = formatCellId(s),
                        pci = s.pci?.toString() ?: "---",
                        rsrp = s.rsrp?.toString() ?: "---",
                        rsrq = s.rsrq?.toString() ?: "---",
                        sinr = s.sinr?.toString() ?: "---",
                        caBands = s.caBands.map { ca ->
                            "B${ca.bandNumber ?: "?"} (PCI ${ca.pci ?: "?"})"
                        }
                    )
                }

                val currentRsrp = _rsrpHistory.value.mapValues { it.value.toMutableList() }.toMutableMap()
                val currentSinr = _sinrHistory.value.mapValues { it.value.toMutableList() }.toMutableMap()
                for (s in snapshots) {
                    val rsrpList = currentRsrp.getOrPut(s.subscriptionId) { mutableListOf() }
                    rsrpList.add(s.rsrp)
                    if (rsrpList.size > MAX_HISTORY) rsrpList.removeFirst()

                    val sinrList = currentSinr.getOrPut(s.subscriptionId) { mutableListOf() }
                    sinrList.add(s.sinr)
                    if (sinrList.size > MAX_HISTORY) sinrList.removeFirst()
                }
                _rsrpHistory.value = currentRsrp
                _sinrHistory.value = currentSinr

                delay(config.cellInfoRefreshIntervalSec * 1000L)
            }
        }
    }

    private fun resolveBandNumber(snapshot: CellRecordSnapshot): String {
        val band = snapshot.bandNumber
            ?: snapshot.earfcn?.let { earfcn ->
                when {
                    snapshot.rat.startsWith("4G") -> BandTableLte.map(earfcn)?.number
                    snapshot.rat.startsWith("5G") -> BandTableNr.map(earfcn)?.number
                    snapshot.rat == "3G" -> BandTableWcdma.map(earfcn)?.number
                    else -> null
                }
            }
        return band?.let { "B$it" } ?: "---"
    }

    private fun formatCellId(snapshot: CellRecordSnapshot): String {
        if (snapshot.enbOrGnbId != null && snapshot.lcid != null) {
            return "${snapshot.enbOrGnbId}:${snapshot.lcid}"
        }
        return snapshot.fullCellIdentity?.toString() ?: "---"
    }
}