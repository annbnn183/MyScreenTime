package com.atharok.screentime.ui.views

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.atharok.screentime.R
import com.atharok.screentime.common.utils.DateTimeUtils
import com.atharok.screentime.domain.entities.usage.DeviceUsage
import com.atharok.screentime.domain.entities.usage.MonthlyDeviceUsage
import com.atharok.screentime.domain.entities.usage.RankedApp
import com.atharok.screentime.ui.components.ApplicationIcon
import java.util.Locale

/**
 * Four-column app ranking, inspired by Usage Analyzer: icon, name, total time, launch count and
 * share, with a switchable long-range period (4 weeks / 6 months / year) and a sort mode.
 * Long ranges read the system's WEEKLY / MONTHLY / YEARLY buckets, so their totals stay complete
 * even on ROMs that discard DAILY buckets after a few days.
 */
@Composable
fun AppUsageGridView(
    deviceUsage: DeviceUsage<*>,
    onAppClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val monthly = deviceUsage as? MonthlyDeviceUsage
    var rangeIndex by remember { mutableIntStateOf(0) }
    var sortIndex by remember { mutableIntStateOf(0) }

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
        }.filter { it.duration > 0L }
    }

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
            Spacer(modifier = Modifier.height(4.dp))
        }

        val maxDuration = remember(apps) {
            apps.maxOfOrNull { it.duration }?.coerceAtLeast(1L) ?: 1L
        }

        apps.chunked(4).forEach { rowApps ->
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
            drawable = app.appIcon,
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
            text = DateTimeUtils.formatToDuration(app.duration),
            maxLines = 1,
            style = MaterialTheme.typography.bodyMedium
        )
        if (totalLaunches > 0) {
            Text(
                text = stringResource(
                    id = R.string.launch_count_line,
                    app.launchCount,
                    app.launchCount * 100f / totalLaunches
                ),
                maxLines = 1,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
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
