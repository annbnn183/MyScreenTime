package com.atharok.screentime.domain.repositories

import com.atharok.screentime.domain.entities.usage.GlanceTodayDeviceUsage

interface GlanceTodayDeviceUsageRepository {

    // Permission

    fun hasAppUsagePermission(): Boolean

    // Calculate

    suspend fun calculateGlanceTodayDeviceUsage(
        timeInterval: Pair<Long, Long>,
        ignoreSystemApps: Boolean,
        ignoredPackages: List<String>,
        dayTimestamp: Long
    ): GlanceTodayDeviceUsage
}