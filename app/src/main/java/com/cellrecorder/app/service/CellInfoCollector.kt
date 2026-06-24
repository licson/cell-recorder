package com.cellrecorder.app.service

import android.annotation.SuppressLint
import com.cellrecorder.app.data.local.entity.AppConfigEntity
import com.cellrecorder.app.domain.model.BandResolver
import com.cellrecorder.app.domain.model.CaBandSnapshot
import com.cellrecorder.app.domain.model.CellRecordSnapshot
import cz.mroczis.netmonster.core.INetMonster
import cz.mroczis.netmonster.core.db.BandTableLte
import cz.mroczis.netmonster.core.db.BandTableWcdma
import cz.mroczis.netmonster.core.db.model.NetworkType
import cz.mroczis.netmonster.core.model.cell.CellGsm
import cz.mroczis.netmonster.core.model.cell.CellLte
import cz.mroczis.netmonster.core.model.cell.CellNr
import cz.mroczis.netmonster.core.model.cell.CellWcdma
import cz.mroczis.netmonster.core.model.cell.ICell
import cz.mroczis.netmonster.core.model.connection.PrimaryConnection
import cz.mroczis.netmonster.core.model.connection.SecondaryConnection
import javax.inject.Inject
import javax.inject.Singleton

@SuppressLint("MissingPermission")
@Singleton
class CellInfoCollector @Inject constructor(
    private val netMonster: INetMonster
) {

    fun snapshots(config: AppConfigEntity): List<CellRecordSnapshot> {
        val cells = netMonster.getCells()
        return cells.groupBy { it.subscriptionId }.map { (subId, subCells) ->
            val serving = subCells.firstOrNull { it.connectionStatus is PrimaryConnection }
            val networkType = netMonster.getNetworkType(subId)
            if (networkType is NetworkType.Nr.Nsa) {
                buildNsaSnapshot(subId, subCells, config)
            } else {
                buildSnapshot(subId, serving, subCells, networkType, config)
            }
        }
    }

    private fun buildNsaSnapshot(
        subId: Int,
        subCells: List<ICell>,
        config: AppConfigEntity
    ): CellRecordSnapshot {
        val nrCell = subCells.firstOrNull { it is CellNr } as? CellNr
        val lteAnchor = subCells.firstOrNull {
            it is CellLte && it.connectionStatus is PrimaryConnection
        } as? CellLte

        if (nrCell == null) {
            if (lteAnchor == null) {
                return CellRecordSnapshot(
                    subscriptionId = subId,
                    rat = "UNKNOWN",
                    networkTypeCode = netMonster.getNetworkType(subId).technology
                )
            }
            val caBands = extractCaBands(lteAnchor, subCells)
            return buildLteSnapshot(
                subId = subId,
                lteCell = lteAnchor,
                subCells = subCells,
                rat = if (caBands.isNotEmpty()) "4G_CA" else "4G",
                networkTypeCode = netMonster.getNetworkType(subId).technology
            )
        }

        val fullId = nrCell.nci
        val shift = 36 - config.nrGnbBitLength
        val caBands = lteAnchor?.let { extractCaBands(it, subCells) } ?: emptyList()

        return CellRecordSnapshot(
            subscriptionId = subId,
            rat = "5G_NSA",
            networkTypeCode = netMonster.getNetworkType(subId).technology,
            fullCellIdentity = fullId,
            enbOrGnbId = fullId?.shr(shift),
            lcid = fullId?.and((1L shl shift) - 1)?.toInt(),
            cellIdBitLength = fullId?.let { config.nrGnbBitLength },
            pci = nrCell.pci,
            tac = lteAnchor?.tac,
            bandNumber = BandResolver.resolveBandNumber(
                nrCell.band?.number, nrCell.band?.downlinkArfcn, "5G_NSA"
            ),
            earfcn = nrCell.band?.downlinkArfcn,
            rsrp = nrCell.signal?.ssRsrp,
            rsrq = nrCell.signal?.ssRsrq,
            sinr = nrCell.signal?.ssSinr,
            mcc = nrCell.network?.mcc ?: lteAnchor?.network?.mcc,
            mnc = nrCell.network?.mnc ?: lteAnchor?.network?.mnc,
            caBands = caBands,
            anchorEnbOrGnbId = lteAnchor?.eci?.toLong()?.shr(8),
            anchorLcid = lteAnchor?.eci?.toLong()?.and(0xFF)?.toInt(),
            anchorPci = lteAnchor?.pci,
            anchorTac = lteAnchor?.tac,
            anchorBandNumber = lteAnchor?.band?.downlinkEarfcn?.let { BandTableLte.map(it).number }
                ?: lteAnchor?.band?.number,
            anchorEarfcn = lteAnchor?.band?.downlinkEarfcn,
            anchorBandwidthKhz = lteAnchor?.bandwidth,
            anchorRsrp = lteAnchor?.signal?.rsrp?.toInt(),
            anchorRsrq = lteAnchor?.signal?.rsrq?.toInt(),
            anchorSinr = lteAnchor?.signal?.snr?.toInt(),
            anchorRssi = lteAnchor?.signal?.rssi,
            anchorCqi = lteAnchor?.signal?.cqi,
            anchorTimingAdvance = lteAnchor?.signal?.timingAdvance
        )
    }

    private fun buildSnapshot(
        subId: Int,
        serving: ICell?,
        subCells: List<ICell>,
        networkType: NetworkType,
        config: AppConfigEntity
    ): CellRecordSnapshot {
        val snapshot = when (serving) {
            is CellLte -> buildLteSnapshot(
                subId = subId,
                lteCell = serving,
                subCells = subCells,
                rat = if (networkType is NetworkType.Lte && networkType.technology == NetworkType.LTE_CA) "4G_CA" else "4G",
                networkTypeCode = networkType.technology
            )
            is CellNr -> {
                val fullId = serving.nci
                val shift = 36 - config.nrGnbBitLength
                CellRecordSnapshot(
                    subscriptionId = subId,
                    rat = "5G_SA",
                    networkTypeCode = networkType.technology,
                    fullCellIdentity = fullId,
                    enbOrGnbId = fullId?.shr(shift),
                    lcid = fullId?.and((1L shl shift) - 1)?.toInt(),
                    cellIdBitLength = config.nrGnbBitLength,
                    pci = serving.pci,
                    tac = serving.tac,
                    bandNumber = BandResolver.resolveBandNumber(serving.band?.number, serving.band?.downlinkArfcn, "5G_SA"),
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
        return snapshot
    }

    private fun buildLteSnapshot(
        subId: Int,
        lteCell: CellLte,
        subCells: List<ICell>,
        rat: String,
        networkTypeCode: Int
    ): CellRecordSnapshot {
        val fullId = lteCell.eci?.toLong()
        val caBands = extractCaBands(lteCell, subCells)
        return CellRecordSnapshot(
            subscriptionId = subId,
            rat = rat,
            networkTypeCode = networkTypeCode,
            fullCellIdentity = fullId,
            enbOrGnbId = fullId?.shr(8),
            lcid = fullId?.and(0xFF)?.toInt(),
            pci = lteCell.pci,
            tac = lteCell.tac,
            bandNumber = lteCell.band?.downlinkEarfcn?.let { BandTableLte.map(it).number } ?: lteCell.band?.number,
            earfcn = lteCell.band?.downlinkEarfcn,
            bandwidthKhz = lteCell.bandwidth,
            rsrp = lteCell.signal?.rsrp?.toInt(),
            rsrq = lteCell.signal?.rsrq?.toInt(),
            sinr = lteCell.signal?.snr?.toInt(),
            rssi = lteCell.signal?.rssi,
            cqi = lteCell.signal?.cqi,
            timingAdvance = lteCell.signal?.timingAdvance,
            mcc = lteCell.network?.mcc,
            mnc = lteCell.network?.mnc,
            caBands = caBands
        )
    }

    private fun extractCaBands(primary: CellLte, subCells: List<ICell>): List<CaBandSnapshot> {
        val secondaryCells = subCells.filter { cell ->
            cell is CellLte && cell !== primary && cell.connectionStatus is SecondaryConnection
        }
        if (secondaryCells.isNotEmpty()) {
            return secondaryCells.map { cell ->
                cell as CellLte
                CaBandSnapshot(
                    bandNumber = cell.band?.downlinkEarfcn?.let { BandTableLte.map(it).number } ?: cell.band?.number,
                    earfcn = cell.band?.downlinkEarfcn,
                    pci = cell.pci,
                    rsrp = cell.signal?.rsrp?.toInt(),
                    rsrq = cell.signal?.rsrq?.toInt(),
                    sinr = cell.signal?.snr?.toInt(),
                    rssi = cell.signal?.rssi,
                    cqi = cell.signal?.cqi,
                    timingAdvance = cell.signal?.timingAdvance
                )
            }
        }
        // Fallback: aggregatedBands from primary cell (no per-band signal metrics available)
        return primary.aggregatedBands.orEmpty().map { aggBand ->
            CaBandSnapshot(
                bandNumber = aggBand.number,
                earfcn = null,
                pci = null,
                rsrp = null,
                rsrq = null,
                sinr = null,
                rssi = null,
                cqi = null,
                timingAdvance = null
            )
        }
    }
}