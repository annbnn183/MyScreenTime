package com.atharok.screentime.domain.usecases

import com.atharok.screentime.domain.entities.Period
import com.atharok.screentime.domain.entities.ThemeEntity
import com.atharok.screentime.domain.repositories.SettingsRepository
import kotlinx.coroutines.flow.Flow

class SettingsUseCase(private val repository: SettingsRepository) {

    fun getTheme(): Flow<ThemeEntity> = repository.getTheme()
    suspend fun saveTheme(themeEntity: ThemeEntity) {
        repository.saveTheme(themeEntity)
    }

    fun useDynamicColors(): Flow<Boolean> = repository.useDynamicColors()
    suspend fun saveUseDynamicColors(useDynamicColors: Boolean) {
        repository.saveUseDynamicColors(useDynamicColors)
    }

    fun useBlackColorForDarkTheme(): Flow<Boolean> = repository.useBlackColorForDarkTheme()
    suspend fun saveUseBlackColorForDarkTheme(useBlackColorForDarkTheme: Boolean) {
        repository.saveUseBlackColorForDarkTheme(useBlackColorForDarkTheme)
    }

    fun getDefaultPeriod(): Flow<Period> = repository.getDefaultPeriod()
    suspend fun saveDefaultPeriod(period: Period) {
        repository.saveDefaultPeriod(period)
    }

    fun shouldIgnoreSystemApps(): Flow<Boolean> = repository.shouldIgnoreSystemApps()
    suspend fun saveIgnoreSystemApps(ignoreSystemApps: Boolean) {
        repository.saveIgnoreSystemApps(ignoreSystemApps)
    }

    fun getIgnoredPackages(): Flow<List<String>> = repository.getIgnoredPackages()
    suspend fun saveIgnoredPackages(ignoredPackages: List<String>) {
        repository.saveIgnoredPackages(ignoredPackages)
    }
}