package com.cellrecorder.app.ui.shared

import com.cellrecorder.app.data.local.entity.CellRecordEntity
import com.cellrecorder.app.data.local.entity.CellRecordWithCaBands
import com.cellrecorder.app.service.SimLiveState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class CellInfoPanelMapperTest {

    private fun simLiveState(
        rat: String = "5G_NSA",
        anchorInfo: String = "B3 PCI 100 RSRP -85",
        anchorCellId: String = "200:5",
        anchorBand: String = "3",
        anchorArfcn: String = "1650",
        anchorPci: String = "100",
        anchorTac: String = "1",
        anchorRsrp: String = "-85",
        anchorRsrq: String = "-9",
        anchorSinr: String = "10"
    ): SimLiveState = SimLiveState(
        rat = rat,
        anchorInfo = anchorInfo,
        anchorCellId = anchorCellId,
        anchorBand = anchorBand,
        anchorArfcn = anchorArfcn,
        anchorPci = anchorPci,
        anchorTac = anchorTac,
        anchorRsrp = anchorRsrp,
        anchorRsrq = anchorRsrq,
        anchorSinr = anchorSinr
    )

    private fun nsaRecord(
        anchorEnbOrGnbId: Long? = 200L,
        anchorLcid: Int? = 5,
        anchorPci: Int? = 100
    ): CellRecordEntity = CellRecordEntity(
        sessionId = 1L,
        timestamp = 1_700_000_000_000L,
        latitude = 37.0,
        longitude = -122.0,
        altitude = 0.0,
        accuracy = 5f,
        rat = "5G_NSA",
        simSlotIndex = 0,
        bandNumber = 78,
        earfcn = 620_000,
        pci = 200,
        anchorPci = anchorPci,
        anchorEnbOrGnbId = anchorEnbOrGnbId,
        anchorLcid = anchorLcid,
        anchorBandNumber = 3,
        anchorEarfcn = 1650
    )

    @Nested
    inner class SimLiveStateToCellInfoData {

        @Test
        fun `anchor cell id is carried from anchorCellId for 5G_NSA record`() {
            val data = simLiveState(anchorCellId = "200:5").toCellInfoData()
            assertEquals("200:5", data.anchorCell?.cellId)
        }

        @Test
        fun `anchor cell id renders dashes when anchorCellId is dashes`() {
            val data = simLiveState(anchorCellId = "---").toCellInfoData()
            assertEquals("---", data.anchorCell?.cellId)
        }

        @Test
        fun `anchor cell is null for non-5G_NSA record`() {
            val data = simLiveState(rat = "4G", anchorInfo = "").toCellInfoData()
            assertNull(data.anchorCell)
        }

        @Test
        fun `anchor cell is null when anchorInfo is empty`() {
            val data = simLiveState(rat = "5G_NSA", anchorInfo = "").toCellInfoData()
            assertNull(data.anchorCell)
        }
    }

    @Nested
    inner class CellRecordWithCaBandsToCellInfoData {

        @Test
        fun `anchor cell id is formatted from anchorEnbOrGnbId and anchorLcid`() {
            val data = CellRecordWithCaBands(record = nsaRecord(anchorEnbOrGnbId = 200L, anchorLcid = 5)).toCellInfoData()
            assertEquals("200:5", data.anchorCell?.cellId)
        }

        @Test
        fun `anchor cell id renders dashes when anchorEnbOrGnbId is null`() {
            val data = CellRecordWithCaBands(record = nsaRecord(anchorEnbOrGnbId = null, anchorLcid = 5)).toCellInfoData()
            assertEquals("---", data.anchorCell?.cellId)
        }

        @Test
        fun `anchor cell id renders dashes when anchorLcid is null`() {
            val data = CellRecordWithCaBands(record = nsaRecord(anchorEnbOrGnbId = 200L, anchorLcid = null)).toCellInfoData()
            assertEquals("---", data.anchorCell?.cellId)
        }

        @Test
        fun `anchor cell id renders dashes when both anchor identity components are null`() {
            val data = CellRecordWithCaBands(record = nsaRecord(anchorEnbOrGnbId = null, anchorLcid = null)).toCellInfoData()
            assertEquals("---", data.anchorCell?.cellId)
        }

        @Test
        fun `anchor cell is null for non-5G_NSA record`() {
            val record = nsaRecord().copy(rat = "4G")
            val data = CellRecordWithCaBands(record = record).toCellInfoData()
            assertNull(data.anchorCell)
        }
    }
}
