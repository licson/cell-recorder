package com.cellrecorder.app.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.cellrecorder.app.ui.detail.SessionDetailScreen
import com.cellrecorder.app.ui.detail.replay.ReplayScreen
import com.cellrecorder.app.ui.liveinfo.LiveInfoScreen
import com.cellrecorder.app.ui.recording.RecordingScreen
import com.cellrecorder.app.ui.sessionlist.SessionListScreen
import com.cellrecorder.app.ui.settings.SettingsScreen
import com.cellrecorder.app.ui.statistics.StatisticsScreen

@Composable
fun AppNavGraph(navController: NavHostController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val showBottomBar = currentRoute in listOf(
        Routes.LIVE_INFO,
        Routes.SESSION_LIST,
        Routes.STATISTICS
    )

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                BottomNavBar(navController = navController)
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.SESSION_LIST,
            modifier = Modifier.padding(padding)
        ) {
            composable(Routes.LIVE_INFO) {
                LiveInfoScreen()
            }

            composable(Routes.SESSION_LIST) {
                SessionListScreen(
                    onStartRecording = { sessionId ->
                        navController.navigate(Routes.recording(sessionId))
                    },
                    onOpenSession = { sessionId ->
                        navController.navigate(Routes.sessionDetail(sessionId))
                    },
                    onOpenSettings = {
                        navController.navigate(Routes.SETTINGS)
                    }
                )
            }

            composable(Routes.STATISTICS) {
                StatisticsScreen()
            }

            composable(
                route = Routes.RECORDING,
                arguments = listOf(navArgument("sessionId") { type = NavType.LongType })
            ) { backStackEntry ->
                val sessionId = backStackEntry.arguments?.getLong("sessionId") ?: return@composable
                RecordingScreen(
                    sessionId = sessionId,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(
                route = Routes.SESSION_DETAIL,
                arguments = listOf(navArgument("sessionId") { type = NavType.LongType })
            ) { backStackEntry ->
                val sessionId = backStackEntry.arguments?.getLong("sessionId") ?: return@composable
                SessionDetailScreen(
                    sessionId = sessionId,
                    onNavigateBack = { navController.popBackStack() },
                    onOpenReplay = { navController.navigate(Routes.replay(sessionId)) }
                )
            }

            composable(
                route = Routes.REPLAY,
                arguments = listOf(navArgument("sessionId") { type = NavType.LongType })
            ) { backStackEntry ->
                val sessionId = backStackEntry.arguments?.getLong("sessionId") ?: return@composable
                ReplayScreen(
                    sessionId = sessionId,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}