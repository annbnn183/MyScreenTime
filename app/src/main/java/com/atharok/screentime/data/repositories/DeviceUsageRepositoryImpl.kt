package com.atharok.screentime.data.repositories

import android.app.usage.UsageStatsManager
import android.content.Context
import com.atharok.screentime.R
import com.atharok.screentime.common.extensions.getApplicationName
import com.atharok.screentime.common.utils.AppLog
import com.atharok.screentime.common.utils.DateTimeUtils
import com.atharok.screentime.common.utils.MILLIS_IN_A_DAY
import com.atharok.screentime.data.dataStore.LaunchHistoryStore
import com.atharok.screentime.data.statsTracker.UsageStatsTracker
import com.atharok.screentime.domain.entities.usage.AppUsageRange
import com.atharok.screentime.domain.repositories.IntervalTotal
import com.atharok.screentime.domain.entities.usage.DailyAppUsage
import com.atharok.screentime.domain.entities.usage.DailyDeviceUsage
import com.atharok.screentime.domain.entities.usage.DailyDuration
import com.atharok.screentime.domain.entities.usage.MonthlyAppUsage
import com.atharok.screentime.domain.entities.usage.MonthlyDeviceUsage
import com.atharok.screentime.domain.entities.usage.RankedApp
import com.atharok.screentime.domain.entities.usage.WeeklyAppUsage
import com.atharok.screentime.domain.entities.usage.WeeklyDeviceUsage
import com.atharok.screentime.domain.repositories.DeviceUsageRepository

class DeviceUsageRepositoryImpl(
    private val context: Context,
    private val tracker: UsageStatsTracker
): DeviceUsageRepository {

    private companion object {
        /** 系统事件明细的实际保留窗口；更早的日期不再发起逐日查询。 */
        const val EVENT_DETAIL_HORIZON_DAYS = 21

        /** 补算时每累积多少天落盘一次。 */
        const val BACKFILL_BATCH_DAYS = 8

        /** 进度回调的最小步长（天），避免每补一天就刷新一次界面。 */
        const val BACKFILL_PROGRESS_STEP = 8
    }

    // Permission

    override fun hasAppUsagePermission(): Boolean = tracker.hasAppUsagePermission()

    // Calculate

    private var deviceUsageCacheByDayAndHour: Map<String, Map<Long, LongArray>>? = null
    private var totalDeviceUsageCacheByDayAndHour: Map<Long, LongArray> = mapOf()
    /** Number of app openings per package, split by day, derived from the foreground intervals. */
    private var launchCountCacheByPackageByDay: Map<String, Map<Long, Int>> = mapOf()
    private var dateOfLastRefresh: String = ""
    /** 永久保存逐日点击量；长周期点击量只能靠它累加。 */
    private val launchHistory = LaunchHistoryStore(context)

    override suspend fun calculateDeviceUsage(
        timeInterval: Pair<Long, Long>,
        ignoreSystemApps: Boolean,
        ignoredPackages: List<String>
    ) {
        val usageIntervalsByPackage: Map<String, List<Pair<Long, Long>>> = tracker
            .calculateUsageIntervalsByPackage(
                startTimestamp = timeInterval.first,
                endTimestamp = timeInterval.second,
                ignoreSystemApps = ignoreSystemApps,
                ignoredPackages = ignoredPackages
            )

        deviceUsageCacheByDayAndHour = tracker.getDurationPerPackageByDayAndHour(
            startTimestamp = timeInterval.first,
            endTimestamp = timeInterval.second,
            usageIntervalsByPackage = usageIntervalsByPackage
        )

        totalDeviceUsageCacheByDayAndHour = tracker.getTotalDurationByDayAndHour(
            startTimestamp = timeInterval.first,
            endTimestamp = timeInterval.second,
            usageIntervalsByPackage = usageIntervalsByPackage
        )

        // Each foreground interval is one "open" of the app, so counting intervals per day gives
        // the click/launch counts with zero extra usage-events queries.
        launchCountCacheByPackageByDay = usageIntervalsByPackage.mapValues { (_, intervals) ->
            intervals.groupingBy { DateTimeUtils.truncateToDay(it.first) }.eachCount()
        }

        dateOfLastRefresh = DateTimeUtils.formatToFullDateTime(timeInterval.second)

        // 事件明细覆盖的每一天都写进永久存档（包括 0 次的天），长周期点击量靠它累加。
        val startDay = DateTimeUtils.truncateToDay(timeInterval.first)
        val endDay = DateTimeUtils.truncateToDay(timeInterval.second)
        var cursor = startDay
        while (cursor in startDay..endDay) {
            val counts = launchCountCacheByPackageByDay.mapNotNull { (pkg, byDay) ->
                byDay[cursor]?.let { pkg to it }
            }.toMap()
            launchHistory.mergeDay(cursor, counts)
            cursor = DateTimeUtils.addDaysToTimestamp(cursor, 1)
        }
    }

    override suspend fun getWeekDeviceUsage(
        timeInterval: Pair<Long, Long>
    ): WeeklyDeviceUsage {

        val deviceUsageCacheByDayAndHourNotNull = deviceUsageCacheByDayAndHour
            ?: throw Exception("DeviceUsageRepositoryImpl.calculateDeviceUsage() not called.")

        val startDay = DateTimeUtils.truncateToDay(timeInterval.first)
        val launchCountByPackage: Map<String, Int> = launchCountCacheByPackageByDay.mapValues { (_, byDay) ->
            byDay.filterKeys { day -> day >= startDay && day <= timeInterval.second }.values.sum()
        }

        return WeeklyDeviceUsage(
            usages = deviceUsageCacheByDayAndHourNotNull.mapValues { (key: String, value: Map<Long, LongArray>) ->
                WeeklyAppUsage(
                    packageName = key,
                    appName = context.packageManager.getApplicationName(key) ?: "",
                    appIcon = null,
                    durations = value
                        .filterKeys { timestamp ->
                            timestamp in timeInterval.first..timeInterval.second
                        },
                    launchCount = launchCountByPackage[key] ?: 0
                )
            },
            timeInterval = timeInterval,
            dateOfLastRefresh = dateOfLastRefresh,
            totalDeviceUsageByDayAndHour = totalDeviceUsageCacheByDayAndHour.filterKeys { timestamp ->
                timestamp in timeInterval.first..timeInterval.second
            },
            totalLaunchCount = launchCountByPackage.values.sum()
        )
    }

    override suspend fun getDayDeviceUsage(
        timeInterval: Pair<Long, Long>,
        dayTimestamp: Long
    ): DailyDeviceUsage {

        val deviceUsageCacheByDayAndHourNotNull = deviceUsageCacheByDayAndHour
            ?: throw Exception("DeviceUsageRepositoryImpl.calculateDeviceUsage() not called.")

        val launchCountByPackage: Map<String, Int> = launchCountCacheByPackageByDay.mapValues { (_, byDay) ->
            byDay[dayTimestamp] ?: 0
        }

        return DailyDeviceUsage(
            usages = deviceUsageCacheByDayAndHourNotNull.mapValues { (key: String, value: Map<Long, LongArray>) ->
                DailyAppUsage(
                    packageName = key,
                    appName = context.packageManager.getApplicationName(key) ?: "",
                    appIcon = null,
                    durations = value[dayTimestamp] ?: LongArray(24),
                    launchCount = launchCountByPackage[key] ?: 0
                )
            },
            timeInterval = timeInterval,
            dateOfLastRefresh = dateOfLastRefresh,
            totalDeviceUsageByHour = totalDeviceUsageCacheByDayAndHour[dayTimestamp] ?: LongArray(24),
            totalLaunchCount = launchCountByPackage.values.sum()
        )
    }

    // ---- Month ----

    override suspend fun getMonthDeviceUsage(
        timeInterval: Pair<Long, Long>,
        dayTimestamps: List<Long>,
        goalMillis: Long,
        ignoreSystemApps: Boolean,
        ignoredPackages: List<String>
    ): MonthlyDeviceUsage {
        // Primary source: the system's per-day pre-aggregated stats (fast, event-count independent).
        val scan = tracker.queryMonthlyUsageByDay(
            startTimestamp = timeInterval.first,
            endTimestamp = timeInterval.second,
            ignoreSystemApps = ignoreSystemApps,
            ignoredPackages = ignoredPackages
        )

        // Secondary source: the event-based cache covers the last 10 days with higher precision.
        // Merge both by keeping the larger value for each (package, day) pair, so they complement
        // each other instead of being counted twice.
        val eventsPerPackage: Map<String, Map<Long, Long>> = deviceUsageCacheByDayAndHour
            ?.mapValues { (_, byDayAndHour) ->
                byDayAndHour.mapValues { (_, hours) -> hours.sum() }
            } ?: emptyMap()
        val eventsDailyTotals: Map<Long, Long> = totalDeviceUsageCacheByDayAndHour
            .mapValues { (_, hours) -> hours.sum() }

        val mergedPerPackage = mutableMapOf<String, MutableMap<Long, Long>>()

        fun merge(source: Map<String, Map<Long, Long>>) {
            source.forEach { (packageName: String, byDay: Map<Long, Long>) ->
                val target = mergedPerPackage.getOrPut(packageName) { mutableMapOf() }
                byDay.forEach { (day: Long, duration: Long) ->
                    target[day] = maxOf(target[day] ?: 0L, duration)
                }
            }
        }
        merge(scan.perPackageByDay)
        merge(eventsPerPackage)

        val dailyTotals: List<DailyDuration> = dayTimestamps.map { day: Long ->
            DailyDuration(
                dayTimestamp = day,
                duration = maxOf(
                    scan.dailyTotals[day] ?: 0L,
                    eventsDailyTotals[day] ?: 0L
                )
            )
        }

        // Launch counts: system pre-aggregated counts (API 28+) merged with the event-based
        // counts for the last 10 days. Take the larger value per (package, day) so a ROM that
        // reports both does not double-count.
        val mergedLaunches = mutableMapOf<String, MutableMap<Long, Int>>()
        fun mergeLaunches(source: Map<String, Map<Long, Int>>) {
            source.forEach { (packageName: String, byDay: Map<Long, Int>) ->
                val target = mergedLaunches.getOrPut(packageName) { mutableMapOf() }
                byDay.forEach { (day: Long, count: Int) ->
                    target[day] = maxOf(target[day] ?: 0, count)
                }
            }
        }
        mergeLaunches(scan.launchCountByPackageByDay)
        mergeLaunches(launchCountCacheByPackageByDay)

        // DAILY 桶覆盖的天数通常比事件明细更长，一并写进永久存档。
        mergedLaunches.values.flatMap { it.keys }.distinct().forEach { day ->
            val counts = mergedLaunches.mapNotNull { (pkg, byDay) ->
                byDay[day]?.let { pkg to it }
            }.toMap()
            launchHistory.mergeDay(day, counts)
        }

        val launchCountByPackage: Map<String, Int> = mergedLaunches.mapValues { (_, byDay) -> byDay.values.sum() }
        val totalLaunchCount = launchCountByPackage.values.sum()
        // When the system provides no launch counts at all, only the last 10 days are covered.
        val systemLaunchTotal: Int = scan.launchCountByPackageByDay.values.flatMap { it.values }.sum()
        val isClickDataLimited = systemLaunchTotal <= 0

        // ---- Data completeness (v1.0.14) --------------------------------------------------
        // Strict rule: fewer days with real per-day data than requested is ALWAYS limited.
        // Separate the two causes so the UI can explain "10 days of data" honestly:
        //   systemRetentionDays -> beyond the DAILY bucket cap (~7 days) Android returns nothing
        //   filteredDays        -> inside that cap but still missing (filtered / no usage / bad ts)
        val requestedDays = dayTimestamps.size
        val daysWithData = dailyTotals.count { it.duration > 0L }
        val dailyRetentionCapDays = 7
        val systemRetentionDays = (requestedDays - dailyRetentionCapDays).coerceAtLeast(0)
        val coverableByDaily = minOf(requestedDays, dailyRetentionCapDays)
        val filteredDays = (coverableByDaily - minOf(daysWithData, coverableByDaily)).coerceAtLeast(0)
        val isDataLimited = daysWithData < requestedDays
        val estimatedDays = (requestedDays - daysWithData).coerceAtLeast(0)

        // ---- Long-range aggregates -------------------------------------------------------
        // Android keeps ~4 weeks of WEEKLY, ~6 months of MONTHLY and ~2 years of YEARLY
        // pre-aggregated stats, while DAILY buckets expire after about a week. Reading the
        // matching bucket gives COMPLETE totals for a long range without scanning events.
        val dayMillis = 24L * 60L * 60L * 1000L
        val nowMillis = timeInterval.second
        val rangeSpecs = listOf(
            Triple(R.string.range_4_weeks, 28, UsageStatsManager.INTERVAL_WEEKLY),
            Triple(R.string.range_half_year, 182, UsageStatsManager.INTERVAL_MONTHLY),
            Triple(R.string.range_year, 365, UsageStatsManager.INTERVAL_YEARLY)
        )
        val rangeRankings: List<AppUsageRange> = rangeSpecs.map { (labelRes, days, interval) ->
            val rangeStart = nowMillis - days.toLong() * dayMillis
            val aggregated = tracker.queryAggregatedUsage(
                intervalType = interval,
                startTimestamp = rangeStart,
                endTimestamp = nowMillis,
                ignoreSystemApps = ignoreSystemApps,
                ignoredPackages = ignoredPackages
            )
            // 系统长期桶不保存启动次数，逐日存档才是 4 周 / 半年 / 一年点击量的真实来源。
            val history = launchHistory.sumRange(rangeStart, nowMillis)
            val packageNames = (aggregated.apps.keys + history.keys).toSet()
            val rankedApps = packageNames.map { packageName ->
                val item = aggregated.apps[packageName]
                RankedApp(
                    packageName = packageName,
                    appName = context.packageManager.getApplicationName(packageName) ?: packageName,
                    // Keep only the package name here. Holding a full-size Drawable for every
                    // app in all 3 long ranges used to keep ~800 large objects alive and required
                    // hundreds of binder calls on every refresh. Icons are now resolved lazily
                    // and downscaled by AppIconCache only for the cells actually on screen.
                    appIcon = null,
                    duration = item?.totalTimeInForeground ?: 0L,
                    launchCount = maxOf(item?.launchCount ?: 0, history[packageName] ?: 0)
                )
            }
                .filter { it.duration > 0L || it.launchCount > 0 }
                .sortedByDescending { it.duration }
            AppUsageRange(
                labelRes = labelRes,
                numberOfDays = days,
                apps = rankedApps,
                totalLaunchCount = rankedApps.sumOf { it.launchCount },
                totalTimeMillis = rankedApps.sumOf { it.duration },
                // Older v1.0.17 backfills marked days outside Android's event retention as
                // scanned empty days. Do not present those placeholders as verified records.
                clickDaysRecorded = launchHistory.countScannedInRange(
                    maxOf(rangeStart, DateTimeUtils.addDaysToTimestamp(DateTimeUtils.truncateToDay(nowMillis), -EVENT_DETAIL_HORIZON_DAYS)),
                    nowMillis
                ),
                clickSinceDay = launchHistory.earliestScannedDay()?.takeIf {
                    it >= DateTimeUtils.addDaysToTimestamp(DateTimeUtils.truncateToDay(nowMillis), -EVENT_DETAIL_HORIZON_DAYS)
                },
                // Android keeps ~4 weeks of WEEKLY, ~6 months of MONTHLY and ~2 years of YEARLY.
                // surfacing the cap lets the UI explain why very old days cannot be exact.
                retentionCapDays = when (interval) {
                    UsageStatsManager.INTERVAL_WEEKLY -> 28
                    UsageStatsManager.INTERVAL_MONTHLY -> 182
                    else -> 730
                },
                rawBucketCount = aggregated.rawEntries,
                keptBucketCount = aggregated.keptBuckets,
                droppedPartialBucketCount = aggregated.droppedPartialBuckets,
                droppedOutsideWindowBucketCount = aggregated.droppedOutsideWindow,
                zeroDurationBucketCount = aggregated.zeroDurationBuckets,
                invalidTimestampBucketCount = aggregated.invalidTimestampBuckets
            )
        }

        AppLog.event(
            "DEBUG", "DeviceUsageRepository", "AGGREGATE_INTERMEDIATE",
            "Long-range rankings built from system buckets",
            "ranges" to rangeRankings.size,
            "apps4w" to rangeRankings[0].apps.size,
            "appsHalfYear" to rangeRankings[1].apps.size,
            "appsYear" to rangeRankings[2].apps.size,
            "totalMs4w" to rangeRankings[0].apps.sumOf { it.duration },
            "rawBuckets4w" to rangeRankings[0].rawBucketCount,
            "keptBuckets4w" to rangeRankings[0].keptBucketCount,
            "partialBuckets4w" to rangeRankings[0].droppedPartialBucketCount,
            "timezone" to java.util.TimeZone.getDefault().id
        )

        // ---- 30-day headline (v1.0.14): segmented, NO INTERVAL_BEST -----------------------
        // INTERVAL_BEST silently picks a bucket (device logs: it returned the YEARLY figures for
        // a 30-day window), so the old headline was reported at a one-year granularity. Instead,
        // concatenate three disjoint segments, each served by the bucket whose retention covers it.
        val segmented = tracker.querySegmentedMonthTotals(
            startTimestamp = timeInterval.first,
            endTimestamp = timeInterval.second,
            ignoreSystemApps = ignoreSystemApps,
            ignoredPackages = ignoredPackages
        )
        val aggregatedDeviceTotal: Long = segmented.totalMs
        AppLog.event(
            "DEBUG", "DeviceUsageRepository", "DATA_COMPLETENESS",
            "30-day window explained: real days vs retention cap vs filtering",
            "dailyBucketTotalMs" to dailyTotals.sumOf { it.duration },
            "segmentedTotalMs" to aggregatedDeviceTotal,
            "headlineSource" to "SEGMENTED DAILY(7d)+WEEKLY(8-28d)+MONTHLY(29d+); INTERVAL_BEST removed",
            "daysWithData" to daysWithData,
            "requestedDays" to requestedDays,
            "dailyRetentionCapDays" to dailyRetentionCapDays,
            "systemRetentionDays" to systemRetentionDays,
            "filteredDays" to filteredDays,
            "estimatedDays" to estimatedDays,
            "isDataLimited" to isDataLimited,
            "isEstimated" to segmented.isEstimated,
            "eventLaunchTotal" to totalLaunchCount,
            "launchSource" to "queryEvents day-by-day permanent store (system buckets report 0 launches)",
            "timezone" to java.util.TimeZone.getDefault().id,
            "rangeBegin" to timeInterval.first,
            "rangeEnd" to timeInterval.second
        )

        val usages: Map<String, MonthlyAppUsage> = mergedPerPackage
            .mapValues { (packageName: String, byDay: Map<Long, Long>) ->
                MonthlyAppUsage(
                    packageName = packageName,
                    appName = context.packageManager.getApplicationName(packageName) ?: packageName,
                    appIcon = null,
                    dayTimestamps = dayTimestamps,
                    durations = byDay,
                    // Launch counts come from the permanent per-day event store; system buckets
                    // expose 0, so there is nothing to merge from the segmented totals.
                    launchCount = launchCountByPackage[packageName] ?: 0,
                    aggregatedDuration = segmented.perPackageMs[packageName] ?: 0L
                )
            }

        // 24 小时时段分布：把事件明细仍保留的每一天按小时累加。
        val hourlyTotals = LongArray(24)
        totalDeviceUsageCacheByDayAndHour.forEach { (day, hours) ->
            if (day in timeInterval.first..timeInterval.second) {
                hours.indices.forEach { index -> hourlyTotals[index] += hours[index] }
            }
        }

        return MonthlyDeviceUsage(
            usages = usages,
            timeInterval = timeInterval,
            dateOfLastRefresh = DateTimeUtils.formatToFullDateTime(timeInterval.second),
            dailyTotals = dailyTotals,
            goalMillis = goalMillis,
            isDataLimited = isDataLimited,
            totalLaunchCount = totalLaunchCount,
            isClickDataLimited = isClickDataLimited,
            rangeRankings = rangeRankings,
            aggregatedTotalTimeMillis = aggregatedDeviceTotal,
            requestedDays = requestedDays,
            daysWithData = daysWithData,
            systemRetentionDays = systemRetentionDays,
            filteredDays = filteredDays,
            estimatedDays = estimatedDays,
            isEstimated = segmented.isEstimated,
            hourlyTotals = hourlyTotals
        )
    }

    override suspend fun backfillLaunchHistory(
        maxDays: Int,
        ignoreSystemApps: Boolean,
        ignoredPackages: List<String>,
        onProgress: ((done: Int, total: Int) -> Unit)?
    ): Int {
        val today = DateTimeUtils.truncateToDay(System.currentTimeMillis())
        // Android's event history has a finite retention window. Only scan dates inside that
        // window; an empty result outside it is ambiguous (no usage OR expired history) and must
        // never be persisted as a verified zero-launch day.
        val eventHorizonStart = DateTimeUtils.addDaysToTimestamp(today, -EVENT_DETAIL_HORIZON_DAYS)
        val oldestAllowed = eventHorizonStart
        // Start near today and skip dates already scanned. This also repairs older installations
        // whose previous backfill marked hundreds of expired dates as scanned empty days.
        var day = DateTimeUtils.addDaysToTimestamp(today, -1)
        var done = 0
        var pending = LinkedHashMap<Long, Map<String, Int>>()
        var lastReported = 0
        while (done < maxDays && day >= oldestAllowed) {
            if (!launchHistory.isScanned(day)) {
                val counts = if (day >= eventHorizonStart) {
                    tracker.countLaunchesByDay(
                        dayStart = day,
                        dayEnd = DateTimeUtils.addDaysToTimestamp(day, 1),
                        ignoreSystemApps = ignoreSystemApps,
                        ignoredPackages = ignoredPackages
                    )
                } else {
                    // Defensive: this branch should be unreachable because the loop is bounded
                    // by eventHorizonStart. Never persist an unavailable historical day as zero.
                    break
                }
                pending[day] = counts
                // 每 8 天落盘一次：既控制单次写入量，又保证进程被杀时最多丢一批。
                if (pending.size >= BACKFILL_BATCH_DAYS) {
                    launchHistory.mergeDays(pending)
                    pending = LinkedHashMap()
                    kotlinx.coroutines.yield()
                }
                if (done - lastReported >= BACKFILL_PROGRESS_STEP) {
                    lastReported = done
                    onProgress?.invoke(done, maxDays)
                }
            }
            day = DateTimeUtils.addDaysToTimestamp(day, -1)
            done++
        }
        if (pending.isNotEmpty()) launchHistory.mergeDays(pending)
        onProgress?.invoke(done, maxDays)
        AppLog.i(
            "DeviceUsageRepository",
            "Backfilled $done event-retained days of launch history (stored days: ${launchHistory.scannedDays()}, verified recent days: ${launchHistory.countScannedInRange(eventHorizonStart, today)})"
        )
        return done
    }

    override suspend fun getIntervalTotal(
        startTimestamp: Long,
        endTimestamp: Long,
        ignoreSystemApps: Boolean,
        ignoredPackages: List<String>
    ): IntervalTotal {
        // 按窗口长度选择能完整覆盖该区间的系统聚合桶：
        // 周级窗口用 WEEKLY（约 4 周保留），月级用 MONTHLY（约 6 月保留），更长用 YEARLY（约 2 年保留）。
        val windowDays = ((endTimestamp - startTimestamp) / MILLIS_IN_A_DAY).toInt()
        val interval = when {
            windowDays <= 9 -> UsageStatsManager.INTERVAL_WEEKLY
            windowDays <= 33 -> UsageStatsManager.INTERVAL_MONTHLY
            else -> UsageStatsManager.INTERVAL_YEARLY
        }
        val aggregated = tracker.queryAggregatedUsage(
            intervalType = interval,
            startTimestamp = startTimestamp,
            endTimestamp = endTimestamp,
            ignoreSystemApps = ignoreSystemApps,
            ignoredPackages = ignoredPackages
        )
        val totalDurationMs = aggregated.apps.values.sumOf { it.totalTimeInForeground }
        // 系统聚合桶不含启动次数，逐日历史才是真实来源（仅覆盖已记录的那些天）。
        val totalLaunches = launchHistory.sumRange(startTimestamp, endTimestamp).values.sum()
        return IntervalTotal(
            totalDurationMs = totalDurationMs,
            totalLaunches = totalLaunches
        )
    }
}