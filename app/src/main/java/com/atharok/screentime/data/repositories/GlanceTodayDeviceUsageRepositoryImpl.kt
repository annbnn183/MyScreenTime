package com.atharok.screentime.data.repositories

import android.content.Context
import com.atharok.screentime.common.extensions.getApplicationName
import com.atharok.screentime.common.extensions.safeDivideBy
import com.atharok.screentime.data.statsTracker.UsageStatsTracker
import com.atharok.screentime.domain.entities.usage.GlanceTodayAppUsage
import com.atharok.screentime.domain.entities.usage.GlanceTodayDeviceUsage
import com.atharok.screentime.domain.repositories.GlanceTodayDeviceUsageRepository

class GlanceTodayDeviceUsageRepositoryImpl(
    private val context: Context,
    private val tracker: UsageStatsTracker
): GlanceTodayDeviceUsageRepository {

    // Permission

    override fun hasAppUsagePermission(): Boolean = tracker.hasAppUsagePermission()

    // Calculate

    override suspend fun calculateGlanceTodayDeviceUsage(
        timeInterval: Pair<Long, Long>,
        ignoreSystemApps: Boolean,
        ignoredPackages: List<String>,
        dayTimestamp: Long
    ): GlanceTodayDeviceUsage {

        val usageIntervalsByPackage: Map<String, List<Pair<Long, Long>>> = tracker
            .calculateUsageIntervalsByPackage(
                startTimestamp = timeInterval.first,
                endTimestamp = timeInterval.second,
                ignoreSystemApps = ignoreSystemApps,
                ignoredPackages = ignoredPackages
            )

        val deviceUsageCacheByDayAndHour = tracker.getDurationPerPackageByDayAndHour(
            startTimestamp = timeInterval.first,
            endTimestamp = timeInterval.second,
            usageIntervalsByPackage = usageIntervalsByPackage
        )

        val totalDeviceUsageCacheByDayAndHour = tracker.getTotalDurationByDayAndHour(
            startTimestamp = timeInterval.first,
            endTimestamp = timeInterval.second,
            usageIntervalsByPackage = usageIntervalsByPackage
        )

        val deviceTimedUsed: Long = totalDeviceUsageCacheByDayAndHour[dayTimestamp]?.sum() ?: 0L

        return GlanceTodayDeviceUsage(
            totalTimeUsed = deviceTimedUsed,
            todayAppsUsage = deviceUsageCacheByDayAndHour
                .mapValues { (key: String, value: Map<Long, LongArray>) ->
                    val appTimedUsed = value[dayTimestamp]?.sum() ?: 0L
                    GlanceTodayAppUsage(
                        appName = context.packageManager.getApplicationName(key) ?: "",
                        totalTimeUsed = appTimedUsed,
                        percentUsage = appTimedUsed.safeDivideBy(deviceTimedUsed).toFloat()
                    )
                }
                .map { (_: String, value: GlanceTodayAppUsage) -> value }
                .sortedByDescending { it.totalTimeUsed }
                .take(3)
        )
    }
}