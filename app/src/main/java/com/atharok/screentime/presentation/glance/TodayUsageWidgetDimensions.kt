package com.atharok.screentime.presentation.glance

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class TodayUsageWidgetDimensions {

    val width: Dp
    val height: Dp
    val padding: Dp
    val iconSize: Dp
    val fontSizeLow: TextUnit
    val fontSizeMedium: TextUnit
    val fontSizeHigh: TextUnit

    private constructor(
        width: Dp,
        height: Dp,
        padding: Dp,
        iconSize: Dp,
        fontSizeLow: TextUnit,
        fontSizeMedium: TextUnit,
        fontSizeHigh: TextUnit
    ) {
        this.width = width
        this.height = height
        this.padding = padding
        this.iconSize = iconSize
        this.fontSizeLow = fontSizeLow
        this.fontSizeMedium = fontSizeMedium
        this.fontSizeHigh = fontSizeHigh
    }

    companion object {

        val WIDTH_LIMIT = 208.dp
        private val WIDTH_LIMIT_LOW = 180.dp

        fun calculateDimensions(width: Dp, height: Dp): TodayUsageWidgetDimensions {
            return when {
                height > 224.dp -> {
                    TodayUsageWidgetDimensions(
                        width = width,
                        height = height,
                        padding = 12.dp,
                        iconSize = if(width < WIDTH_LIMIT) 30.dp else 36.dp,
                        fontSizeLow = 12.sp,
                        fontSizeMedium = 14.sp,
                        fontSizeHigh = if(width < WIDTH_LIMIT_LOW) 24.sp else if(width < WIDTH_LIMIT) 28.sp else 36.sp
                    )
                }

                height > 192.dp -> {
                    TodayUsageWidgetDimensions(
                        width = width,
                        height = height,
                        padding = 10.dp,
                        iconSize = if(width < WIDTH_LIMIT) 30.dp else 32.dp,
                        fontSizeLow = 11.sp,
                        fontSizeMedium = 13.sp,
                        fontSizeHigh = if(width < WIDTH_LIMIT_LOW) 24.sp else if(width < WIDTH_LIMIT) 28.sp else 32.sp
                    )
                }

                height > 112.dp -> {
                    TodayUsageWidgetDimensions(
                        width = width,
                        height = height,
                        padding = 12.dp,
                        iconSize = if(width < WIDTH_LIMIT) 30.dp else 36.dp,
                        fontSizeLow = 12.sp,
                        fontSizeMedium = 14.sp,
                        fontSizeHigh = if(width < WIDTH_LIMIT_LOW) 24.sp else if(width < WIDTH_LIMIT) 28.sp else 36.sp
                    )
                }

                height > 88.dp -> {
                    TodayUsageWidgetDimensions(
                        width = width,
                        height = height,
                        padding = 10.dp,
                        iconSize = if(width < WIDTH_LIMIT) 30.dp else 32.dp,
                        fontSizeLow = 11.sp,
                        fontSizeMedium = 13.sp,
                        fontSizeHigh = if(width < WIDTH_LIMIT_LOW) 24.sp else if(width < WIDTH_LIMIT) 28.sp else 32.sp
                    )
                }

                else -> {
                    TodayUsageWidgetDimensions(
                        width = width,
                        height = height,
                        padding = 8.dp,
                        iconSize = if(width < WIDTH_LIMIT) 26.dp else 28.dp,
                        fontSizeLow = 10.sp,
                        fontSizeMedium = 12.sp,
                        fontSizeHigh = if(width < WIDTH_LIMIT_LOW) 24.sp else if(width < WIDTH_LIMIT) 24.sp else 28.sp
                    )
                }
            }
        }
    }
}