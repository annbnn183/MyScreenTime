package com.atharok.screentime.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable

private val enterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    slideInHorizontally(
        initialOffsetX = { fullWidth -> fullWidth }
    )
}

private val exitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    slideOutHorizontally (
        targetOffsetX = { fullWidth -> -fullWidth }
    )
}

private val popEnterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    slideInHorizontally(
        initialOffsetX = { fullWidth -> -fullWidth }
    )
}

private val popExitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    slideOutHorizontally (
        targetOffsetX = { fullWidth -> fullWidth }
    )
}

@Composable
fun AppNavHost(
    navController: NavHostController,
    startDestination: String,
    permissionScreen: @Composable () -> Unit,
    deviceUsageScreen: @Composable () -> Unit,
    applicationUsageScreen: @Composable (appPackageName: String?) -> Unit,
    settingsScreen: @Composable () -> Unit,
    ignoredAppsScreen: @Composable () -> Unit,
    thirdLibrariesScreen: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
        enterTransition = enterTransition,
        exitTransition = exitTransition,
        popEnterTransition = popEnterTransition,
        popExitTransition = popExitTransition,
        predictivePopEnterTransition = { swipeEdge -> popEnterTransition() },
        predictivePopExitTransition = { swipeEdge -> popExitTransition() }
    ) {

        composable(
            route = AppNavDestination.PermissionDestination.route,
            enterTransition = enterTransition,
            exitTransition = exitTransition,
            popEnterTransition = popEnterTransition,
            popExitTransition = popExitTransition
        ) {
            permissionScreen()
        }

        composable(
            route = AppNavDestination.DeviceUsageDestination.route,
            enterTransition = enterTransition,
            exitTransition = exitTransition,
            popEnterTransition = popEnterTransition,
            popExitTransition = popExitTransition
        ) {
            deviceUsageScreen()
        }

        composable(
            route = AppNavDestination.ApplicationUsageDestination.routeWithArgs,
            arguments = AppNavDestination.ApplicationUsageDestination.arguments,
            enterTransition = enterTransition,
            exitTransition = exitTransition,
            popEnterTransition = popEnterTransition,
            popExitTransition = popExitTransition
        ) {
            val appPackageName: String? = it.arguments?.getString(AppNavDestination.ApplicationUsageDestination.APP_PACKAGE_ARG)
            applicationUsageScreen(appPackageName)
        }

        composable(
            route = AppNavDestination.SettingsDestination.route,
            enterTransition = enterTransition,
            exitTransition = exitTransition,
            popEnterTransition = popEnterTransition,
            popExitTransition = popExitTransition
        ) {
            settingsScreen()
        }

        composable(
            route = AppNavDestination.IgnoredAppsDestination.route,
            enterTransition = enterTransition,
            exitTransition = exitTransition,
            popEnterTransition = popEnterTransition,
            popExitTransition = popExitTransition
        ) {
            ignoredAppsScreen()
        }

        composable(
            route = AppNavDestination.ThirdLibrariesDestination.route,
            enterTransition = enterTransition,
            exitTransition = exitTransition,
            popEnterTransition = popEnterTransition,
            popExitTransition = popExitTransition
        ) {
            thirdLibrariesScreen()
        }
    }
}

fun NavHostController.navigateTo(
    route: String,
    launchSingleTop: Boolean = true
) {
    this.navigate(route) {
        this.launchSingleTop = launchSingleTop
    }
}