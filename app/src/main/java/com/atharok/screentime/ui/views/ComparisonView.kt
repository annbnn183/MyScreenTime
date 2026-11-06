package com.atharok.screentime.ui.views

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.atharok.screentime.R
import com.atharok.screentime.common.utils.DateTimeUtils
import com.atharok.screentime.domain.entities.Period
import com.atharok.screentime.domain.usecases.UsageComparison
import com.atharok.screentime.ui.components.OutlinedCard
import com.atharok.screentime.ui.components.TextMedium
import com.atharok.screentime.ui.components.TextNormal
import com.atharok.screentime.ui.components.TextSmall

/**
 * 本周期 vs 上一周期对比卡片（P3 长期趋势与深度分析）。
 *
 * 时长取自系统聚合桶、打开次数取自本机永久逐日记录（见 [UsageComparison]）。
 * DAY 周期在调用处已过滤，不会进入此卡片。
 */
@Composable
fun ComparisonView(
    comparison: UsageComparison,
    modifier: Modifier = Modifier
) {
    val hasPrevious = comparison.previousDurationMs > 0L || comparison.previousLaunches > 0

    val subtitleRes = when (comparison.period) {
        Period.WEEK -> R.string.vs_last_week
        Period.MONTH -> R.string.vs_last_month
        Period.YEAR -> R.string.vs_last_year
        else -> R.string.comparison_title
    }

    OutlinedCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(dimensionResource(id = R.dimen.padding_medium)),
            verticalArrangement = Arrangement.spacedBy(dimensionResource(id = R.dimen.padding_small))
        ) {
            TextMedium(text = stringResource(id = R.string.comparison_title))
            TextSmall(text = stringResource(id = subtitleRes))

            if (!hasPrevious) {
                TextSmall(text = stringResource(id = R.string.no_comparison_data))
                return@Column
            }

            ComparisonRow(
                label = stringResource(id = R.string.comparison_current),
                durationMs = comparison.currentDurationMs,
                launches = comparison.currentLaunches
            )

            ComparisonRow(
                label = stringResource(id = R.string.comparison_previous),
                durationMs = comparison.previousDurationMs,
                launches = comparison.previousLaunches
            )

            comparison.durationDeltaRatio?.let { ratio ->
                val up = ratio >= 0
                val pct = "%.0f%%".format(kotlin.math.abs(ratio) * 100.0)
                TextNormal(
                    text = stringResource(
                        id = if (up) R.string.increased_by else R.string.decreased_by,
                        pct
                    ),
                    color = if (up) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            }

            comparison.launchDeltaRatio?.let { ratio ->
                val up = ratio >= 0
                val pct = "%.0f%%".format(kotlin.math.abs(ratio) * 100.0)
                TextSmall(
                    text = stringResource(
                        id = if (up) R.string.increased_by else R.string.decreased_by,
                        pct
                    )
                )
            }
        }
    }
}

@Composable
private fun ComparisonRow(
    label: String,
    durationMs: Long,
    launches: Int
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextNormal(text = label)
        Column(horizontalAlignment = Alignment.End) {
            TextNormal(text = DateTimeUtils.formatToDuration(durationMs))
            TextSmall(text = stringResource(id = R.string.launch_count_plain, launches))
        }
    }
}
