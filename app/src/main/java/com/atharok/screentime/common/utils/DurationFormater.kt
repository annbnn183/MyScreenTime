package com.atharok.screentime.common.utils

import android.content.Context
import androidx.annotation.StringRes
import com.atharok.screentime.R
import java.util.Locale

class DurationFormater(private val context: Context) {

    private data class Time(val days: Int, val hours: Int, val minutes: Int, val seconds: Int)

    companion object {
        private const val SECONDS_PER_MINUTE = 60
        private const val SECONDS_PER_HOUR = 60 * SECONDS_PER_MINUTE
        private const val SECONDS_PER_DAY = 24 * SECONDS_PER_HOUR
    }

    // ---- Format ----

    /**
     * Timestamp (Long) to "dd, HH" or "HH:mm" or "mm:ss" (String).
     */
    fun formatTimestampToDuration(timestamp: Long): String {
        val (days, hours, minutes, seconds) = calculateTime(timestamp)
        return when {
            days > 0 -> "${getString(R.string.day_unit, days)}, ${formatTime(hours, R.string.hour_unit)}"
            hours > 0 -> "${getString(R.string.hour_unit, hours)} ${formatTime(minutes, R.string.minute_unit)}"
            minutes > 0 -> "${getString(R.string.minute_unit, minutes)} ${formatTime(seconds, R.string.second_unit)}"
            else -> getString(R.string.second_unit, seconds)
        }
    }

    private fun calculateTime(milliseconds: Long): Time {
        val seconds = (milliseconds / 1000).toInt()
        val days = seconds / SECONDS_PER_DAY
        val hours = (seconds % SECONDS_PER_DAY) / SECONDS_PER_HOUR
        val minutes = ((seconds % SECONDS_PER_DAY) % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
        val remainingSeconds = seconds % SECONDS_PER_MINUTE
        return Time(days, hours, minutes, remainingSeconds)
    }

    // ---- String ----

    private fun getString(@StringRes stringRes: Int, value: Any): String {
        return context.getString(stringRes, value)
    }

    private fun formatTime(value: Number, @StringRes stringRes: Int): String {
        return getString(stringRes, String.format(Locale.getDefault(), "%02d", value))
    }

    // ---------------------------------------------------------------------------------------------

    /**
     * Hour (Float) to "HH:mm" or "HH" or "mm" (String).
     */
    fun formatHoursToHoursMinutes(time: Float): String {
        val hours = time.toInt()
        val minutes = ((time - hours) * SECONDS_PER_MINUTE).toInt()
        return when {
            minutes == 0 -> hoursStr(hours)
            hours == 0 -> minutesStr(minutes)
            else -> "${hoursStr(hours)}${minutesStr(minutes)}"
        }
    }

    private fun secondsStr(value: Number): String = getString(R.string.second_unit, value)
    private fun minutesStr(value: Number): String = getString(R.string.minute_unit, value)
    private fun hoursStr(value: Number): String = getString(R.string.hour_unit, value)
    private fun daysStr(value: Number): String = getString(R.string.day_unit, value)
}