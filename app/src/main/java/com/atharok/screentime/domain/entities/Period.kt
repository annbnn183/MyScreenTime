package com.atharok.screentime.domain.entities

import androidx.annotation.StringRes
import com.atharok.screentime.R

enum class Period(@param:StringRes val stringRes: Int) {
    WEEK(R.string.week),
    DAY(R.string.day)
}