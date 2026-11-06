package com.atharok.screentime.data.dataStore

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.atharok.screentime.common.extensions.dataStore
import com.atharok.screentime.common.utils.isDynamicColorsAvailable
import com.atharok.screentime.domain.entities.DailyGoalSettings
import com.atharok.screentime.domain.entities.Period
import com.atharok.screentime.domain.entities.ThemeEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import java.io.IOException

class SettingsDataStore(private val context: Context) {

    companion object {
        private const val THEME_KEY = "theme_key"
        private const val DYNAMIC_COLORS_KEY = "material_you_key"
        private const val BLACK_COLOR_KEY = "black_color_key"
        private const val DEFAULT_PERIOD_KEY = "default_period_key"
        private const val IGNORE_SYSTEM_APP_KEY = "ignore_system_app_key"
        private const val IGNORED_PACKAGES_KEY = "ignored_packages_key"
        private const val DAILY_GOAL_ENABLED_KEY = "daily_goal_enabled_key"
        private const val DAILY_GOAL_MINUTES_KEY = "daily_goal_minutes_key"
        private const val DAILY_GOAL_WARNING_PERCENT_KEY = "daily_goal_warning_percent_key"
        private const val DAILY_GOAL_LAST_NOTIFIED_DATE_KEY = "daily_goal_last_notified_date_key"
    }

    private val themeKey = stringPreferencesKey(THEME_KEY)
    private val useDynamicColorsKey = booleanPreferencesKey(DYNAMIC_COLORS_KEY)
    private val useBlackColorForDarkThemeKey = booleanPreferencesKey(BLACK_COLOR_KEY)
    private val defaultPeriodKey = stringPreferencesKey(DEFAULT_PERIOD_KEY)
    private val ignoreSystemAppsKey = booleanPreferencesKey(IGNORE_SYSTEM_APP_KEY)
    private val ignoredPackagesKey = stringPreferencesKey(IGNORED_PACKAGES_KEY)
    private val dailyGoalEnabledKey = booleanPreferencesKey(DAILY_GOAL_ENABLED_KEY)
    private val dailyGoalMinutesKey = intPreferencesKey(DAILY_GOAL_MINUTES_KEY)
    private val dailyGoalWarningPercentKey = intPreferencesKey(DAILY_GOAL_WARNING_PERCENT_KEY)
    private val dailyGoalLastNotifiedDateKey = stringPreferencesKey(DAILY_GOAL_LAST_NOTIFIED_DATE_KEY)

    private fun Flow<Preferences>.catchException(): Flow<Preferences> = this.catch {
        if (it is IOException) {
            it.printStackTrace()
            emit(emptyPreferences())
        } else {
            throw it
        }
    }

    // ---- Appearance ----

    val themeFlow: Flow<ThemeEntity> = context.dataStore.data
        .catchException()
        .map { preferences ->
            preferences[themeKey] ?: ThemeEntity.SYSTEM.name
        }.map {
            try {
                ThemeEntity.valueOf(it)
            } catch (_: IllegalArgumentException) {
                ThemeEntity.SYSTEM
            }
        }

    suspend fun saveTheme(themeEntity: ThemeEntity) {
        context.dataStore.edit {
            it[themeKey] = themeEntity.name
        }
    }

    val useDynamicColorsFlow: Flow<Boolean> = context.dataStore.data
        .catchException()
        .map { preferences ->
            preferences[useDynamicColorsKey] ?: isDynamicColorsAvailable()
        }

    suspend fun saveUseDynamicColors(useDynamicColors: Boolean) {
        context.dataStore.edit {
            it[useDynamicColorsKey] = if(isDynamicColorsAvailable()) useDynamicColors else false
        }
    }

    val useBlackColorForDarkThemeFlow: Flow<Boolean> = context.dataStore.data
        .catchException()
        .map { preferences ->
            preferences[useBlackColorForDarkThemeKey] == true
        }

    suspend fun saveUseBlackColorForDarkTheme(useBlackColorForDarkTheme: Boolean) {
        context.dataStore.edit {
            it[useBlackColorForDarkThemeKey] = useBlackColorForDarkTheme
        }
    }

    // ---- Screen Time ----

    val defaultPeriodFlow: Flow<Period> = context.dataStore.data
        .catchException()
        .map { preferences ->
            preferences[defaultPeriodKey] ?: Period.DAY.name
        }.map {
            try {
                Period.valueOf(it)
            } catch (_: IllegalArgumentException) {
                Period.DAY
            }
        }

    suspend fun saveDefaultPeriod(period: Period) {
        context.dataStore.edit {
            it[defaultPeriodKey] = period.name
        }
    }

    // ---- Ignore system apps ----

    val ignoreSystemAppsFlow: Flow<Boolean> = context.dataStore.data
        .catchException()
        .map { preferences ->
            preferences[ignoreSystemAppsKey] == true
        }

    suspend fun saveIgnoreSystemApps(ignoreSystemApps: Boolean) {
        context.dataStore.edit {
            it[ignoreSystemAppsKey] = ignoreSystemApps
        }
    }

    // ---- Ignored packages ----

    val ignoredPackagesFlow: Flow<List<String>> = context.dataStore.data
        .catchException()
        .map {
            val jsonString: String? = it[ignoredPackagesKey]
            if(jsonString == null)
                emptyList()
            else
                try { Json.decodeFromString(jsonString) } catch (_: Exception) { emptyList() }
        }

    suspend fun saveIgnoredPackages(ignoredPackages: List<String>) {
        runCatching {
            Json.encodeToString(ignoredPackages)
        }.getOrNull()?.let { jsonString: String ->
            context.dataStore.edit {
                it[ignoredPackagesKey] = jsonString
            }
        }
    }

    // ---- Daily screen time goal ----

    val dailyGoalSettingsFlow: Flow<DailyGoalSettings> = context.dataStore.data
        .catchException()
        .map { preferences ->
            DailyGoalSettings(
                enabled = preferences[dailyGoalEnabledKey] ?: false,
                goalMinutes = (preferences[dailyGoalMinutesKey] ?: DailyGoalSettings.DEFAULT_GOAL_MINUTES)
                    .coerceAtLeast(1),
                warningPercent = (preferences[dailyGoalWarningPercentKey] ?: DailyGoalSettings.DEFAULT_WARNING_PERCENT)
                    .coerceIn(1, 100)
            )
        }

    suspend fun saveDailyGoalEnabled(enabled: Boolean) {
        context.dataStore.edit {
            it[dailyGoalEnabledKey] = enabled
        }
        // A fresh day must be able to warn again after the feature is re-enabled.
        if(!enabled) {
            context.dataStore.edit {
                it[dailyGoalLastNotifiedDateKey] = ""
            }
        }
    }

    suspend fun saveDailyGoalMinutes(minutes: Int) {
        context.dataStore.edit {
            it[dailyGoalMinutesKey] = minutes.coerceAtLeast(1)
        }
    }

    suspend fun saveDailyGoalWarningPercent(percent: Int) {
        context.dataStore.edit {
            it[dailyGoalWarningPercentKey] = percent.coerceIn(1, 100)
        }
    }

    /** `yyyy-MM-dd` of the day the warning was last sent, empty when never / reset. */
    val lastGoalNotifiedDateFlow: Flow<String> = context.dataStore.data
        .catchException()
        .map { preferences ->
            preferences[dailyGoalLastNotifiedDateKey] ?: ""
        }

    suspend fun saveLastGoalNotifiedDate(date: String) {
        context.dataStore.edit {
            it[dailyGoalLastNotifiedDateKey] = date
        }
    }
}