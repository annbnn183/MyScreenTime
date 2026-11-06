package com.atharok.screentime.ui.views

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.atharok.screentime.R
import com.atharok.screentime.common.utils.DateTimeUtils
import com.atharok.screentime.domain.entities.usage.AppUsageRange
import com.atharok.screentime.domain.entities.usage.DeviceUsage
import com.atharok.screentime.domain.entities.usage.MonthlyDeviceUsage
import com.atharok.screentime.domain.entities.usage.RankedApp
import com.atharok.screentime.ui.components.ApplicationIcon
import java.util.Locale

/**
 * Four-column app ranking, inspired by Usage Analyzer. Long ranges (4 weeks / 6 months / year)
 * read the system's WEEKLY / MONTHLY / YEARLY buckets for screen time, while the launch counts
 * come from the app's own permanent day-by-day history. A summary card shows how many hours the
 * whole period adds up to instead of only per-day increments.
 */
@Composable
fun AppUsageGridView(
    deviceUsage: DeviceUsage<*>,
    onAppClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    onBackfill: (() -> Unit)? = null,
    backfillRunning: Boolean = false
) {
    val monthly = deviceUsage as? MonthlyDeviceUsage
    var rangeIndex by remember { mutableIntStateOf(0) }
    var sortIndex by remember { mutableIntStateOf(0) }
    // Only a few rows are composed at first. Long ranges can hold 200-300 apps; rendering all of
    // them inside a single LazyColumn item produced multi-hundred-ms frames. "Show 40 more" grows
    // the window incrementally, and switching range/sort resets it.
    var shownCount by remember(rangeIndex, sortIndex) { mutableIntStateOf(40) }

    val ranges = monthly?.rangeRankings.orEmpty()
    val fallbackApps: List<RankedApp> = remember(deviceUsage) {
        deviceUsage.appUsageList.map { app ->
            RankedApp(
                packageName = app.packageName,
                appName = app.appName,
                appIcon = app.appIcon,
                duration = app.getTotalTimeUsed(),
                launchCount = app.launchCount
            )
        }
    }

    val sourceApps: List<RankedApp> = if (ranges.isNotEmpty()) {
        ranges.getOrNull(rangeIndex)?.apps ?: fallbackApps
    } else {
        fallbackApps
    }
    val totalLaunches: Int = if (ranges.isNotEmpty()) {
        ranges.getOrNull(rangeIndex)?.totalLaunchCount ?: deviceUsage.totalLaunchCount
    } else {
        deviceUsage.totalLaunchCount
    }

    val apps: List<RankedApp> = remember(sourceApps, sortIndex) {
        when (sortIndex) {
            1 -> sourceApps.sortedWith(
                compareByDescending<RankedApp> { it.launchCount }
                    .thenByDescending { it.duration }
            )
            2 -> sourceApps.sortedBy { it.appName.lowercase(Locale.getDefault()) }
            else -> sourceApps.sortedByDescending { it.duration }
        }.filter { it.duration > 0L || it.launchCount > 0 }
    }

    val visibleApps = apps.take(shownCount)
    val sortLabels = listOf(R.string.sort_time, R.string.sort_launches, R.string.sort_name)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(id = R.string.app_usage_ranking),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { sortIndex = (sortIndex + 1) % sortLabels.size }) {
                Text(
                    text = stringResource(
                        id = R.string.sort_label,
                        stringResource(id = sortLabels[sortIndex])
                    ),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        if (ranges.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ranges.forEachIndexed { index, range ->
                    FilterChip(
                        selected = rangeIndex == index,
                        onClick = { rangeIndex = index },
                        label = { Text(text = stringResource(id = range.labelRes)) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            ranges.getOrNull(rangeIndex)?.let { range ->
                RangeSummaryCard(
                    range = range,
                    onBackfill = onBackfill,
                    backfillRunning = backfillRunning
                )
                Spacer(modifier = Modifier.height(10.dp))
            }
        }

        val maxDuration = remember(apps) {
            apps.maxOfOrNull { it.duration }?.coerceAtLeast(1L) ?: 1L
        }

        visibleApps.chunked(4).forEach { rowApps ->
            Row(modifier = Modifier.fillMaxWidth()) {
                rowApps.forEach { app ->
                    Box(modifier = Modifier.weight(1f)) {
                        GridAppItem(
                            app = app,
                            totalLaunches = totalLaunches,
                            maxDuration = maxDuration,
                            onAppClick = onAppClick
                        )
                    }
                }
                repeat(4 - rowApps.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }

        if (apps.size > 40) {
            if (shownCount < apps.size) {
                TextButton(
                    onClick = { shownCount = (shownCount + 40).coerceAtMost(apps.size) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(
                            id = R.string.grid_show_more,
                            shownCount,
                            apps.size
                        ),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Text(
                    text = stringResource(id = R.string.grid_lazy_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            } else {
                TextButton(
                    onClick = { shownCount = 40 },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(id = R.string.grid_collapse),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        if (apps.isEmpty()) {
            Text(
                text = stringResource(id = R.string.no_usage_data),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp)
            )
        } else {
            Text(
                text = stringResource(id = R.string.aggregate_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        // 按小时的数量统计：一天 24 小时各用了多久。
        if (monthly != null && monthly.hourlyTotals.any { it > 0L }) {
            Spacer(modifier = Modifier.height(16.dp))
            HourlyDistributionCard(hours = monthly.hourlyTotals)
        }
    }
}

/** 这一个范围总共多少小时、日均多少、点击总量多少、点击量覆盖了多少天。 */
@Composable
private fun RangeSummaryCard(
    range: AppUsageRange,
    onBackfill: (() -> Unit)?,
    backfillRunning: Boolean
) {
    val hours = DateTimeUtils.convertToHourFloat(range.totalTimeMillis)
    val dailyHours = hours / range.numberOfDays.coerceAtLeast(1)
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = stringResource(id = R.string.range_total_hours),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(id = R.string.hours_value, hours),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                StatColumn(
                    label = stringResource(id = R.string.range_daily_average),
                    value = stringResource(id = R.string.hours_value, dailyHours)
                )
                StatColumn(
                    label = stringResource(id = R.string.range_total_launches),
                    value = range.totalLaunchCount.toString()
                )
                StatColumn(
                    label = stringResource(id = R.string.range_days_recorded),
                    value = "${range.clickDaysRecorded} / ${range.numberOfDays}"
                )
            }

            // v1.0.14: be honest about each bucket's retention ceiling and the source of the
            // click counts (the system buckets carry no launch counts at all).
            if (range.retentionCapDays > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(id = R.string.range_retention_hint, range.retentionCapDays),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (onBackfill != null && range.clickDaysRecorded < range.numberOfDays) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(
                        id = R.string.click_backfill_hint,
                        range.clickDaysRecorded,
                        range.numberOfDays
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Button(onClick = onBackfill, enabled = !backfillRunning) {
                    Text(
                        text = if (backfillRunning) {
                            stringResource(id = R.string.click_backfill_running)
                        } else {
                            stringResource(id = R.string.click_backfill)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.StatColumn(label: String, value: String) {
    Column(modifier = Modifier.weight(1f)) {
        Text(
            text = label,
            maxLines = 1,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            maxLines = 1,
            style = MaterialTheme.typography.titleSmall
        )
    }
}

@Composable
private fun HourlyDistributionCard(hours: LongArray) {
    val max = hours.maxOrNull()?.coerceAtLeast(1L) ?: 1L
    val peak = hours.indices.maxByOrNull { hours[it] } ?: 0
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(id = R.string.hourly_distribution_title),
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = stringResource(
                id = R.string.hourly_peak,
                peak,
                DateTimeUtils.formatToDuration(hours[peak])
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(90.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            hours.forEach { value ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight((value.toFloat() / max.toFloat()).coerceIn(0.02f, 1f))
                        .padding(horizontal = 1.dp)
                        .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            listOf("0:00", "6:00", "12:00", "18:00", "23:00").forEach { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(id = R.string.hourly_distribution_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun GridAppItem(
    app: RankedApp,
    totalLaunches: Int,
    maxDuration: Long,
    onAppClick: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onAppClick(app.packageName) }
            .padding(vertical = 8.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ApplicationIcon(
            packageName = app.packageName,
            contentDescription = app.appName,
            modifier = Modifier.size(dimensionResource(id = R.dimen.icon_large_size))
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = app.appName,
            maxLines = 1,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            text = stringResource(
                id = R.string.hours_value,
                DateTimeUtils.convertToHourFloat(app.duration)
            ),
            maxLines = 1,
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = DateTimeUtils.formatToDuration(app.duration),
            maxLines = 1,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        // 点击量始终显示；即使总量为 0 也不隐藏这一行。
        Text(
            text = if (totalLaunches > 0) {
                stringResource(
                    id = R.string.launch_count_line,
                    app.launchCount,
                    app.launchCount * 100f / totalLaunches
                )
            } else {
                stringResource(id = R.string.launch_count_plain, app.launchCount)
            },
            maxLines = 1,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        LinearProgressIndicator(
            progress = { (app.duration.toFloat() / maxDuration.toFloat()).coerceIn(0f, 1f) },
            modifier = Modifier
                .padding(top = 6.dp)
                .fillMaxWidth()
                .height(3.dp)
                .clip(CircleShape),
            drawStopIndicator = {}
        )
    }
}
