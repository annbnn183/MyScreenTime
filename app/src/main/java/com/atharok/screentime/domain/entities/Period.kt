package com.atharok.screentime.domain.entities

import androidx.annotation.StringRes
import com.atharok.screentime.R

enum class Period(@param:StringRes val stringRes: Int) {
    MONTH(R.string.month),
    WEEK(R.string.week),
    DAY(R.string.day);

    /** Number of calendar days displayed for this period (including today). */
    val numberOfDays: Int
        get() = when (this) {
            MONTH -> 30
            WEEK -> 7
            DAY -> 1
        }
}