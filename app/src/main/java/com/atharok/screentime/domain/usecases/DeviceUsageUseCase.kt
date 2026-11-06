package com.atharok.screentime.domain.usecases

import com.atharok.screentime.common.utils.AppLog
import com.atharok.screentime.common.utils.DateTimeUtils
import com.atharok.screentime.common.utils.NUMBER_OF_DAYS
import com.atharok.screentime.common.utils.NUMBER_OF_DAYS_IN_MONTH
import com.atharok.screentime.domain.entities.usage.DailyDeviceUsage
import com.atharok.screentime.domain.entities.usage.DeviceUsage
import com.atharok.screentime.domain.entities.usage.MonthlyDeviceUsage
import com.atharok.screentime.domain.entities.usage.WeeklyDeviceUsage
import com.atharok.screentime.domain.repositories.DeviceUsageRepository
import com.atharok.screentime.domain.resources.Resource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

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
