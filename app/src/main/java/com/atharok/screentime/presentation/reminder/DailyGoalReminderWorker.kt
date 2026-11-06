package com.atharok.screentime.presentation.reminder

import android.content.Context
import android.os.SystemClock
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.atharok.screentime.common.utils.AppLog
import com.atharok.screentime.domain.entities.usage.GlanceTodayDeviceUsage
import com.atharok.screentime.domain.resources.Resource
import com.atharok.screentime.domain.usecases.GlanceTodayDeviceUsageUseCase
import com.atharok.screentime.domain.usecases.SettingsUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Periodically checks today's screen time against the configured daily goal and posts a warning
 * notification once a day.
 *
 * This worker is independent from the glance widget: the widget only schedules its own work when a
 * widget is added to the home screen, so relying on it would make the reminder silently stop
 * working for users without a widget.
 */
class DailyGoalReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params), KoinComponent {

    override suspend fun doWork(): Result {
        val started = SystemClock.uptimeMillis()
        AppLog.event(
            "INFO", "WorkManager", "WORK_START",
            "Daily goal reminder worker started",
            "attempt" to runAttemptCount,
            "tags" to tags.joinToString(","),
            "thread" to Thread.currentThread().name
        )
        return try {
            val settingsUseCase by inject<SettingsUseCase>()
            val todayUsageUseCase by inject<GlanceTodayDeviceUsageUseCase>()

            val settings = settingsUseCase.getDailyGoalSettings().first()
            if(!settings.enabled) {
                finish("SKIPPED_DISABLED", started, "feature disabled")
                return Result.success()
            }

            // One notification per day at most. Storing the date (instead of a boolean) also handles
            // the app not being opened for several days and the day rolling over at midnight.
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            if(settingsUseCase.getLastGoalNotifiedDate().first() == today) {
                finish("SKIPPED_ALREADY_NOTIFIED", started, "date=$today")
                return Result.success()
            }

            val totalTimeUsed: Long = when(val resource = todayUsageUseCase.calculateTodayDeviceUsage(
                ignoreSystemApps = settingsUseCase.shouldIgnoreSystemApps().first(),
                ignoredPackages = settingsUseCase.getIgnoredPackages().firstOrNull() ?: emptyList(),
                context = applicationContext
            )) {
                is Resource.Success<GlanceTodayDeviceUsage> -> resource.data.totalTimeUsed
                else -> {
                    finish("SKIPPED_NO_DATA", started, "today usage unavailable")
                    return Result.success()
                }
            }

            var outcome = "BELOW_THRESHOLD"
            if(totalTimeUsed >= settings.thresholdMillis) {
                DailyGoalNotifier.notify(
                    context = applicationContext,
                    usedMillis = totalTimeUsed,
                    goalMillis = settings.goalMillis
                )
                settingsUseCase.saveLastGoalNotifiedDate(today)
                outcome = "NOTIFIED"
                AppLog.event(
                    "INFO", "DailyGoalReminderWorker", "GOAL_WARNING_POSTED",
                    "Posted goal warning notification",
                    "usedMs" to totalTimeUsed,
                    "thresholdMs" to settings.thresholdMillis,
                    "goalMs" to settings.goalMillis,
                    "date" to today
                )
            }
            finish(outcome, started, "usedMs=$totalTimeUsed thresholdMs=${settings.thresholdMillis}")
            Result.success()
        } catch (e: Exception) {
            // A transient failure (e.g. usage access temporarily unavailable) should be retried by
            // WorkManager, not silently swallow the reminder for the whole day.
            AppLog.event(
                "ERROR", "WorkManager", "WORK_FAILED",
                "Daily goal reminder failed, requesting retry: ${e.javaClass.name}: ${e.message}",
                "attempt" to runAttemptCount,
                "costMs" to (SystemClock.uptimeMillis() - started),
                "error" to e.javaClass.name
            )
            AppLog.e("DailyGoalReminderWorker", "Reminder check failed, requesting retry", e)
            Result.retry()
        }
    }

    private fun finish(outcome: String, started: Long, detail: String) {
        AppLog.event(
            "INFO", "WorkManager", "WORK_DONE",
            "Daily goal reminder finished: $outcome",
            "outcome" to outcome,
            "attempt" to runAttemptCount,
            "costMs" to (SystemClock.uptimeMillis() - started),
            "detail" to detail
        )
    }
}
