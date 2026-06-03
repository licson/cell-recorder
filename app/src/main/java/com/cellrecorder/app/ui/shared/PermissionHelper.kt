package com.cellrecorder.app.ui.shared

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

sealed class PermissionUiState {
    object Checking : PermissionUiState()
    object AllGranted : PermissionUiState()
    object ShowRationale : PermissionUiState()
    object ShowSettings : PermissionUiState()
}

object PermissionHelper {

    private const val TAG = "PermissionHelper"

    fun foregroundPermissions(): Array<String> = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.READ_PHONE_STATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    fun backgroundPermissions(): Array<String> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
    }.toTypedArray()

    fun requiredPermissions(): Array<String> = foregroundPermissions() + backgroundPermissions()

    fun allGranted(context: Context): Boolean =
        requiredPermissions().all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

    fun allForegroundGranted(context: Context): Boolean =
        foregroundPermissions().all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

    fun allBackgroundGranted(context: Context): Boolean =
        backgroundPermissions().all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

    fun missingForegroundPermissions(context: Context): Array<String> =
        foregroundPermissions().filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

    fun missingBackgroundPermissions(context: Context): Array<String> =
        backgroundPermissions().filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

    fun hasPermanentDenial(activity: Activity): Boolean =
        requiredPermissions().any { permission ->
            ContextCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED &&
                    !ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
        }

    fun openAppSettings(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }

    fun logPermissionState(context: Context, label: String) {
        val fg = foregroundPermissions().associateWith {
            ContextCompat.checkSelfPermission(context, it).permissionLabel()
        }
        val bg = backgroundPermissions().associateWith {
            ContextCompat.checkSelfPermission(context, it).permissionLabel()
        }
        android.util.Log.d(TAG, "[$label] foreground=$fg  background=$bg")
    }

    private fun Int.permissionLabel(): String = when (this) {
        PackageManager.PERMISSION_GRANTED -> "GRANTED"
        PackageManager.PERMISSION_DENIED -> "DENIED"
        else -> "UNKNOWN($this)"
    }
}