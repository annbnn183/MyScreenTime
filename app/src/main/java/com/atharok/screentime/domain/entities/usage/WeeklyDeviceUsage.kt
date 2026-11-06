package com.atharok.screentime.domain.entities.usage

import com.atharok.screentime.common.extensions.median
import com.atharok.screentime.common.extensions.safeDivideBy
import com.atharok.screentime.common.utils.DateTimeUtils
import kotlin.math.roundToLong

class WeeklyDeviceUsage(
    usages: Map<String, WeeklyAppUsage>,
    timeInterval: Pair<Long, Long>,
    dateOfLastRefresh: String,
    totalDeviceUsageByDayAndHour: Map<Long, LongArray>
) : DeviceUsage<WeeklyAppUsage>(usages, timeInterval, dateOfLastRefresh) {

    private val totalDurationByDay: Map<Long, Long> = totalDeviceUsageByDayAndHour.mapValues { (_, durations) -> durations.sum() }

    private val _totalTimeUsed: Long = totalDurationByDay.values.sum()

    private val _averageTimeUsed: Long =
        _totalTimeUsed.safeDivideBy(totalDeviceUsageByDayAndHour.size).roundToLong()

    private val _medianTimeUsed: Long by lazy { totalDurationByDay.values.toLongArray().median() }

    private val _chartValues: Map<String, Float> = totalDurationByDay
        .map { (dayTimestamp: Long, hourTimestamps: Long) ->
            DateTimeUtils.formatToDayOfTheWeek(dayTimestamp) to DateTimeUtils.convertToHourFloat(hourTimestamps)
        }.toMap()

    override fun getTotalTimeUsed(): Long = _totalTimeUsed

    override fun getAverageTimeUsed(): Long = _averageTimeUsed

    override fun getMedianTimeUsed(): Long = _medianTimeUsed

    override fun getChartValues(): Map<String, Float> = _chartValues
}