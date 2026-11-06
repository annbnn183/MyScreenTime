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
    private val durations: Map<Long, Long>,
    launchCount: Int = 0,
    /** Full-period total read from the system's long-lived aggregation bucket. */
    private val aggregatedDuration: Long = 0L
) : AppUsage(packageName, appName, appIcon, launchCount) {

    private val _totalTimeUsed: Long = durations.values.fold(0L) { acc, value -> acc + value }

    /** Daily buckets expire quickly on many ROMs; the aggregated total fills the missing days. */
    private val effectiveTotal: Long = maxOf(_totalTimeUsed, aggregatedDuration)

    private val _averageTimeUsed: Long =
        if (dayTimestamps.isEmpty()) 0L else effectiveTotal / dayTimestamps.size

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

    override fun getTotalTimeUsed(): Long = effectiveTotal

    override fun getAverageTimeUsed(): Long = _averageTimeUsed

    override fun getMedianTimeUsed(): Long = _medianTimeUsed

    override fun getChartValues(): Map<String, Float> = _chartValues
}
