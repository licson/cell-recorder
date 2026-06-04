package com.cellrecorder.app.domain.model

import cz.mroczis.netmonster.core.db.BandTableLte
import cz.mroczis.netmonster.core.db.BandTableNr
import cz.mroczis.netmonster.core.db.BandTableWcdma

object BandResolver {

    fun resolveBandNumber(bandNumber: Int?, earfcn: Int?, rat: String): Int? {
        return bandNumber
            ?: earfcn?.let { mapEarfcn(it, rat) }
    }

    fun formatBand(bandNumber: Int?, earfcn: Int?, rat: String): String {
        val band = resolveBandNumber(bandNumber, earfcn, rat) ?: return "---"
        val prefix = if (rat.startsWith("5G") && (earfcn == null || earfcn >= 82_000)) "n" else "B"
        return "$prefix$band"
    }

    private fun mapEarfcn(earfcn: Int, rat: String): Int? = when {
        rat.startsWith("4G") -> BandTableLte.map(earfcn)?.number
        rat.startsWith("5G") -> BandTableNr.map(earfcn)?.number
        rat == "3G" -> BandTableWcdma.map(earfcn)?.number
        else -> null
    }
}
