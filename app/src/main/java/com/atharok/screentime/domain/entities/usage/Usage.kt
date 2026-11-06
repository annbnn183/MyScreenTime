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
    val dateOfLastRefresh: String,
    /** Total number of times any app was brought to the foreground in this period. */
    val totalLaunchCount: Int = 0,
    /** True when the system did not expose launch counts for the whole requested range. */
    val isClickDataLimited: Boolean = false
): Usage() {

    val timeIntervalStart: Long get() = timeInterval.first
    val timeIntervalEnd: Long get() = timeInterval.second

    val appUsageList: List<T> = usages
        .map { (_: String, value: T) -> value }
        .sortedByDescending { it.getTotalTimeUsed() }

    /** Apps ordered by how often they were opened (tie-break: screen time). */
    val launchRanking: List<T> get() = appUsageList
        .filter { it.launchCount > 0 }
        .sortedWith(compareByDescending<T> { it.launchCount }.thenByDescending { it.getTotalTimeUsed() })

    // ---- Usage for a specific App ----

    fun getAppUsage(packageName: String): AppUsage = usages[packageName] ?: EmptyAppUsage(packageName)
}

abstract class AppUsage(
    val packageName: String,
    val appName: String,
    val appIcon: Drawable?,
    /** Number of times this app was opened (brought to the foreground) in the period. */
    val launchCount: Int = 0
): Usage() {
    fun getPercentUsed(deviceTotalTimeUsed: Long): Float =
        getTotalTimeUsed().safeDivideBy(deviceTotalTimeUsed).toFloat()

    /** Share (0..1) of all app openings in the period. */
    fun getLaunchRate(totalLaunchCount: Int): Float =
        if (totalLaunchCount <= 0) 0f else launchCount.toFloat() / totalLaunchCount
}

private class EmptyAppUsage(packageName: String): AppUsage("Unknown package", packageName, null) {
    override fun getTotalTimeUsed(): Long = 0L
    override fun getAverageTimeUsed(): Long = 0L
    override fun getMedianTimeUsed(): Long = 0L
    override fun getChartValues(): Map<String, Float> = emptyMap()
}