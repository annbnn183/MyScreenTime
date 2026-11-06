package com.atharok.screentime.common.utils

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.atharok.screentime.R
import com.atharok.screentime.domain.entities.Period
import com.atharok.screentime.domain.entities.usage.DeviceUsage
import com.atharok.screentime.domain.entities.usage.MonthlyDeviceUsage
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Exports the currently displayed usage report as a CSV file and opens the system share sheet.
 * Uses a FileProvider so the receiving app can read the file without storage permissions.
 */
object CsvExporter {

    fun export(context: Context, deviceUsage: DeviceUsage<*>, period: Period) {
        val directory = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(
            directory,
            "MyScreenTime-${period.name.lowercase(Locale.ROOT)}-${System.currentTimeMillis()}.csv"
        )
        file.writeText(buildCsv(deviceUsage, period), Charsets.UTF_8)

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.export_report_subject))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(sendIntent, context.getString(R.string.export_report)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(chooser) }
    }

    private fun buildCsv(deviceUsage: DeviceUsage<*>, period: Period): String = buildString {
        // BOM so Excel on Windows reads UTF-8 application names correctly.
        append("\uFEFF")
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

        append("MyScreenTime,Usage report\n")
        append("Period,").append(period.name).append('\n')
        append("From,").append(dateFormat.format(Date(deviceUsage.timeIntervalStart))).append('\n')
        append("To,").append(dateFormat.format(Date(deviceUsage.timeIntervalEnd))).append('\n')
        append("Updated,").append(cell(deviceUsage.dateOfLastRefresh)).append('\n')
        append("Total minutes,").append(deviceUsage.getTotalTimeUsed() / 60_000L).append('\n')
        append("Daily average minutes,").append(deviceUsage.getAverageTimeUsed() / 60_000L).append('\n')
        append("Total launches,").append(deviceUsage.totalLaunchCount).append("\n\n")

        if (deviceUsage is MonthlyDeviceUsage) {
            append("Date,Daily minutes\n")
            deviceUsage.dailyTotals.forEach { day ->
                append(dateFormat.format(Date(day.dayTimestamp)))
                    .append(',')
                    .append(day.duration / 60_000L)
                    .append('\n')
            }
            append('\n')
        }

        append("App,Package,Minutes,Percent,Launches,LaunchRate\n")
        val total = deviceUsage.getTotalTimeUsed().coerceAtLeast(1L)
        deviceUsage.appUsageList.forEach { app ->
            append(cell(app.appName)).append(',')
            append(cell(app.packageName)).append(',')
            append(app.getTotalTimeUsed() / 60_000L).append(',')
            append(String.format(Locale.US, "%.1f", app.getTotalTimeUsed() * 100.0 / total))
                .append(',')
                .append(app.launchCount)
                .append(',')
                .append(String.format(Locale.US, "%.1f", app.getLaunchRate(deviceUsage.totalLaunchCount) * 100.0))
            append('\n')
        }
    }

    private fun cell(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
}
