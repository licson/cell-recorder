package com.cellrecorder.app.domain.usecase

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class BatchResplitUseCaseTest {

    @Test
    fun `lte split with default shift 8`() {
        val fullCellIdentity = 0x12345678L
        val shift = 8
        val enb = fullCellIdentity shr shift
        val mask = (1L shl shift) - 1
        val cid = fullCellIdentity and mask

        assertEquals(0x123456, enb)
        assertEquals(0x78L, cid)
    }

    @Test
    fun `lte split with custom shift 10`() {
        val fullCellIdentity = 0x12345678L
        val shift = 10
        val enb = fullCellIdentity shr shift
        val mask = (1L shl shift) - 1
        val cid = fullCellIdentity and mask

        assertEquals(0x48D15, enb)
        assertEquals(0x278L, cid)
    }

    @Test
    fun `nr split with default bit length 24`() {
        val nci = 0x123456789L
        val nrBitLen = 24
        val shift = 36 - nrBitLen
        val gnb = nci shr shift
        val mask = (1L shl shift) - 1
        val clId = nci and mask

        assertEquals(0x123456L, gnb)
        assertEquals(0x789L, clId)
    }

    @Test
    fun `nr split with custom bit length 22`() {
        val nci = 0x123456789L
        val nrBitLen = 22
        val shift = 36 - nrBitLen
        val gnb = nci shr shift
        val mask = (1L shl shift) - 1
        val clId = nci and mask

        assertEquals(0x48D15L, gnb)
        assertEquals(0x2789L, clId)
    }

    @Test
    fun `lte split handles null fullCellIdentity`() {
        val fullCellIdentity: Long? = null
        val shift = 8
        assertNull(fullCellIdentity?.shr(shift))
        assertNull(fullCellIdentity?.and((1L shl shift) - 1))
    }

    @Test
    fun `nr split handles null nci`() {
        val nci: Long? = null
        val nrBitLen = 24
        val shift = 36 - nrBitLen
        assertNull(nci?.shr(shift))
        assertNull(nci?.and((1L shl shift) - 1))
    }
}