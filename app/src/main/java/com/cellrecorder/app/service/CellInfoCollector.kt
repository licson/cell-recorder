package com.cellrecorder.app.service

import com.cellrecorder.app.data.local.entity.AppConfigEntity
import com.cellrecorder.app.domain.model.CellRecordSnapshot
import cz.mroczis.netmonster.core.INetMonster
import cz.mroczis.netmonster.core.db.BandTableLte
import cz.mroczis.netmonster.core.db.BandTableNr
import cz.mroczis.netmonster.core.db.BandTableWcdma
import cz.mroczis.netmonster.core.db.model.NetworkType
import cz.mroczis.netmonster.core.model.cell.CellGsm
import cz.mroczis.netmonster.core.model.cell.CellLte
import cz.mroczis.netmonster.core.model.cell.CellNr
import cz.mroczis.netmonster.core.model.cell.CellWcdma
import cz.mroczis.netmonster.core.model.cell.ICell
import cz.mroczis.netmonster.core.model.connection.PrimaryConnection
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CellInfoCollector @Inject constructor(
    private val netMonster: INetMonster
) {

    fun snapshots(config: AppConfigEntity): List<CellRecordSnapshot> {
        val cells = netMonster.getCells()
        return cells.groupBy { it.subscriptionId }.map { (subId, subCells) ->
            val serving = subCells.firstOrNull { it.connectionStatus is PrimaryConnection }
            val networkType = netMonster.getNetworkType(subId)
            buildSnapshot(subId, serving, networkType, config)
        }
    }

    private fun buildSnapshot(
        subId: Int,
        serving: ICell?,
        networkType: NetworkType,
        config: AppConfigEntity
    ): CellRecordSnapshot {
        return when (serving) {
            is CellLte -> {
                val fullId = serving.eci?.toLong()
                CellRecordSnapshot(
                    subscriptionId = subId,
                    rat = if (networkType is NetworkType.Lte && networkType.technology == NetworkType.LTE_CA) "4G_CA" else "4G",
                    networkTypeCode = networkType.technology,
                    fullCellIdentity = fullId,
                    enbOrGnbId = fullId?.shr(8),
                    lcid = fullId?.and(0xFF)?.toInt(),
                    pci = serving.pci,
                    tac = serving.tac,
                    bandNumber = serving.band?.downlinkEarfcn?.let { BandTableLte.map(it).number } ?: serving.band?.number,
                    earfcn = serving.band?.downlinkEarfcn,
                    bandwidthKhz = serving.bandwidth,
                    rsrp = serving.signal?.rsrp?.toInt(),
                    rsrq = serving.signal?.rsrq?.toInt(),
                    sinr = serving.signal?.snr?.toInt(),
                    rssi = serving.signal?.rssi,
                    cqi = serving.signal?.cqi,
                    timingAdvance = serving.signal?.timingAdvance,
                    mcc = serving.network?.mcc,
                    mnc = serving.network?.mnc
                )
            }
            is CellNr -> {
                val fullId = serving.nci
                val shift = 36 - config.nrGnbBitLength
                CellRecordSnapshot(
                    subscriptionId = subId,
                    rat = if (networkType is NetworkType.Nr.Nsa) "5G_NSA" else "5G_SA",
                    networkTypeCode = networkType.technology,
                    fullCellIdentity = fullId,
                    enbOrGnbId = fullId?.shr(shift),
                    lcid = fullId?.and((1L shl shift) - 1)?.toInt(),
                    cellIdBitLength = config.nrGnbBitLength,
                    pci = serving.pci,
                    tac = serving.tac,
                    bandNumber = serving.band?.downlinkArfcn?.let { BandTableNr.map(it).number } ?: serving.band?.number,
                    earfcn = serving.band?.downlinkArfcn,
                    rsrp = serving.signal?.ssRsrp,
                    rsrq = serving.signal?.ssRsrq,
                    sinr = serving.signal?.ssSinr,
                    mcc = serving.network?.mcc,
                    mnc = serving.network?.mnc
                )
            }
            is CellWcdma -> {
                CellRecordSnapshot(
                    subscriptionId = subId,
                    rat = "3G",
                    networkTypeCode = networkType.technology,
                    fullCellIdentity = serving.ci?.toLong(),
                    pci = serving.psc,
                    mcc = serving.network?.mcc,
                    mnc = serving.network?.mnc,
                    bandNumber = serving.band?.downlinkUarfcn?.let { BandTableWcdma.map(it).number } ?: serving.band?.number,
                    earfcn = serving.band?.downlinkUarfcn
                )
            }
            is CellGsm -> {
                CellRecordSnapshot(
                    subscriptionId = subId,
                    rat = "2G",
                    networkTypeCode = networkType.technology,
                    fullCellIdentity = serving.cid?.toLong(),
                    pci = serving.bsic,
                    mcc = serving.network?.mcc,
                    mnc = serving.network?.mnc,
                    bandNumber = serving.band?.number,
                    earfcn = serving.band?.arfcn
                )
            }
            else -> CellRecordSnapshot(
                subscriptionId = subId,
                rat = "UNKNOWN",
                networkTypeCode = networkType.technology
            )
        }
    }
}
