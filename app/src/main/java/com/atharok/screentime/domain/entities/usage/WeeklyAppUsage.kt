package com.atharok.screentime.domain.entities.usage

import android.graphics.drawable.Drawable
import com.atharok.screentime.common.extensions.median
import com.atharok.screentime.common.extensions.safeDivideBy
import com.atharok.screentime.common.utils.DateTimeUtils
import kotlin.math.roundToLong

class WeeklyAppUsage(
    packageName: String,
    appName: String,
    appIcon: Drawable?,
    val durations: Map<Long, LongArray>
) : AppUsage(packageName, appName, appIcon) {

    private val totalDurationByDay: Map<Long, Long> = durations.mapValues { (_, durations) -> durations.sum() }

    private val _totalTimeUsed: Long = totalDurationByDay.values.sum()

    private val _averageTimeUsed: Long = _totalTimeUsed.safeDivideBy(durations.size).roundToLong()

    private val _medianTimeUsed: Long by lazy { totalDurationByDay.values.toLongArray().median() }

    private val _chartValues: Map<String, Float> by lazy {
        totalDurationByDay.map { (dayTimestamp: Long, hourTimestamps) ->
            DateTimeUtils.formatToDayOfTheWeek(dayTimestamp) to DateTimeUtils.convertToHourFloat(hourTimestamps)
        }.toMap()
    }
    
    override fun getTotalTimeUsed(): Long = _totalTimeUsed

    override fun getAverageTimeUsed(): Long = _averageTimeUsed

    override fun getMedianTimeUsed(): Long = _medianTimeUsed

    override fun getChartValues(): Map<String, Float> = _chartValues
}