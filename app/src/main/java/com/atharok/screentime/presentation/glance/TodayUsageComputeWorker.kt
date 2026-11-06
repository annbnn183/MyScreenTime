package com.atharok.screentime.presentation.glance

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.atharok.screentime.R
import com.atharok.screentime.common.utils.GLANCE_PREFERENCES_FAILURE
import com.atharok.screentime.common.utils.GLANCE_PREFERENCES_LOADING
import com.atharok.screentime.common.utils.GLANCE_PREFERENCES_REFRESH_TIMESTAMP
import com.atharok.screentime.common.utils.GLANCE_PREFERENCES_TODAY_DEVICE_USAGE
import com.atharok.screentime.domain.entities.usage.GlanceTodayDeviceUsage
import com.atharok.screentime.domain.resources.Resource
import com.atharok.screentime.domain.usecases.GlanceTodayDeviceUsageUseCase
import com.atharok.screentime.domain.usecases.SettingsUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.json.Json
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class TodayUsageComputeWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params), KoinComponent {

    companion object {
        private var isWorking = false
    }

    override suspend fun doWork(): Result {

        if(isWorking) {
            return Result.retry()
        }

        isWorking = true

        try {
            val todayUsageResource = calculateTodayDeviceUsage()

            GlanceAppWidgetManager(applicationContext).getGlanceIds(
                provider = TodayUsageWidget::class.java
            ).forEach { glanceId: GlanceId ->
                updateAppWidgetState(
                    context = applicationContext,
                    glanceId = glanceId
                ) {
                    when (todayUsageResource) {
                        is Resource.Failure<*> -> {
                            it[stringPreferencesKey(GLANCE_PREFERENCES_FAILURE)] =
                                todayUsageResource.throwable.message
                                    ?: applicationContext.getString(R.string.error_failed_to_retrieve_usage_data)
                        }

                        is Resource.Success<GlanceTodayDeviceUsage> -> {
                            try {
                                val todayDeviceUsageJsonString: String =
                                    Json.encodeToString(todayUsageResource.data)
                                it[stringPreferencesKey(GLANCE_PREFERENCES_TODAY_DEVICE_USAGE)] =
                                    todayDeviceUsageJsonString
                                it[stringPreferencesKey(GLANCE_PREFERENCES_FAILURE)] = ""
                            } catch (e: Exception) {
                                it[stringPreferencesKey(GLANCE_PREFERENCES_FAILURE)] = e.message
                                    ?: applicationContext.getString(R.string.error_failed_to_retrieve_usage_data)
                            }
                        }

                        else -> {
                            it[stringPreferencesKey(GLANCE_PREFERENCES_FAILURE)] = ""
                        }
                    }
                    it[longPreferencesKey(GLANCE_PREFERENCES_REFRESH_TIMESTAMP)] = System.currentTimeMillis()
                    it[booleanPreferencesKey(GLANCE_PREFERENCES_LOADING)] = false
                }

                TodayUsageWidget().update(applicationContext, glanceId)
            }
        } finally {
            isWorking = false
        }

        return Result.success()
    }

    private suspend fun calculateTodayDeviceUsage(): Resource<GlanceTodayDeviceUsage> {
        val deviceUsageUseCase by inject<GlanceTodayDeviceUsageUseCase>()
        val settingsUseCase by inject<SettingsUseCase>()

        val todayUsageResource: Resource<GlanceTodayDeviceUsage> =
            deviceUsageUseCase.calculateTodayDeviceUsage(
                ignoreSystemApps = settingsUseCase.shouldIgnoreSystemApps().first(),
                ignoredPackages = settingsUseCase.getIgnoredPackages().firstOrNull() ?: emptyList(),
                context = applicationContext
            )

        return todayUsageResource
    }
}