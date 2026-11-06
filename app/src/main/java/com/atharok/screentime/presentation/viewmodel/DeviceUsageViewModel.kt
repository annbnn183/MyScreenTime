package com.atharok.screentime.presentation.viewmodel

import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atharok.screentime.common.utils.AppLog
import com.atharok.screentime.common.utils.NUMBER_OF_DAYS
import com.atharok.screentime.domain.entities.CentralTendency
import com.atharok.screentime.domain.entities.DailyGoalSettings
import com.atharok.screentime.domain.entities.Period
import com.atharok.screentime.domain.entities.usage.AppUsage
import com.atharok.screentime.domain.entities.usage.DeviceUsage
import com.atharok.screentime.domain.resources.Resource
import com.atharok.screentime.domain.usecases.DeviceUsageUseCase
import com.atharok.screentime.domain.usecases.SettingsUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class DeviceUsageViewModel(
    private val usageUseCase: DeviceUsageUseCase,
    private val settingsUseCase: SettingsUseCase
): ViewModel() {

    // Permission

    fun hasAppUsagePermission(): Boolean = usageUseCase.hasAppUsagePermission()

    // Usage calculation

    val defaultPeriod: Period = runBlocking { settingsUseCase.getDefaultPeriod().first() }
    val periodState: MutableState<Period> = mutableStateOf(defaultPeriod)
    val dayIndexState: MutableIntState = mutableIntStateOf(NUMBER_OF_DAYS - 1) // We display 9 days max.

    val selectedCentralTendency: MutableState<CentralTendency> = mutableStateOf(CentralTendency.AVERAGE)

    fun getDeviceUsageFlow(): StateFlow<Resource<DeviceUsage<out AppUsage>>> = usageUseCase.deviceUsageFlow

    /** Daily goal configuration, used by the month view's goal calendar. */
    fun getDailyGoalSettings(): Flow<DailyGoalSettings> = settingsUseCase.getDailyGoalSettings()

    /** 是否正在补算历史点击量。 */
    val backfillRunning: MutableState<Boolean> = mutableStateOf(false)

    fun backfillLaunchHistory() = viewModelScope.launch(Dispatchers.Default + AppLog.ceh) {
        if (backfillRunning.value) return@launch
        backfillRunning.value = true
        try {
            val ignoreSystemApps = settingsUseCase.shouldIgnoreSystemApps().first()
            val ignoredPackages = try {
                settingsUseCase.getIgnoredPackages().first()
            } catch (_: Exception) {
                emptyList()
            }
            val goalMillis = try {
                settingsUseCase.getDailyGoalSettings().first().goalMillis
            } catch (_: Exception) {
                0L
            }
            val done = usageUseCase.backfillLaunchHistory(ignoreSystemApps, ignoredPackages, 31)
            AppLog.i("DeviceUsageViewModel", "Backfilled $done days of launch history")
            // 强制重算，让界面立刻读到累加后的点击量。
            usageUseCase.calculateMonthDeviceUsage(
                ignoreSystemApps = ignoreSystemApps,
                ignoredPackages = ignoredPackages,
                goalMillis = goalMillis,
                useCache = false
            )
        } catch (e: Exception) {
            AppLog.e("DeviceUsageViewModel", "Launch history backfill failed", e)
        } finally {
            backfillRunning.value = false
        }
    }

    fun calculateMonthDeviceUsage(useCache: Boolean = true) = viewModelScope.launch(Dispatchers.Default + AppLog.ceh) {
        usageUseCase.calculateMonthDeviceUsage(
            ignoreSystemApps = settingsUseCase.shouldIgnoreSystemApps().first(),
            ignoredPackages = try {
                settingsUseCase.getIgnoredPackages().first()
            } catch (_: Exception) {
                emptyList()
            },
            goalMillis = try {
                settingsUseCase.getDailyGoalSettings().first().goalMillis
            } catch (_: Exception) {
                0L
            },
            useCache = useCache
        )
    }

    fun calculateWeekDeviceUsage(useCache: Boolean = true) = viewModelScope.launch(Dispatchers.Default + AppLog.ceh) {
        usageUseCase.calculateWeekDeviceUsage(
            ignoreSystemApps = settingsUseCase.shouldIgnoreSystemApps().first(),
            ignoredPackages = try {
                settingsUseCase.getIgnoredPackages().first()
            } catch (_: Exception) {
                emptyList()
            },
            useCache = useCache
        )
    }

    fun calculateDayDeviceUsage(useCache: Boolean = true) = viewModelScope.launch(Dispatchers.Default + AppLog.ceh) {
        usageUseCase.calculateDayDeviceUsage(
            ignoreSystemApps = settingsUseCase.shouldIgnoreSystemApps().first(),
            ignoredPackages = try {
                settingsUseCase.getIgnoredPackages().first()
            } catch (_: Exception) {
                emptyList()
            },
            dayIndex = dayIndexState.intValue,
            useCache = useCache
        )
    }
}
