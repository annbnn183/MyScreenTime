package com.atharok.screentime.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atharok.screentime.domain.entities.Period
import com.atharok.screentime.domain.entities.ThemeEntity
import com.atharok.screentime.domain.usecases.SettingsUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val useCase: SettingsUseCase
): ViewModel() {

    val theme: Flow<ThemeEntity> get() = useCase.getTheme()
    fun changeTheme(newTheme: ThemeEntity) = viewModelScope.launch {
        useCase.saveTheme(newTheme)
    }

    val useDynamicColors: Flow<Boolean> get() = useCase.useDynamicColors()
    fun setUseDynamicColors(useDynamicColors: Boolean) = viewModelScope.launch {
        useCase.saveUseDynamicColors(useDynamicColors)
    }

    val useBlackColorForDarkTheme: Flow<Boolean> get() = useCase.useBlackColorForDarkTheme()
    fun setUseBlackColorForDarkTheme(useBlackColorForDarkTheme: Boolean) = viewModelScope.launch {
        useCase.saveUseBlackColorForDarkTheme(useBlackColorForDarkTheme)
    }

    val defaultPeriod: Flow<Period> get() = useCase.getDefaultPeriod()
    fun saveDefaultPeriod(period: Period) = viewModelScope.launch {
        useCase.saveDefaultPeriod(period)
    }

    val ignoreSystemApps: Flow<Boolean> get() = useCase.shouldIgnoreSystemApps()
    fun saveIgnoreSystemApps(ignoreSystemApps: Boolean) = viewModelScope.launch {
        useCase.saveIgnoreSystemApps(ignoreSystemApps)
    }

    val ignoredPackages: Flow<List<String>> get() = useCase.getIgnoredPackages()
    fun saveIgnoredPackages(ignoredPackages: List<String>) = viewModelScope.launch {
        useCase.saveIgnoredPackages(ignoredPackages)
    }
}