package com.atharok.screentime.domain.entities.usage

import kotlinx.serialization.Serializable

@Serializable
data class GlanceTodayDeviceUsage(
    val totalTimeUsed: Long,
    val todayAppsUsage: List<GlanceTodayAppUsage>
)

@Serializable
data class GlanceTodayAppUsage(
    val appName: String,
    val totalTimeUsed: Long,
    val percentUsage: Float
)