package com.atharok.screentime.domain.entities

import androidx.annotation.StringRes
import com.atharok.screentime.R

enum class ThemeEntity(@param:StringRes val stringRes: Int) {
    SYSTEM(R.string.theme_system),
    LIGHT(R.string.theme_light),
    DARK(R.string.theme_dark)
}