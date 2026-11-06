package com.atharok.screentime.presentation.reminder

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
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

    override suspend fun doWork(): Result = try {
        val settingsUseCase by inject<SettingsUseCase>()
        val todayUsageUseCase by inject<GlanceTodayDeviceUsageUseCase>()

        val settings = settingsUseCase.getDailyGoalSettings().first()
        if(!settings.enabled) {
            return Result.success()
        }

        // One notification per day at most. Storing the date (instead of a boolean) also handles
        // the app not being opened for several days and the day rolling over at midnight.
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        if(settingsUseCase.getLastGoalNotifiedDate().first() == today) {
            return Result.success()
        }

        val totalTimeUsed: Long = when(val resource = todayUsageUseCase.calculateTodayDeviceUsage(
            ignoreSystemApps = settingsUseCase.shouldIgnoreSystemApps().first(),
            ignoredPackages = settingsUseCase.getIgnoredPackages().firstOrNull() ?: emptyList(),
            context = applicationContext
        )) {
            is Resource.Success<GlanceTodayDeviceUsage> -> resource.data.totalTimeUsed
            else -> return Result.success()
        }

        if(totalTimeUsed >= settings.thresholdMillis) {
            DailyGoalNotifier.notify(
                context = applicationContext,
                usedMillis = totalTimeUsed,
                goalMillis = settings.goalMillis
            )
            settingsUseCase.saveLastGoalNotifiedDate(today)
        }

        Result.success()
    } catch (_: Exception) {
        // A transient failure (e.g. usage access temporarily unavailable) should be retried by
        // WorkManager, not silently swallow the reminder for the whole day.
        Result.retry()
    }
}
