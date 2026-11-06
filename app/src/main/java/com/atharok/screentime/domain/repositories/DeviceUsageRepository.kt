package com.atharok.screentime.domain.repositories

import com.atharok.screentime.domain.entities.usage.DailyDeviceUsage
import com.atharok.screentime.domain.entities.usage.MonthlyDeviceUsage
import com.atharok.screentime.domain.entities.usage.WeeklyDeviceUsage

/** 某一时间区间的合计：时长来自系统聚合桶，打开次数来自本机永久逐日记录。 */
data class IntervalTotal(
    val totalDurationMs: Long,
    val totalLaunches: Int
)

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
     * [onProgress] 会按批回报进度，供界面显示"正在补算 x / N 天"。
     */
    suspend fun backfillLaunchHistory(
        maxDays: Int,
        ignoreSystemApps: Boolean,
        ignoredPackages: List<String>,
        onProgress: ((done: Int, total: Int) -> Unit)? = null
    ): Int

    /**
     * 某一时间区间的合计：屏幕时长来自系统聚合桶，打开次数来自本机永久逐日记录。
     * 用于「本周 vs 上周 / 本月 vs 上月 / 本年 vs 去年」对比，避免重复做事件明细扫描。
     */
    suspend fun getIntervalTotal(
        startTimestamp: Long,
        endTimestamp: Long,
        ignoreSystemApps: Boolean,
        ignoredPackages: List<String>
    ): IntervalTotal
}