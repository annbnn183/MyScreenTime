package com.atharok.screentime.domain.usecases

import android.content.Context
import com.atharok.screentime.R
import com.atharok.screentime.common.utils.DateTimeUtils
import com.atharok.screentime.domain.entities.usage.GlanceTodayDeviceUsage
import com.atharok.screentime.domain.repositories.GlanceTodayDeviceUsageRepository
import com.atharok.screentime.domain.resources.Resource

class GlanceTodayDeviceUsageUseCase(private val repository: GlanceTodayDeviceUsageRepository) {

    // ---- Permission ----

    fun hasAppUsagePermission(): Boolean = repository.hasAppUsagePermission()

    // ---- Compute Device Usage ----

    suspend fun calculateTodayDeviceUsage(
        ignoreSystemApps: Boolean,
        ignoredPackages: List<String>,
        context: Context
    ): Resource<GlanceTodayDeviceUsage> {
        return if(!hasAppUsagePermission()) {
            Resource.failure(
                throwable = Throwable(
                    message = context.getString(R.string.access_to_usage_data_message)
                )
            )
        } else {
            try {
                val calculationTimestamp = System.currentTimeMillis()
                val dayTimestamp = DateTimeUtils.truncateToDay(calculationTimestamp)

                val usage = repository.calculateGlanceTodayDeviceUsage(
                    timeInterval = Pair(
                        first = DateTimeUtils.addDaysToTimestamp(dayTimestamp, -1),
                        second = calculationTimestamp
                    ),
                    ignoreSystemApps = ignoreSystemApps,
                    ignoredPackages = ignoredPackages,
                    dayTimestamp = dayTimestamp
                )

                Resource.success(usage)
            } catch (e: Exception) {
                Resource.failure(e)
            }
        }
    }
}