package com.atharok.screentime.domain.usecases

import com.atharok.screentime.common.utils.DateTimeUtils
import com.atharok.screentime.common.utils.NUMBER_OF_DAYS
import com.atharok.screentime.domain.entities.usage.DailyDeviceUsage
import com.atharok.screentime.domain.entities.usage.DeviceUsage
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
        calculationTimestamp = System.currentTimeMillis()
        repository.calculateDeviceUsage(
            timeInterval = DateTimeUtils.computeInterval(calculationTimestamp, NUMBER_OF_DAYS - 1),
            ignoreSystemApps = ignoreSystemApps,
            ignoredPackages = ignoredPackages
        )
        isInitialised = true
    }

    // ---- Compute Device Usage By Week and Day ----

    private val _deviceUsageFlow: MutableStateFlow<Resource<DeviceUsage<*>>> = MutableStateFlow(Resource.loading())
    val deviceUsageFlow: StateFlow<Resource<DeviceUsage<*>>> = _deviceUsageFlow

    // ---- Week ----

    private var weeklyDeviceUsageCache: WeeklyDeviceUsage? = null

    suspend fun calculateWeekDeviceUsage(
        ignoreSystemApps: Boolean,
        ignoredPackages: List<String>,
        useCache: Boolean = true
    ) {
        _deviceUsageFlow.value = Resource.loading()

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
            _deviceUsageFlow.value = Resource.loading()

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
            _deviceUsageFlow.value = Resource.failure(e)
        }
    }

    // ---- Reset ----

    private suspend fun reset(
        ignoreSystemApps: Boolean,
        ignoredPackages: List<String>
    ) {
        weeklyDeviceUsageCache = null
        for(i in dailyDeviceUsageCache.indices) {
            dailyDeviceUsageCache[i] = null
        }
        calculateDeviceUsage(ignoreSystemApps, ignoredPackages)
    }
}