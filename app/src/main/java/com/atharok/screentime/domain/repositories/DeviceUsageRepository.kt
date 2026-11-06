package com.atharok.screentime.domain.repositories

import com.atharok.screentime.domain.entities.usage.DailyDeviceUsage
import com.atharok.screentime.domain.entities.usage.MonthlyDeviceUsage
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

    suspend fun getMonthDeviceUsage(
        timeInterval: Pair<Long, Long>,
        dayTimestamps: List<Long>,
        goalMillis: Long,
        ignoreSystemApps: Boolean,
        ignoredPackages: List<String>
    ): MonthlyDeviceUsage

    /**
     * 按天往前补算历史点击量并永久落盘，单次最多 [maxDays] 天，返回本次补算天数。
     * 分批执行，即使中途被系统回收，已算过的天也不会丢失。
     */
    suspend fun backfillLaunchHistory(
        maxDays: Int,
        ignoreSystemApps: Boolean,
        ignoredPackages: List<String>
    ): Int
}