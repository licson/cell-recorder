package com.cellrecorder.app.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class BandResolverTest {

    @Nested
    inner class FormatBandPrefixSelection {

        @Test
        fun `5G with null earfcn uses n prefix`() {
            assertEquals("n78", BandResolver.formatBand(78, null, "5G_NSA"))
            assertEquals("n41", BandResolver.formatBand(41, null, "5G_SA"))
        }

        @Test
        fun `5G with earfcn at 82000 boundary uses n prefix`() {
            assertEquals("n78", BandResolver.formatBand(78, 82_000, "5G_NSA"))
            assertEquals("n78", BandResolver.formatBand(78, 82_001, "5G_NSA"))
        }

        @Test
        fun `5G with earfcn below 82000 uses B prefix (LTE anchor band)`() {
            assertEquals("B3", BandResolver.formatBand(3, 1_800, "5G_NSA"))
            assertEquals("B7", BandResolver.formatBand(7, 9_210, "5G_NSA"))
        }

        @Test
        fun `4G RAT always uses B prefix`() {
            assertEquals("B3", BandResolver.formatBand(3, null, "4G_LTE"))
            assertEquals("B3", BandResolver.formatBand(3, 1_800, "4G_LTE"))
            assertEquals("B7", BandResolver.formatBand(7, 9_210, "4G_LTE"))
        }

        @Test
        fun `3G RAT uses B prefix`() {
            assertEquals("B1", BandResolver.formatBand(1, null, "3G"))
            assertEquals("B5", BandResolver.formatBand(5, 4_000, "3G"))
        }

        @Test
        fun `unknown band number and unresolvable earfcn returns dashes`() {
            assertEquals("---", BandResolver.formatBand(null, null, "5G_NSA"))
            assertEquals("---", BandResolver.formatBand(null, null, "4G_LTE"))
            assertEquals("---", BandResolver.formatBand(null, null, "3G"))
        }
    }

    @Nested
    inner class ResolveBandNumberFallbackToMapEarfcn {

        @Test
        fun `bandNumber present takes precedence over earfcn`() {
            assertEquals(7, BandResolver.resolveBandNumber(7, 1_800, "4G_LTE"))
            assertEquals(78, BandResolver.resolveBandNumber(78, null, "5G_NSA"))
        }

        @Test
        fun `null bandNumber falls back to mapEarfcn`() {
            val resolved = BandResolver.resolveBandNumber(null, 1_800, "4G_LTE")
            assertEquals(3, resolved)
        }

        @Test
        fun `both null returns null`() {
            assertNull(BandResolver.resolveBandNumber(null, null, "4G_LTE"))
            assertNull(BandResolver.resolveBandNumber(null, null, "5G_NSA"))
            assertNull(BandResolver.resolveBandNumber(null, null, "3G"))
        }
    }

    @Nested
    inner class MapEarfcnDispatchByRatPrefix {

        @Test
        fun `4G prefix dispatches to BandTableLte`() {
            val resolved = BandResolver.resolveBandNumber(null, 1_800, "4G_LTE")
            assertEquals(3, resolved)
        }

        @Test
        fun `5G prefix dispatches to BandTableNr or fallback`() {
            val resolved = BandResolver.resolveBandNumber(null, 620_000, "5G_NSA")
            assertEquals(78, resolved)
        }

        @Test
        fun `exact 3G RAT dispatches to BandTableWcdma`() {
            val resolved = BandResolver.resolveBandNumber(null, 500, "3G")
            assertEquals(2, resolved)
        }

        @Test
        fun `unknown RAT prefix returns null`() {
            assertNull(BandResolver.resolveBandNumber(null, 1_800, "2G_GSM"))
            assertNull(BandResolver.resolveBandNumber(null, 1_800, "UNKNOWN"))
            assertNull(BandResolver.resolveBandNumber(null, 1_800, ""))
        }
    }

    @Nested
    inner class FallbackNrBandRanges {

        @Test
        fun `band 78 range 620000 to 653333`() {
            assertEquals(78, BandResolver.resolveBandNumber(null, 620_000, "5G_NSA"))
            assertEquals(78, BandResolver.resolveBandNumber(null, 636_666, "5G_NSA"))
            assertEquals(78, BandResolver.resolveBandNumber(null, 653_333, "5G_NSA"))
        }

        @Test
        fun `band 77 range 653334 to 680000`() {
            assertEquals(77, BandResolver.resolveBandNumber(null, 653_334, "5G_NSA"))
            assertEquals(77, BandResolver.resolveBandNumber(null, 666_667, "5G_NSA"))
            assertEquals(77, BandResolver.resolveBandNumber(null, 680_000, "5G_NSA"))
        }

        @Test
        fun `band 41 range 499200 to 537999`() {
            assertEquals(41, BandResolver.resolveBandNumber(null, 499_200, "5G_NSA"))
            assertEquals(41, BandResolver.resolveBandNumber(null, 518_600, "5G_NSA"))
            assertEquals(41, BandResolver.resolveBandNumber(null, 537_999, "5G_NSA"))
        }

        @Test
        fun `band 28 range 151600 to 153600`() {
            assertEquals(28, BandResolver.resolveBandNumber(null, 151_600, "5G_NSA"))
            assertEquals(28, BandResolver.resolveBandNumber(null, 152_600, "5G_NSA"))
            assertEquals(28, BandResolver.resolveBandNumber(null, 153_600, "5G_NSA"))
        }

        @Test
        fun `band 1 range 422000 to 434000`() {
            assertEquals(1, BandResolver.resolveBandNumber(null, 422_000, "5G_NSA"))
            assertEquals(1, BandResolver.resolveBandNumber(null, 428_000, "5G_NSA"))
            assertEquals(1, BandResolver.resolveBandNumber(null, 434_000, "5G_NSA"))
        }

        @Test
        fun `band 65 range 434001 to 435000`() {
            assertEquals(65, BandResolver.resolveBandNumber(null, 434_001, "5G_NSA"))
            assertEquals(65, BandResolver.resolveBandNumber(null, 434_500, "5G_NSA"))
            assertEquals(65, BandResolver.resolveBandNumber(null, 435_000, "5G_NSA"))
        }

        @Test
        fun `out-of-range EARFCN returns null`() {
            assertNull(BandResolver.resolveBandNumber(null, 0, "5G_NSA"))
            assertNull(BandResolver.resolveBandNumber(null, 1, "5G_NSA"))
            assertNull(BandResolver.resolveBandNumber(null, 680_001, "5G_NSA"))
            assertNull(BandResolver.resolveBandNumber(null, 999_999, "5G_NSA"))
            assertNull(BandResolver.resolveBandNumber(null, 1_000_000, "5G_NSA"))
        }

        @Test
        fun `boundary between band 1 and band 65 — 434000 maps to band 1, 434001 to band 65`() {
            assertEquals(1, BandResolver.resolveBandNumber(null, 434_000, "5G_NSA"))
            assertEquals(65, BandResolver.resolveBandNumber(null, 434_001, "5G_NSA"))
        }

        @Test
        fun `boundary between band 78 and band 77 — 653333 maps to band 78, 653334 to band 77`() {
            assertEquals(78, BandResolver.resolveBandNumber(null, 653_333, "5G_NSA"))
            assertEquals(77, BandResolver.resolveBandNumber(null, 653_334, "5G_NSA"))
        }
    }
}
