package com.atharok.screentime.ui.views

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.atharok.screentime.R
import com.atharok.screentime.common.utils.DateTimeUtils
import com.atharok.screentime.common.utils.NUMBER_OF_DAYS
import com.atharok.screentime.domain.entities.CentralTendency
import com.atharok.screentime.domain.entities.Period
import com.atharok.screentime.ui.components.AnimatedFade
import com.atharok.screentime.ui.components.OutlinedCard
import com.atharok.screentime.ui.components.TextExtraLarge
import com.atharok.screentime.ui.components.TextNormal
import com.atharok.screentime.ui.components.TextSmall
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer

@Composable
fun TotalUsageView(
    period: Period,
    totalTime: Long,
    averageTime: Long,
    medianTime: Long,
    selectedCentralTendency: CentralTendency,
    onSelectedCentralTendencyChange: (CentralTendency) -> Unit,
    timeIntervalStart: Long,
    timeIntervalEnd: Long,
    modelProducer: CartesianChartModelProducer,
    chartValues: Map<String, Float>,
    dateOfLastRefresh: String,
    modifier: Modifier = Modifier
) {
    TotalUsageView(
        period = period,
        totalTime = totalTime,
        averageTime = averageTime,
        medianTime = medianTime,
        selectedCentralTendency = selectedCentralTendency,
        onSelectedCentralTendencyChange = onSelectedCentralTendencyChange,
        modelProducer = modelProducer,
        chartValues = chartValues,
        dateOfLastRefresh = dateOfLastRefresh,
        modifier = modifier
    ) {
        IntervalView(
            timeIntervalStart = timeIntervalStart,
            timeIntervalEnd = timeIntervalEnd,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun TotalUsageView(
    dayIndex: Int,
    onDayIndexChange: (Int) -> Unit,
    period: Period,
    totalTime: Long,
    averageTime: Long,
    medianTime: Long,
    selectedCentralTendency: CentralTendency,
    onSelectedCentralTendencyChange: (CentralTendency) -> Unit,
    timestamp: Long,
    modelProducer: CartesianChartModelProducer,
    chartValues: Map<String, Float>,
    dateOfLastRefresh: String,
    modifier: Modifier = Modifier
) {
    TotalUsageView(
        period = period,
        totalTime = totalTime,
        averageTime = averageTime,
        medianTime = medianTime,
        selectedCentralTendency = selectedCentralTendency,
        onSelectedCentralTendencyChange = onSelectedCentralTendencyChange,
        modelProducer = modelProducer,
        chartValues = chartValues,
        dateOfLastRefresh = dateOfLastRefresh,
        modifier = modifier
    ) {
        DaySelectorView(
            dayIndex = dayIndex,
            onDayIndexChange = onDayIndexChange,
            timestamp = timestamp,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun TotalUsageView(
    period: Period,
    totalTime: Long,
    averageTime: Long,
    medianTime: Long,
    selectedCentralTendency: CentralTendency,
    onSelectedCentralTendencyChange: (CentralTendency) -> Unit,
    modelProducer: CartesianChartModelProducer,
    chartValues: Map<String, Float>,
    dateOfLastRefresh: String,
    modifier: Modifier = Modifier,
    bottomOfCard: @Composable () -> Unit
) {
    Column(modifier = modifier) {
        OutlinedCard {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(dimensionResource(id = R.dimen.padding_medium)),
                horizontalAlignment = Alignment.Start
            ) {

                UsageView(
                    period = period,
                    totalTime = totalTime,
                    averageTime = averageTime,
                    medianTime = medianTime,
                    selectedCentralTendency = selectedCentralTendency,
                    onSelectedCentralTendencyChange = onSelectedCentralTendencyChange,
                    modifier = Modifier.fillMaxWidth()
                )

                if(chartValues.isNotEmpty()) {
                    Chart(
                        period = period,
                        data = chartValues,
                        average = if(selectedCentralTendency == CentralTendency.AVERAGE){
                            DateTimeUtils.convertToHourDouble(averageTime)
                        } else {
                            DateTimeUtils.convertToHourDouble(medianTime)
                        },
                        modelProducer = modelProducer,
                        modifier = Modifier.padding(vertical = dimensionResource(id = R.dimen.padding_large))
                    )
                }

                bottomOfCard()
            }
        }

        TextSmall(
            text = stringResource(id = R.string.data_updated, dateOfLastRefresh),
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = dimensionResource(id = R.dimen.padding_small),
                    vertical = dimensionResource(id = R.dimen.padding_normal)
                )
        )
    }
}

// ---- Top ----

@Composable
private fun UsageView(
    period: Period,
    totalTime: Long,
    averageTime: Long,
    medianTime: Long,
    selectedCentralTendency: CentralTendency,
    onSelectedCentralTendencyChange: (CentralTendency) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        DurationView(
            timestamp = totalTime,
            title = stringResource(id = R.string.total_screen_time),
            modifier = Modifier.weight(1f).padding(vertical = dimensionResource(R.dimen.padding_small))
        )

        VerticalDivider()

        Box(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = dimensionResource(R.dimen.padding_small)),
            contentAlignment = Alignment.Center
        ) {
            AnimatedFade(selectedCentralTendency) { centralTendency ->
                when(centralTendency) {
                    CentralTendency.AVERAGE -> {
                        DurationView(
                            timestamp = averageTime,
                            title = stringResource(id = if(period == Period.WEEK) R.string.daily_average else R.string.hourly_average),
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    onSelectedCentralTendencyChange(CentralTendency.MEDIAN)
                                }
                        )
                    }
                    CentralTendency.MEDIAN -> {
                        DurationView(
                            timestamp = medianTime,
                            title = stringResource(id = if(period == Period.WEEK) R.string.daily_median else R.string.hourly_median),
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    onSelectedCentralTendencyChange(CentralTendency.AVERAGE)
                                }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DurationView(
    timestamp: Long,
    title: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        TextSmall(text = title)
        TextExtraLarge(
            text = DateTimeUtils.formatToDuration(timestamp),
            modifier = Modifier.padding(top = dimensionResource(id = R.dimen.padding_normal))
        )
    }
}

// ---- Bottom ----

@Composable
private fun IntervalView(
    timeIntervalStart: Long,
    timeIntervalEnd: Long,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {

        TextNormal(
            text = DateTimeUtils.formatToMediumDate(timeIntervalStart),
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center
        )

        VerticalDivider(
            modifier = Modifier.padding(horizontal = dimensionResource(R.dimen.padding_small)).height(48.dp)
        )

        TextNormal(
            text = DateTimeUtils.formatToMediumDate(timeIntervalEnd),
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun DaySelectorView(
    dayIndex: Int,
    onDayIndexChange: (Int) -> Unit,
    timestamp: Long,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {

        IconButton(
            onClick = {
                onDayIndexChange(dayIndex - 1)
            },
            modifier = Modifier.weight(0.2f),
            enabled = dayIndex > 1 // On ne gère pas le premier indice car c'est une journée qui peut être incomplète.
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.previous)
            )
        }

        Column(
            modifier = Modifier.weight(0.6f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            TextSmall(
                text = DateTimeUtils.formatToRelativeTimeSpanString(timestamp),
                textAlign = TextAlign.Center
            )
            TextNormal(
                text = DateTimeUtils.formatToMediumDate(timestamp),
                textAlign = TextAlign.Center
            )
        }


        IconButton(
            onClick = {
                onDayIndexChange(dayIndex + 1)
            },
            modifier = Modifier.weight(0.2f),
            enabled = dayIndex < NUMBER_OF_DAYS - 1
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = stringResource(R.string.next)
            )
        }
    }
}