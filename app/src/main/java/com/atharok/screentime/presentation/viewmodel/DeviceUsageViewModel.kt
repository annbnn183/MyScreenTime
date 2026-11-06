package com.atharok.screentime.presentation.viewmodel

import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atharok.screentime.common.utils.NUMBER_OF_DAYS
import com.atharok.screentime.domain.entities.CentralTendency
import com.atharok.screentime.domain.entities.Period
import com.atharok.screentime.domain.entities.usage.AppUsage
import com.atharok.screentime.domain.entities.usage.DeviceUsage
import com.atharok.screentime.domain.resources.Resource
import com.atharok.screentime.domain.usecases.DeviceUsageUseCase
import com.atharok.screentime.domain.usecases.SettingsUseCase
import kotlinx.coroutines.Dispatchers
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

    fun calculateWeekDeviceUsage(useCache: Boolean = true) = viewModelScope.launch(Dispatchers.Default) {
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

    fun calculateDayDeviceUsage(useCache: Boolean = true) = viewModelScope.launch(Dispatchers.Default) {
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