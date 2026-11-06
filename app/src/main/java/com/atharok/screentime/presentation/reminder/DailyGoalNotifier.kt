package com.atharok.screentime.presentation.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.atharok.screentime.R
import com.atharok.screentime.common.utils.DAILY_GOAL_NOTIFICATION_CHANNEL_ID
import com.atharok.screentime.common.utils.DAILY_GOAL_NOTIFICATION_ID
import com.atharok.screentime.common.utils.DateTimeUtils
import com.atharok.screentime.presentation.activities.MainActivity

/** Builds and posts the "daily goal reached" notification. */
object DailyGoalNotifier {

    fun notify(context: Context, usedMillis: Long, goalMillis: Long) {
        if(!canPostNotifications(context)) return
        createChannel(context)

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val message = context.getString(
            R.string.daily_goal_notification_message,
            DateTimeUtils.formatToDuration(usedMillis),
            DateTimeUtils.formatToDuration(goalMillis)
        )

        val notification = NotificationCompat.Builder(context, DAILY_GOAL_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_goal)
            .setContentTitle(context.getString(R.string.daily_goal_notification_title))
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        @Suppress("MissingPermission")
        NotificationManagerCompat.from(context).notify(DAILY_GOAL_NOTIFICATION_ID, notification)
    }

    /** Android 13+ requires the runtime POST_NOTIFICATIONS permission, plus the channel being enabled. */
    private fun canPostNotifications(context: Context): Boolean {
        if(!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        return if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun createChannel(context: Context) {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                DAILY_GOAL_NOTIFICATION_CHANNEL_ID,
                context.getString(R.string.daily_goal_notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.daily_goal_notification_channel_description)
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}
