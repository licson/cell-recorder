package com.cellrecorder.app.ui.shared

import android.app.Activity
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Holds the mutable state for a runtime permission checkpoint.
 *
 * Flow:
 * 1. When permissions are missing, the rationale dialog is shown
 *    ([PermissionUiState.ShowRationale]).
 * 2. The user taps "Grant Permissions" → [prepareForRequest] sets [hasAttemptedOnce] = true,
 *    [isRequestingPermissions] = true, clears [permissionState], and the call site launches the
 *    system request.
 * 3. The system request returns → the call site evaluates [PermissionHelper.decidePermissionState]
 *    via [handleResult] (or sets [permissionState] directly).
 *    - If all granted → [PermissionUiState.AllGranted] → call site proceeds with the gated action.
 *    - If denied but can retry → [PermissionUiState.ShowRationale] → rationale re-shown.
 *    - If permanently denied → [PermissionUiState.ShowSettings] → Settings dialog shown.
 *
 * [hasAttemptedOnce] resets on screen leave / cold start (no persistence). A permanently denied
 * user sees the rationale dialog once per session, taps Grant, the system request returns denied
 * immediately, and then the Settings dialog is shown.
 *
 * The rationale dialog is ALWAYS shown before launching a runtime request, except when the
 * permission is permanently denied (in which case the Settings dialog is shown instead).
 */
@Stable
class PermissionFlowState(
    private val _permissionState: MutableState<PermissionUiState?>,
    private val _hasAttemptedOnce: MutableState<Boolean>,
    private val _isRequestingPermissions: MutableState<Boolean>,
) {
    var permissionState: PermissionUiState?
        get() = _permissionState.value
        set(value) { _permissionState.value = value }

    var hasAttemptedOnce: Boolean
        get() = _hasAttemptedOnce.value
        set(value) { _hasAttemptedOnce.value = value }

    var isRequestingPermissions: Boolean
        get() = _isRequestingPermissions.value
        set(value) { _isRequestingPermissions.value = value }

    /**
     * Call right before launching a system permission request.
     * Sets [hasAttemptedOnce] = true, [isRequestingPermissions] = true, clears [permissionState].
     */
    fun prepareForRequest() {
        hasAttemptedOnce = true
        isRequestingPermissions = true
        permissionState = null
    }

    /**
     * Evaluate the post-request state using [PermissionHelper.decidePermissionState].
     * If all granted, resets [hasAttemptedOnce] and [isRequestingPermissions], and calls [onAllGranted].
     */
    fun handleResult(
        missingPermissions: Array<String>,
        activity: Activity?,
        onAllGranted: () -> Unit,
    ) {
        val newState = PermissionHelper.decidePermissionState(hasAttemptedOnce, missingPermissions, activity)
        permissionState = newState
        if (newState == PermissionUiState.AllGranted) {
            hasAttemptedOnce = false
            isRequestingPermissions = false
            onAllGranted()
        }
    }

    /** Reset all state to initial values. */
    fun reset() {
        permissionState = null
        hasAttemptedOnce = false
        isRequestingPermissions = false
    }
}

/**
 * Creates and remembers a [PermissionFlowState] for use in a Compose screen that does not need
 * lifecycle-driven re-checks (e.g. [RecordingScreen], which only checks on user action).
 *
 * For checkpoints that need [android.app.Activity.onResume] re-checks (e.g. [MainActivity]),
 * construct [PermissionFlowState] directly at the activity level using [mutableStateOf] so the
 * activity's lifecycle callbacks can access it.
 *
 * @param permissions The set of permissions this checkpoint gates on. May be the full required
 *   set or just the currently-missing subset — [decidePermissionState] only cares about which are
 *   missing, so either works. Updated via [rememberUpdatedState] so lifecycle-driven re-checks
 *   always read the current value.
 * @param onAllGranted Called when all permissions are granted (proceed with the gated action).
 * @param autoRequestOnLaunch If true, evaluates the permission state on first composition and
 *   on every [androidx.lifecycle.Lifecycle.Event.ON_RESUME] (re-check after returning from Settings).
 */
@Composable
fun rememberPermissionFlowState(
    permissions: List<String>,
    onAllGranted: () -> Unit,
    autoRequestOnLaunch: Boolean,
): PermissionFlowState {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val currentPermissions = rememberUpdatedState(permissions)
    val currentOnAllGranted = rememberUpdatedState(onAllGranted)

    val state = remember {
        PermissionFlowState(
            _permissionState = mutableStateOf(null),
            _hasAttemptedOnce = mutableStateOf(false),
            _isRequestingPermissions = mutableStateOf(false),
        )
    }

    fun recheck() {
        if (state.isRequestingPermissions) return
        val missing = currentPermissions.value.filter {
            ContextCompat.checkSelfPermission(context, it) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }.toTypedArray()
        val newState = PermissionHelper.decidePermissionState(state.hasAttemptedOnce, missing, activity)
        state.permissionState = newState
        if (newState == PermissionUiState.AllGranted) {
            currentOnAllGranted.value()
        }
    }

    LaunchedEffect(autoRequestOnLaunch) {
        if (autoRequestOnLaunch) {
            recheck()
        }
    }

    if (autoRequestOnLaunch) {
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    recheck()
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }
    }

    return state
}

/**
 * Renders the rationale and settings dialogs based on [PermissionFlowState.permissionState].
 *
 * @param recordingMode Passed to [PermissionRationaleDialog] so the physical activity permission
 *   entry is shown only for indoor mode.
 * @param onRationaleGrant Called when the rationale dialog's Grant button is tapped. The call site
 *   should call [PermissionFlowState.prepareForRequest] and launch the system request.
 * @param onOpenSettings Called when the Settings dialog's Open Settings button is tapped.
 */
@Composable
fun PermissionFlowDialogs(
    state: PermissionFlowState,
    recordingMode: String = "OUTDOOR",
    onRationaleGrant: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    when (state.permissionState) {
        PermissionUiState.ShowRationale -> PermissionRationaleDialog(
            recordingMode = recordingMode,
            onGrant = onRationaleGrant,
        )
        PermissionUiState.ShowSettings -> PermissionDeniedDialog(onOpenSettings = onOpenSettings)
        else -> {}
    }
}
