package com.cellrecorder.app.domain.model

enum class MarkerType(val storageString: String) {
    WAYPOINT("WAYPOINT"),
    SEGMENT_START("SEGMENT_START"),
    SEGMENT_END("SEGMENT_END"),
    STOP("STOP"),
    NOTE("NOTE");

    fun toStorageString(): String = storageString

    companion object {
        fun fromStorageString(value: String): MarkerType? =
            values().firstOrNull { it.storageString == value }
    }
}
