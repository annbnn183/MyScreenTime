package com.atharok.screentime.domain.entities.usage

import com.atharok.screentime.common.extensions.median
import com.atharok.screentime.common.utils.DateTimeUtils

/**
 * Aggregated device usage over a 30-day window.
 *
 * The daily totals come from the system's per-day pre-aggregated usage stats, which keeps the
 * query fast regardless of how many events the device has recorded. [goalMillis] is the daily
 * screen time budget configured in the settings (0 when no goal is configured).
 */
class MonthlyDeviceUsage(
    usages: Map<String, MonthlyAppUsage>,
    timeInterval: Pair<Long, Long>,
    dateOfLastRefresh: String,
    val dailyTotals: List<DailyDuration>,
    val goalMillis: Long = 0L,
    /** True when the system only returned a small slice of the requested range. */
    val isDataLimited: Boolean = false,
    totalLaunchCount: Int = 0,
    isClickDataLimited: Boolean = false,
    /** Long-range rankings built from WEEKLY / MONTHLY / YEARLY system buckets. */
    val rangeRankings: List<AppUsageRange> = emptyList(),
    /** Complete period total from the best-fit aggregation bucket. */
    val aggregatedTotalTimeMillis: Long = 0L,
    /** Calendar days covered by the view; used for an honest daily average. */
    val requestedDays: Int = 0
) : DeviceUsage<MonthlyAppUsage>(
    usages = usages,
    timeInterval = timeInterval,
    dateOfLastRefresh = dateOfLastRefresh,
    totalLaunchCount = totalLaunchCount,
    isClickDataLimited = isClickDataLimited
) {

    private val _totalTimeUsed: Long = dailyTotals.fold(0L) { acc, day -> acc + day.duration }

    /** DAILY buckets expire after ~a week on many ROMs; the aggregate covers the whole range. */
    private val effectiveTotal: Long = maxOf(_totalTimeUsed, aggregatedTotalTimeMillis)

    private val effectiveDays: Int =
        if (requestedDays > 0) requestedDays else dailyTotals.size.coerceAtLeast(1)

    private val _averageTimeUsed: Long = effectiveTotal / effectiveDays

    private val _medianTimeUsed: Long by lazy {
        dailyTotals.map { it.duration }.toLongArray().median()
    }

    private val _chartValues: Map<String, Float> by lazy {
        dailyTotals.associate { day ->
            DateTimeUtils.formatToShortDate(day.dayTimestamp) to
                    DateTimeUtils.convertToHourFloat(day.duration)
        }
    }

    /** Days on which the device was actually used. */
    val activeDays: Int = dailyTotals.count { it.duration > 0L }

    /** Day with the highest screen time, null when there is no usage at all. */
    val peakDay: DailyDuration? =
        if (activeDays == 0) null else dailyTotals.maxByOrNull { it.duration }

    /** Days at or below the configured daily goal (only meaningful when [goalMillis] > 0). */
    val daysWithinGoal: Int by lazy {
        if (goalMillis <= 0L) 0 else dailyTotals.count { it.duration > 0L && it.duration <= goalMillis }
    }

    /** Days above the configured daily goal. */
    val daysOverGoal: Int by lazy {
        if (goalMillis <= 0L) 0 else dailyTotals.count { it.duration > goalMillis }
    }

    override fun getTotalTimeUsed(): Long = effectiveTotal

    override fun getAverageTimeUsed(): Long = _averageTimeUsed

    override fun getMedianTimeUsed(): Long = _medianTimeUsed

    override fun getChartValues(): Map<String, Float> = _chartValues
}
