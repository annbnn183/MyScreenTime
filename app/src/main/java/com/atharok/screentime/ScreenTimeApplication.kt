package com.atharok.screentime

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.atharok.screentime.common.injections.appModules
import com.atharok.screentime.common.utils.DAILY_GOAL_WORK_NAME
import com.atharok.screentime.presentation.reminder.DailyGoalReminderWorker
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import java.util.concurrent.TimeUnit

class ScreenTimeApplication: Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@ScreenTimeApplication)
            modules(appModules)
        }
        scheduleDailyGoalReminder()
    }

    /**
     * Registers the daily-goal check independently of the glance widget. WorkManager's periodic
     * minimum is 15 minutes; the worker itself exits immediately when the feature is disabled, so
     * the cost is negligible for users who never turn it on.
     */
    private fun scheduleDailyGoalReminder() {
        val work: PeriodicWorkRequest = PeriodicWorkRequestBuilder<DailyGoalReminderWorker>(
            repeatInterval = PeriodicWorkRequest.MIN_PERIODIC_INTERVAL_MILLIS,
            repeatIntervalTimeUnit = TimeUnit.MILLISECONDS
        ).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            DAILY_GOAL_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            work
        )
    }
}
