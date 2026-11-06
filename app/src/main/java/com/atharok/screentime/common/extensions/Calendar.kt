package com.atharok.screentime.common.extensions

import java.util.Calendar

fun Calendar.setTime(hourOfDay: Int, minute: Int, second: Int, millisecond: Int) {
    set(Calendar.HOUR_OF_DAY, hourOfDay)
    set(Calendar.MINUTE, minute)
    set(Calendar.SECOND, second)
    set(Calendar.MILLISECOND, millisecond)
}