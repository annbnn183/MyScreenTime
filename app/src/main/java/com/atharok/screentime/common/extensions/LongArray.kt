package com.atharok.screentime.common.extensions

import kotlin.math.roundToLong

fun LongArray.median(): Long {
    return if (this.isEmpty()) {
        0L
    } else {
        val sorted: LongArray = this.sortedArray()
        val size: Int = sorted.size
        val middle: Int = size / 2

        // if even number else odd number
        if (size % 2 == 0) {
            ((sorted[middle - 1] + sorted[middle]) / 2.0).roundToLong()
        } else {
            sorted[middle]
        }
    }
}