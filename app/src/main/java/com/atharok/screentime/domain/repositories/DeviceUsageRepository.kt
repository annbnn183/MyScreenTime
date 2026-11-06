package com.atharok.screentime.domain.repositories

import com.atharok.screentime.domain.entities.usage.DailyDeviceUsage
import com.atharok.screentime.domain.entities.usage.WeeklyDeviceUsage

interface DeviceUsageRepository {

    // Permission

    fun hasAppUsagePermission(): Boolean

    // Calculate

    suspend fun calculateDeviceUsage(
        timeInterval: Pair<Long, Long>,
        ignoreSystemApps: Boolean,
        ignoredPackages: List<String>
    )

    suspend fun getWeekDeviceUsage(
        timeInterval: Pair<Long, Long>
    ): WeeklyDeviceUsage

    suspend fun getDayDeviceUsage(
        timeInterval: Pair<Long, Long>,
        dayTimestamp: Long
    ): DailyDeviceUsage
}