package com.atharok.screentime.data.repositories

import android.content.Context
import com.atharok.screentime.common.extensions.getApplicationIconSafety
import com.atharok.screentime.common.extensions.getApplicationName
import com.atharok.screentime.common.utils.DateTimeUtils
import com.atharok.screentime.data.statsTracker.UsageStatsTracker
import com.atharok.screentime.domain.entities.usage.DailyAppUsage
import com.atharok.screentime.domain.entities.usage.DailyDeviceUsage
import com.atharok.screentime.domain.entities.usage.WeeklyAppUsage
import com.atharok.screentime.domain.entities.usage.WeeklyDeviceUsage
import com.atharok.screentime.domain.repositories.DeviceUsageRepository

class DeviceUsageRepositoryImpl(
    private val context: Context,
    private val tracker: UsageStatsTracker
): DeviceUsageRepository {

    // Permission

    override fun hasAppUsagePermission(): Boolean = tracker.hasAppUsagePermission()

    // Calculate

    private var deviceUsageCacheByDayAndHour: Map<String, Map<Long, LongArray>>? = null
    private var totalDeviceUsageCacheByDayAndHour: Map<Long, LongArray> = mapOf()
    private var dateOfLastRefresh: String = ""

    override suspend fun calculateDeviceUsage(
        timeInterval: Pair<Long, Long>,
        ignoreSystemApps: Boolean,
        ignoredPackages: List<String>
    ) {
        val usageIntervalsByPackage: Map<String, List<Pair<Long, Long>>> = tracker
            .calculateUsageIntervalsByPackage(
                startTimestamp = timeInterval.first,
                endTimestamp = timeInterval.second,
                ignoreSystemApps = ignoreSystemApps,
                ignoredPackages = ignoredPackages
            )

        deviceUsageCacheByDayAndHour = tracker.getDurationPerPackageByDayAndHour(
            startTimestamp = timeInterval.first,
            endTimestamp = timeInterval.second,
            usageIntervalsByPackage = usageIntervalsByPackage
        )

        totalDeviceUsageCacheByDayAndHour = tracker.getTotalDurationByDayAndHour(
            startTimestamp = timeInterval.first,
            endTimestamp = timeInterval.second,
            usageIntervalsByPackage = usageIntervalsByPackage
        )

        dateOfLastRefresh = DateTimeUtils.formatToFullDateTime(timeInterval.second)
    }

    override suspend fun getWeekDeviceUsage(
        timeInterval: Pair<Long, Long>
    ): WeeklyDeviceUsage {

        val deviceUsageCacheByDayAndHourNotNull = deviceUsageCacheByDayAndHour
            ?: throw Exception("DeviceUsageRepositoryImpl.calculateDeviceUsage() not called.")

        return WeeklyDeviceUsage(
            usages = deviceUsageCacheByDayAndHourNotNull.mapValues { (key: String, value: Map<Long, LongArray>) ->
                WeeklyAppUsage(
                    packageName = key,
                    appName = context.packageManager.getApplicationName(key) ?: "",
                    appIcon = context.packageManager.getApplicationIconSafety(key),
                    durations = value
                        .filterKeys { timestamp ->
                            timestamp in timeInterval.first..timeInterval.second
                        }
                )
            },
            timeInterval = timeInterval,
            dateOfLastRefresh = dateOfLastRefresh,
            totalDeviceUsageByDayAndHour = totalDeviceUsageCacheByDayAndHour.filterKeys { timestamp ->
                timestamp in timeInterval.first..timeInterval.second
            }
        )
    }

    override suspend fun getDayDeviceUsage(
        timeInterval: Pair<Long, Long>,
        dayTimestamp: Long
    ): DailyDeviceUsage {

        val deviceUsageCacheByDayAndHourNotNull = deviceUsageCacheByDayAndHour
            ?: throw Exception("DeviceUsageRepositoryImpl.calculateDeviceUsage() not called.")

        return DailyDeviceUsage(
            usages = deviceUsageCacheByDayAndHourNotNull.mapValues { (key: String, value: Map<Long, LongArray>) ->
                DailyAppUsage(
                    packageName = key,
                    appName = context.packageManager.getApplicationName(key) ?: "",
                    appIcon = context.packageManager.getApplicationIconSafety(key),
                    durations = value[dayTimestamp] ?: LongArray(24)
                )
            },
            timeInterval = timeInterval,
            dateOfLastRefresh = dateOfLastRefresh,
            totalDeviceUsageByHour = totalDeviceUsageCacheByDayAndHour[dayTimestamp] ?: LongArray(24)
        )
    }
}