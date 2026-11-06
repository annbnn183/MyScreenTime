package com.atharok.screentime.common.utils

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.util.Log
import androidx.core.content.FileProvider
import com.atharok.screentime.R
import com.atharok.screentime.common.extensions.getAppVersion
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

/**
 * Always-on file logger.
 *
 * Every line is appended to a per-day file in the app's private `files/logs/` directory. Files are
 * never rotated, truncated or deleted, so the history keeps growing for as long as the app is
 * installed (only uninstalling / clearing app data removes it).
 *
 * The exported TXT is meant to be handed straight to an AI assistant: it starts with a prompt,
 * device/app context and permission state, then the full runtime log, then this process' logcat.
 */
object AppLog {

    private const val LOG_DIR = "logs"
    private const val EXPORT_DIR = "exports"

    private val lineFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val fileStampFormat = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)

    private lateinit var context: Context
    private var ready = false

    data class LogStats(
        val fileCount: Int,
        val totalBytes: Long,
        val lineCount: Int,
        val firstDay: String,
        val lastDay: String
    )

    @Synchronized
    fun init(context: Context) {
        if (ready) return
        this.context = context.applicationContext
        runCatching { File(this.context.filesDir, LOG_DIR).mkdirs() }
        ready = true
        installCrashHandler()
        i("App", "===== App started =====")
        i("App", environment().replace('\n', '|'))
    }

    private fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            write("FATAL", "CRASH", "Uncaught exception on thread '${thread.name}'", error)
            previous?.uncaughtException(thread, error)
        }
    }

    // ---- Public logging API ----

    fun d(tag: String, message: String) = write("DEBUG", tag, message, null)

    fun i(tag: String, message: String) = write("INFO", tag, message, null)

    fun w(tag: String, message: String, error: Throwable? = null) = write("WARN", tag, message, error)

    fun e(tag: String, message: String, error: Throwable? = null) = write("ERROR", tag, message, error)

    @Synchronized
    private fun write(level: String, tag: String, message: String, error: Throwable?) {
        if (!ready) return
        val now = System.currentTimeMillis()
        val line = buildString {
            append(lineFormat.format(Date(now)))
            append(" | ").append(level)
            append(" | ").append(tag)
            append(" | ").append(message.replace('\n', ' '))
            append('\n')
            if (error != null) {
                append(Log.getStackTraceString(error))
                if (!endsWith('\n')) append('\n')
            }
        }
        // Append-only. Never rotate, never delete.
        runCatching {
            val file = File(File(context.filesDir, LOG_DIR), dayFormat.format(Date(now)) + ".log")
            FileOutputStream(file, true).use { it.write(line.toByteArray(Charsets.UTF_8)) }
        }
    }

    // ---- Reading ----

    fun logFiles(): List<File> {
        if (!ready) return emptyList()
        val dir = File(context.filesDir, LOG_DIR)
        return dir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".log") }
            ?.sortedBy { it.name }
            ?: emptyList()
    }

    fun stats(): LogStats {
        val files = logFiles()
        var bytes = 0L
        var lines = 0
        files.forEach { file ->
            bytes += file.length()
            runCatching {
                file.useLines(Charsets.UTF_8) { seq -> lines += seq.count() }
            }
        }
        return LogStats(
            fileCount = files.size,
            totalBytes = bytes,
            lineCount = lines,
            firstDay = files.firstOrNull()?.name?.removeSuffix(".log") ?: "-",
            lastDay = files.lastOrNull()?.name?.removeSuffix(".log") ?: "-"
        )
    }

    /** Streams every log file but keeps only the last [maxLines] lines in memory. */
    fun readTail(maxLines: Int = 500): String {
        val queue = ArrayDeque<String>()
        logFiles().forEach { file ->
            runCatching {
                file.useLines(Charsets.UTF_8) { seq ->
                    seq.forEach { line ->
                        if (queue.size >= maxLines) queue.removeFirst()
                        queue.addLast(line)
                    }
                }
            }
        }
        return queue.joinToString("\n")
    }

    // ---- Export ----

    private val aiPrompt = """
【给 AI 的提示 / Prompt】
这是一份 Android 应用「Screen Time（MyScreenTime）」的完整运行日志，由应用自动记录并永久保存。
请阅读以下内容并完成：
  1. 找出所有 ERROR / FATAL / CRASH 记录，分析崩溃与异常的根本原因，给出可落地的修复方案
     （尽量指出应修改的模块、类或取数逻辑）。
  2. 检查 WARN 与带耗时的记录，指出可能导致卡顿、被系统杀进程、或统计结果不准的地方。
  3. 结合日志里体现的用户操作路径，提出功能与体验上的改进建议。
  4. 如果日志不足以判断，请列出还需要补充哪些埋点信息。
日志格式：时间 | 级别 | 模块 | 内容；文末附本进程 logcat（若系统允许读取）。
    """.trimIndent()

    private fun environment(): String = buildString {
        append("App        : Screen Time / MyScreenTime\n")
        append("Version    : ").append(runCatching { context.getAppVersion() }.getOrDefault("?")).append('\n')
        append("Package    : ").append(context.packageName).append('\n')
        append("Device     : ").append(Build.MANUFACTURER).append(" / ").append(Build.MODEL)
            .append(" / ").append(Build.PRODUCT).append('\n')
        append("Android    : ").append(Build.VERSION.RELEASE).append(" (SDK ")
            .append(Build.VERSION.SDK_INT).append("), ").append(Build.DISPLAY).append('\n')
        append("ABI        : ").append(Build.SUPPORTED_ABIS.joinToString(", ")).append('\n')
        append("Locale     : ").append(Locale.getDefault().toString())
            .append(", TimeZone: ").append(java.util.TimeZone.getDefault().id).append('\n')
        append("Usage access: ").append(usageAccess()).append('\n')
    }

    @Suppress("DEPRECATION")
    private fun usageAccess(): String = try {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
        val mode = appOps?.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName
        )
        if (mode == AppOpsManager.MODE_ALLOWED) "GRANTED" else "DENIED"
    } catch (_: Exception) {
        "UNKNOWN"
    }

    private fun header(): String = buildString {
        val s = stats()
        append("================================================================\n")
        append(" MyScreenTime / Screen Time — 运行日志导出 (Runtime Log)\n")
        append(" 导出时间: ").append(lineFormat.format(Date())).append("\n")
        append("================================================================\n\n")
        append(aiPrompt).append("\n\n")
        append("【应用与设备信息 / Environment】\n")
        append(environment())
        append("Log files  : ").append(s.fileCount).append(" 天 (")
            .append(s.firstDay).append(" ~ ").append(s.lastDay).append("), ")
            .append(s.lineCount).append(" 行 / ")
            .append(String.format(Locale.US, "%.1f", s.totalBytes / 1024.0)).append(" KB\n")
        append("Retention  : 日志按天保存在应用私有目录 files/logs/，永久累积、不自动删除；\n")
        append("             只有卸载应用或清除应用数据才会清空。\n")
        append("================================================================\n")
    }

    /** Builds one TXT containing everything, without ever loading the whole history into memory. */
    fun buildExportFile(): File {
        val dir = File(context.cacheDir, EXPORT_DIR).apply { mkdirs() }
        val out = File(dir, "MyScreenTime-logs-${fileStampFormat.format(Date())}.txt")
        out.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write(header())
            writer.write("\n===== 应用运行日志 / APP RUNTIME LOG =====\n")
            logFiles().forEach { file ->
                writer.write("\n----- ${file.name} -----\n")
                runCatching {
                    file.forEachLine(Charsets.UTF_8) { line ->
                        writer.write(line)
                        writer.newLine()
                    }
                }
            }
            writer.write("\n===== 系统 logcat（本进程，最近 400 行）/ SYSTEM LOGCAT =====\n")
            writer.write(readLogcat())
        }
        return out
    }

    private fun readLogcat(): String = try {
        val process = Runtime.getRuntime().exec(
            arrayOf("logcat", "-d", "-t", "400", "--pid=${Process.myPid()}")
        )
        val text = process.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        process.destroy()
        when {
            text.isBlank() -> "(logcat 无输出或不可用)\n"
            text.endsWith("\n") -> text
            else -> text + "\n"
        }
    } catch (e: Exception) {
        "(无法读取 logcat: ${e.javaClass.simpleName})\n"
    }

    /** Shares the TXT through the system share sheet (no storage permission needed). */
    fun export(): Boolean = try {
        val file = buildExportFile()
        i("AppLog", "Exported runtime log ${file.name} (${file.length()} bytes)")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.export_logs_subject))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, context.getString(R.string.export_logs)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
        true
    } catch (e: Exception) {
        e("AppLog", "Failed to export runtime log", e)
        false
    }

    /** Short version for the clipboard: header + the most recent lines. */
    fun clipboardText(): String =
        header() + "\n===== 最近日志 / RECENT LOG (last 500 lines) =====\n" + readTail(500)
}
