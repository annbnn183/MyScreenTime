package com.atharok.screentime.ui.views

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.atharok.screentime.R
import com.atharok.screentime.common.utils.DateTimeUtils
import com.atharok.screentime.domain.entities.usage.MonthlyDeviceUsage
import com.atharok.screentime.ui.components.OutlinedCard
import com.atharok.screentime.ui.components.TextMedium
import com.atharok.screentime.ui.components.TextNormal
import com.atharok.screentime.ui.components.TextSmall

/**
 * Month-only cards: peak day, active days and a 30-cell goal calendar. Everything here is derived
 * from the data already computed for the chart, so it adds no extra usage-stats query.
 */
@Composable
fun MonthlyInsightsView(
    deviceUsage: MonthlyDeviceUsage,
    modifier: Modifier = Modifier
) {
    OutlinedCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(dimensionResource(id = R.dimen.padding_medium)),
            verticalArrangement = Arrangement.spacedBy(dimensionResource(id = R.dimen.padding_small))
        ) {
            TextMedium(text = stringResource(id = R.string.monthly_insights))

            deviceUsage.peakDay?.let { peak ->
                TextNormal(
                    text = stringResource(
                        id = R.string.peak_usage_day,
                        DateTimeUtils.formatToMediumDate(peak.dayTimestamp),
                        DateTimeUtils.formatToDuration(peak.duration)
                    )
                )
            }

            TextSmall(
                text = stringResource(
                    id = R.string.active_days_count,
                    deviceUsage.activeDays,
                    deviceUsage.dailyTotals.size
                )
            )

            if (deviceUsage.goalMillis > 0L) {
                TextSmall(text = stringResource(id = R.string.goal_calendar_title))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    deviceUsage.dailyTotals.forEach { day ->
                        val color = when {
                            day.duration <= 0L -> MaterialTheme.colorScheme.surfaceVariant
                            day.duration > deviceUsage.goalMillis -> MaterialTheme.colorScheme.error
                            else -> Color(0xFF4CAF50)
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(14.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(color)
                        )
                    }
                }

                TextSmall(
                    text = stringResource(
                        id = R.string.goal_calendar_summary,
                        deviceUsage.daysWithinGoal,
                        deviceUsage.daysOverGoal,
                        DateTimeUtils.formatToDuration(deviceUsage.goalMillis)
                    )
                )
            } else {
                TextSmall(text = stringResource(id = R.string.goal_calendar_hint))
            }

            if (deviceUsage.isDataLimited) {
                // v1.0.14: explain exactly HOW MANY days are real and WHY the rest is missing,
                // separating the Android DAILY retention cap from filtering/unused days.
                TextSmall(
                    text = stringResource(
                        id = R.string.monthly_data_limited,
                        deviceUsage.daysWithData,
                        deviceUsage.requestedDays,
                        deviceUsage.systemRetentionDays,
                        deviceUsage.filteredDays
                    )
                )
            }
        }
    }
}
