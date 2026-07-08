package com.timelock.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.timelock.app.ui.config.LockConfigScreen
import com.timelock.app.ui.dashboard.DashboardScreen
import com.timelock.app.ui.guide.KeepAliveGuideScreen
import com.timelock.app.ui.permission.PermissionGate
import com.timelock.app.ui.trend.TrendScreen

object Routes {
    const val DASHBOARD = "dashboard"
    const val CONFIG = "config"
    const val KEEP_ALIVE_GUIDE = "keep_alive_guide"
    const val TREND = "trend"
}

@Composable
fun TimeLockNavHost() {
    val navController = rememberNavController()

    PermissionGate(
        content = {
            NavHost(
                navController = navController,
                startDestination = Routes.DASHBOARD
            ) {
                composable(Routes.DASHBOARD) {
                    DashboardScreen(
                        onNavigateToConfig = {
                            navController.navigate(Routes.CONFIG)
                        },
                        onNavigateToTrend = {
                            navController.navigate(Routes.TREND)
                        }
                    )
                }
                composable(Routes.CONFIG) {
                    LockConfigScreen(
                        onBack = { navController.popBackStack() },
                        onNavigateToGuide = {
                            navController.navigate(Routes.KEEP_ALIVE_GUIDE)
                        }
                    )
                }
                composable(Routes.KEEP_ALIVE_GUIDE) {
                    KeepAliveGuideScreen(
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(Routes.TREND) {
                    TrendScreen(
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }
    )
}
