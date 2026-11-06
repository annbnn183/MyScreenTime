package com.atharok.screentime.domain.usecases

import com.atharok.screentime.common.utils.AppLog
import com.atharok.screentime.common.utils.DateTimeUtils
import com.atharok.screentime.common.utils.NUMBER_OF_DAYS
import com.atharok.screentime.common.utils.NUMBER_OF_DAYS_IN_MONTH
import com.atharok.screentime.common.utils.MILLIS_IN_A_DAY
import com.atharok.screentime.domain.entities.usage.DailyDeviceUsage
import com.atharok.screentime.domain.entities.usage.DeviceUsage
import com.atharok.screentime.domain.entities.usage.MonthlyDeviceUsage
import com.atharok.screentime.domain.entities.usage.WeeklyDeviceUsage
import com.atharok.screentime.domain.entities.Period
import com.atharok.screentime.domain.repositories.DeviceUsageRepository
import com.atharok.screentime.domain.repositories.IntervalTotal
import com.atharok.screentime.domain.resources.Resource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** 本周期与上一周期的对比结果（时长 + 打开次数）。 */
data class UsageComparison(
    val period: Period,
    val currentDurationMs: Long,
    val previousDurationMs: Long,
    val currentLaunches: Int,
    val previousLaunches: Int
) {
    /** 时长相对上一周期的变化比例（正=增加，负=减少），无上一周期数据时返回 null。 */
    val durationDeltaRatio: Double?
        get() = if (previousDurationMs <= 0L) null
        else (currentDurationMs - previousDurationMs).toDouble() / previousDurationMs
    val launchDeltaRatio: Double?
        get() = if (previousLaunches <= 0) null
        else (currentLaunches - previousLaunches).toDouble() / previousLaunches
}

class DeviceUsageUseCase(private val repository: DeviceUsageRepository) {

    // ---- Permission ----

    fun hasAppUsagePermission(): Boolean = repository.hasAppUsagePermission()

    // ---- Compute Device Usage ----

    private var calculationTimestamp = System.currentTimeMillis()
    private var isInitialised = false

    private suspend fun calculateDeviceUsage(
        ignoreSystemApps: Boolean,
        ignoredPackages: List<String>
    ) {
        val start = System.currentTimeMillis()
        calculationTimestamp = start
        AppLog.i(
            "DeviceUsageUseCase",
            "Scanning usage events for $NUMBER_OF_DAYS days (ignoreSystemApps=$ignoreSystemApps, ignored=${ignoredPackages.size})"
        )
        repository.calculateDeviceUsage(
            timeInterval = DateTimeUtils.computeInterval(calculationTimestamp, NUMBER_OF_DAYS - 1),
            ignoreSystemApps = ignoreSystemApps,
            ignoredPackages = ignoredPackages
        )
        AppLog.i(
            "DeviceUsageUseCase",
            "Usage event scan finished in ${System.currentTimeMillis() - start} ms"
        )
        isInitialised = true
    }

    // ---- Compute Device Usage By Period ----

    private val _deviceUsageFlow: MutableStateFlow<Resource<DeviceUsage<*>>> = MutableStateFlow(Resource.loading())
    val deviceUsageFlow: StateFlow<Resource<DeviceUsage<*>>> = _deviceUsageFlow

    /** Keeps the previous content on screen while another period is being computed. */
    private fun showLoadingIfEmpty() {
        if (_deviceUsageFlow.value !is Resource.Success) {
            _deviceUsageFlow.value = Resource.loading()
        }
    }

    // ---- Week ----

    private var weeklyDeviceUsageCache: WeeklyDeviceUsage? = null

    suspend fun calculateWeekDeviceUsage(
        ignoreSystemApps: Boolean,
        ignoredPackages: List<String>,
        useCache: Boolean = true
    ) {
        showLoadingIfEmpty()

        try {
            if(!isInitialised || !useCache) {
                reset(ignoreSystemApps, ignoredPackages)
            }

            if(weeklyDeviceUsageCache == null) {
                weeklyDeviceUsageCache = repository.getWeekDeviceUsage(
                    timeInterval = DateTimeUtils.computeLast7DaysIntervalFrom(
                        now = calculationTimestamp
                    )
                )
            }

            weeklyDeviceUsageCache?.let {
                _deviceUsageFlow.value = Resource.success(it)
            } ?: run {
                throw Exception("Cache is null.")
            }

        } catch (e: Exception) {
            AppLog.e("DeviceUsageUseCase", "Failed to compute WEEK usage", e)
            weeklyDeviceUsageCache = null
            _deviceUsageFlow.value = Resource.failure(e)
        }
    }

    // ---- Day ----

    private val dailyDeviceUsageCache: Array<DailyDeviceUsage?> = Array(NUMBER_OF_DAYS) { null }

    suspend fun calculateDayDeviceUsage(
        ignoreSystemApps: Boolean,
        ignoredPackages: List<String>,
        dayIndex: Int,
        useCache: Boolean = true
    ) {
        if(!useCache) // -> Condition to prevent re-displaying the loading screen when switching days
            showLoadingIfEmpty()

        try {
            if(!isInitialised || !useCache) {
                reset(ignoreSystemApps, ignoredPackages)
            }

            if(dailyDeviceUsageCache[dayIndex] == null) {
                dailyDeviceUsageCache[dayIndex] = repository.getDayDeviceUsage(
                    timeInterval = DateTimeUtils.computeDayIntervalFrom(
                        now = calculationTimestamp,
                        dayOffset = dayIndex
                    ),
                    dayTimestamp = DateTimeUtils.truncateToDay(
                        timestamp = DateTimeUtils.addDaysToTimestamp(
                            timestamp = calculationTimestamp,
                            dayOffset = -(dailyDeviceUsageCache.lastIndex - dayIndex)
                        )
                    )
                )
            }

            dailyDeviceUsageCache[dayIndex]?.let {
                _deviceUsageFlow.value = Resource.success(it)
            } ?: run {
                throw Exception("Cache is null.")
            }

        } catch (e: Exception) {
            AppLog.e("DeviceUsageUseCase", "Failed to compute DAY usage (dayIndex=$dayIndex)", e)
            _deviceUsageFlow.value = Resource.failure(e)
        }
    }

    // ---- Month ----

    private var monthlyDeviceUsageCache: MonthlyDeviceUsage? = null
    private var monthlyGoalMillis: Long = 0L

    /**
     * Builds the 30-day view. The heavy part is delegated to the system's per-day aggregated
     * usage stats, so this does NOT extend the event scan beyond the usual 10 days.
     */
    suspend fun calculateMonthDeviceUsage(
        ignoreSystemApps: Boolean,
        ignoredPackages: List<String>,
        goalMillis: Long,
        useCache: Boolean = true
    ) {
        showLoadingIfEmpty()

        try {
            if(!isInitialised || !useCache) {
                reset(ignoreSystemApps, ignoredPackages)
            }

            if(monthlyDeviceUsageCache == null || !useCache || monthlyGoalMillis != goalMillis) {
                val now = System.currentTimeMillis()
                val timeInterval = DateTimeUtils.computeInterval(now, NUMBER_OF_DAYS_IN_MONTH - 1)
                val today = DateTimeUtils.truncateToDay(now)
                val dayTimestamps: List<Long> = (0 until NUMBER_OF_DAYS_IN_MONTH).map { index ->
                    DateTimeUtils.addDaysToTimestamp(
                        timestamp = today,
                        dayOffset = -(NUMBER_OF_DAYS_IN_MONTH - 1 - index)
                    )
                }

                monthlyDeviceUsageCache = repository.getMonthDeviceUsage(
                    timeInterval = timeInterval,
                    dayTimestamps = dayTimestamps,
                    goalMillis = goalMillis,
                    ignoreSystemApps = ignoreSystemApps,
                    ignoredPackages = ignoredPackages
                )
                monthlyGoalMillis = goalMillis
            }

            monthlyDeviceUsageCache?.let {
                _deviceUsageFlow.value = Resource.success(it)
            } ?: run {
                throw Exception("Cache is null.")
            }

        } catch (e: Exception) {
            AppLog.e("DeviceUsageUseCase", "Failed to compute MONTH usage", e)
            monthlyDeviceUsageCache = null
            _deviceUsageFlow.value = Resource.failure(e)
        }
    }

    // ---- Year ----

    private var yearlyDeviceUsageCache: MonthlyDeviceUsage? = null
    private var yearlyGoalMillis: Long = 0L

    /**
     * Builds the 365-day view by reusing the month pipeline with a full year of day timestamps.
     * The system's WEEKLY / MONTHLY / YEARLY buckets (each with its own retention) fill the older
     * days; days beyond the DAILY retention cap are flagged as estimates by the repository, so the
     * UI can still be honest about completeness.
     */
    suspend fun calculateYearDeviceUsage(
        ignoreSystemApps: Boolean,
        ignoredPackages: List<String>,
        goalMillis: Long,
        useCache: Boolean = true
    ) {
        showLoadingIfEmpty()
        try {
            if(!isInitialised || !useCache) {
                reset(ignoreSystemApps, ignoredPackages)
            }
            if(yearlyDeviceUsageCache == null || !useCache || yearlyGoalMillis != goalMillis) {
                val now = System.currentTimeMillis()
                val days = 365
                val timeInterval = DateTimeUtils.computeInterval(now, days - 1)
                val today = DateTimeUtils.truncateToDay(now)
                val dayTimestamps: List<Long> = (0 until days).map { index ->
                    DateTimeUtils.addDaysToTimestamp(timestamp = today, dayOffset = -(days - 1 - index))
                }
                yearlyDeviceUsageCache = repository.getMonthDeviceUsage(
                    timeInterval = timeInterval,
                    dayTimestamps = dayTimestamps,
                    goalMillis = goalMillis,
                    ignoreSystemApps = ignoreSystemApps,
                    ignoredPackages = ignoredPackages
                )
                yearlyGoalMillis = goalMillis
            }
            yearlyDeviceUsageCache?.let {
                _deviceUsageFlow.value = Resource.success(it)
            } ?: run { throw Exception("Cache is null.") }
        } catch (e: Exception) {
            AppLog.e("DeviceUsageUseCase", "Failed to compute YEAR usage", e)
            yearlyDeviceUsageCache = null
            _deviceUsageFlow.value = Resource.failure(e)
        }
    }

    // ---- Launch history backfill ----

    /** 分批补算历史点击量（每次最多 maxDays 天），返回本次补算天数。 */
    suspend fun backfillLaunchHistory(
        ignoreSystemApps: Boolean,
        ignoredPackages: List<String>,
        maxDays: Int = 31
    ): Int = repository.backfillLaunchHistory(maxDays, ignoreSystemApps, ignoredPackages)

    // ---- Comparison (P3): this period vs previous period ----

    private val _comparisonFlow: MutableStateFlow<UsageComparison?> = MutableStateFlow(null)
    val comparisonFlow: StateFlow<UsageComparison?> = _comparisonFlow

    /**
     * 计算「本周期 vs 上一周期」：当前窗口 [now-N, now]，上一窗口 [now-2N, now-N]。
     * 时长来自系统聚合桶、打开次数来自本机永久逐日记录，无需重复事件明细扫描。
     * DAY 周期无上一周期，comparison 置空。
     */
    suspend fun calculateComparison(
        period: Period,
        ignoreSystemApps: Boolean,
        ignoredPackages: List<String>
    ) {
        if (period == Period.DAY) {
            _comparisonFlow.value = null
            return
        }
        val n = period.numberOfDays
        val now = System.currentTimeMillis()
        val dayMs = MILLIS_IN_A_DAY
        val curEnd = now
        val curStart = now - n.toLong() * dayMs
        val prevEnd = curStart
        val prevStart = now - 2L * n.toLong() * dayMs
        val current = repository.getIntervalTotal(curStart, curEnd, ignoreSystemApps, ignoredPackages)
        val previous = repository.getIntervalTotal(prevStart, prevEnd, ignoreSystemApps, ignoredPackages)
        _comparisonFlow.value = UsageComparison(
            period = period,
            currentDurationMs = current.totalDurationMs,
            previousDurationMs = previous.totalDurationMs,
            currentLaunches = current.totalLaunches,
            previousLaunches = previous.totalLaunches
        )
    }

    // ---- Reset ----

    private suspend fun reset(
        ignoreSystemApps: Boolean,
        ignoredPackages: List<String>
    ) {
        weeklyDeviceUsageCache = null
        monthlyDeviceUsageCache = null
        for(i in dailyDeviceUsageCache.indices) {
            dailyDeviceUsageCache[i] = null
        }
        calculateDeviceUsage(ignoreSystemApps, ignoredPackages)
    }
}
