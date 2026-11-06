package com.atharok.screentime.common.extensions

/**
 * Safely divides this Int by the given denominator.
 * Returns the result as a Double, or 0.0 if the denominator is zero to avoid division by zero errors.
 */
fun Int.safeDivideBy(denominator: Number): Double {
    return if (denominator.toDouble() != 0.0) this.toDouble() / denominator.toDouble() else 0.0
}

/**
 * Safely divides this Long by the given denominator.
 * Returns the result as a Double, or 0.0 if the denominator is zero to avoid division by zero errors.
 */
fun Long.safeDivideBy(denominator: Number): Double {
    return if (denominator.toDouble() != 0.0) this.toDouble() / denominator.toDouble() else 0.0
}