package com.atharok.screentime.domain.repositories

import com.atharok.screentime.domain.entities.DailyGoalSettings
import com.atharok.screentime.domain.entities.Period
import com.atharok.screentime.domain.entities.ThemeEntity
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    fun getTheme(): Flow<ThemeEntity>
    suspend fun saveTheme(themeEntity: ThemeEntity)

    fun useDynamicColors(): Flow<Boolean>
    suspend fun saveUseDynamicColors(useDynamicColors: Boolean)

    fun useBlackColorForDarkTheme(): Flow<Boolean>
    suspend fun saveUseBlackColorForDarkTheme(useBlackColorForDarkTheme: Boolean)

    fun getDefaultPeriod(): Flow<Period>
    suspend fun saveDefaultPeriod(period: Period)

    fun shouldIgnoreSystemApps(): Flow<Boolean>
    suspend fun saveIgnoreSystemApps(ignoreSystemApps: Boolean)

    fun getIgnoredPackages(): Flow<List<String>>
    suspend fun saveIgnoredPackages(ignoredPackages: List<String>)

    // ---- Daily screen time goal ----

    fun getDailyGoalSettings(): Flow<DailyGoalSettings>
    suspend fun saveDailyGoalEnabled(enabled: Boolean)
    suspend fun saveDailyGoalMinutes(minutes: Int)
    suspend fun saveDailyGoalWarningPercent(percent: Int)

    fun getLastGoalNotifiedDate(): Flow<String>
    suspend fun saveLastGoalNotifiedDate(date: String)

    // ---- Backup / restore / delete ----

    fun getAutoBackupEnabled(): Flow<Boolean>
    suspend fun saveAutoBackupEnabled(enabled: Boolean)
}