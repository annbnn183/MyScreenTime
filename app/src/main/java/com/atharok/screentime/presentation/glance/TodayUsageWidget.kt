package com.atharok.screentime.presentation.glance

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.CircularProgressIndicator
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.components.CircleIconButton
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontStyle
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.atharok.screentime.R
import com.atharok.screentime.common.utils.DateTimeUtils
import com.atharok.screentime.common.utils.GLANCE_PREFERENCES_FAILURE
import com.atharok.screentime.common.utils.GLANCE_PREFERENCES_LOADING
import com.atharok.screentime.common.utils.GLANCE_PREFERENCES_REFRESH_TIMESTAMP
import com.atharok.screentime.common.utils.GLANCE_PREFERENCES_TODAY_DEVICE_USAGE
import com.atharok.screentime.common.utils.GLANCE_TODAY_USAGE_PERIODIC_WORK_NAME
import com.atharok.screentime.domain.entities.usage.GlanceTodayAppUsage
import com.atharok.screentime.domain.entities.usage.GlanceTodayDeviceUsage
import com.atharok.screentime.presentation.activities.MainActivity
import kotlinx.serialization.json.Json
import org.koin.core.component.KoinComponent
import java.util.concurrent.TimeUnit

class TodayUsageWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = TodayUsageWidget()

    override fun onEnabled(context: Context?) {
        super.onEnabled(context)

        if(context != null) {
            val work = PeriodicWorkRequestBuilder<TodayUsageComputeWorker>(
                repeatInterval = PeriodicWorkRequest.MIN_PERIODIC_INTERVAL_MILLIS,
                repeatIntervalTimeUnit = TimeUnit.MILLISECONDS
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                uniqueWorkName = GLANCE_TODAY_USAGE_PERIODIC_WORK_NAME,
                existingPeriodicWorkPolicy = ExistingPeriodicWorkPolicy.KEEP,
                request = work
            )
        }
    }

    override fun onDisabled(context: Context?) {
        super.onDisabled(context)

        if(context != null) {
            WorkManager.getInstance(context).cancelUniqueWork(
                uniqueWorkName = GLANCE_TODAY_USAGE_PERIODIC_WORK_NAME
            )
        }
    }
}

class TodayUsageWidget : GlanceAppWidget(), KoinComponent {
    override val sizeMode: SizeMode = SizeMode.Exact

    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {

        provideContent {
            GlanceTheme {
                val dimensions = TodayUsageWidgetDimensions.calculateDimensions(
                    width = LocalSize.current.width,
                    height = LocalSize.current.height
                )

                val state: Preferences = currentState()
                val refreshTimestamp: Long = state[longPreferencesKey(GLANCE_PREFERENCES_REFRESH_TIMESTAMP)] ?: System.currentTimeMillis()
                val loading: Boolean = state[booleanPreferencesKey(GLANCE_PREFERENCES_LOADING)] ?: false
                val failure: String = state[stringPreferencesKey(GLANCE_PREFERENCES_FAILURE)] ?: ""
                val todayDeviceUsage: GlanceTodayDeviceUsage? =
                    state[stringPreferencesKey(GLANCE_PREFERENCES_TODAY_DEVICE_USAGE)]?.let { jsonString ->
                        runCatching {
                            Json.decodeFromString<GlanceTodayDeviceUsage>(jsonString)
                        }.getOrNull()
                    }

                StatelessTodayUsageView(
                    dimensions = dimensions,
                    loading = loading,
                    failure = failure,
                    todayDeviceUsage = todayDeviceUsage,
                    refreshTimestamp = refreshTimestamp,
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .padding(dimensions.padding)
                        .background(GlanceTheme.colors.widgetBackground),
                    context = context
                )
            }
        }
    }

    @Composable
    private fun StatelessTodayUsageView(
        dimensions: TodayUsageWidgetDimensions,
        loading: Boolean,
        failure: String,
        todayDeviceUsage: GlanceTodayDeviceUsage?,
        refreshTimestamp: Long,
        modifier: GlanceModifier = GlanceModifier,
        context: Context = LocalContext.current
    ) {
        Column(
            modifier = modifier,
            verticalAlignment = Alignment.Top,
            horizontalAlignment = Alignment.Start,
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = GlanceModifier
                        .defaultWeight()
                        .clickable(
                            onClick = actionStartActivity(
                                intent = getStartActivityIntent(context)
                            )
                        )
                        .background(Color.Transparent)
                ) {
                    Text(
                        text = context.getString(R.string.app_name),
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurfaceVariant,
                            fontSize = dimensions.fontSizeLow,
                            fontWeight = FontWeight.Normal,
                            fontStyle = FontStyle.Normal,
                            textAlign = TextAlign.Start
                        ),
                        maxLines = 1
                    )

                    if(failure.isNotBlank()) {
                        Text(
                            text = "${context.getString(R.string.error)}… $failure",
                            style = TextStyle(
                                color = GlanceTheme.colors.error,
                                fontSize = dimensions.fontSizeMedium,
                                fontWeight = FontWeight.Bold,
                                fontStyle = FontStyle.Normal,
                                textAlign = TextAlign.Start
                            ),
                            maxLines = 3
                        )
                    } else if(todayDeviceUsage != null) {
                        Text(
                            text = DateTimeUtils.formatToDuration(todayDeviceUsage.totalTimeUsed),
                            style = TextStyle(
                                color = GlanceTheme.colors.onSurface,
                                fontSize = dimensions.fontSizeHigh,
                                fontWeight = FontWeight.Bold,
                                fontStyle = FontStyle.Normal,
                                textAlign = TextAlign.Start
                            ),
                            maxLines = 1
                        )
                    }
                }

                Spacer(GlanceModifier.width(8.dp))

                if (loading) {
                    CircularProgressIndicator(
                        modifier = GlanceModifier.size(dimensions.iconSize),
                        color = GlanceTheme.colors.primary
                    )
                } else {
                    CircleIconButton(
                        imageProvider = ImageProvider(R.drawable.round_refresh_24),
                        contentDescription = context.getString(R.string.refresh),
                        onClick = actionRunCallback<TodayUsageActionCallback>(),
                        modifier = GlanceModifier.size(dimensions.iconSize),
                        backgroundColor = GlanceTheme.colors.primary,
                        contentColor = GlanceTheme.colors.onPrimary
                    )
                }
            }

            if(failure.isBlank() &&
                todayDeviceUsage != null &&
                dimensions.height >= 192.dp &&
                todayDeviceUsage.todayAppsUsage.isNotEmpty()) {

                todayDeviceUsage.todayAppsUsage.forEach { appUsage ->
                    AppUsageView(
                        dimensions = dimensions,
                        appUsage = appUsage,
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .padding(
                                vertical = dimensions.padding / 2
                            )
                    )
                }
            }

            Spacer(GlanceModifier.defaultWeight())

            Text(
                text = if(dimensions.width < TodayUsageWidgetDimensions.WIDTH_LIMIT)
                    DateTimeUtils.formatToMediumDateTime(refreshTimestamp)
                else
                    DateTimeUtils.formatToFullDateTime(refreshTimestamp),
                modifier = GlanceModifier.fillMaxWidth(),
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = dimensions.fontSizeLow,
                    fontWeight = FontWeight.Normal,
                    fontStyle = FontStyle.Normal,
                    textAlign = TextAlign.End
                ),
                maxLines = 1
            )
        }
    }

    @Composable
    private fun AppUsageView(
        dimensions: TodayUsageWidgetDimensions,
        appUsage: GlanceTodayAppUsage,
        modifier: GlanceModifier
    ) {
        Column(modifier = modifier) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = appUsage.appName,
                    modifier = GlanceModifier.defaultWeight(),
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = dimensions.fontSizeMedium,
                        fontWeight = FontWeight.Bold,
                        fontStyle = FontStyle.Normal,
                        textAlign = TextAlign.Start,
                    ),
                    maxLines = 1
                )

                Spacer(GlanceModifier.width(4.dp))

                Text(
                    text = DateTimeUtils.formatToDuration(appUsage.totalTimeUsed),
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = dimensions.fontSizeMedium,
                        fontWeight = FontWeight.Normal,
                        fontStyle = FontStyle.Normal,
                        textAlign = TextAlign.End
                    ),
                    maxLines = 1
                )
            }

            Spacer(GlanceModifier.height(2.dp))

            LinearProgressIndicator(
                progress = appUsage.percentUsage,
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .height(6.dp),
                color = GlanceTheme.colors.primary,
                backgroundColor = GlanceTheme.colors.inversePrimary
            )
        }
    }

    private fun getStartActivityIntent(context: Context): Intent {
        return Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
    }
}
