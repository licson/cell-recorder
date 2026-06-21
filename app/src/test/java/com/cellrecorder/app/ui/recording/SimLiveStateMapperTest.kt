package com.cellrecorder.app.ui.recording

import com.cellrecorder.app.domain.model.CaBandSnapshot
import com.cellrecorder.app.domain.model.CellRecordSnapshot
import com.cellrecorder.app.service.SimLiveState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class SimLiveStateMapperTest {

    private fun snapshot(
        subscriptionId: Int = 1,
        rat: String = "4G_LTE",
        mcc: String? = "310",
        mnc: String? = "260",
        tac: Int? = 1234,
        bandNumber: Int? = 3,
        earfcn: Int? = 1800,
        pci: Int? = 42,
        rsrp: Int? = -85,
        rsrq: Int? = -90,
        sinr: Int? = 8,
        enbOrGnbId: Long? = 555L,
        lcid: Int? = 99,
        fullCellIdentity: Long? = null,
        caBands: List<CaBandSnapshot> = emptyList(),
        anchorPci: Int? = null,
        anchorBandNumber: Int? = null,
        anchorRsrp: Int? = null
    ): CellRecordSnapshot = CellRecordSnapshot(
        subscriptionId = subscriptionId,
        rat = rat,
        mcc = mcc,
        mnc = mnc,
        tac = tac,
        bandNumber = bandNumber,
        earfcn = earfcn,
        pci = pci,
        rsrp = rsrp,
        rsrq = rsrq,
        sinr = sinr,
        enbOrGnbId = enbOrGnbId,
        lcid = lcid,
        fullCellIdentity = fullCellIdentity,
        caBands = caBands,
        anchorPci = anchorPci,
        anchorBandNumber = anchorBandNumber,
        anchorRsrp = anchorRsrp
    )

    @Nested
    inner class PlainFourGRecord {

        @Test
        fun `maps all primary fields for a 4G LTE record`() {
            val s = snapshot()
            val state = SimLiveStateMapper.map(s, simSlotIndex = 0)
            assertEquals(1, state.subscriptionId)
            assertEquals(0, state.simSlotIndex)
            assertEquals("310-260", state.plmn)
            assertEquals("4G_LTE", state.rat)
            assertEquals("1234", state.tac)
            assertEquals("B3", state.bandNumber)
            assertEquals("1800", state.earfcn)
            assertEquals("555:99", state.cellId)
            assertEquals("42", state.pci)
            assertEquals("-85", state.rsrp)
            assertEquals("-90", state.rsrq)
            assertEquals("8", state.sinr)
            assertEquals(emptyList<String>(), state.caBands)
            assertEquals("", state.anchorInfo)
        }

        @Test
        fun `simSlotIndex overrides snapshot's own simSlotIndex when provided`() {
            val s = snapshot()
            val state = SimLiveStateMapper.map(s, simSlotIndex = 2)
            assertEquals(2, state.simSlotIndex)
        }

        @Test
        fun `plmn parameter overrides derived plmn when provided`() {
            val s = snapshot()
            val state = SimLiveStateMapper.map(s, simSlotIndex = 0, plmn = "Custom")
            assertEquals("Custom", state.plmn)
        }
    }

    @Nested
    inner class FiveG_NsaAnchorRecord {

        @Test
        fun `anchorInfo is populated for 5G_NSA record with anchorPci`() {
            val s = snapshot(
                rat = "5G_NSA",
                bandNumber = 78,
                earfcn = 620_000,
                anchorPci = 100,
                anchorBandNumber = 3,
                anchorRsrp = -85
            )
            val state = SimLiveStateMapper.map(s, simSlotIndex = 0)
            assertEquals("B3 PCI 100 RSRP -85", state.anchorInfo)
            assertEquals("n78", state.bandNumber)
        }

        @Test
        fun `anchorInfo is empty for 5G_NSA record without anchorPci`() {
            val s = snapshot(rat = "5G_NSA", anchorPci = null)
            val state = SimLiveStateMapper.map(s, simSlotIndex = 0)
            assertEquals("", state.anchorInfo)
        }

        @Test
        fun `anchorInfo is empty for non-5G_NSA record even with anchorPci`() {
            val s = snapshot(rat = "4G_LTE", anchorPci = 100)
            val state = SimLiveStateMapper.map(s, simSlotIndex = 0)
            assertEquals("", state.anchorInfo)
        }

        @Test
        fun `anchorInfo substitutes question marks when anchor fields are missing`() {
            val s = snapshot(rat = "5G_NSA", anchorPci = 100, anchorBandNumber = null, anchorRsrp = null)
            val state = SimLiveStateMapper.map(s, simSlotIndex = 0)
            assertEquals("B? PCI 100 RSRP ---", state.anchorInfo)
        }
    }

    @Nested
    inner class FourGCaBands {

        @Test
        fun `caBands are formatted as B-N-PCI-N strings`() {
            val s = snapshot(
                caBands = listOf(
                    CaBandSnapshot(bandNumber = 7, pci = 50),
                    CaBandSnapshot(bandNumber = 3, pci = 42)
                )
            )
            val state = SimLiveStateMapper.map(s, simSlotIndex = 0)
            assertEquals(2, state.caBands.size)
            assertEquals("B7 (PCI 50)", state.caBands[0])
            assertEquals("B3 (PCI 42)", state.caBands[1])
        }

        @Test
        fun `caBands substitute question marks when bandNumber or pci are null`() {
            val s = snapshot(
                caBands = listOf(CaBandSnapshot(bandNumber = null, pci = null))
            )
            val state = SimLiveStateMapper.map(s, simSlotIndex = 0)
            assertEquals(1, state.caBands.size)
            assertEquals("B? (PCI ?)", state.caBands[0])
        }

        @Test
        fun `empty caBands list produces empty caBands in state`() {
            val s = snapshot(caBands = emptyList())
            val state = SimLiveStateMapper.map(s, simSlotIndex = 0)
            assertTrue(state.caBands.isEmpty())
        }
    }

    @Nested
    inner class NullFields {

        @Test
        fun `null signal fields render as dashes`() {
            val s = snapshot(rsrp = null, rsrq = null, sinr = null, pci = null)
            val state = SimLiveStateMapper.map(s, simSlotIndex = 0)
            assertEquals("---", state.pci)
            assertEquals("---", state.rsrp)
            assertEquals("---", state.rsrq)
            assertEquals("---", state.sinr)
        }

        @Test
        fun `null tac and earfcn render as dashes`() {
            val s = snapshot(tac = null, earfcn = null)
            val state = SimLiveStateMapper.map(s, simSlotIndex = 0)
            assertEquals("---", state.tac)
            assertEquals("---", state.earfcn)
        }

        @Test
        fun `null mcc and mnc produce dashes plmn`() {
            val s = snapshot(mcc = null, mnc = null)
            val state = SimLiveStateMapper.map(s, simSlotIndex = 0)
            assertEquals("---", state.plmn)
        }

        @Test
        fun `mcc non-null but mnc null produces mcc alone (FormatUtils behavior)`() {
            val s = snapshot(mcc = "310", mnc = null)
            val state = SimLiveStateMapper.map(s, simSlotIndex = 0)
            assertEquals("310", state.plmn)
        }
    }

    @Nested
    inner class CellIdFormatting {

        @Test
        fun `enbOrGnbId and lcid both present produce enb_lcid format`() {
            val s = snapshot(enbOrGnbId = 555L, lcid = 99, fullCellIdentity = null)
            val state = SimLiveStateMapper.map(s, simSlotIndex = 0)
            assertEquals("555:99", state.cellId)
        }

        @Test
        fun `only enbOrGnbId falls back to fullCellIdentity`() {
            val s = snapshot(enbOrGnbId = 555L, lcid = null, fullCellIdentity = 99999L)
            val state = SimLiveStateMapper.map(s, simSlotIndex = 0)
            assertEquals("99999", state.cellId)
        }

        @Test
        fun `null enbOrGnbId and lcid falls back to fullCellIdentity`() {
            val s = snapshot(enbOrGnbId = null, lcid = null, fullCellIdentity = 99999L)
            val state = SimLiveStateMapper.map(s, simSlotIndex = 0)
            assertEquals("99999", state.cellId)
        }

        @Test
        fun `null enbOrGnbId lcid and fullCellIdentity produce dashes`() {
            val s = snapshot(enbOrGnbId = null, lcid = null, fullCellIdentity = null)
            val state = SimLiveStateMapper.map(s, simSlotIndex = 0)
            assertEquals("---", state.cellId)
        }
    }

    @Nested
    inner class BandFormatting {

        @Test
        fun `4G LTE band uses B prefix`() {
            val s = snapshot(rat = "4G_LTE", bandNumber = 3, earfcn = 1800)
            val state = SimLiveStateMapper.map(s, simSlotIndex = 0)
            assertEquals("B3", state.bandNumber)
        }

        @Test
        fun `5G NR band with high earfcn uses n prefix`() {
            val s = snapshot(rat = "5G_NSA", bandNumber = 78, earfcn = 620_000)
            val state = SimLiveStateMapper.map(s, simSlotIndex = 0)
            assertEquals("n78", state.bandNumber)
        }

        @Test
        fun `5G NSA with low earfcn (LTE anchor) uses B prefix`() {
            val s = snapshot(rat = "5G_NSA", bandNumber = 3, earfcn = 1800)
            val state = SimLiveStateMapper.map(s, simSlotIndex = 0)
            assertEquals("B3", state.bandNumber)
        }

        @Test
        fun `null band and unresolvable earfcn produce dashes`() {
            val s = snapshot(bandNumber = null, earfcn = null)
            val state = SimLiveStateMapper.map(s, simSlotIndex = 0)
            assertEquals("---", state.bandNumber)
        }
    }
}
