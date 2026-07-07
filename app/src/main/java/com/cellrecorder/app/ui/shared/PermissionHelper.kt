package com.cellrecorder.app.ui.shared

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.cellrecorder.app.BuildConfig
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

    fun tunnelForegroundPermissions(): Array<String> = buildList {
        add(Manifest.permission.READ_PHONE_STATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    fun backgroundPermissions(): Array<String> = buildList {
        add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    }.toTypedArray()

    fun indoorPermissions(): Array<String> = buildList {
        add(Manifest.permission.ACTIVITY_RECOGNITION)
    }.toTypedArray()

    fun requiredPermissions(): Array<String> = foregroundPermissions() + backgroundPermissions()

    fun allGranted(context: Context): Boolean =
        requiredPermissions().all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

    fun allGrantedForMode(recordingMode: String, context: Context): Boolean {
        if (recordingMode == "TUNNEL") {
            return tunnelForegroundPermissions().all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
        }
        if (!allGranted(context)) return false
        if (recordingMode == "INDOOR") return allIndoorGranted(context)
        return true
    }

    fun allIndoorGranted(context: Context): Boolean =
        indoorPermissions().all {
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

    fun missingTunnelForegroundPermissions(context: Context): Array<String> =
        tunnelForegroundPermissions().filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

    fun missingIndoorPermissions(context: Context): Array<String> =
        indoorPermissions().filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

    fun missingPermissionsForMode(recordingMode: String, context: Context): Array<String> {
        if (recordingMode == "TUNNEL") {
            return missingTunnelForegroundPermissions(context)
        }
        val result = mutableListOf<String>()
        result.addAll(missingForegroundPermissions(context).toList())
        if (recordingMode == "INDOOR") {
            result.addAll(missingIndoorPermissions(context).toList())
        }
        return result.toTypedArray()
    }

    fun missingAllForMode(recordingMode: String, context: Context): Array<String> {
        if (recordingMode == "TUNNEL") {
            return missingTunnelForegroundPermissions(context)
        }
        val result = mutableListOf<String>()
        result.addAll(missingForegroundPermissions(context).toList())
        if (recordingMode == "INDOOR") {
            result.addAll(missingIndoorPermissions(context).toList())
        }
        result.addAll(missingBackgroundPermissions(context).toList())
        return result.toTypedArray()
    }

    fun missingBackgroundPermissions(context: Context): Array<String> =
        backgroundPermissions().filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

    fun decidePermissionState(
        hasAttemptedOnce: Boolean,
        missingPermissions: Array<String>,
        activity: Activity?
    ): PermissionUiState {
        if (missingPermissions.isEmpty()) return PermissionUiState.AllGranted
        if (activity == null) return PermissionUiState.ShowRationale
        return decidePermissionState(hasAttemptedOnce, missingPermissions) { permission ->
            ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
        }
    }

    fun decidePermissionState(
        hasAttemptedOnce: Boolean,
        missingPermissions: Array<String>,
        rationaleProvider: (String) -> Boolean
    ): PermissionUiState {
        if (missingPermissions.isEmpty()) return PermissionUiState.AllGranted
        val anyRationale = missingPermissions.any(rationaleProvider)
        return when {
            anyRationale -> PermissionUiState.ShowRationale
            hasAttemptedOnce -> PermissionUiState.ShowSettings
            else -> PermissionUiState.ShowRationale
        }
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
        if (BuildConfig.DEBUG) android.util.Log.d(TAG, "[$label] foreground=$fg  background=$bg")
    }

    private fun Int.permissionLabel(): String = when (this) {
        PackageManager.PERMISSION_GRANTED -> "GRANTED"
        PackageManager.PERMISSION_DENIED -> "DENIED"
        else -> "UNKNOWN($this)"
    }
}