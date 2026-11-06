package com.atharok.screentime.data.dataStore

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.atharok.screentime.common.extensions.dataStore
import com.atharok.screentime.common.utils.isDynamicColorsAvailable
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
    }

    private val themeKey = stringPreferencesKey(THEME_KEY)
    private val useDynamicColorsKey = booleanPreferencesKey(DYNAMIC_COLORS_KEY)
    private val useBlackColorForDarkThemeKey = booleanPreferencesKey(BLACK_COLOR_KEY)
    private val defaultPeriodKey = stringPreferencesKey(DEFAULT_PERIOD_KEY)
    private val ignoreSystemAppsKey = booleanPreferencesKey(IGNORE_SYSTEM_APP_KEY)
    private val ignoredPackagesKey = stringPreferencesKey(IGNORED_PACKAGES_KEY)

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
}