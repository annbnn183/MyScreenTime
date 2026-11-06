package com.atharok.screentime.data.statsTracker

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.atharok.screentime.common.utils.AppLog
import com.atharok.screentime.common.utils.DateTimeUtils
import com.atharok.screentime.common.utils.MILLIS_IN_A_DAY

class UsageStatsTracker(
    private val context: Context,
    private val usageStatsManager: UsageStatsManager,
    private val appOps: AppOpsManager
) {

    // ---- Permission ----

    fun hasAppUsagePermission(): Boolean {
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    // ---- Usage calculation ----

    private val launchableApp: Set<String> by lazy {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val resolveInfos = pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)

        resolveInfos.map { it.activityInfo.packageName }.toSet()
    }

    private data class ResumedPackage(
        val packageName: String,
        val timestamp: Long,
    )

    /**
     * Calculates the different intervals for each app.
     */
    suspend fun calculateUsageIntervalsByPackage(
        startTimestamp: Long,
        endTimestamp: Long,
        ignoreSystemApps: Boolean,
        ignoredPackages: List<String>
    ): Map<String, List<Pair<Long, Long>>> {
        val usageIntervals = mutableMapOf<String, MutableList<Pair<Long, Long>>>()

        usageStatsManager.queryEvents(startTimestamp, endTimestamp)?.let { usageEvents: UsageEvents ->

            val resumedPackages = mutableMapOf<String, ResumedPackage>()
            //val isFirstResumedCalled = mutableMapOf<String, Boolean>()

            while (usageEvents.hasNextEvent()) {
                val event = UsageEvents.Event()

                if(usageEvents.getNextEvent(event)) {

                    if(ignoreSystemApps && event.packageName !in launchableApp) continue

                    if(ignoredPackages.contains(event.packageName)) continue

                    event.packageName?.let { eventPackageName: String ->
                        val packageClassName: String = event.className ?: eventPackageName

                        when(event.eventType) {

                            UsageEvents.Event.ACTIVITY_RESUMED -> {
                                resumedPackages[packageClassName] = ResumedPackage(
                                    packageName = eventPackageName,
                                    timestamp = event.timeStamp
                                )
                                //isFirstResumedCalled[packageClassName] = true
                            }

                            UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED -> {
                                // If the app is in the foreground.
                                resumedPackages[packageClassName]?.let { resumedPackage ->
                                    usageIntervals
                                        .getOrPut(eventPackageName) { mutableListOf() }
                                        .add(resumedPackage.timestamp to event.timeStamp)
                                    resumedPackages.remove(packageClassName)
                                }/* ?: run {
                                    // If the first RESUMED state was not called, we assume it
                                    // occurred before the start timestamp passed as a function
                                    // parameter. Therefore we add the interval between the provided
                                    // start timestamp and the current PAUSED/STOPPED timestamp.
                                    if(isFirstResumedCalled[packageClassName] != true) {
                                        usageIntervals
                                            .getOrPut(eventPackageName) { mutableListOf() }
                                            .add(startTimestamp to event.timeStamp)
                                        isFirstResumedCalled[packageClassName] = true
                                    }
                                }*/ // -> May cause errors, so we remove it
                            }

                        }

                    }
                }
            }

            // If the last retrieved event was in the RESUMED state, we add the interval between
            // the timestamp of that RESUMED event and the end timestamp provided as a function
            // parameter.
            resumedPackages.maxByOrNull {
                it.value.timestamp
            }?.value?.let { lastResumedPackage: ResumedPackage ->
                usageIntervals
                    .getOrPut(lastResumedPackage.packageName) { mutableListOf() }
                    .add(lastResumedPackage.timestamp to endTimestamp)
            }
        }

        return usageIntervals
    }

    /**
     * Calculates screen time for each app by day and hour.
     */
    suspend fun getDurationPerPackageByDayAndHour(
        startTimestamp: Long,
        endTimestamp: Long,
        usageIntervalsByPackage: Map<String, List<Pair<Long, Long>>>
    ): Map<String, Map<Long, LongArray>> {
        val durationPerPackageByDayAndHour = mutableMapOf<String, Map<Long, LongArray>>()

        usageIntervalsByPackage.forEach { (packageName: String, intervals: List<Pair<Long, Long>>) ->
            durationPerPackageByDayAndHour[packageName] = generateDurationMapByDayAndHour(
                intervals = intervals,
                startTimestamp = startTimestamp,
                endTimestamp = endTimestamp
            )
        }

        return durationPerPackageByDayAndHour
    }

    /**
     * Calculates total screen time independently of individual apps. For this calculation,
     * overlapping intervals are merged.
     */
    suspend fun getTotalDurationByDayAndHour(
        startTimestamp: Long,
        endTimestamp: Long,
        usageIntervalsByPackage: Map<String, List<Pair<Long, Long>>>
    ): Map<Long, LongArray> {
        val intervals: List<Pair<Long, Long>> = mergeOverlappingIntervals(
            intervals = usageIntervalsByPackage.values.flatMap { it }
        )

        val totalDurationPerByDayAndHour = generateDurationMapByDayAndHour(
            intervals = intervals,
            startTimestamp = startTimestamp,
            endTimestamp = endTimestamp
        )

        return totalDurationPerByDayAndHour
    }

    /**
     * Merge the overlapping intervals.
     * Handles the case where multiple apps are brought to the foreground at the same time
     * (split-screen mode). To do this, overlapping intervals are merged.
     */
    private fun mergeOverlappingIntervals(intervals: List<Pair<Long, Long>>): List<Pair<Long, Long>> {
        if (intervals.isEmpty()) return emptyList()

        // Sorts the intervals
        val sortedIntervals = intervals.sortedBy { it.first }

        // Initializes the result list with the first interval.
        val mergedIntervals = mutableListOf<Pair<Long, Long>>()
        var currentInterval = sortedIntervals[0]

        for (i in 1 until sortedIntervals.size) {
            val nextInterval = sortedIntervals[i]

            // Checks if there is an overlap.
            if (currentInterval.second >= nextInterval.first) {
                // Merges the intervals.
                currentInterval = Pair(
                    currentInterval.first,
                    maxOf(currentInterval.second, nextInterval.second)
                )
            } else {
                // Adds the current interval to the list of merged intervals.
                mergedIntervals.add(currentInterval)
                // Updates the current interval with the next one.
                currentInterval = nextInterval
            }
        }

        // Adds the last merged interval.
        mergedIntervals.add(currentInterval)

        return mergedIntervals
    }

    /**
     * @return MutableMap<Long, Array<Long>>.
     * Key: Long (Timestamp truncate to day).
     * Value: Array<Long> (Duration in milliseconds for each hour of the day).
     */
    private fun generateDurationMapByDayAndHour(
        intervals: List<Pair<Long, Long>>,
        startTimestamp: Long,
        endTimestamp: Long
    ): Map<Long, LongArray> {

        val durationsByDayAndHour = initializeDurationsByDayAndHour(startTimestamp, endTimestamp)

        for ((intervalStart, intervalEnd) in intervals) {

            var currentTimestamp: Long = intervalStart

            while (currentTimestamp < intervalEnd) {
                val dayTimestamp: Long = DateTimeUtils.truncateToDay(currentTimestamp)
                val hourIndex: Int = DateTimeUtils.getHourOfDay(currentTimestamp)

                // Computes the next split point: either the next full hour or intervalEnd
                val timestampTruncatedToNextHour: Long = DateTimeUtils.truncateToNextHour(currentTimestamp)
                val nextTimestamp = minOf(timestampTruncatedToNextHour, intervalEnd)

                // Add the elapsed time between the two intervals.
                val durationToAdd = nextTimestamp - currentTimestamp
                durationsByDayAndHour.getOrPut(dayTimestamp) { LongArray(24) }[hourIndex] += durationToAdd

                // Move to the next duration
                currentTimestamp = nextTimestamp
            }
        }

        return durationsByDayAndHour
    }

    private fun initializeDurationsByDayAndHour(
        startTimestamp: Long,
        endTimestamp: Long
    ): MutableMap<Long, LongArray> {
        val durationsByDayAndHour = mutableMapOf<Long, LongArray>()

        var currentTimestamp = DateTimeUtils.truncateToDay(startTimestamp)
        while(currentTimestamp < endTimestamp) {
            durationsByDayAndHour[currentTimestamp] = LongArray(24)
            currentTimestamp = DateTimeUtils.addDaysToTimestamp(currentTimestamp, 1)
        }

        return durationsByDayAndHour
    }

    // ---- Monthly aggregation (system pre-aggregated daily stats) ----

    data class MonthlyScanResult(
        val perPackageByDay: Map<String, Map<Long, Long>>,
        val dailyTotals: Map<Long, Long>,
        /** Launch (open) counts reported by the system, per package and day. */
        val launchCountByPackageByDay: Map<String, Map<Long, Int>>
    )

    /**
     * Reads the system's per-day aggregated usage stats. Unlike queryEvents(), the cost of this
     * call does not grow with the number of recorded events, so a 30-day range stays fast even on
     * devices with a very long usage history.
     */
    fun queryMonthlyUsageByDay(
        startTimestamp: Long,
        endTimestamp: Long,
        ignoreSystemApps: Boolean,
        ignoredPackages: List<String>
    ): MonthlyScanResult {
        val perPackageByDay = mutableMapOf<String, MutableMap<Long, Long>>()
        val launchCountByPackageByDay = mutableMapOf<String, MutableMap<Long, Int>>()

        val usageStatsList = try {
            usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                startTimestamp,
                endTimestamp
            )
        } catch (e: Exception) {
            AppLog.e("UsageStatsTracker", "queryUsageStats(INTERVAL_DAILY) failed", e)
            null
        }.orEmpty()
        AppLog.d(
            "UsageStatsTracker",
            "INTERVAL_DAILY returned ${usageStatsList.size} buckets for range $startTimestamp..$endTimestamp"
        )

        for (stats in usageStatsList) {
            val packageName = stats.packageName ?: continue
            if (packageName.isEmpty() || ignoredPackages.contains(packageName)) continue
            if (ignoreSystemApps && packageName !in launchableApp) continue

            val duration = stats.totalTimeInForeground
            if (duration <= 0L) continue

            // INTERVAL_DAILY buckets are day-scoped, but a few ROMs report an interval crossing
            // midnight. Prefer the last timestamp and fall back to the first one.
            var timestamp = stats.lastTimeStamp
            if (timestamp < startTimestamp || timestamp > endTimestamp) {
                timestamp = stats.firstTimeStamp
            }
            if (timestamp < startTimestamp || timestamp > endTimestamp) continue

            val dayTimestamp = DateTimeUtils.truncateToDay(timestamp)
            val clampedDuration = duration.coerceAtMost(MILLIS_IN_A_DAY)
            val byDay = perPackageByDay.getOrPut(packageName) { mutableMapOf() }
            byDay[dayTimestamp] = (byDay[dayTimestamp] ?: 0L) + clampedDuration

            // getLaunchCount() is hidden on some ROMs; the repository merges event-based counts
            // for the days where the system reports nothing.
            val launchCount = readLaunchCount(stats)
            if (launchCount > 0) {
                val byDayClicks = launchCountByPackageByDay.getOrPut(packageName) { mutableMapOf() }
                byDayClicks[dayTimestamp] = (byDayClicks[dayTimestamp] ?: 0) + launchCount
            }
        }

        val dailyTotals = mutableMapOf<Long, Long>()
        perPackageByDay.values.forEach { byDay ->
            byDay.forEach { (dayTimestamp, duration) ->
                dailyTotals[dayTimestamp] = (dailyTotals[dayTimestamp] ?: 0L) + duration
            }
        }

        return MonthlyScanResult(
            perPackageByDay = perPackageByDay,
            dailyTotals = dailyTotals,
            launchCountByPackageByDay = launchCountByPackageByDay
        )
    }

    /**
     * UsageStats.getLaunchCount() is hidden in current SDK stubs and absent on some ROMs, so read
     * it reflectively and return 0 when it is unavailable. The repository merges the event-based
     * launch counts for the days the system does not report.
     */
    private fun readLaunchCount(stats: android.app.usage.UsageStats): Int {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.P) return 0
        return try {
            val method = stats.javaClass.getMethod("getLaunchCount")
            (method.invoke(stats) as? Int)?.coerceAtLeast(0) ?: 0
        } catch (_: Exception) {
            0
        }
    }

    // ---- Long-range aggregated stats (weekly / monthly / yearly buckets) ----

    data class AggregatedUsage(
        val packageName: String,
        val totalTimeInForeground: Long,
        val launchCount: Int
    )

    /**
     * Reads one of the system's long-lived aggregation buckets. Android keeps roughly 4 weeks of
     * WEEKLY, 6 months of MONTHLY and 2 years of YEARLY stats, while DAILY buckets expire after
     * about a week. Using the matching bucket for a long range therefore returns COMPLETE totals
     * without scanning any usage events (and without the stall of a long queryEvents pass).
     * A package can appear in several buckets, so every entry is accumulated.
     */
    fun queryAggregatedUsage(
        intervalType: Int,
        startTimestamp: Long,
        endTimestamp: Long,
        ignoreSystemApps: Boolean,
        ignoredPackages: List<String>
    ): Map<String, AggregatedUsage> {
        val result = mutableMapOf<String, AggregatedUsage>()
        val statsList = try {
            usageStatsManager.queryUsageStats(intervalType, startTimestamp, endTimestamp)
        } catch (e: Exception) {
            AppLog.e("UsageStatsTracker", "queryUsageStats(intervalType=$intervalType) failed", e)
            null
        }.orEmpty()
        AppLog.d(
            "UsageStatsTracker",
            "Aggregated bucket $intervalType returned ${statsList.size} entries for range $startTimestamp..$endTimestamp"
        )

        for (stats in statsList) {
            val packageName = stats.packageName ?: continue
            if (packageName.isEmpty() || ignoredPackages.contains(packageName)) continue
            if (ignoreSystemApps && packageName !in launchableApp) continue

            val duration = stats.totalTimeInForeground.coerceAtLeast(0L)
            val launches = readLaunchCount(stats)
            if (duration <= 0L && launches <= 0) continue

            val previous = result[packageName]
            result[packageName] = AggregatedUsage(
                packageName = packageName,
                totalTimeInForeground = (previous?.totalTimeInForeground ?: 0L) + duration,
                launchCount = (previous?.launchCount ?: 0) + launches
            )
        }
        return result
    }
}