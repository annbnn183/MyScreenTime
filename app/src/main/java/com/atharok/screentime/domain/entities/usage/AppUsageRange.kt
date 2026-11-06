package com.atharok.screentime.domain.entities.usage

import android.graphics.drawable.Drawable

/**
 * A long-range ranking built from one of Android's aggregation buckets (WEEKLY / MONTHLY /
 * YEARLY). Unlike the per-day buckets, these survive for weeks to years, so the totals are
 * complete even for a whole year, without ever scanning usage events.
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
    val clickSinceDay: Long? = null
)

data class RankedApp(
    val packageName: String,
    val appName: String,
    val appIcon: Drawable?,
    val duration: Long,
    val launchCount: Int
)
