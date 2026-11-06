package com.atharok.screentime.domain.entities.usage

/** Total screen time of one calendar day, in milliseconds. */
data class DailyDuration(
    val dayTimestamp: Long,
    val duration: Long
)
