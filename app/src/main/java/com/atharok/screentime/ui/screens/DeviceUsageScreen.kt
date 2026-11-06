package com.atharok.screentime.ui.screens

import android.graphics.drawable.Drawable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.atharok.screentime.R
import com.atharok.screentime.common.utils.DateTimeUtils
import com.atharok.screentime.domain.entities.CentralTendency
import com.atharok.screentime.domain.entities.Period
import com.atharok.screentime.domain.entities.usage.DeviceUsage
import com.atharok.screentime.domain.resources.Resource
import com.atharok.screentime.presentation.viewmodel.DeviceUsageViewModel
import com.atharok.screentime.ui.components.AppScaffold
import com.atharok.screentime.ui.components.ApplicationIcon
import com.atharok.screentime.ui.components.LoadingDialog
import com.atharok.screentime.ui.components.RefreshAction
import com.atharok.screentime.ui.components.SettingsAction
import com.atharok.screentime.ui.components.TextNormal
import com.atharok.screentime.ui.components.TextNormalSecondary
import com.atharok.screentime.ui.components.TextSmall
import com.atharok.screentime.ui.views.PeriodSelector
import com.atharok.screentime.ui.views.TotalUsageView
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer

@Composable
fun DeviceUsageScreen(
    viewModel: DeviceUsageViewModel,
    openApplicationUsageScreen: (appPackageName: String) -> Unit,
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

    StatelessDeviceUsageScreen(
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
        openApplicationUsageScreen = openApplicationUsageScreen,
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
private fun StatelessDeviceUsageScreen(
    period: Period,
    onPeriodChange: (Period) -> Unit,
    dayIndex: Int,
    onDayIndexChange: (Int) -> Unit,
    selectedCentralTendency: CentralTendency,
    onSelectedCentralTendencyChange: (CentralTendency) -> Unit,
    deviceUsageResource: Resource<DeviceUsage<*>>,
    openApplicationUsageScreen: (appPackageName: String) -> Unit,
    openSettingsScreen: () -> Unit,
    refresh: (Period) -> Unit,
    modifier: Modifier = Modifier,
    modelProducer: CartesianChartModelProducer = remember { CartesianChartModelProducer() }
) {
    AppScaffold(
        title = stringResource(id = R.string.app_name),
        modifier = modifier,
        topBarActions = {
            RefreshAction(refresh = { refresh(period) })
            SettingsAction(openSettingsScreen = openSettingsScreen)
        }
    ) { innerPadding ->

        //val configuration = LocalConfiguration.current
        //val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        /*if(isLandscape) {
            LandscapeDeviceUsageScreen(
                period = period,
                onPeriodChange = onPeriodChange,
                dayIndex = dayIndex,
                onDayIndexChange = onDayIndexChange,
                deviceUsageResource = deviceUsageResource,
                openApplicationUsageScreen = openApplicationUsageScreen,
                innerPadding = innerPadding
            )
        } else {*/
            PortraitDeviceUsageScreen(
                period = period,
                onPeriodChange = onPeriodChange,
                dayIndex = dayIndex,
                onDayIndexChange = onDayIndexChange,
                selectedCentralTendency = selectedCentralTendency,
                onSelectedCentralTendencyChange = onSelectedCentralTendencyChange,
                deviceUsageResource = deviceUsageResource,
                openApplicationUsageScreen = openApplicationUsageScreen,
                innerPadding = innerPadding,
                modelProducer = modelProducer
            )
        //}
    }
}

@Composable
private fun PortraitDeviceUsageScreen(
    period: Period,
    onPeriodChange: (Period) -> Unit,
    dayIndex: Int,
    onDayIndexChange: (Int) -> Unit,
    selectedCentralTendency: CentralTendency,
    onSelectedCentralTendencyChange: (CentralTendency) -> Unit,
    deviceUsageResource: Resource<DeviceUsage<*>>,
    openApplicationUsageScreen: (appPackageName: String) -> Unit,
    innerPadding: PaddingValues,
    modelProducer: CartesianChartModelProducer,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = innerPadding
    ) {

        item {
            PeriodSelector(
                period = period,
                onPeriodChange = onPeriodChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(dimensionResource(id = R.dimen.padding_medium))
            )
        }

        when(deviceUsageResource) {
            is Resource.Failure -> {
                item {
                    FailureView(deviceUsageResource.throwable.toString())
                }
            }
            is Resource.Progress -> {
                item {
                    LoadingDialog(
                        title = stringResource(id = R.string.calculating),
                        message = stringResource(id = R.string.calculating_message)
                    )
                }
            }
            is Resource.Success -> {
                val deviceUsage: DeviceUsage<*> = deviceUsageResource.data

                item {
                    TotalUsageView(
                        period = period,
                        dayIndex = dayIndex,
                        onDayIndexChange = onDayIndexChange,
                        selectedCentralTendency = selectedCentralTendency,
                        onSelectedCentralTendencyChange = onSelectedCentralTendencyChange,
                        deviceUsage = deviceUsage,
                        modelProducer = modelProducer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = dimensionResource(id = R.dimen.padding_medium))
                    )
                }

                items(deviceUsage.appUsageList) { appUsage ->
                    ApplicationUsageItem(
                        packageName = appUsage.packageName,
                        appName = appUsage.appName,
                        appIcon = appUsage.appIcon,
                        timeUsed = appUsage.getTotalTimeUsed(),
                        percentUsed = appUsage.getPercentUsed(deviceUsage.getTotalTimeUsed()),
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable {
                                openApplicationUsageScreen(appUsage.packageName)
                            }
                            .padding(dimensionResource(id = R.dimen.padding_large))
                    )
                }
            }
        }
    }
}

/*@Composable
private fun LandscapeDeviceUsageScreen(
    period: Period,
    onPeriodChange: (Period) -> Unit,
    dayIndex: Int,
    onDayIndexChange: (Int) -> Unit,
    deviceUsageResource: Resource<DeviceUsage<*>>,
    openApplicationUsageScreen: (appPackageName: String) -> Unit,
    innerPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    LazyVerticalStaggeredGrid (
        columns = StaggeredGridCells.Fixed(2),
        modifier = modifier,
        contentPadding = innerPadding
    ) {
        item(span = StaggeredGridItemSpan.FullLine) {
            PeriodSelector(
                period = period,
                onPeriodChange = onPeriodChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(dimensionResource(id = R.dimen.padding_medium))
            )
        }

        when(deviceUsageResource) {
            is Resource.Failure -> {
                item(span = StaggeredGridItemSpan.FullLine) {
                    FailureView(deviceUsageResource.throwable.toString())
                }
            }
            is Resource.Progress -> {
                item(span = StaggeredGridItemSpan.FullLine) {
                    LoadingDialog(
                        title = stringResource(id = R.string.calculating),
                        message = stringResource(id = R.string.calculating_message)
                    )
                }
            }
            is Resource.Success -> {
                val deviceUsage: DeviceUsage<*> = deviceUsageResource.data

                item {
                    TotalUsageView(
                        period = period,
                        dayIndex = dayIndex,
                        onDayIndexChange = onDayIndexChange,
                        deviceUsage = deviceUsage,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = dimensionResource(id = R.dimen.padding_medium))
                    )
                }

                items(deviceUsage.appUsageList) { appUsage ->
                    ApplicationUsageItem(
                        packageName = appUsage.packageName,
                        appName = appUsage.appName,
                        appIcon = appUsage.appIcon,
                        timeUsed = appUsage.getTotalTimeUsed(),
                        percentUsed = appUsage.getPercentUsed(deviceUsage.getTotalTimeUsed()),
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable {
                                openApplicationUsageScreen(appUsage.packageName)
                            }
                            .padding(dimensionResource(id = R.dimen.padding_large))
                    )
                }
            }
        }
    }
}*/

@Composable
private fun FailureView(exceptionMessage: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = dimensionResource(id = R.dimen.padding_medium))
    ) {
        TextNormal(text = stringResource(id = R.string.error_failed_to_retrieve_usage_data))
        TextSmall(text = exceptionMessage)
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
    modelProducer: CartesianChartModelProducer,
    modifier: Modifier = Modifier
) {
    if(period == Period.WEEK) {
        TotalUsageView(
            period = period,
            totalTime = deviceUsage.getTotalTimeUsed(),
            averageTime = deviceUsage.getAverageTimeUsed(),
            medianTime = deviceUsage.getMedianTimeUsed(),
            selectedCentralTendency = selectedCentralTendency,
            onSelectedCentralTendencyChange = onSelectedCentralTendencyChange,
            timeIntervalStart = deviceUsage.timeIntervalStart,
            timeIntervalEnd = deviceUsage.timeIntervalEnd,
            modelProducer = modelProducer,
            chartValues = deviceUsage.getChartValues(),
            dateOfLastRefresh = deviceUsage.dateOfLastRefresh,
            modifier = modifier
        )
    } else {
        TotalUsageView(
            dayIndex = dayIndex,
            onDayIndexChange = onDayIndexChange,
            period = period,
            totalTime = deviceUsage.getTotalTimeUsed(),
            averageTime = deviceUsage.getAverageTimeUsed(),
            medianTime = deviceUsage.getMedianTimeUsed(),
            selectedCentralTendency = selectedCentralTendency,
            onSelectedCentralTendencyChange = onSelectedCentralTendencyChange,
            timestamp = deviceUsage.timeIntervalStart,
            modelProducer = modelProducer,
            chartValues = deviceUsage.getChartValues(),
            dateOfLastRefresh = deviceUsage.dateOfLastRefresh,
            modifier = modifier
        )
    }
}

@Composable
private fun ApplicationUsageItem(
    packageName: String,
    appName: String,
    appIcon: Drawable?,
    timeUsed: Long,
    percentUsed: Float,
    modifier: Modifier = Modifier
) {
    if(timeUsed > 0) {
        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimensionResource(id = R.dimen.padding_medium))
        ) {

            ApplicationIcon(
                drawable = appIcon,
                contentDescription = appName,
                modifier = Modifier.size(dimensionResource(id = R.dimen.icon_large_size))
            )

            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextNormal(
                        text = appName,
                        modifier = Modifier.weight(1f),
                        maxLines = 1
                    )
                    TextNormal(
                        text = DateTimeUtils.formatToDuration(timeUsed),
                        maxLines = 1
                    )
                }

                TextNormalSecondary(
                    text = packageName,
                    maxLines = 1
                )

                LinearProgressIndicator(
                    progress = { percentUsed },
                    modifier = Modifier
                        .padding(top = dimensionResource(id = R.dimen.linear_progress_indicator_height))
                        .fillMaxWidth()
                        .height(dimensionResource(id = R.dimen.linear_progress_indicator_height))
                        .clip(CircleShape),
                    drawStopIndicator = {}
                )
            }
        }
    }
}