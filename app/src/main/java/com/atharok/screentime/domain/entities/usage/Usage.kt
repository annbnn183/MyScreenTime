package com.atharok.screentime.domain.entities.usage

import android.graphics.drawable.Drawable
import com.atharok.screentime.common.extensions.safeDivideBy

sealed class Usage {
    abstract fun getTotalTimeUsed(): Long
    abstract fun getAverageTimeUsed(): Long
    abstract fun getMedianTimeUsed(): Long
    abstract fun getChartValues(): Map<String, Float>
}

abstract class DeviceUsage<T: AppUsage>(
    val usages: Map<String, T>,
    private val timeInterval: Pair<Long, Long>,
    val dateOfLastRefresh: String
): Usage() {

    val timeIntervalStart: Long get() = timeInterval.first
    val timeIntervalEnd: Long get() = timeInterval.second

    val appUsageList: List<T> = usages
        .map { (_: String, value: T) -> value }
        .sortedByDescending { it.getTotalTimeUsed() }

    // ---- Usage for a specific App ----

    fun getAppUsage(packageName: String): AppUsage = usages[packageName] ?: EmptyAppUsage(packageName)
}

abstract class AppUsage(
    val packageName: String,
    val appName: String,
    val appIcon: Drawable?
): Usage() {
    fun getPercentUsed(deviceTotalTimeUsed: Long): Float =
        getTotalTimeUsed().safeDivideBy(deviceTotalTimeUsed).toFloat()
}

private class EmptyAppUsage(packageName: String): AppUsage("Unknown package", packageName, null) {
    override fun getTotalTimeUsed(): Long = 0L
    override fun getAverageTimeUsed(): Long = 0L
    override fun getMedianTimeUsed(): Long = 0L
    override fun getChartValues(): Map<String, Float> = emptyMap()
}