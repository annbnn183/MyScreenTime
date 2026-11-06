package com.atharok.screentime.domain.entities

import androidx.annotation.StringRes
import com.atharok.screentime.R

enum class Period(@param:StringRes val stringRes: Int) {
    YEAR(R.string.year),
    MONTH(R.string.month),
    WEEK(R.string.week),
    DAY(R.string.day);

    /** Number of calendar days displayed for this period (including today). */
    val numberOfDays: Int
        get() = when (this) {
            YEAR -> 365
            MONTH -> 30
            WEEK -> 7
            DAY -> 1
        }
}