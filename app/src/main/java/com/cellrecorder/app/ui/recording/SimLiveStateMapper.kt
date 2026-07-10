package com.cellrecorder.app.ui.recording

import com.cellrecorder.app.domain.model.BandResolver
import com.cellrecorder.app.domain.model.CellRecordSnapshot
import com.cellrecorder.app.service.CaBandDetail
import com.cellrecorder.app.service.SimLiveState
import com.cellrecorder.app.ui.shared.formatPlmn

/**
 * Pure mapping from [CellRecordSnapshot] to [SimLiveState], shared by
 * [RecordingViewModel] and [LiveInfoViewModel] to keep their `populate` logic in sync.
 *
 * The mapper uses [formatPlmn] for PLMN formatting; previously [LiveInfoViewModel] used
 * a slightly different inline format (`"---"` when mcc or mnc was null) — that edge case
 * (mcc non-null, mnc null) now returns mcc alone, matching [RecordingViewModel]'s prior
 * behavior. This is the only observable change for the LiveInfoViewModel refactor.
 */
object SimLiveStateMapper {

    fun map(snapshot: CellRecordSnapshot, simSlotIndex: Int, plmn: String? = null): SimLiveState {
        return SimLiveState(
            subscriptionId = snapshot.subscriptionId,
            simSlotIndex = simSlotIndex,
            plmn = plmn ?: formatPlmn(snapshot.mcc, snapshot.mnc),
            rat = snapshot.rat,
            tac = snapshot.tac?.toString() ?: "---",
            bandNumber = BandResolver.formatBand(snapshot.bandNumber, snapshot.earfcn, snapshot.rat),
            earfcn = snapshot.earfcn?.toString() ?: "---",
            cellId = formatCellId(snapshot),
            pci = snapshot.pci?.toString() ?: "---",
            rsrp = snapshot.rsrp?.toString() ?: "---",
            rsrq = snapshot.rsrq?.toString() ?: "---",
            sinr = snapshot.sinr?.toString() ?: "---",
            caBands = snapshot.caBands.map { ca ->
                "B${ca.bandNumber ?: "?"} (PCI ${ca.pci ?: "?"})"
            },
            anchorInfo = if (snapshot.rat.startsWith("5G_NSA") && snapshot.anchorPci != null) {
                "B${snapshot.anchorBandNumber ?: "?"} PCI ${snapshot.anchorPci} RSRP ${snapshot.anchorRsrp ?: "---"}"
            } else "",
            anchorCellId = if (snapshot.anchorEnbOrGnbId != null && snapshot.anchorLcid != null) {
                "${snapshot.anchorEnbOrGnbId}:${snapshot.anchorLcid}"
            } else "---",
            anchorBand = snapshot.anchorBandNumber?.toString() ?: "",
            anchorPci = snapshot.anchorPci?.toString() ?: "",
            anchorArfcn = snapshot.anchorEarfcn?.toString() ?: "",
            anchorTac = snapshot.anchorTac?.toString() ?: "",
            anchorRsrp = snapshot.anchorRsrp?.toString() ?: "",
            anchorRsrq = snapshot.anchorRsrq?.toString() ?: "",
            anchorSinr = snapshot.anchorSinr?.toString() ?: "",
            caBandDetails = snapshot.caBands.map { ca ->
                CaBandDetail(
                    band = ca.bandNumber?.toString() ?: "?",
                    pci = ca.pci?.toString() ?: "?",
                    rsrp = ca.rsrp?.toString() ?: "---",
                    rsrq = ca.rsrq?.toString() ?: "---",
                    sinr = ca.sinr?.toString() ?: "---",
                    earfcn = ca.earfcn
                )
            }
        )
    }

    private fun formatCellId(snapshot: CellRecordSnapshot): String {
        if (snapshot.enbOrGnbId != null && snapshot.lcid != null) {
            return "${snapshot.enbOrGnbId}:${snapshot.lcid}"
        }
        return snapshot.fullCellIdentity?.toString() ?: "---"
    }
}
