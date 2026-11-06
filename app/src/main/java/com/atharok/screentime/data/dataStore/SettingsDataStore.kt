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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import org.json.JSONObject
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
        private const val AUTO_BACKUP_ENABLED_KEY = "auto_backup_enabled_key"
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
    private val autoBackupEnabledKey = booleanPreferencesKey(AUTO_BACKUP_ENABLED_KEY)

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

    // ---- Backup / restore / delete (P2) ----

    /**
     * 把所有设置序列化成 JSON，供「完整数据备份」复用。
     * 直接读底层 Preferences，避免逐条走 Flow，保证一次性快照一致。
     */
    suspend fun exportAll(): org.json.JSONObject {
        val prefs = context.dataStore.data.first()
        return org.json.JSONObject().apply {
            put("theme", prefs[themeKey] ?: ThemeEntity.SYSTEM.name)
            put("dynamicColors", prefs[useDynamicColorsKey] ?: isDynamicColorsAvailable())
            put("blackColor", prefs[useBlackColorForDarkThemeKey] == true)
            put("defaultPeriod", prefs[defaultPeriodKey] ?: Period.DAY.name)
            put("ignoreSystemApps", prefs[ignoreSystemAppsKey] == true)
            put("ignoredPackages", prefs[ignoredPackagesKey] ?: "[]")
            put("dailyGoalEnabled", prefs[dailyGoalEnabledKey] ?: false)
            put("dailyGoalMinutes", prefs[dailyGoalMinutesKey] ?: DailyGoalSettings.DEFAULT_GOAL_MINUTES)
            put("dailyGoalWarningPercent", prefs[dailyGoalWarningPercentKey] ?: DailyGoalSettings.DEFAULT_WARNING_PERCENT)
            put("autoBackupEnabled", prefs[autoBackupEnabledKey] == true)
        }
    }

    /** 把 [exportAll] 产出的 JSON 写回；遇到缺字段或类型不符时跳过该项，保证部分恢复也不崩。 */
    suspend fun importAll(json: org.json.JSONObject) {
        if (json.optBoolean("__clearFirst__", false)) {
            context.dataStore.edit { it.clear() }
        }
        context.dataStore.edit { prefs ->
            if (json.has("theme")) runCatching { ThemeEntity.valueOf(json.getString("theme")) }
                .getOrNull()?.let { prefs[themeKey] = it.name }
            if (json.has("dynamicColors")) prefs[useDynamicColorsKey] = json.getBoolean("dynamicColors")
            if (json.has("blackColor")) prefs[useBlackColorForDarkThemeKey] = json.getBoolean("blackColor")
            if (json.has("defaultPeriod")) runCatching { Period.valueOf(json.getString("defaultPeriod")) }
                .getOrNull()?.let { prefs[defaultPeriodKey] = it.name }
            if (json.has("ignoreSystemApps")) prefs[ignoreSystemAppsKey] = json.getBoolean("ignoreSystemApps")
            if (json.has("ignoredPackages")) prefs[ignoredPackagesKey] = json.getString("ignoredPackages")
            if (json.has("dailyGoalEnabled")) prefs[dailyGoalEnabledKey] = json.getBoolean("dailyGoalEnabled")
            if (json.has("dailyGoalMinutes")) prefs[dailyGoalMinutesKey] = json.getInt("dailyGoalMinutes").coerceAtLeast(1)
            if (json.has("dailyGoalWarningPercent")) prefs[dailyGoalWarningPercentKey] = json.getInt("dailyGoalWarningPercent").coerceIn(1, 100)
            if (json.has("autoBackupEnabled")) prefs[autoBackupEnabledKey] = json.getBoolean("autoBackupEnabled")
        }
    }

    /** 清空全部设置（恢复出厂），打开次数等历史由 LaunchHistoryStore 单独处理。 */
    suspend fun clearAll() {
        context.dataStore.edit { it.clear() }
    }

    val autoBackupEnabledFlow: Flow<Boolean> = context.dataStore.data
        .catchException()
        .map { preferences -> preferences[autoBackupEnabledKey] == true }

    suspend fun saveAutoBackupEnabled(enabled: Boolean) {
        context.dataStore.edit {
            it[autoBackupEnabledKey] = enabled
        }
    }
}