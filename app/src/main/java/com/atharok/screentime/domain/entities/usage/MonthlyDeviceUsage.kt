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
    /** Strict: true whenever fewer days have real per-day data than were requested. */
    val isDataLimited: Boolean = false,
    totalLaunchCount: Int = 0,
    isClickDataLimited: Boolean = false,
    /** Long-range rankings built from WEEKLY / MONTHLY / YEARLY system buckets. */
    val rangeRankings: List<AppUsageRange> = emptyList(),
    /** 30-day total from DISJOINT DAILY/WEEKLY/MONTHLY segments (v1.0.14, no INTERVAL_BEST). */
    val aggregatedTotalTimeMillis: Long = 0L,
    /** Calendar days covered by the view; used for an honest daily average. */
    val requestedDays: Int = 0,
    /** 逐日柱状图里真实有数据的天数。 */
    val daysWithData: Int = 0,
    /** 超出 DAILY 桶理论保留上限（约 7 天）、必然由聚合桶补全的天数。 */
    val systemRetentionDays: Int = 0,
    /** 在保留上限之内、却因过滤/无时间戳/无使用而缺失的天数。 */
    val filteredDays: Int = 0,
    /** Total calendar days covered by complete, non-overlapping DAILY/WEEKLY/MONTHLY buckets. */
    val systemBucketCoveredDays: Int = 0,
    /** 总时长中属于系统聚合估算（而非逐日实测）的天数。 */
    val estimatedDays: Int = 0,
    /** 总时长是否含聚合桶估算部分（分段拼接口径）。 */
    val isEstimated: Boolean = false,
    /** 按一天 24 小时累计的屏幕时间，来自系统仍保留的事件明细。 */
    val hourlyTotals: LongArray = LongArray(24)
) : DeviceUsage<MonthlyAppUsage>(
    usages = usages,
    timeInterval = timeInterval,
    dateOfLastRefresh = dateOfLastRefresh,
    totalLaunchCount = totalLaunchCount,
    isClickDataLimited = isClickDataLimited
) {

    private val _totalTimeUsed: Long = dailyTotals.fold(0L) { acc, day -> acc + day.duration }

    /** Never lower than the real per-day sum. The segmented total adds only complete,
     *  non-overlapping system intervals; missing periods are not synthesized. */
    private val effectiveTotal: Long = maxOf(_totalTimeUsed, aggregatedTotalTimeMillis)

    // Do not divide a partial total by the full requested window. Prefer the calendar coverage
    // represented by complete system buckets, then fall back to days with positive daily usage.
    private val effectiveDays: Int =
        systemBucketCoveredDays.takeIf { it > 0 }
            ?: daysWithData.takeIf { it > 0 }
            ?: requestedDays.coerceAtLeast(1)

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
