package com.atharok.screentime.ui.app

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.atharok.screentime.presentation.viewmodel.DeviceUsageViewModel
import com.atharok.screentime.presentation.viewmodel.SettingsViewModel
import com.atharok.screentime.ui.navigation.AppNavDestination
import com.atharok.screentime.ui.navigation.AppNavHost
import com.atharok.screentime.ui.navigation.navigateTo
import com.atharok.screentime.ui.screens.ApplicationUsageScreen
import com.atharok.screentime.ui.screens.DeviceUsageScreen
import com.atharok.screentime.ui.screens.IgnoredAppsScreen
import com.atharok.screentime.ui.screens.PermissionScreen
import com.atharok.screentime.ui.screens.SettingsScreen
import com.atharok.screentime.ui.screens.ThirdLibrariesScreen
import com.atharok.screentime.ui.theme.ScreenTimeTheme
import org.koin.androidx.compose.koinViewModel

@Composable
fun ComposeRoot(
    navController: NavHostController = rememberNavController(),
    deviceUsageViewModel: DeviceUsageViewModel = koinViewModel(),
    settingsViewModel: SettingsViewModel = koinViewModel(),
    openSettings: () -> Unit = {
        navController.navigateTo(AppNavDestination.SettingsDestination.route)
    }
) {
    ScreenTimeTheme(settingsViewModel) {
        Surface(modifier = Modifier.fillMaxSize()) {

            AppNavHost(
                navController = navController,

                startDestination = if (deviceUsageViewModel.hasAppUsagePermission()) {
                    AppNavDestination.DeviceUsageDestination.route
                } else {
                    AppNavDestination.PermissionDestination.route
                },

                permissionScreen = {
                    PermissionScreen(
                        isAppUsageAccessAllowed = {
                            deviceUsageViewModel.hasAppUsagePermission()
                        },
                        openDeviceUsageScreen = {
                            navController.navigate(AppNavDestination.DeviceUsageDestination.route) {
                                popUpTo(0) {
                                    this.saveState = false
                                }
                                launchSingleTop = true
                            }
                        },
                        openSettingsScreen = openSettings,
                        modifier = Modifier
                    )
                },

                deviceUsageScreen = {
                    DeviceUsageScreen(
                        viewModel = deviceUsageViewModel,
                        openApplicationUsageScreen = { appPackageName: String ->
                            navController.navigateTo(
                                "${AppNavDestination.ApplicationUsageDestination.route}/$appPackageName"
                            )
                        },
                        openSettingsScreen = openSettings,
                        modifier = Modifier
                    )
                },

                applicationUsageScreen = { appPackageName: String? ->
                    appPackageName?.let {
                        ApplicationUsageScreen(
                            viewModel = deviceUsageViewModel,
                            appPackageName = appPackageName,
                            navigateUp = { navController.navigateUp() },
                            openSettingsScreen = openSettings,
                            modifier = Modifier
                        )
                    } ?: run {
                        navController.navigateUp()
                    }
                },

                settingsScreen = {
                    SettingsScreen(
                        navigateUp = { navController.navigateUp() },
                        openIgnoredAppsScreen = {
                            navController.navigateTo(AppNavDestination.IgnoredAppsDestination.route)
                        },
                        openThirdLibrariesScreen = {
                            navController.navigateTo(AppNavDestination.ThirdLibrariesDestination.route)
                        },
                        settingsViewModel = settingsViewModel,
                        modifier = Modifier
                    )
                },

                ignoredAppsScreen = {
                    IgnoredAppsScreen(
                        navigateUp = { navController.navigateUp() },
                        settingsViewModel = settingsViewModel,
                        modifier = Modifier
                    )
                },

                thirdLibrariesScreen = {
                    ThirdLibrariesScreen(
                        navigateUp = { navController.navigateUp() },
                        modifier = Modifier
                    )
                },

                modifier = Modifier.windowInsetsPadding(WindowInsets.displayCutout.exclude(WindowInsets.systemBars))
            )
        }
    }
}