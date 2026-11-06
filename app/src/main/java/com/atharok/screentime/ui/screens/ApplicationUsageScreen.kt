package com.atharok.screentime.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.atharok.screentime.R
import com.atharok.screentime.domain.entities.CentralTendency
import com.atharok.screentime.domain.entities.Period
import com.atharok.screentime.domain.entities.usage.AppUsage
import com.atharok.screentime.domain.entities.usage.DeviceUsage
import com.atharok.screentime.domain.resources.Resource
import com.atharok.screentime.presentation.viewmodel.DeviceUsageViewModel
import com.atharok.screentime.ui.components.AppScaffold
import com.atharok.screentime.ui.components.LoadingDialog
import com.atharok.screentime.ui.components.NavigateUpAction
import com.atharok.screentime.ui.components.RefreshAction
import com.atharok.screentime.ui.components.SettingsAction
import com.atharok.screentime.ui.components.TextSmall
import com.atharok.screentime.ui.views.PeriodSelector
import com.atharok.screentime.ui.views.TotalUsageView
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer

@Composable
fun ApplicationUsageScreen(
    viewModel: DeviceUsageViewModel,
    appPackageName: String,
    navigateUp: () -> Unit,
    openSettingsScreen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val deviceUsageResource: Resource<DeviceUsage<*>> by viewModel.getDeviceUsageFlow().collectAsStateWithLifecycle()

    LaunchedEffect(viewModel.periodState.value, viewModel.dayIndexState.intValue) {
        when(viewModel.periodState.value) {
            Period.WEEK -> viewModel.calculateWeekDeviceUsage(true)
            Period.DAY -> viewModel.calculateDayDeviceUsage(true)
        }
    }

    StatelessApplicationUsageScreen(
        period = viewModel.periodState.value,
        onPeriodChange = {
            viewModel.periodState.value = it
        },
        dayIndex = viewModel.dayIndexState.intValue,
        onDayIndexChange = {
            viewModel.dayIndexState.intValue = it
        },
        selectedCentralTendency = viewModel.selectedCentralTendency.value,
        onSelectedCentralTendencyChange = {
            viewModel.selectedCentralTendency.value = it
        },
        deviceUsageResource = deviceUsageResource,
        packageName = appPackageName,
        navigateUp = navigateUp,
        openSettingsScreen = openSettingsScreen,
        refresh = {
            when(it) {
                Period.WEEK -> viewModel.calculateWeekDeviceUsage(false)
                Period.DAY -> viewModel.calculateDayDeviceUsage(false)
            }
        },
        modifier = modifier
    )
}

@Composable
private fun StatelessApplicationUsageScreen(
    period: Period,
    onPeriodChange: (Period) -> Unit,
    dayIndex: Int,
    onDayIndexChange: (Int) -> Unit,
    selectedCentralTendency: CentralTendency,
    onSelectedCentralTendencyChange: (CentralTendency) -> Unit,
    packageName: String,
    deviceUsageResource: Resource<DeviceUsage<*>>,
    navigateUp: () -> Unit,
    openSettingsScreen: () -> Unit,
    refresh: (Period) -> Unit,
    modifier: Modifier = Modifier,
    modelProducer: CartesianChartModelProducer = remember { CartesianChartModelProducer() }
) {
    ApplicationUsageScaffold(
        packageName = packageName,
        deviceUsageResource = deviceUsageResource,
        modifier = modifier,
        navigateUp = {
            NavigateUpAction(navigateUp)
        },
        topBarActions = {
            RefreshAction(refresh = { refresh(period) })
            SettingsAction(openSettingsScreen = openSettingsScreen)
        }
    ) { innerPadding ->

        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .fillMaxSize()
                .padding(innerPadding)
        ) {

            PeriodSelector(
                period = period,
                onPeriodChange = onPeriodChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(dimensionResource(id = R.dimen.padding_medium))
            )

            when(deviceUsageResource) {
                is Resource.Failure -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = dimensionResource(id = R.dimen.padding_medium))
                    ) {
                        TextSmall(text = stringResource(id = R.string.error_no_usage_data_was_found_for_this_application))
                    }
                }
                is Resource.Progress -> {
                    LoadingDialog(
                        title = stringResource(id = R.string.calculating),
                        message = stringResource(id = R.string.calculating_message)
                    )
                }
                is Resource.Success -> {
                    TotalUsageView(
                        period = period,
                        dayIndex = dayIndex,
                        onDayIndexChange = onDayIndexChange,
                        selectedCentralTendency = selectedCentralTendency,
                        onSelectedCentralTendencyChange = onSelectedCentralTendencyChange,
                        deviceUsage = deviceUsageResource.data,
                        appUsage = deviceUsageResource.data.getAppUsage(packageName),
                        modelProducer = modelProducer,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = dimensionResource(id = R.dimen.padding_medium))
                    )
                }
            }
        }
    }
}

@Composable
private fun TotalUsageView(
    period: Period,
    dayIndex: Int,
    onDayIndexChange: (Int) -> Unit,
    selectedCentralTendency: CentralTendency,
    onSelectedCentralTendencyChange: (CentralTendency) -> Unit,
    deviceUsage: DeviceUsage<*>,
    appUsage: AppUsage,
    modelProducer: CartesianChartModelProducer,
    modifier: Modifier = Modifier
) {
    if(period == Period.WEEK) {
        TotalUsageView(
            period = period,
            totalTime = appUsage.getTotalTimeUsed(),
            averageTime = appUsage.getAverageTimeUsed(),
            medianTime = appUsage.getMedianTimeUsed(),
            selectedCentralTendency = selectedCentralTendency,
            onSelectedCentralTendencyChange = onSelectedCentralTendencyChange,
            timeIntervalStart = deviceUsage.timeIntervalStart,
            timeIntervalEnd = deviceUsage.timeIntervalEnd,
            modelProducer = modelProducer,
            chartValues = appUsage.getChartValues(),
            dateOfLastRefresh = deviceUsage.dateOfLastRefresh,
            modifier = modifier
        )
    } else {
        TotalUsageView(
            dayIndex = dayIndex,
            onDayIndexChange = onDayIndexChange,
            period = period,
            totalTime = appUsage.getTotalTimeUsed(),
            averageTime = appUsage.getAverageTimeUsed(),
            medianTime = appUsage.getMedianTimeUsed(),
            selectedCentralTendency = selectedCentralTendency,
            onSelectedCentralTendencyChange = onSelectedCentralTendencyChange,
            timestamp = deviceUsage.timeIntervalStart,
            modelProducer = modelProducer,
            chartValues = appUsage.getChartValues(),
            dateOfLastRefresh = deviceUsage.dateOfLastRefresh,
            modifier = modifier
        )
    }
}

@Composable
private fun ApplicationUsageScaffold(
    packageName: String,
    deviceUsageResource: Resource<DeviceUsage<*>>,
    modifier: Modifier = Modifier,
    navigateUp: @Composable () -> Unit = {},
    topBarActions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    if(deviceUsageResource is Resource.Success) {
        AppScaffold(
            title = deviceUsageResource.data.getAppUsage(packageName).appName,
            icon = deviceUsageResource.data.getAppUsage(packageName).appIcon,
            modifier = modifier,
            navigateUp = navigateUp,
            topBarActions = topBarActions,
            content = content
        )
    } else {
        AppScaffold(
            title = stringResource(id = R.string.app_name),
            modifier = modifier,
            navigateUp = navigateUp,
            topBarActions = topBarActions,
            content = content
        )
    }
}