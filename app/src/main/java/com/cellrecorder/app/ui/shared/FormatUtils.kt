package com.cellrecorder.app.ui.shared

fun formatPlmn(mcc: String?, mnc: String?): String {
    if (mcc != null && mnc != null) return "$mcc-$mnc"
    if (mcc != null) return mcc
    return "---"
}