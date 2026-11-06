package com.atharok.screentime.domain.entities

/**
 * Configuration of the "daily screen time goal" reminder.
 *
 * The user sets a daily budget ([goalMinutes]) and a [warningPercent]. When the screen time of the
 * current day reaches `goalMinutes * warningPercent / 100`, a notification is sent once a day.
 */
data class DailyGoalSettings(
    val enabled: Boolean = false,
    val goalMinutes: Int = DEFAULT_GOAL_MINUTES,
    val warningPercent: Int = DEFAULT_WARNING_PERCENT
) {

    /** Screen time at which the warning should fire, in milliseconds. */
    val thresholdMillis: Long
        get() = goalMinutes * 60_000L * warningPercent / 100

    /** Human readable daily budget, in milliseconds. */
    val goalMillis: Long
        get() = goalMinutes * 60_000L

    companion object {
        const val DEFAULT_GOAL_MINUTES = 120
        const val DEFAULT_WARNING_PERCENT = 80

        /** Presets shown in the settings dialog (minutes). */
        val GOAL_MINUTES_OPTIONS: List<Int> = listOf(30, 60, 90, 120, 180, 240, 300, 360, 480)

        /** Presets shown in the settings dialog (percent of the daily goal). */
        val WARNING_PERCENT_OPTIONS: List<Int> = listOf(50, 60, 75, 80, 90, 100)
    }
}
