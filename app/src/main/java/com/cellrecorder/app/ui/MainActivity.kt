package com.cellrecorder.app.ui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.cellrecorder.app.BuildConfig
import com.cellrecorder.app.service.RecordingService
import com.cellrecorder.app.ui.navigation.AppNavGraph
import com.cellrecorder.app.ui.navigation.Routes
import com.cellrecorder.app.ui.shared.PermissionDeniedDialog
import com.cellrecorder.app.ui.shared.PermissionHelper
import com.cellrecorder.app.ui.shared.PermissionRationaleDialog
import com.cellrecorder.app.ui.shared.PermissionUiState
import com.cellrecorder.app.ui.theme.CellRecorderTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val foregroundPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        isRequestingPermissions = false
        PermissionHelper.logPermissionState(this, "fg-result")
        handleForegroundResult()
    }

    private val backgroundPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        isRequestingPermissions = false
        PermissionHelper.logPermissionState(this, "bg-result")
        handlePermissionResult()
    }

    private var permissionState: PermissionUiState by mutableStateOf(PermissionUiState.Checking)
    private var pendingSessionId by mutableStateOf<Long?>(null)
    private var isRequestingPermissions by mutableStateOf(false)
    private var hasAttemptedOnce by mutableStateOf(false)
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        checkPermissionState()

        enableEdgeToEdge()
        setContent {
            CellRecorderTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()

                    LaunchedEffect(pendingSessionId) {
                        pendingSessionId?.let { id ->
                            pendingSessionId = null
                            navController.navigate(Routes.recording(id)) {
                                popUpTo(Routes.SESSION_LIST)
                            }
                        }
                    }

                    AppNavGraph(navController = navController)

                    when (permissionState) {
                        PermissionUiState.ShowRationale -> PermissionRationaleDialog(
                            onGrant = {
                                permissionState = PermissionUiState.Checking
                                isRequestingPermissions = true
                                requestNextPermissions()
                            }
                        )
                        PermissionUiState.ShowSettings -> PermissionDeniedDialog(
                            onOpenSettings = { PermissionHelper.openAppSettings(this@MainActivity) }
                        )
                        else -> {}
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!isRequestingPermissions) {
            checkPermissionState()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun checkPermissionState() {
        PermissionHelper.logPermissionState(this, "check-state")
        permissionState = if (PermissionHelper.allGranted(this)) {
            PermissionUiState.AllGranted
        } else {
            PermissionUiState.ShowRationale
        }
    }

    private fun requestNextPermissions() {
        val missingFg = PermissionHelper.missingForegroundPermissions(this)
        val missingBg = PermissionHelper.missingBackgroundPermissions(this)

        if (BuildConfig.DEBUG) android.util.Log.d("MainActivity", "requestNext: missingFg=${missingFg.toList()} missingBg=${missingBg.toList()}")

        when {
            missingFg.isNotEmpty() -> {
                mainHandler.postDelayed({
                    if (BuildConfig.DEBUG) android.util.Log.d("MainActivity", "Launching foreground permissions: ${missingFg.toList()}")
                    foregroundPermissionLauncher.launch(missingFg)
                }, 200)
            }
            missingBg.isNotEmpty() -> {
                mainHandler.postDelayed({
                    if (BuildConfig.DEBUG) android.util.Log.d("MainActivity", "Launching background permissions: ${missingBg.toList()}")
                    backgroundPermissionLauncher.launch(missingBg)
                }, 200)
            }
            else -> {
                isRequestingPermissions = false
                handlePermissionResult()
            }
        }
    }

    private fun handleForegroundResult() {
        if (PermissionHelper.allForegroundGranted(this) && PermissionHelper.missingBackgroundPermissions(this).isNotEmpty()) {
            isRequestingPermissions = true
            mainHandler.postDelayed({
                val missingBg = PermissionHelper.missingBackgroundPermissions(this)
                if (BuildConfig.DEBUG) android.util.Log.d("MainActivity", "Foreground done, launching background: ${missingBg.toList()}")
                backgroundPermissionLauncher.launch(missingBg)
            }, 200)
        } else {
            handlePermissionResult()
        }
    }

    private fun handlePermissionResult() {
        PermissionHelper.logPermissionState(this, "handle-result")
        permissionState = when {
            PermissionHelper.allGranted(this) -> PermissionUiState.AllGranted
            hasAttemptedOnce -> PermissionUiState.ShowSettings
            PermissionHelper.hasPermanentDenial(this) -> PermissionUiState.ShowSettings
            else -> {
                hasAttemptedOnce = true
                PermissionUiState.ShowRationale
            }
        }
    }

    private fun handleIntent(intent: Intent) {
        intent.getLongExtra(RecordingService.EXTRA_SESSION_ID, -1L)
            .takeIf { it != -1L }
            ?.let { pendingSessionId = it }
    }
}