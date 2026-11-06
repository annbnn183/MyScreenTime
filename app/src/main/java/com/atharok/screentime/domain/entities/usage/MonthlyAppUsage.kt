package com.atharok.screentime.domain.entities.usage

import android.graphics.drawable.Drawable
import com.atharok.screentime.common.extensions.median
import com.atharok.screentime.common.utils.DateTimeUtils

/**
 * One application over the 30-day period. [durations] only contains days with usage; the chart is
 * built over the full [dayTimestamps] list so every application shares the same X axis.
 */
class MonthlyAppUsage(
    packageName: String,
    appName: String,
    appIcon: Drawable?,
    private val dayTimestamps: List<Long>,
    private val durations: Map<Long, Long>
) : AppUsage(packageName, appName, appIcon) {

    private val _totalTimeUsed: Long = durations.values.fold(0L) { acc, value -> acc + value }

    private val _averageTimeUsed: Long =
        if (dayTimestamps.isEmpty()) 0L else _totalTimeUsed / dayTimestamps.size

    private val _medianTimeUsed: Long by lazy {
        dayTimestamps.map { timestamp -> durations[timestamp] ?: 0L }.toLongArray().median()
    }

    // Lazy: only the applications that are actually opened need their 30 labels computed.
    private val _chartValues: Map<String, Float> by lazy {
        dayTimestamps.associate { timestamp ->
            DateTimeUtils.formatToShortDate(timestamp) to
                    DateTimeUtils.convertToHourFloat(durations[timestamp] ?: 0L)
        }
    }

    override fun getTotalTimeUsed(): Long = _totalTimeUsed

    override fun getAverageTimeUsed(): Long = _averageTimeUsed

    override fun getMedianTimeUsed(): Long = _medianTimeUsed

    override fun getChartValues(): Map<String, Float> = _chartValues
}
