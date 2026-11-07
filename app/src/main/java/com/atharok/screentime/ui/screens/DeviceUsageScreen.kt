package com.atharok.screentime.ui.screens

import android.graphics.drawable.Drawable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.atharok.screentime.R
import com.atharok.screentime.common.utils.CsvExporter
import com.atharok.screentime.common.utils.DateTimeUtils
import com.atharok.screentime.domain.entities.CentralTendency
import com.atharok.screentime.domain.entities.Period
import com.atharok.screentime.domain.entities.usage.DeviceUsage
import com.atharok.screentime.domain.entities.usage.MonthlyDeviceUsage
import com.atharok.screentime.domain.resources.Resource
import com.atharok.screentime.presentation.viewmodel.DeviceUsageViewModel
import com.atharok.screentime.ui.components.AppScaffold
import com.atharok.screentime.ui.components.ApplicationIcon
import com.atharok.screentime.ui.components.ExportAction
import com.atharok.screentime.ui.components.LoadingDialog
import com.atharok.screentime.ui.components.LogsAction
import com.atharok.screentime.ui.components.RefreshAction
import com.atharok.screentime.ui.components.SettingsAction
import com.atharok.screentime.ui.components.TextNormal
import com.atharok.screentime.ui.components.TextNormalSecondary
import com.atharok.screentime.ui.components.TextSmall
import com.atharok.screentime.ui.views.AppUsageGridView
import com.atharok.screentime.ui.views.MaintainerNotice
import com.atharok.screentime.ui.views.MonthlyInsightsView
import com.atharok.screentime.ui.views.PeriodSelector
import com.atharok.screentime.ui.views.TotalUsageView
import com.atharok.screentime.ui.views.ComparisonView
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer

@Composable
fun DeviceUsageScreen(
    viewModel: DeviceUsageViewModel,
    openApplicationUsageScreen: (appPackageName: String) -> Unit,
    openSettingsScreen: () -> Unit,
    openLogsScreen: () -> Unit,
    modifier: Modifier = Modifier
) {

    val deviceUsageResource: Resource<DeviceUsage<*>> by viewModel.getDeviceUsageFlow().collectAsStateWithLifecycle()

    LaunchedEffect(viewModel.periodState.value, viewModel.dayIndexState.intValue) {
        when(viewModel.periodState.value) {
            Period.MONTH -> viewModel.calculateMonthDeviceUsage(true)
            Period.WEEK -> viewModel.calculateWeekDeviceUsage(true)
            Period.DAY -> viewModel.calculateDayDeviceUsage(true)
            Period.YEAR -> viewModel.calculateYearDeviceUsage(true)
        }
        viewModel.calculateComparison()
    }

    // 首次成功拿到数据后，静默把历史点击量补齐（只补一次，补过的天会被直接跳过）。
    // 旧版本要用户自己反复点「补算」按钮十几次，每次 2~10 秒。
    val loadSucceeded = deviceUsageResource is Resource.Success
    LaunchedEffect(loadSucceeded) {
        if (loadSucceeded) viewModel.backfillLaunchHistoryIfNeeded()
    }

    StatelessDeviceUsageScreen(
        period = viewModel.periodState.value,
        onPeriodChange = {
            viewModel.setPeriod(it)
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
        openLogsScreen = openLogsScreen,
        refresh = {
            when(it) {
                Period.MONTH -> viewModel.calculateMonthDeviceUsage(false)
                Period.WEEK -> viewModel.calculateWeekDeviceUsage(false)
                Period.DAY -> viewModel.calculateDayDeviceUsage(false)
                Period.YEAR -> viewModel.calculateYearDeviceUsage(false)
            }
        },
        onBackfill = { viewModel.backfillLaunchHistory() },
        backfillRunning = viewModel.backfillRunning.value,
        backfillProgress = viewModel.backfillProgress.intValue,
        comparison = viewModel.comparisonFlow.collectAsStateWithLifecycle(initialValue = null).value,
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
    openLogsScreen: () -> Unit,
    refresh: (Period) -> Unit,
    onBackfill: (() -> Unit)? = null,
    backfillRunning: Boolean = false,
    backfillProgress: Int = 0,
    comparison: com.atharok.screentime.domain.usecases.UsageComparison? = null,
    modifier: Modifier = Modifier,
    modelProducer: CartesianChartModelProducer = remember { CartesianChartModelProducer() }
) {
    AppScaffold(
        title = stringResource(id = R.string.usage_analysis),
        modifier = modifier,
        topBarActions = {
            val context = LocalContext.current
            ExportAction(export = {
                if (deviceUsageResource is Resource.Success) {
                    CsvExporter.export(context, deviceUsageResource.data, period)
                }
            })
            LogsAction(openLogsScreen = openLogsScreen)
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
                modelProducer = modelProducer,
                onBackfill = onBackfill,
                backfillRunning = backfillRunning,
                backfillProgress = backfillProgress,
                comparison = comparison
            )
        //}
    }
}

@OptIn(ExperimentalFoundationApi::class)
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
    onBackfill: (() -> Unit)? = null,
    backfillRunning: Boolean = false,
    backfillProgress: Int = 0,
    comparison: com.atharok.screentime.domain.usecases.UsageComparison? = null,
    modifier: Modifier = Modifier
) {
    // Four horizontal period pages; each page keeps the existing two vertical chapters.
    // Pager snapping supplies the slide animation and limits a fling to one adjacent period.
    val periodPagerState = rememberPagerState(
        initialPage = period.ordinal,
        pageCount = { Period.entries.size }
    )

    LaunchedEffect(period) {
        if (periodPagerState.currentPage != period.ordinal) {
            periodPagerState.animateScrollToPage(period.ordinal)
        }
    }
    LaunchedEffect(periodPagerState.settledPage) {
        val settledPeriod = Period.entries.getOrNull(periodPagerState.settledPage)
        if (settledPeriod != null && settledPeriod != period) {
            onPeriodChange(settledPeriod)
        }
    }

    HorizontalPager(
        state = periodPagerState,
        flingBehavior = PagerDefaults.flingBehavior(
            state = periodPagerState,
            pagerSnapDistance = PagerSnapDistance.atMost(1),
            snapPositionalThreshold = 0.12f
        ),
        modifier = modifier
            .fillMaxSize()
            .padding(innerPadding)
    ) { periodPage ->
        val pagePeriod = Period.entries[periodPage]
        // Overview first, app ranking second. Each chapter keeps a LazyColumn for long lists.
        val chapterPagerState = rememberPagerState(pageCount = { 2 })

        Column(modifier = Modifier.fillMaxSize()) {
            TextSmall(
                text = if (chapterPagerState.currentPage == 0) {
                    "1 / 2  ·  ${stringResource(id = R.string.usage_analysis)}  ·  ↑"
                } else {
                    "2 / 2  ·  ${stringResource(id = R.string.app_usage_ranking)}  ·  ↓"
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = dimensionResource(id = R.dimen.padding_medium))
                    .padding(top = dimensionResource(id = R.dimen.padding_small))
            )

            VerticalPager(
                state = chapterPagerState,
                flingBehavior = PagerDefaults.flingBehavior(
                    state = chapterPagerState,
                    pagerSnapDistance = PagerSnapDistance.atMost(1),
                    snapPositionalThreshold = 0.12f
                ),
                modifier = Modifier.weight(1f)
            ) { page ->
                when (page) {
                    0 -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            bottom = dimensionResource(id = R.dimen.padding_large)
                        )
                    ) {
                        item {
                            MaintainerNotice(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        horizontal = dimensionResource(id = R.dimen.padding_medium),
                                        vertical = dimensionResource(id = R.dimen.padding_normal)
                                    )
                            )
                        }

                        item {
                            PeriodSelector(
                                period = pagePeriod,
                                onPeriodChange = onPeriodChange,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(dimensionResource(id = R.dimen.padding_medium))
                            )
                        }

                        when (deviceUsageResource) {
                            is Resource.Failure -> item {
                                FailureView(deviceUsageResource.throwable.toString())
                            }
                            is Resource.Progress -> item {
                                LoadingDialog(
                                    title = stringResource(id = R.string.calculating),
                                    message = stringResource(id = R.string.calculating_message)
                                )
                            }
                            is Resource.Success -> {
                                val deviceUsage = deviceUsageResource.data
                                item {
                                    TotalUsageView(
                                        period = pagePeriod,
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
                                comparison?.let { cmp ->
                                    if (cmp.period != Period.DAY) {
                                        item {
                                            ComparisonView(
                                                comparison = cmp,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(
                                                        horizontal = dimensionResource(id = R.dimen.padding_medium),
                                                        vertical = dimensionResource(id = R.dimen.padding_small)
                                                    )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            horizontal = dimensionResource(id = R.dimen.padding_medium),
                            vertical = dimensionResource(id = R.dimen.padding_medium)
                        )
                    ) {
                        when (deviceUsageResource) {
                            is Resource.Failure -> item {
                                FailureView(deviceUsageResource.throwable.toString())
                            }
                            is Resource.Progress -> item {
                                LoadingDialog(
                                    title = stringResource(id = R.string.calculating),
                                    message = stringResource(id = R.string.calculating_message)
                                )
                            }
                            is Resource.Success -> {
                                val deviceUsage = deviceUsageResource.data
                                if (pagePeriod == Period.MONTH && deviceUsage is MonthlyDeviceUsage) {
                                    item {
                                        MonthlyInsightsView(
                                            deviceUsage = deviceUsage,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(bottom = dimensionResource(id = R.dimen.padding_small))
                                        )
                                    }
                                }
                                item {
                                    AppUsageGridView(
                                        deviceUsage = deviceUsage,
                                        onAppClick = openApplicationUsageScreen,
                                        onBackfill = onBackfill,
                                        backfillRunning = backfillRunning,
                                        backfillProgress = backfillProgress,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
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
    if(period != Period.DAY) {
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
                packageName = packageName,
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