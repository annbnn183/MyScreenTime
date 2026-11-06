package com.atharok.screentime.domain.entities.usage

import android.graphics.drawable.Drawable
import com.atharok.screentime.common.extensions.median
import com.atharok.screentime.common.extensions.safeDivideBy
import com.atharok.screentime.common.utils.DateTimeUtils
import kotlin.math.roundToLong

class DailyAppUsage(
    packageName: String,
    appName: String,
    appIcon: Drawable?,
    val durations: LongArray
) : AppUsage(packageName, appName, appIcon) {

    private val _totalTimeUsed: Long = durations.sum()

    private val _averageTimeUsed: Long = _totalTimeUsed.safeDivideBy(durations.size).roundToLong()

    private val _medianTimeUsed: Long by lazy { durations.median() }

    private val _chartValues: Map<String, Float> by lazy {
        durations.mapIndexed { index: Int, hourTimestamp: Long ->
            DateTimeUtils.formatToHour(index) to DateTimeUtils.convertToHourFloat(hourTimestamp)
        }.toMap()
    }

    override fun getTotalTimeUsed(): Long = _totalTimeUsed

    override fun getAverageTimeUsed(): Long = _averageTimeUsed

    override fun getMedianTimeUsed(): Long = _medianTimeUsed

    override fun getChartValues(): Map<String, Float> = _chartValues
}