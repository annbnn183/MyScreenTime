package com.atharok.screentime.data.repositories

import android.content.Context
import com.atharok.screentime.common.extensions.getApplicationIconSafety
import com.atharok.screentime.common.extensions.getApplicationName
import com.atharok.screentime.common.utils.DateTimeUtils
import com.atharok.screentime.data.statsTracker.UsageStatsTracker
import com.atharok.screentime.domain.entities.usage.DailyAppUsage
import com.atharok.screentime.domain.entities.usage.DailyDeviceUsage
import com.atharok.screentime.domain.entities.usage.DailyDuration
import com.atharok.screentime.domain.entities.usage.MonthlyAppUsage
import com.atharok.screentime.domain.entities.usage.MonthlyDeviceUsage
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

    // ---- Month ----

    override suspend fun getMonthDeviceUsage(
        timeInterval: Pair<Long, Long>,
        dayTimestamps: List<Long>,
        goalMillis: Long,
        ignoreSystemApps: Boolean,
        ignoredPackages: List<String>
    ): MonthlyDeviceUsage {
        // Primary source: the system's per-day pre-aggregated stats (fast, event-count independent).
        val scan = tracker.queryMonthlyUsageByDay(
            startTimestamp = timeInterval.first,
            endTimestamp = timeInterval.second,
            ignoreSystemApps = ignoreSystemApps,
            ignoredPackages = ignoredPackages
        )

        // Secondary source: the event-based cache covers the last 10 days with higher precision.
        // Merge both by keeping the larger value for each (package, day) pair, so they complement
        // each other instead of being counted twice.
        val eventsPerPackage: Map<String, Map<Long, Long>> = deviceUsageCacheByDayAndHour
            ?.mapValues { (_, byDayAndHour) ->
                byDayAndHour.mapValues { (_, hours) -> hours.sum() }
            } ?: emptyMap()
        val eventsDailyTotals: Map<Long, Long> = totalDeviceUsageCacheByDayAndHour
            .mapValues { (_, hours) -> hours.sum() }

        val mergedPerPackage = mutableMapOf<String, MutableMap<Long, Long>>()

        fun merge(source: Map<String, Map<Long, Long>>) {
            source.forEach { (packageName: String, byDay: Map<Long, Long>) ->
                val target = mergedPerPackage.getOrPut(packageName) { mutableMapOf() }
                byDay.forEach { (day: Long, duration: Long) ->
                    target[day] = maxOf(target[day] ?: 0L, duration)
                }
            }
        }
        merge(scan.perPackageByDay)
        merge(eventsPerPackage)

        val dailyTotals: List<DailyDuration> = dayTimestamps.map { day: Long ->
            DailyDuration(
                dayTimestamp = day,
                duration = maxOf(
                    scan.dailyTotals[day] ?: 0L,
                    eventsDailyTotals[day] ?: 0L
                )
            )
        }

        // Some ROMs only expose a few days through queryUsageStats. Warn the user instead of
        // silently showing an almost empty month.
        val isDataLimited = scan.dailyTotals.size < dayTimestamps.size / 3

        val usages: Map<String, MonthlyAppUsage> = mergedPerPackage
            .mapValues { (packageName: String, byDay: Map<Long, Long>) ->
                MonthlyAppUsage(
                    packageName = packageName,
                    appName = context.packageManager.getApplicationName(packageName) ?: packageName,
                    appIcon = context.packageManager.getApplicationIconSafety(packageName),
                    dayTimestamps = dayTimestamps,
                    durations = byDay
                )
            }

        return MonthlyDeviceUsage(
            usages = usages,
            timeInterval = timeInterval,
            dateOfLastRefresh = DateTimeUtils.formatToFullDateTime(timeInterval.second),
            dailyTotals = dailyTotals,
            goalMillis = goalMillis,
            isDataLimited = isDataLimited
        )
    }
}