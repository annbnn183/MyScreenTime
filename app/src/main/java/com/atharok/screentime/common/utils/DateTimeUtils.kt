package com.atharok.screentime.common.utils

import android.text.format.DateUtils
import com.atharok.screentime.common.extensions.capitalizeFirstChar
import com.atharok.screentime.common.extensions.setTime
import org.koin.core.Koin
import org.koin.core.context.GlobalContext
import org.koin.core.qualifier.named
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import kotlin.math.ceil

object DateTimeUtils {
    private val koin: Koin by lazy { GlobalContext.get() }
    private val calendar: Calendar by koin.inject()
    private val formater: DurationFormater by koin.inject()

    /**
     * Timestamp (Long) to "dd, HH" or "HH:mm" or "mm:ss" (String).
     * Format example:
     * - (en): 01d, 10h | 07h30m | 30m15s | 15s
     * - (fr): 01j, 10h | 07h30m | 30m15s | 15s
     */
    fun formatToDuration(timestamp: Long): String = formater.formatTimestampToDuration(timestamp)

    /**
     * Hour (Float) to "HH:mm" or "HH" or "mm" (String).
     * Format example:
     * - (en): 4h30m | 30m
     * - (fr): 4h30m | 30m
     */
    fun formatToHoursMinutes(hours: Float): String = formater.formatHoursToHoursMinutes(hours)

    /**
     * Format example:
     * - (en): Wednesday, January 1, 2025 9:30 AM
     * - (fr): Mercredi 1 janvier 2025 9:30
     */
    fun formatToFullDateTime(timestamp: Long): String = (koin.get<DateFormat>(named(KOIN_FULL_DATE_TIME_FORMAT)).format(timestamp)).capitalizeFirstChar()

    /**
     * Format example:
     * - (en): January 1, 2025 9:30 AM
     * - (fr): 1 janvier 2025 9:30
     */
    fun formatToMediumDateTime(timestamp: Long): String = (koin.get<DateFormat>(named(KOIN_MEDIUM_DATE_TIME_FORMAT)).format(timestamp)).capitalizeFirstChar()

    /**
     * Format example:
     * - (en): Wed, Jan 1, 2025
     * - (fr): Mer, 1 jan 2025
     */
    fun formatToMediumDate(timestamp: Long): String = "${formatToDayOfTheWeek(timestamp).removeSuffix(".")}, ${koin.get<DateFormat>(named(KOIN_DATE_FORMAT)).format(timestamp)}"

    /**
     * Format example:
     * - (en): Mon | Tue | Wed | Thu | Fri | Sat | Sun
     * - (fr): Lun. | Mar. | Mer. | Jeu. | Ven. | Sam. | Dim.
     */
    fun formatToDayOfTheWeek(timestamp: Long): String = koin.get<SimpleDateFormat>(named(KOIN_SIMPLE_DAY_FORMAT)).format(timestamp).capitalizeFirstChar()

    /**
     * Format example:
     * - (en): 12 AM | 4 AM | 8 AM | 12 PM | 4 PM | 8 PM
     * - (fr): 00h | 04h | 08h | 12ħ | 16h | 20h
     */
    fun formatToHour(value: Int): String {
        calendar.apply {
            // Avoids incorrect values during the time change (only the time is displayed, not the date, so these values will not be shown).
            set(Calendar.MONTH, Calendar.JANUARY)
            set(Calendar.DAY_OF_MONTH, 1)

            // Sets the time to be displayed.
            setTime(value, 0, 0, 0)
        }
        return koin.get<SimpleDateFormat>(named(KOIN_SIMPLE_HOUR_FORMAT)).format(calendar.timeInMillis)
    }

    fun convertToHourDouble(timestamp: Long): Double = timestamp / MILLIS_IN_A_HOUR.toDouble()
    fun convertToHourFloat(timestamp: Long): Float = timestamp / MILLIS_IN_A_HOUR.toFloat()

    /**
     * Format example:
     * - (en): Today | Yesterday | 2 days ago | 1 week ago
     * - (fr): Aujourd'hui | Hier | Avant-hier | Il y a 3 jours | Il y a 1 semaine
     */
    fun formatToRelativeTimeSpanString(timestamp: Long): String {
        val now: Long = System.currentTimeMillis()
        val nbDays: Int = calculateNumberOfDays(timestamp, now)
        return DateUtils.getRelativeTimeSpanString(
            timestamp,
            now,
            if(nbDays <= 7) DateUtils.DAY_IN_MILLIS else DateUtils.WEEK_IN_MILLIS,
            DateUtils.FORMAT_SHOW_DATE
        ).toString()
    }

    private fun calculateNumberOfDays(startTimestamp: Long, endTimestamp: Long): Int =
        ceil((endTimestamp - startTimestamp) / MILLIS_IN_A_DAY.toDouble()).toInt()

    private fun calculateNumberOfHours(startTimestamp: Long, endTimestamp: Long): Int =
        ceil((endTimestamp - startTimestamp) / MILLIS_IN_A_HOUR.toDouble()).toInt()

    // ---- Timestamp (Long) calculation ----

    /**
     * Example:
     * 1735742198000 (Wednesday 1 January 2025 14:36:38) -> 1735689600000 (Wednesday 1 January 2025 00:00:00)
     */
    fun truncateToDay(timestamp: Long): Long = calendar.apply {
        this.timeInMillis = timestamp
        this.setTime(0, 0, 0, 0)
    }.timeInMillis

    /**
     * Example:
     * 1735742198000 (Wednesday 1 January 2025 14:36:38) -> 1735743600000 (Wednesday 1 January 2025 15:00:00)
     */
    fun truncateToNextHour(timestamp: Long): Long = calendar.apply {
        timeInMillis = timestamp
        add(Calendar.HOUR_OF_DAY, 1)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    /**
     * Example:
     * 1735742198000 (Wednesday 1 January 2025 14:36:38) -> 1735828598000 (Thursday 2 January 2025 14:36:38)
     */
    fun addDaysToTimestamp(timestamp: Long, dayOffset: Int): Long = calendar.apply {
        timeInMillis = timestamp
        add(Calendar.DATE, dayOffset)
    }.timeInMillis

    /**
     * Example:
     * 1735742198000 (Wednesday 1 January 2025 14:36:38) -> 14
     */
    fun getHourOfDay(timestamp: Long): Int = calendar.apply {
        this.timeInMillis = timestamp
    }.get(Calendar.HOUR_OF_DAY)

    fun computeInterval(now: Long, numberOfDays: Int): Pair<Long, Long> {
        calendar.timeInMillis = now
        val periodEnd: Long = calendar.timeInMillis
        calendar.add(Calendar.DATE, -numberOfDays)
        calendar.setTime(0, 0, 0, 0)
        val periodBegin = calendar.timeInMillis

        return Pair(first = periodBegin, second = periodEnd)
    }

    fun computeLast7DaysIntervalFrom(now: Long): Pair<Long, Long> {
        calendar.timeInMillis = now
        calendar.setTime(0, 0, 0, 0)
        val periodEnd = calendar.timeInMillis - 1
        calendar.add(Calendar.DATE, -7)
        val periodBegin = calendar.timeInMillis

        return Pair(periodBegin, periodEnd)
    }

    fun computeDayIntervalFrom(now: Long, dayOffset: Int): Pair<Long, Long> {
        calendar.timeInMillis = now
        calendar.add(Calendar.DATE, -(NUMBER_OF_DAYS - 1) + dayOffset)
        calendar.setTime(0, 0, 0, 0)
        val periodBegin = calendar.timeInMillis
        calendar.add(Calendar.DATE, 1)
        val periodEnd = calendar.timeInMillis - 1

        return Pair(periodBegin, periodEnd)
    }
}