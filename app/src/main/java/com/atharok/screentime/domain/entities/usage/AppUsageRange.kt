package com.atharok.screentime.domain.entities.usage

import android.graphics.drawable.Drawable

/**
 * A long-range ranking built from Android aggregation buckets (WEEKLY / MONTHLY / YEARLY).
 * Only buckets whose full represented time range fits inside the requested window contribute;
 * partial boundary buckets are excluded because their durations cannot be split safely.
 */
data class AppUsageRange(
    val labelRes: Int,
    val numberOfDays: Int,
    val apps: List<RankedApp>,
    val totalLaunchCount: Int,
    /** 这一个范围里累计的屏幕时间（毫秒），用于「总共多少小时」。 */
    val totalTimeMillis: Long = 0L,
    /** 该范围内已经保存了逐日点击量记录的天数。 */
    val clickDaysRecorded: Int = 0,
    /** 点击量最早记录日；null 表示还没有任何记录。 */
    val clickSinceDay: Long? = null,
    /** Number of recent days for which event-based backfill is still potentially possible. */
    val backfillLimitDays: Int = 0,
    /** 系统该聚合桶的理论保留上限（天）：WEEKLY≈28 / MONTHLY≈182 / YEARLY≈730。 */
    val retentionCapDays: Int = 0,
    /** Number of calendar days covered by accepted, non-overlapping aggregate intervals. */
    val screenTimeDaysCovered: Int = 0,
    /** Raw system entries returned before validating their represented time range. */
    val rawBucketCount: Int = 0,
    /** Buckets whose full represented time range is inside the requested window. */
    val keptBucketCount: Int = 0,
    /** Buckets crossing either requested boundary; their aggregate cannot be prorated safely. */
    val droppedPartialBucketCount: Int = 0,
    val droppedOutsideWindowBucketCount: Int = 0,
    val zeroDurationBucketCount: Int = 0,
    val invalidTimestampBucketCount: Int = 0
)

data class RankedApp(
    val packageName: String,
    val appName: String,
    val appIcon: Drawable?,
    val duration: Long,
    val launchCount: Int
)
