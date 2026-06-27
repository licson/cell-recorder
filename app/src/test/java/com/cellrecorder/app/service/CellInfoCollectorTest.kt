package com.cellrecorder.app.service

import com.cellrecorder.app.data.local.entity.AppConfigEntity
import cz.mroczis.netmonster.core.INetMonster
import cz.mroczis.netmonster.core.db.BandTableLte
import cz.mroczis.netmonster.core.db.model.NetworkType
import cz.mroczis.netmonster.core.model.Network
import cz.mroczis.netmonster.core.model.band.BandLte
import cz.mroczis.netmonster.core.model.band.BandNr
import cz.mroczis.netmonster.core.model.cell.CellLte
import cz.mroczis.netmonster.core.model.cell.CellNr
import cz.mroczis.netmonster.core.model.connection.PrimaryConnection
import cz.mroczis.netmonster.core.model.connection.SecondaryConnection
import cz.mroczis.netmonster.core.model.signal.SignalLte
import cz.mroczis.netmonster.core.model.signal.SignalNr
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pure-logic JVM tests for [CellInfoCollector]. The single framework dependency
 * [INetMonster] is mocked with mockk; netmonster cell classes are constructed directly
 * since they expose public constructors.
 */
class CellInfoCollectorTest {

    private val config = AppConfigEntity() // nrGnbBitLength = 24 by default
    private val netMonster = mockk<INetMonster>()
    private val collector = CellInfoCollector(netMonster)

    private fun network() = mockk<Network> {
        every { mcc } returns "310"
        every { mnc } returns "260"
    }

    private fun lteCell(
        eci: Int = 100_000,
        tac: Int = 1234,
        pci: Int = 42,
        bandNumber: Int = 3,
        earfcn: Int = 1800,
        bandwidth: Int = 20_000,
        rsrp: Int = -85,
        rsrq: Int = -90,
        snr: Int = 8,
        rssi: Int = -75,
        cqi: Int = 7,
        timingAdvance: Int = 14,
        primary: Boolean = true,
        subId: Int = 1
    ): CellLte {
        val band = BandLte(earfcn, bandNumber, "B$bandNumber")
        val signal = SignalLte(rssi, rsrp.toDouble(), rsrq.toDouble(), cqi, snr.toDouble(), timingAdvance)
        val connection = if (primary) PrimaryConnection() else SecondaryConnection(true)
        return CellLte(
            network(), eci, tac, pci, band, emptyList(), bandwidth, signal, connection, subId, 0L
        )
    }

    private fun nrCell(
        nci: Long = 123_456_789L,
        tac: Int = 5678,
        pci: Int = 100,
        bandNumber: Int = 78,
        earfcn: Int = 620_000,
        ssRsrp: Int = -85,
        ssRsrq: Int = -90,
        ssSinr: Int = 8,
        primary: Boolean = true,
        subId: Int = 1
    ): CellNr {
        val band = BandNr(earfcn, 0, bandNumber, "n$bandNumber")
        val signal = SignalNr(null, null, null, ssRsrp, ssRsrq, ssSinr, null)
        val connection = if (primary) PrimaryConnection() else SecondaryConnection(true)
        return CellNr(network(), nci, tac, pci, band, signal, connection, subId, 0L)
    }

    private fun nsaType(tech: Int = 45) = mockk<NetworkType.Nr.Nsa> { every { technology } returns tech }
    private fun lteType(tech: Int = 13) = mockk<NetworkType.Lte> { every { technology } returns tech }
    private fun saType(tech: Int = 45) = mockk<NetworkType.Nr.Sa> { every { technology } returns tech }

    private fun snap(vararg cells: cz.mroczis.netmonster.core.model.cell.ICell): List<com.cellrecorder.app.domain.model.CellRecordSnapshot> {
        every { netMonster.getCells() } returns cells.toList()
        return collector.snapshots(config)
    }

    // ── 3.2 NSA with NR + LTE anchor ──────────────────────────────

    @Test
    fun `NSA with NR and LTE anchor produces 5G_NSA record with anchor fields`() {
        every { netMonster.getNetworkType(1) } returns nsaType()
        val anchor = lteCell(eci = 50_000, tac = 4321, pci = 200, bandNumber = 3, earfcn = 1800, rsrp = -80)
        val nr = nrCell(tac = 9999, pci = 100, bandNumber = 78, earfcn = 620_000)

        val snapshots = snap(anchor, nr)
        assertEquals(1, snapshots.size)
        val s = snapshots[0]
        assertEquals("5G_NSA", s.rat)
        assertEquals(45, s.networkTypeCode)
        // NR primary fields
        assertEquals(100, s.pci)
        assertEquals(78, s.bandNumber)
        assertEquals(620_000, s.earfcn)
        assertEquals(-85, s.rsrp)
        assertEquals(-90, s.rsrq)
        assertEquals(8, s.sinr)
        // Primary TAC sourced from NR cell when available
        assertEquals(9999, s.tac)
        // Anchor fields populated from LTE anchor
        assertEquals(200, s.anchorPci)
        assertEquals(4321, s.anchorTac)
        assertEquals(3, s.anchorBandNumber)
        assertEquals(-80, s.anchorRsrp)
        assertNotNull(s.anchorEnbOrGnbId)
        assertNotNull(s.anchorLcid)
    }

    @Test
    fun `NSA primary TAC falls back to LTE anchor TAC when NR TAC is null`() {
        every { netMonster.getNetworkType(1) } returns nsaType()
        val anchor = lteCell(tac = 4321, pci = 200)
        val nr = nrCell(tac = 9999, pci = 100).let { copyNrTac(it, null) }

        val s = snap(anchor, nr)[0]
        assertEquals("5G_NSA", s.rat)
        // NR tac null → fall back to anchor tac
        assertEquals(4321, s.tac)
    }

    // ── 3.3 NSA fallback to 4G / 4G_CA ────────────────────────────

    @Test
    fun `NSA with no NR cell but LTE anchor falls back to 4G with full LTE fields`() {
        every { netMonster.getNetworkType(1) } returns nsaType()
        val anchor = lteCell(eci = 100_000, tac = 1234, pci = 42, rsrp = -85)

        val s = snap(anchor)[0]
        assertEquals("4G", s.rat)
        // networkTypeCode preserved from modem NSA code
        assertEquals(45, s.networkTypeCode)
        // Full LTE field coverage
        assertEquals(8, s.cellIdBitLength)
        assertEquals(42, s.pci)
        assertEquals(1234, s.tac)
        assertEquals(20_000, s.bandwidthKhz)
        assertEquals(1800, s.earfcn)
        assertEquals(-85, s.rsrp)
        assertEquals(-90, s.rsrq)
        assertEquals(8, s.sinr)
        assertEquals(-75, s.rssi)
        assertEquals(7, s.cqi)
        assertEquals(14, s.timingAdvance)
        assertEquals("310", s.mcc)
        assertEquals("260", s.mnc)
        assertNotNull(s.enbOrGnbId)
        assertNotNull(s.lcid)
        assertTrue(s.caBands.isEmpty())
        assertNull(s.anchorPci)
    }

    @Test
    fun `NSA with no NR cell but LTE anchor and CA bands falls back to 4G_CA`() {
        every { netMonster.getNetworkType(1) } returns nsaType()
        val anchor = lteCell(pci = 42)
        val secondary = lteCell(eci = 50_000, pci = 50, bandNumber = 7, earfcn = 2150, bandwidth = 10_000, primary = false)

        val s = snap(anchor, secondary)[0]
        assertEquals("4G_CA", s.rat)
        assertEquals(1, s.caBands.size)
        assertEquals(50, s.caBands[0].pci)
        assertEquals(10_000, s.caBands[0].bandwidthKhz)
    }

    // ── 3.4 NSA no NR no LTE ──────────────────────────────────────

    @Test
    fun `NSA with no NR and no LTE produces UNKNOWN with networkTypeCode`() {
        every { netMonster.getNetworkType(1) } returns nsaType()
        // A secondary-only LTE cell (no primary LTE anchor, no NR)
        val secondaryOnly = lteCell(eci = 50_000, pci = 50, primary = false)

        val s = snap(secondaryOnly)[0]
        assertEquals("UNKNOWN", s.rat)
        assertEquals(45, s.networkTypeCode)
        assertNull(s.pci)
        assertNull(s.tac)
        assertNull(s.bandNumber)
        assertNull(s.rsrp)
        assertNull(s.enbOrGnbId)
        assertNull(s.anchorPci)
    }

    // ── 3.5 LTE with secondary cells extracts CA bands ────────────

    @Test
    fun `LTE with secondary cells extracts CA bands with bandwidth and sets 4G_CA`() {
        every { netMonster.getNetworkType(1) } returns lteType()
        val primary = lteCell(eci = 100_000, pci = 42, bandNumber = 3, earfcn = 1800, bandwidth = 20_000)
        val secondary = lteCell(
            eci = 50_000, pci = 50, bandNumber = 7, earfcn = 2150, bandwidth = 10_000,
            rsrp = -90, rsrq = -95, snr = 5, rssi = -80, cqi = 5, timingAdvance = 12, primary = false
        )

        val s = snap(primary, secondary)[0]
        assertEquals("4G_CA", s.rat)
        assertEquals(13, s.networkTypeCode)
        assertEquals(8, s.cellIdBitLength)
        // Primary bandwidth still captured
        assertEquals(20_000, s.bandwidthKhz)
        assertEquals(1, s.caBands.size)
        val ca = s.caBands[0]
        assertEquals(50, ca.pci)
        assertEquals(10_000, ca.bandwidthKhz)
        assertEquals(-90, ca.rsrp)
        assertEquals(-95, ca.rsrq)
        assertEquals(5, ca.sinr)
        assertEquals(-80, ca.rssi)
        assertEquals(5, ca.cqi)
        assertEquals(12, ca.timingAdvance)
        // bandNumber derived via BandTableLte from the secondary EARFCN
        assertNotNull(ca.bandNumber)
    }

    @Test
    fun `LTE with no secondary cells sets 4G regardless of modem network type`() {
        every { netMonster.getNetworkType(1) } returns lteType()
        val primary = lteCell()

        val s = snap(primary)[0]
        assertEquals("4G", s.rat)
        assertTrue(s.caBands.isEmpty())
    }

    // ── 3.6 5G SA and NSA bandwidthKhz ────────────────────────────

    @Test
    fun `5G SA record leaves bandwidthKhz null because netmonster does not expose NR bandwidth`() {
        every { netMonster.getNetworkType(1) } returns saType()
        val nr = nrCell(bandNumber = 78, earfcn = 620_000)

        val s = snap(nr)[0]
        assertEquals("5G_SA", s.rat)
        assertEquals(78, s.bandNumber)
        assertEquals(620_000, s.earfcn)
        // NR bandwidth is not available from netmonster 1.3.0 → stays null
        assertNull(s.bandwidthKhz)
    }

    @Test
    fun `5G NSA record leaves bandwidthKhz null because netmonster does not expose NR bandwidth`() {
        every { netMonster.getNetworkType(1) } returns nsaType()
        val anchor = lteCell(pci = 200, bandwidth = 20_000)
        val nr = nrCell(bandNumber = 78, earfcn = 620_000)

        val s = snap(anchor, nr)[0]
        assertEquals("5G_NSA", s.rat)
        // NR primary bandwidth not available; only the LTE anchor bandwidth is captured
        assertNull(s.bandwidthKhz)
        assertEquals(20_000, s.anchorBandwidthKhz)
    }

    @Test
    fun `LTE record captures bandwidthKhz from the cell`() {
        every { netMonster.getNetworkType(1) } returns lteType()
        val primary = lteCell(bandwidth = 20_000)

        val s = snap(primary)[0]
        assertEquals(20_000, s.bandwidthKhz)
        assertEquals("4G", s.rat)
    }

    @Test
    fun `NSA with no LTE anchor produces 5G_NSA with null anchor fields`() {
        every { netMonster.getNetworkType(1) } returns nsaType()
        val nr = nrCell(pci = 100, tac = 5555)

        val s = snap(nr)[0]
        assertEquals("5G_NSA", s.rat)
        assertEquals(100, s.pci)
        // NR TAC used as primary even with no anchor
        assertEquals(5555, s.tac)
        assertNull(s.anchorPci)
        assertNull(s.anchorTac)
        assertNull(s.anchorEnbOrGnbId)
    }

    @Test
    fun `LTE primary cell splits identity into enb and lcid with bit length 8`() {
        every { netMonster.getNetworkType(1) } returns lteType()
        // eci = 0x010064 = 65636 → enb = 65636 shr 8 = 256, lcid = 65636 & 0xFF = 100
        val primary = lteCell(eci = 65_636)

        val s = snap(primary)[0]
        assertEquals(8, s.cellIdBitLength)
        assertEquals(256L, s.enbOrGnbId)
        assertEquals(100, s.lcid)
    }

    // helper: copy a CellNr with a different TAC (CellNr has no copy() exposed for tac only,
    // so rebuild it)
    private fun copyNrTac(src: CellNr, tac: Int?): CellNr =
        CellNr(src.network, src.nci, tac, src.pci, src.band, src.signal, src.connectionStatus, src.subscriptionId, src.timestamp)

    // Sanity check that BandTableLte maps the EARFCNs we use, keeping LTE band assertions stable.
    @Test
    fun `assumption - BandTableLte maps earfcn 1800 to band 3`() {
        assertEquals(3, BandTableLte.map(1800)?.number)
    }
}
