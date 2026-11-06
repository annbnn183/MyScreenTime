package com.atharok.screentime.domain.entities.usage

import com.atharok.screentime.common.extensions.median
import com.atharok.screentime.common.extensions.safeDivideBy
import com.atharok.screentime.common.utils.DateTimeUtils
import kotlin.math.roundToLong

class DailyDeviceUsage(
    usages: Map<String, DailyAppUsage>,
    timeInterval: Pair<Long, Long>,
    dateOfLastRefresh: String,
    totalDeviceUsageByHour: LongArray
) : DeviceUsage<DailyAppUsage>(usages, timeInterval, dateOfLastRefresh) {

    private val _totalTimeUsed: Long = totalDeviceUsageByHour.sum()

    private val _averageTimeUsed: Long =
        _totalTimeUsed.safeDivideBy(totalDeviceUsageByHour.size).roundToLong()

    private val _medianTimeUsed: Long by lazy { totalDeviceUsageByHour.median() }

    private val _chartValues: Map<String, Float> = totalDeviceUsageByHour
        .mapIndexed { index: Int, hourTimestamp: Long ->
            DateTimeUtils.formatToHour(index) to DateTimeUtils.convertToHourFloat(hourTimestamp)
        }.toMap()

    override fun getTotalTimeUsed(): Long = _totalTimeUsed

    override fun getAverageTimeUsed(): Long = _averageTimeUsed

    override fun getMedianTimeUsed(): Long = _medianTimeUsed

    override fun getChartValues(): Map<String, Float> = _chartValues
}