package com.atharok.screentime.common.utils

import android.os.Build

fun isDynamicColorsAvailable(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S