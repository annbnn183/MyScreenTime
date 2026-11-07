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
        if (mode == AppOpsManager.MODE_ALLOWED) return true
        // Some ROMs (ColorOS / OxygenOS / MIUI) transiently report MODE_DEFAULT during the first
        // moments after process start even though usage access is granted. That made the app open
        // on the permission screen and wait for an ON_RESUME that had already fired. A real query
        // is the authoritative fallback: without the permission the system returns an empty list.
        return try {
            val now = System.currentTimeMillis()
            usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY, now - MILLIS_IN_A_DAY, now
            ).orEmpty().isNotEmpty()
        } catch (_: Throwable) {
            false
        }
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
        val queryStarted = android.os.SystemClock.uptimeMillis()
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
        AppLog.event(
            "DEBUG", "UsageStatsTracker", "QUERY",
            "INTERVAL_DAILY returned ${usageStatsList.size} raw buckets",
            "interval" to "INTERVAL_DAILY",
            "begin" to startTimestamp,
            "end" to endTimestamp,
            "rangeDays" to ((endTimestamp - startTimestamp) / MILLIS_IN_A_DAY),
            "rawBuckets" to usageStatsList.size,
            "ignoreSystemApps" to ignoreSystemApps,
            "ignoredPackages" to ignoredPackages.size,
            "costMs" to (android.os.SystemClock.uptimeMillis() - queryStarted),
            "thread" to Thread.currentThread().name
        )

        var keptEntries = 0
        var filteredZeroDuration = 0
        var filteredByPolicy = 0
        var filteredOutOfWindow = 0
        var unattributedTimestamps = 0
        var duplicateMaxMerged = 0
        val startDay = DateTimeUtils.truncateToDay(startTimestamp)
        val endDay = DateTimeUtils.truncateToDay(endTimestamp)
        val selfPackage = context.packageName

        for (stats in usageStatsList) {
            val packageName = stats.packageName ?: continue
            // Filter policy (v1.0.14): drop ONLY entries with no foreground time, our own
            // package, explicitly ignored apps and (optionally) non-launchable system apps.
            // We no longer require lastTimeUsed to sit inside the exact millisecond window:
            // many ROMs report DAILY buckets whose first/last timestamps fall outside the query
            // range even though the bucket belongs to a day inside it. That old rule silently
            // discarded ~71% of DAILY buckets (device log: 2364 -> 688).
            if (packageName.isEmpty() || packageName == selfPackage) { filteredByPolicy++; continue }
            if (ignoredPackages.contains(packageName)) { filteredByPolicy++; continue }
            if (ignoreSystemApps && packageName !in launchableApp) { filteredByPolicy++; continue }

            val duration = stats.totalTimeInForeground
            if (duration <= 0L) { filteredZeroDuration++; continue }

            // firstTimeStamp is the beginning of the time range represented by this daily
            // bucket. Use it for calendar-day attribution; lastTimeStamp is the range end and may
            // fall on the following midnight on some Android builds.
            val bucketBegin = stats.firstTimeStamp
            if (bucketBegin <= 0L) { unattributedTimestamps++; continue }
            val dayTimestamp = DateTimeUtils.truncateToDay(bucketBegin)
            if (dayTimestamp < startDay || dayTimestamp > endDay) { filteredOutOfWindow++; continue }

            val clampedDuration = duration.coerceAtMost(MILLIS_IN_A_DAY)
            val byDay = perPackageByDay.getOrPut(packageName) { mutableMapOf() }
            val previous = byDay[dayTimestamp]
            if (previous != null) duplicateMaxMerged++
            // INTERVAL_DAILY frequently returns DUPLICATE rows for the same (package, day).
            // Taking the max instead of summing keeps those duplicates from inflating totals.
            byDay[dayTimestamp] = maxOf(previous ?: 0L, clampedDuration)
            keptEntries++

            // getLaunchCount() is hidden on some ROMs; the repository merges event-based counts
            // for the days where the system reports nothing.
            val launchCount = readLaunchCount(stats)
            if (launchCount > 0) {
                val byDayClicks = launchCountByPackageByDay.getOrPut(packageName) { mutableMapOf() }
                byDayClicks[dayTimestamp] = maxOf(byDayClicks[dayTimestamp] ?: 0, launchCount)
            }
        }

        val dailyTotals = mutableMapOf<Long, Long>()
        perPackageByDay.values.forEach { byDay ->
            byDay.forEach { (dayTimestamp, duration) ->
                dailyTotals[dayTimestamp] = (dailyTotals[dayTimestamp] ?: 0L) + duration
            }
        }

        AppLog.event(
            "DEBUG", "UsageStatsTracker", "AGGREGATE_INTERMEDIATE",
            "Daily buckets reduced to per-package per-day durations (v1.0.14 filter policy)",
            "rawBuckets" to usageStatsList.size,
            "keptEntries" to keptEntries,
            "filteredEntries" to (usageStatsList.size - keptEntries).coerceAtLeast(0),
            "filteredZeroDuration" to filteredZeroDuration,
            "filteredByPolicy" to filteredByPolicy,
            "filteredOutOfWindow" to filteredOutOfWindow,
            "unattributedTimestamps" to unattributedTimestamps,
            "duplicateMaxMerged" to duplicateMaxMerged,
            "distinctPackages" to perPackageByDay.size,
            "daysWithData" to dailyTotals.size,
            "rawTotalMs" to dailyTotals.values.sum(),
            "retentionCapDays" to 7,
            "filterPolicy" to "drop ONLY duration<=0 | self | ignored | system; NO lastTimeUsed day-filter",
            "deduplication" to "max per (package, day)",
            "timezone" to java.util.TimeZone.getDefault().id,
            "dayBoundary" to "local midnight (DateTimeUtils.truncateToDay)"
        )

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

    /**
     * Counts foreground intervals for a single day, using exactly the same event rules as
     * [calculateUsageIntervalsByPackage]. Splitting a long backfill into one-day queries keeps
     * memory flat and prevents the process from being killed during a months-long scan.
     */
    suspend fun countLaunchesByDay(
        dayStart: Long,
        dayEnd: Long,
        ignoreSystemApps: Boolean,
        ignoredPackages: List<String>
    ): Map<String, Int> = try {
        calculateUsageIntervalsByPackage(dayStart, dayEnd, ignoreSystemApps, ignoredPackages)
            .mapValues { (_, intervals) -> intervals.size }
    } catch (e: Exception) {
        AppLog.e("UsageStatsTracker", "countLaunchesByDay failed for $dayStart", e)
        emptyMap()
    }

    // ---- Long-range aggregated stats (weekly / monthly / yearly buckets) ----

    data class AggregatedUsage(
        val packageName: String,
        val totalTimeInForeground: Long,
        val launchCount: Int
    )

    data class AggregatedUsageQuery(
        val apps: Map<String, AggregatedUsage>,
        val coveredDays: Int,
        val rawEntries: Int,
        val keptBuckets: Int,
        val droppedPartialBuckets: Int,
        val droppedOutsideWindow: Int,
        val zeroDurationBuckets: Int,
        val invalidTimestampBuckets: Int
    )

    /**
     * Reads long-lived system aggregates. UsageStatsManager may expand a query to whole calendar
     * intervals; a bucket that crosses either requested boundary cannot be split reliably, so it
     * is excluded rather than crediting out-of-range time to this ranking.
     */
    fun queryAggregatedUsage(
        intervalType: Int,
        startTimestamp: Long,
        endTimestamp: Long,
        ignoreSystemApps: Boolean,
        ignoredPackages: List<String>
    ): AggregatedUsageQuery {
        val queryStarted = android.os.SystemClock.uptimeMillis()
        val result = mutableMapOf<String, AggregatedUsage>()
        val coveredRanges = mutableSetOf<Pair<Long, Long>>()
        val statsList = try {
            usageStatsManager.queryUsageStats(intervalType, startTimestamp, endTimestamp)
        } catch (e: Exception) {
            AppLog.e("UsageStatsTracker", "queryUsageStats(intervalType=$intervalType) failed", e)
            null
        }.orEmpty()
        var keptBuckets = 0
        var droppedPartialBuckets = 0
        var droppedOutsideWindow = 0
        var zeroDurationBuckets = 0
        var invalidTimestampBuckets = 0

        for (stats in statsList) {
            val bucketBegin = stats.firstTimeStamp
            val bucketEnd = stats.lastTimeStamp
            if (bucketBegin <= 0L || bucketEnd <= bucketBegin) {
                invalidTimestampBuckets++
                continue
            }
            if (bucketEnd <= startTimestamp || bucketBegin >= endTimestamp) {
                droppedOutsideWindow++
                continue
            }
            if (bucketBegin < startTimestamp || bucketEnd > endTimestamp) {
                droppedPartialBuckets++
                continue
            }

            val packageName = stats.packageName ?: continue
            if (packageName.isEmpty() || packageName == context.packageName ||
                ignoredPackages.contains(packageName)
            ) continue
            if (ignoreSystemApps && packageName !in launchableApp) continue

            val duration = stats.totalTimeInForeground.coerceAtLeast(0L)
            if (duration <= 0L) {
                zeroDurationBuckets++
                continue
            }
            val launches = readLaunchCount(stats)
            coveredRanges.add(bucketBegin to bucketEnd)
            val previous = result[packageName]
            result[packageName] = AggregatedUsage(
                packageName = packageName,
                totalTimeInForeground = (previous?.totalTimeInForeground ?: 0L) + duration,
                launchCount = (previous?.launchCount ?: 0) + launches
            )
            keptBuckets++
        }

        val query = AggregatedUsageQuery(
            apps = result,
            coveredDays = coveredRanges.sumOf { (begin, end) ->
                ((end - begin) / MILLIS_IN_A_DAY).toInt().coerceAtLeast(0)
            },
            rawEntries = statsList.size,
            keptBuckets = keptBuckets,
            droppedPartialBuckets = droppedPartialBuckets,
            droppedOutsideWindow = droppedOutsideWindow,
            zeroDurationBuckets = zeroDurationBuckets,
            invalidTimestampBuckets = invalidTimestampBuckets
        )
        AppLog.event(
            "DEBUG", "UsageStatsTracker", "AGGREGATE_INTERMEDIATE",
            "Long-range aggregate filtered to complete in-window buckets",
            "interval" to intervalType,
            "intervalName" to intervalName(intervalType),
            "windowBegin" to startTimestamp,
            "windowEnd" to endTimestamp,
            "rawEntries" to query.rawEntries,
            "keptBuckets" to query.keptBuckets,
            "coveredDays" to query.coveredDays,
            "droppedPartialBuckets" to query.droppedPartialBuckets,
            "droppedOutsideWindow" to query.droppedOutsideWindow,
            "zeroDurationBuckets" to query.zeroDurationBuckets,
            "invalidTimestampBuckets" to query.invalidTimestampBuckets,
            "distinctPackages" to result.size,
            "totalForegroundMs" to result.values.sumOf { it.totalTimeInForeground },
            "costMs" to (android.os.SystemClock.uptimeMillis() - queryStarted),
            "thread" to Thread.currentThread().name
        )
        return query
    }

    // ---- Segmented month totals (v1.0.14, replaces INTERVAL_BEST) -------------------------

    data class SegmentEvidence(
        val bucketName: String,
        val windowBegin: Long,
        val windowEnd: Long,
        val windowDays: Int,
        val rawEntries: Int,
        val keptBuckets: Int,
        val droppedOutsideWindow: Int,
        val droppedPartialBucket: Int,
        val zeroDurationBuckets: Int,
        val invalidTimestampBuckets: Int,
        val contributedMs: Long,
        val retentionCapDays: Int,
        val reflectedLaunches: Int
    )

    data class SegmentedMonthTotals(
        val perPackageMs: Map<String, Long>,
        val totalMs: Long,
        val segments: List<SegmentEvidence>,
        /** True only when at least one non-daily aggregate bucket contributes to the total. */
        val isEstimated: Boolean = false
    )

    /**
     * Builds an EXPLAINABLE total for the requested window by concatenating three DISJOINT time
     * segments, each served by the system bucket whose retention actually covers it:
     *   - last 7 days   -> INTERVAL_DAILY   (per-day, precise, ~7 day retention)
     *   - day 8 .. 28   -> INTERVAL_WEEKLY  (~4 week retention)
     *   - day 29+       -> INTERVAL_MONTHLY (~6 month retention)
     *
     * INTERVAL_BEST is intentionally NOT used: it silently picks whichever bucket the system
     * considers "best" (device logs: BEST returned exactly the YEARLY figures), so a 30-day window
     * would be reported with a one-year granularity. Each bucket is credited to exactly one segment
     * only when its full represented time range fits inside one segment. Partial buckets are
     * rejected because their aggregate duration cannot be split across segment boundaries.
     */
    fun querySegmentedMonthTotals(
        startTimestamp: Long,
        endTimestamp: Long,
        ignoreSystemApps: Boolean,
        ignoredPackages: List<String>
    ): SegmentedMonthTotals {
        val day = MILLIS_IN_A_DAY
        val requestedDays = ((endTimestamp - startTimestamp) / day).toInt().coerceAtLeast(1)

        val dailyStart = maxOf(startTimestamp, endTimestamp - 7L * day)
        val weeklyStart = maxOf(startTimestamp, endTimestamp - 28L * day)
        // Disjoint windows: [start, weeklyStart) MONTHLY, [weeklyStart, dailyStart) WEEKLY,
        // [dailyStart, end] DAILY. Their union is exactly the requested [start, end].
        val specs = listOf(
            Triple(UsageStatsManager.INTERVAL_DAILY, dailyStart to endTimestamp, 7),
            Triple(UsageStatsManager.INTERVAL_WEEKLY, weeklyStart to dailyStart, 28),
            Triple(UsageStatsManager.INTERVAL_MONTHLY, startTimestamp to weeklyStart, 180)
        ).filter { (_, window, _) -> window.second > window.first }

        val perPackageMs = mutableMapOf<String, Long>()
        val evidences = mutableListOf<SegmentEvidence>()
        for ((interval, window, capDays) in specs) {
            val (segStart, segEnd) = window
            val ev = queryBucketWithinSegment(
                intervalType = interval,
                segStart = segStart,
                segEnd = segEnd,
                retentionCapDays = capDays,
                ignoreSystemApps = ignoreSystemApps,
                ignoredPackages = ignoredPackages,
                sink = perPackageMs
            )
            evidences.add(ev)
            AppLog.event(
                "DEBUG", "UsageStatsTracker", "SEGMENT",
                "${ev.bucketName} contributed ${ev.contributedMs} ms over ${ev.windowDays}d",
                "bucket" to ev.bucketName,
                "windowBegin" to ev.windowBegin,
                "windowEnd" to ev.windowEnd,
                "windowDays" to ev.windowDays,
                "rawEntries" to ev.rawEntries,
                "keptBuckets" to ev.keptBuckets,
                "droppedOutsideWindow" to ev.droppedOutsideWindow,
                "droppedPartialBucket" to ev.droppedPartialBucket,
                "zeroDurationBuckets" to ev.zeroDurationBuckets,
                "invalidTimestampBuckets" to ev.invalidTimestampBuckets,
                "contributedMs" to ev.contributedMs,
                "retentionCapDays" to ev.retentionCapDays,
                "reflectedLaunches" to ev.reflectedLaunches,
                "note" to "disjoint segments; INTERVAL_BEST is never used"
            )
        }
        val totalMs = perPackageMs.values.sum()
        val isEstimated = evidences.any {
            it.bucketName != "INTERVAL_DAILY" && it.keptBuckets > 0
        }
        AppLog.event(
            "DEBUG", "UsageStatsTracker", "SEGMENT_TOTALS",
            "Segmented ${requestedDays}d total = $totalMs ms from ${evidences.size} disjoint buckets",
            "requestedDays" to requestedDays,
            "totalMs" to totalMs,
            "distinctPackages" to perPackageMs.size,
            "segments" to evidences.joinToString("|") { "${it.bucketName}:${it.contributedMs}" },
            "isEstimated" to isEstimated,
            "bestBucketUsed" to false,
            "launchSource" to "queryEvents day-by-day permanent store (system buckets expose no launch counts)"
        )
        return SegmentedMonthTotals(perPackageMs, totalMs, evidences, isEstimated)
    }

    /** Reads one bucket for a single disjoint segment and folds each entry into [sink]. */
    private fun queryBucketWithinSegment(
        intervalType: Int,
        segStart: Long,
        segEnd: Long,
        retentionCapDays: Int,
        ignoreSystemApps: Boolean,
        ignoredPackages: List<String>,
        sink: MutableMap<String, Long>
    ): SegmentEvidence {
        val statsList = try {
            usageStatsManager.queryUsageStats(intervalType, segStart, segEnd)
        } catch (e: Exception) {
            AppLog.e("UsageStatsTracker", "queryUsageStats(segment $intervalType) failed", e)
            null
        }.orEmpty()

        val selfPackage = context.packageName
        var keptBuckets = 0
        var droppedOutsideWindow = 0
        var droppedPartialBucket = 0
        var zeroDurationBuckets = 0
        var invalidTimestampBuckets = 0
        var contributedMs = 0L
        var reflectedLaunches = 0
        for (stats in statsList) {
            val packageName = stats.packageName ?: continue
            if (packageName.isEmpty() || packageName == selfPackage) continue
            if (ignoredPackages.contains(packageName)) continue
            if (ignoreSystemApps && packageName !in launchableApp) continue
            val duration = stats.totalTimeInForeground.coerceAtLeast(0L)
            if (duration <= 0L) {
                zeroDurationBuckets++
                continue
            }

            // firstTimeStamp / lastTimeStamp describe the time range represented by this
            // UsageStats object, not the app's last foreground event. Android may expand
            // queryUsageStats() to whole weekly/monthly intervals. Never assign a partial bucket
            // to a disjoint segment: its aggregate duration cannot be prorated safely.
            val bucketBegin = stats.firstTimeStamp
            val bucketEnd = stats.lastTimeStamp
            if (bucketBegin <= 0L || bucketEnd <= bucketBegin) {
                invalidTimestampBuckets++
                continue
            }
            if (bucketEnd <= segStart || bucketBegin >= segEnd) {
                droppedOutsideWindow++
                continue
            }
            if (bucketBegin < segStart || bucketEnd > segEnd) {
                droppedPartialBucket++
                continue
            }
            sink[packageName] = (sink[packageName] ?: 0L) + duration
            contributedMs += duration
            keptBuckets++
            // getAppLaunchCount() cross-check: on most ROMs the long-lived buckets report 0,
            // so the authoritative launch counts stay in the permanent per-day event store.
            reflectedLaunches += readLaunchCount(stats)
        }
        return SegmentEvidence(
            bucketName = intervalName(intervalType),
            windowBegin = segStart,
            windowEnd = segEnd,
            windowDays = ((segEnd - segStart) / MILLIS_IN_A_DAY).toInt(),
            rawEntries = statsList.size,
            keptBuckets = keptBuckets,
            droppedOutsideWindow = droppedOutsideWindow,
            droppedPartialBucket = droppedPartialBucket,
            zeroDurationBuckets = zeroDurationBuckets,
            invalidTimestampBuckets = invalidTimestampBuckets,
            contributedMs = contributedMs,
            retentionCapDays = retentionCapDays,
            reflectedLaunches = reflectedLaunches
        )
    }

    private fun intervalName(intervalType: Int): String = when (intervalType) {
        UsageStatsManager.INTERVAL_DAILY -> "INTERVAL_DAILY"
        UsageStatsManager.INTERVAL_WEEKLY -> "INTERVAL_WEEKLY"
        UsageStatsManager.INTERVAL_MONTHLY -> "INTERVAL_MONTHLY"
        UsageStatsManager.INTERVAL_YEARLY -> "INTERVAL_YEARLY"
        UsageStatsManager.INTERVAL_BEST -> "INTERVAL_BEST"
        else -> "INTERVAL_$intervalType"
    }
}