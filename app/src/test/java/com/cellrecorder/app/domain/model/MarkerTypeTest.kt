package com.cellrecorder.app.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class MarkerTypeTest {

    @Test
    fun toStorageString_roundTripsAllValues() {
        MarkerType.values().forEach { type ->
            assertEquals(type.storageString, type.toStorageString())
            assertEquals(type, MarkerType.fromStorageString(type.storageString))
        }
    }

    @Test
    fun fromStorageString_returnsNullForUnknown() {
        assertNull(MarkerType.fromStorageString("INVALID"))
        assertNull(MarkerType.fromStorageString(""))
    }

    @Test
    fun allExpectedValuesExist() {
        val values = MarkerType.values().map { it.name }.toSet()
        assertEquals(
            setOf("WAYPOINT", "SEGMENT_START", "SEGMENT_END", "STOP", "NOTE"),
            values
        )
    }
}
