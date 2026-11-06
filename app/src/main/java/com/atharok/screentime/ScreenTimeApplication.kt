package com.atharok.screentime

import android.app.Application
import android.os.SystemClock
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.atharok.screentime.common.injections.appModules
import com.atharok.screentime.common.utils.AppLog
import com.atharok.screentime.common.utils.DAILY_GOAL_WORK_NAME
import com.atharok.screentime.common.utils.RuntimeMonitors
import com.atharok.screentime.presentation.reminder.DailyGoalReminderWorker
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import java.util.concurrent.TimeUnit

class ScreenTimeApplication: Application() {
    override fun onCreate() {
        super.onCreate()
        // Must run before anything else so crashes during startup are still captured.
        AppLog.init(this)
        // Full-capture runtime collectors: lifecycle/foreground, system broadcasts, StrictMode,
        // frame jank, periodic memory/battery samples and historical process exit reasons.
        RuntimeMonitors.install(this)
        startKoin {
            androidContext(this@ScreenTimeApplication)
            modules(appModules)
        }
        scheduleDailyGoalReminder()
        AppLog.i("Application", "Koin started; daily goal reminder work scheduled")
    }

    /**
     * Registers the daily-goal check independently of the glance widget. WorkManager's periodic
     * minimum is 15 minutes; the worker itself exits immediately when the feature is disabled, so
     * the cost is negligible for users who never turn it on.
     */
    private fun scheduleDailyGoalReminder() {
        val started = SystemClock.uptimeMillis()
        val work: PeriodicWorkRequest = PeriodicWorkRequestBuilder<DailyGoalReminderWorker>(
            repeatInterval = PeriodicWorkRequest.MIN_PERIODIC_INTERVAL_MILLIS,
            repeatIntervalTimeUnit = TimeUnit.MILLISECONDS
        ).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            DAILY_GOAL_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            work
        )
        AppLog.event(
            "INFO", "WorkManager", "WORK_ENQUEUED",
            "Unique periodic work '$DAILY_GOAL_WORK_NAME' submitted",
            "name" to DAILY_GOAL_WORK_NAME,
            "policy" to "KEEP",
            "intervalMs" to PeriodicWorkRequest.MIN_PERIODIC_INTERVAL_MILLIS,
            "costMs" to (SystemClock.uptimeMillis() - started)
        )
    }

    override fun onTrimMemory(level: Int) {
        RuntimeMonitors.onTrimMemory(level)
        super.onTrimMemory(level)
    }

    override fun onLowMemory() {
        RuntimeMonitors.onLowMemory()
        super.onLowMemory()
    }
}
