package com.atharok.screentime.ui.navigation

import androidx.navigation.NavType
import androidx.navigation.navArgument

sealed class AppNavDestination(val route: String) {
    data object PermissionDestination: AppNavDestination(route = "permission_route")
    data object DeviceUsageDestination: AppNavDestination(route = "device_usage_route")
    data object ApplicationUsageDestination: AppNavDestination(route = "application_usage_route") {
        const val APP_PACKAGE_ARG = "appPackageArg"
        val routeWithArgs = "$route/{$APP_PACKAGE_ARG}"
        val arguments = listOf(
            navArgument(APP_PACKAGE_ARG) {
                type = NavType.StringType
            }
        )
    }
    data object SettingsDestination: AppNavDestination(route = "settings_route")
    data object IgnoredAppsDestination: AppNavDestination(route = "ignored_apps_route")
    data object ThirdLibrariesDestination: AppNavDestination(route = "third_libraries_route")
}