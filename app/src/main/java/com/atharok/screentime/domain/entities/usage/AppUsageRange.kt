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
    val totalLaunchCount: Int
)

data class RankedApp(
    val packageName: String,
    val appName: String,
    val appIcon: Drawable?,
    val duration: Long,
    val launchCount: Int
)
