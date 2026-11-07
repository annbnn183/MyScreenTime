package com.atharok.screentime.common.utils

import android.app.ActivityManager
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.util.Log
import androidx.core.content.FileProvider
import com.atharok.screentime.R
import com.atharok.screentime.common.extensions.getAppVersion
import kotlinx.coroutines.CoroutineExceptionHandler
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Always-on, structured, full-capture file logger.
 *
 * Design goals (this build is for the owner's own device, so verbosity wins):
 *  - One JSON object per line, so the history can be grepped / aggregated / plotted by scripts.
 *  - A single daemon writer thread batches writes; logging never blocks the main thread.
 *  - Per-day files, automatically split into parts when a file grows too large. NOTHING is ever
 *    deleted or rotated away: the only way to clear history is uninstalling or clearing app data.
 *  - Every event automatically carries timestamp, level, module, event type, current screen,
 *    thread name, pid, foreground state and monotonic elapsed time.
 *  - Legacy plain-text lines from older builds are still readable.
 *
 * The exported TXT is meant to be handed straight to an AI assistant: prompt, full device
 * snapshot, aggregated statistics, then the complete runtime log, then this process' logcat.
 */
object AppLog {

    private const val LOG_DIR = "logs"
    private const val EXPORT_DIR = "exports"
    private const val MAX_FILE_BYTES = 4L * 1024 * 1024
    private const val BATCH_SIZE = 128
    private const val FLUSH_INTERVAL_MS = 250L

    private val lineFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val fileStampFormat = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)

    private lateinit var context: Context
    @Volatile private var ready = false

    private val queue = LinkedBlockingQueue<String>()
    private val seq = AtomicLong(0)

    @Volatile var screen: String = "-"
        private set
    @Volatile var isForeground: Boolean = false
        private set
    @Volatile private var foregroundSince: Long = 0L
    @Volatile private var screenSince: Long = 0L

    /** Full device snapshot captured once at startup; rendered in the export header. */
    @Volatile var deviceSnapshotJson: String = "{}"
        private set

    @Volatile private var lastLogcatAvailable: Boolean = false

    /** Attach to any viewModelScope.launch { ... } so coroutine failures are never swallowed. */
    val ceh = CoroutineExceptionHandler { _, throwable ->
        e(
            "Coroutine",
            "Unhandled coroutine exception: ${throwable.javaClass.name}: ${throwable.message}",
            throwable
        )
    }

    data class LogStats(
        val fileCount: Int = 0,
        val totalBytes: Long = 0L,
        val lineCount: Int = 0,
        val firstDay: String = "-",
        val lastDay: String = "-"
    )

    @Synchronized
    fun init(context: Context) {
        if (ready) return
        this.context = context.applicationContext
        runCatching { File(this.context.filesDir, LOG_DIR).mkdirs() }
        ready = true
        startWriter()
        installCrashHandler()
        startAnrWatchdog()
        i("App", "===== App started =====")
        i("App", environment().replace('\n', '|'))
        // Device snapshot touches /proc, PackageManager and several system services. Do it off
        // the main thread so cold start is not delayed, but keep it as a permanent log event.
        Thread({
            val snapshot = DeviceSnapshot.collect(this.context)
            deviceSnapshotJson = snapshot
            event(
                "INFO", "Device", "DEVICE_SNAPSHOT",
                "Full device / system / environment snapshot captured",
                "bytes" to snapshot.length
            )
        }, "AppLog-snapshot").apply { isDaemon = true; start() }
    }

    // ---- Public logging API -------------------------------------------------

    /** Structured event with arbitrary extra fields, e.g. event("INFO","Perf","JANK","...","gapMs" to 42). */
    fun event(level: String, mod: String, ev: String, msg: String, vararg fields: Pair<String, Any>) =
        enqueue(level, mod, ev, msg, null, fields)

    fun d(tag: String, message: String) = enqueue("DEBUG", tag, "LOG", message, null, emptyArray())
    fun i(tag: String, message: String) = enqueue("INFO", tag, "LOG", message, null, emptyArray())
    fun w(tag: String, message: String, error: Throwable? = null) =
        enqueue("WARN", tag, "LOG", message, error, emptyArray())
    fun e(tag: String, message: String, error: Throwable? = null) =
        enqueue("ERROR", tag, "LOG", message, error, emptyArray())

    /** Manual breadcrumb for the owner: "this number looks wrong", "felt laggy here", etc. */
    fun mark(note: String) = event(
        "INFO", "User", "FEEDBACK", note.ifBlank { "(empty note)" },
        "screen" to screen
    )

    @Synchronized
    fun setScreen(route: String) {
        val now = SystemClock.uptimeMillis()
        val previous = screen
        if (previous == route) return
        if (previous != "-" && screenSince > 0L) {
            event(
                "INFO", "Nav", "PAGE_EXIT", "Leave $previous",
                "screen" to previous, "stayMs" to (now - screenSince)
            )
        }
        screen = route
        screenSince = now
        event("INFO", "Nav", "PAGE_ENTER", "Enter $route", "screen" to route, "from" to previous)
    }

    @Synchronized
    fun setForeground(fg: Boolean) {
        if (isForeground == fg) return
        val now = SystemClock.elapsedRealtime()
        isForeground = fg
        if (fg) {
            foregroundSince = now
            event("INFO", "Lifecycle", "APP_FOREGROUND", "App entered foreground")
        } else {
            event(
                "INFO", "Lifecycle", "APP_BACKGROUND", "App left foreground",
                "foregroundMs" to (now - foregroundSince).coerceAtLeast(0L)
            )
        }
    }

    private fun buildEvent(
        level: String, mod: String, ev: String, msg: String,
        error: Throwable?, fields: Array<out Pair<String, Any>>
    ): JSONObject {
        val now = System.currentTimeMillis()
        val obj = JSONObject()
        obj.put("ts", now)
        obj.put("t", lineFormat.format(Date(now)))
        obj.put("seq", seq.incrementAndGet())
        obj.put("lv", level)
        obj.put("mod", mod)
        obj.put("ev", ev)
        obj.put("msg", msg)
        obj.put("scr", screen)
        obj.put("thr", Thread.currentThread().name)
        obj.put("pid", Process.myPid())
        obj.put("fg", isForeground)
        obj.put("elapsed", SystemClock.elapsedRealtime())
        if (error != null) obj.put("stack", Log.getStackTraceString(error))
        if (fields.isNotEmpty()) {
            val extra = JSONObject()
            for ((k, v) in fields) extra.put(k, v)
            obj.put("x", extra)
        }
        return obj
    }

    private fun enqueue(
        level: String, mod: String, ev: String, msg: String,
        error: Throwable?, fields: Array<out Pair<String, Any>>
    ) {
        if (!ready) return
        runCatching { queue.offer(buildEvent(level, mod, ev, msg, error, fields).toString()) }
    }

    // ---- Writer thread (batched, never on the caller's thread) --------------

    private fun startWriter() {
        Thread({
            val batch = ArrayList<String>(BATCH_SIZE)
            while (true) {
                try {
                    val first = queue.poll(FLUSH_INTERVAL_MS, TimeUnit.MILLISECONDS)
                    if (first != null) batch.add(first)
                    while (batch.size < BATCH_SIZE) {
                        batch.add(queue.poll() ?: break)
                    }
                    if (batch.isNotEmpty()) {
                        flush(batch)
                        batch.clear()
                    }
                } catch (_: Throwable) {
                    // The writer must never die: a single malformed line cannot stop logging.
                    batch.clear()
                }
            }
        }, "AppLog-writer").apply { isDaemon = true; start() }
    }

    private fun flush(lines: List<String>) {
        val byDay = LinkedHashMap<String, StringBuilder>()
        for (line in lines) {
            val day = extractDay(line) ?: dayFormat.format(Date())
            byDay.getOrPut(day) { StringBuilder() }.append(line).append('\n')
        }
        byDay.forEach { (day, text) -> appendToFile(day, text.toString()) }
    }

    private fun extractDay(line: String): String? = runCatching {
        JSONObject(line).optString("t").takeIf {
            it.length >= 10 && it[4] == '-' && it[7] == '-'
        }?.substring(0, 10)
    }.getOrNull()

    /** Append-only. Splits into numbered parts past [MAX_FILE_BYTES]; NEVER deletes anything. */
    @Synchronized
    private fun appendToFile(day: String, text: String) {
        runCatching {
            val dir = File(context.filesDir, LOG_DIR).apply { mkdirs() }
            var index = 0
            var file = File(dir, "$day.log")
            while (file.exists() && file.length() >= MAX_FILE_BYTES && index < 999) {
                index++
                file = File(dir, "$day.p%03d.log".format(Locale.US, index))
            }
            FileOutputStream(file, true).use { it.write(text.toByteArray(Charsets.UTF_8)) }
        }
    }

    private fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val fatal = buildEvent(
                    "FATAL", "Crash", "CRASH",
                    "Uncaught exception on thread '${thread.name}': ${error.javaClass.name}: ${error.message}",
                    error,
                    arrayOf("screen" to screen, "fg" to isForeground, "thread" to thread.name)
                )
                // Synchronous: the process may die immediately after this returns.
                flush(listOf(fatal.toString()))
                val pending = ArrayList<String>()
                queue.drainTo(pending)
                if (pending.isNotEmpty()) flush(pending)
            }
            previous?.uncaughtException(thread, error)
        }
    }

    /** Main-thread watchdog: a tick that fails to run for >5s while foreground is an ANR. */
    private fun startAnrWatchdog() {
        val mainHandler = Handler(Looper.getMainLooper())
        val lastTick = AtomicLong(SystemClock.uptimeMillis())
        val ticker = object : Runnable {
            override fun run() {
                lastTick.set(SystemClock.uptimeMillis())
                mainHandler.postDelayed(this, 2000L)
            }
        }
        mainHandler.postDelayed(ticker, 2000L)
        Thread({
            while (true) {
                SystemClock.sleep(2500L)
                if (!isForeground) continue
                val lag = SystemClock.uptimeMillis() - lastTick.get()
                if (lag > 5000L) {
                    event(
                        "ERROR", "ANR", "MAIN_THREAD_BLOCKED",
                        "Main thread unresponsive for ${lag}ms",
                        "lagMs" to lag, "screen" to screen
                    )
                    lastTick.set(SystemClock.uptimeMillis()) // avoid spamming every 2.5s
                }
            }
        }, "AppLog-anr").apply { isDaemon = true; start() }
    }

    /**
     * API 30+: reads why the PREVIOUS process exited. This is the only reliable way to tell
     * "killed by the system (LMK)", "ANR", "native crash" or "user swiped away" after the fact.
     * All API-30 references live in [readExitInfo] so older devices never resolve that class.
     */
    fun logHistoricalExitInfo(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            i("ExitInfo", "ApplicationExitInfo unavailable on SDK ${Build.VERSION.SDK_INT}")
            return
        }
        readExitInfo(context)
    }

    private fun readExitInfo(context: Context) {
        runCatching {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return
            val reasons = am.getHistoricalProcessExitReasons(null, 0, 8)
            i("ExitInfo", "Historical process exit reasons found: ${reasons.size}")
            // 系统每次启动都返回同一批历史。若不去重，一次被杀会在日志里出现 8 次，
            // 摘要里就会变成「109 次 LOW_MEMORY」，把结论彻底带偏。
            val prefs = context.getSharedPreferences("applog_exit_seen", Context.MODE_PRIVATE)
            val seen = prefs.getStringSet("ts", emptySet()).orEmpty().toMutableSet()
            var added = 0
            reasons.forEach { info ->
                if (!seen.add(info.timestamp.toString())) return@forEach
                added++
                val name = exitReasonName(info.reason)
                event(
                    "INFO", "ExitInfo", "EXIT_INFO",
                    "Previous process exit: $name (${info.description ?: "no description"})",
                    "reason" to info.reason,
                    "reasonName" to name,
                    "timestamp" to info.timestamp,
                    "pid" to info.pid,
                    "importance" to info.importance,
                    "pssKb" to info.pss,
                    "rssKb" to info.rss,
                    "description" to (info.description ?: "")
                )
                if (info.reason == 6 || info.reason == 4 || info.reason == 5) { // ANR / CRASH / NATIVE_CRASH
                    val trace = runCatching {
                        info.traceInputStream?.bufferedReader(Charsets.UTF_8)?.use {
                            it.readText().take(12000)
                        }
                    }.getOrNull()
                    if (!trace.isNullOrBlank()) {
                        event("ERROR", "ExitInfo", "EXIT_TRACE", "System trace for $name", "trace" to trace)
                    }
                }
            }
            if (added > 0) {
                // 只保留最近 200 条，避免这个集合无限增长。
                val kept = seen.sortedByDescending { it.toLongOrNull() ?: 0L }.take(200).toSet()
                prefs.edit().putStringSet("ts", kept).apply()
            }
        }.onFailure { e("ExitInfo", "Failed to read historical exit reasons", it) }
    }

    /** Numeric literals keep this method free of any API-30 class reference. */
    private fun exitReasonName(reason: Int): String = when (reason) {
        0 -> "UNKNOWN"
        1 -> "EXIT_SELF"
        2 -> "SIGNALED"
        3 -> "LOW_MEMORY (LMK killed)"
        4 -> "CRASH"
        5 -> "CRASH_NATIVE"
        6 -> "ANR"
        7 -> "INITIALIZATION_FAILURE"
        8 -> "PERMISSION_CHANGE"
        9 -> "EXCESSIVE_RESOURCE_USAGE"
        10 -> "USER_REQUESTED"
        11 -> "USER_STOPPED"
        12 -> "DEPENDENCY_DIED"
        13 -> "OTHER"
        14 -> "FREEZER"
        15 -> "PACKAGE_STATE_CHANGE"
        16 -> "PACKAGE_UPDATED"
        else -> "CODE_$reason"
    }

    // ---- Reading ------------------------------------------------------------

    private fun partIndex(name: String): Int {
        val base = name.removeSuffix(".log")
        val marker = base.indexOf(".p")
        return if (marker < 0) -1 else base.substring(marker + 2).toIntOrNull() ?: 0
    }

    fun logFiles(): List<File> {
        if (!ready) return emptyList()
        val dir = File(context.filesDir, LOG_DIR)
        return dir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".log") }
            ?.sortedWith(compareBy({ it.name.take(10) }, { partIndex(it.name) }))
            ?: emptyList()
    }

    fun stats(): LogStats {
        val files = logFiles()
        var bytes = 0L
        var lines = 0
        files.forEach { file ->
            bytes += file.length()
            runCatching { file.useLines(Charsets.UTF_8) { seq -> lines += seq.count() } }
        }
        return LogStats(
            fileCount = files.size,
            totalBytes = bytes,
            lineCount = lines,
            firstDay = files.firstOrNull()?.name?.take(10) ?: "-",
            lastDay = files.lastOrNull()?.name?.take(10) ?: "-"
        )
    }

    /** Converts a JSON line into the human-readable form used by the viewer and the TXT export. */
    private fun render(line: String): String = try {
        val o = JSONObject(line)
        buildString {
            append(o.optString("t")).append(" | ")
            append(o.optString("lv")).append(" | ")
            append(o.optString("mod")).append(" | ")
            val ev = o.optString("ev")
            if (ev.isNotEmpty() && ev != "LOG") append('[').append(ev).append("] ")
            if (ev == "DEVICE_SNAPSHOT") {
                append("Full device/environment snapshot captured (see Environment section)")
            } else {
                val msg = o.optString("msg").replace('\n', ' ')
                append(if (msg.length > 400) msg.take(400) + "…" else msg)
                val scr = o.optString("scr")
                if (scr.isNotEmpty() && scr != "-") append(" | scr=").append(scr)
                val thr = o.optString("thr")
                if (thr.isNotEmpty()) append(" thr=").append(thr)
                val extra = o.optJSONObject("x")
                if (extra != null && extra.length() > 0) {
                    val parts = mutableListOf<String>()
                    val keys = extra.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        val raw = extra.opt(k)?.toString() ?: ""
                        parts.add("$k=${if (raw.length > 120) raw.take(120) + "…" else raw}")
                    }
                    append(" | ").append(parts.joinToString(" "))
                }
                val stack = o.optString("stack")
                if (stack.isNotEmpty()) append('\n').append(stack)
            }
        }
    } catch (_: Throwable) {
        line // legacy plain-text line from an older build
    }

    /** Streams every log file but keeps only the last [maxLines] rendered lines in memory. */
    fun readTail(maxLines: Int = 500): String = tailOf(logFiles(), maxLines)

    /**
     * Only scans the two newest files. The permanent history grows forever, so re-reading every
     * file on each 3-second auto-refresh would get slower the longer the app is used. Nothing is
     * deleted or skipped in the saved files themselves -- this is viewer-only.
     */
    fun readRecent(maxLines: Int = 500): String = tailOf(logFiles().takeLast(2), maxLines)

    private fun tailOf(files: List<File>, maxLines: Int): String {
        val queue = ArrayDeque<String>()
        files.forEach { file ->
            runCatching {
                file.useLines(Charsets.UTF_8) { seq ->
                    seq.forEach { raw ->
                        render(raw).lineSequence().forEach { line ->
                            if (queue.size >= maxLines) queue.removeFirst()
                            queue.addLast(line)
                        }
                    }
                }
            }
        }
        return queue.joinToString("\n")
    }

    /** Aggregated, AI-friendly summary computed by streaming the whole history (no full load). */
    fun summary(): String {
        val levels = mutableMapOf<String, Int>()
        val modules = mutableMapOf<String, Int>()
        val events = mutableMapOf<String, Int>()
        val screens = mutableMapOf<String, Int>()
        val errors = mutableListOf<String>()
        var total = 0
        var firstTs = Long.MAX_VALUE
        var lastTs = 0L
        var jank = 0
        var jankMax = 0L
        var jankSum = 0L
        // 界面静止时的无帧间隔，单独统计，不计入真实掉帧。
        var idleGaps = 0
        var idleGapMax = 0L
        var queryCount = 0
        var queryMax = 0L
        var querySum = 0L
        var touches = 0
        var taps = 0
        var swipes = 0
        var trims = 0
        var foregrounds = 0
        var backgrounds = 0
        var totalStay = 0L
        var stays = 0

        logFiles().forEach { file ->
            runCatching {
                file.useLines(Charsets.UTF_8) { seq ->
                    seq.forEach { raw ->
                        val o = runCatching { JSONObject(raw) }.getOrNull() ?: return@forEach
                        total++
                        val ts = o.optLong("ts", 0L)
                        if (ts > 0) { if (ts < firstTs) firstTs = ts; if (ts > lastTs) lastTs = ts }
                        val lv = o.optString("lv", "?")
                        val mod = o.optString("mod", "?")
                        val ev = o.optString("ev", "?")
                        levels[lv] = (levels[lv] ?: 0) + 1
                        modules[mod] = (modules[mod] ?: 0) + 1
                        events[ev] = (events[ev] ?: 0) + 1
                        val scr = o.optString("scr", "")
                        if (scr.isNotEmpty() && scr != "-") screens[scr] = (screens[scr] ?: 0) + 1
                        if (lv == "ERROR" || lv == "FATAL") {
                            if (errors.size < 12) errors.add("${o.optString("t")} [$lv] $mod/$ev: ${o.optString("msg")}")
                        }
                        val x = o.optJSONObject("x")
                        when (ev) {
                            "JANK" -> {
                                jank++
                                val g = x?.optLong("gapMs", 0L) ?: 0L
                                jankSum += g
                                if (g > jankMax) jankMax = g
                            }
                            "IDLE_GAP" -> {
                                idleGaps++
                                val g = x?.optLong("gapMs", 0L) ?: 0L
                                if (g > idleGapMax) idleGapMax = g
                            }
                            "QUERY_RESULT" -> {
                                queryCount++
                                val c = x?.optLong("costMs", 0L) ?: 0L
                                querySum += c
                                if (c > queryMax) queryMax = c
                            }
                            "TOUCH" -> {
                                touches++
                                when (x?.optString("gesture", "")) {
                                    "tap" -> taps++
                                    "swipe" -> swipes++
                                }
                            }
                            "PAGE_EXIT" -> {
                                totalStay += x?.optLong("stayMs", 0L) ?: 0L
                                stays++
                            }
                            "TRIM_MEMORY" -> trims++
                            "APP_FOREGROUND" -> foregrounds++
                            "APP_BACKGROUND" -> backgrounds++
                        }
                    }
                }
            }
        }

        fun top(m: Map<String, Int>, n: Int = 8): String =
            m.entries.sortedByDescending { it.value }.take(n)
                .joinToString(", ") { "${it.key}=${it.value}" }.ifBlank { "-" }

        return buildString {
            append("Events total : ").append(total).append('\n')
            append("Time range   : ")
                .append(if (firstTs == Long.MAX_VALUE) "-" else lineFormat.format(Date(firstTs)))
                .append(" ~ ")
                .append(if (lastTs == 0L) "-" else lineFormat.format(Date(lastTs)))
                .append('\n')
            append("Levels       : ").append(top(levels)).append('\n')
            append("Modules      : ").append(top(modules)).append('\n')
            append("Event types  : ").append(top(events, 14)).append('\n')
            append("Screens      : ").append(top(screens)).append('\n')
            append("Fore/Back    : foregrounds=$foregrounds backgrounds=$backgrounds trims=$trims\n")
            append("Frames       : jank=$jank maxGapMs=$jankMax avgGapMs=${if (jank > 0) jankSum / jank else 0}\n")
            if (idleGaps > 0) {
                append("Idle gaps    : count=$idleGaps maxIdleGapMs=$idleGapMax (no touch in 1.2s; not counted as jank)\n")
            }
            append("Queries      : count=$queryCount maxCostMs=$queryMax avgCostMs=${if (queryCount > 0) querySum / queryCount else 0}\n")
            append("Touches      : total=$touches taps=$taps swipes=$swipes\n")
            append("Page stays   : count=$stays avgStayMs=${if (stays > 0) totalStay / stays else 0}\n")
            append("Errors/FATAL : ").append((levels["ERROR"] ?: 0) + (levels["FATAL"] ?: 0)).append('\n')
            if (errors.isNotEmpty()) {
                append("First errors :\n")
                errors.forEach { append("  - ").append(it).append('\n') }
            }
        }
    }

    // ---- Digest: short, paste-into-a-chat version ---------------------------

    /** 用户主动退出、装包、改权限导致的进程结束，不算故障。 */
    private val benignExitReasons = setOf(
        "USER_REQUESTED", "APP_REQUESTED", "EXIT_SELF", "USER_STOPPED", "PERMISSION_CHANGE",
        "PACKAGE_UPDATED", "PACKAGE_STATE_CHANGE"
    )

    private val digestTimeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

    private fun shortTime(ts: Long): String =
        if (ts <= 0) "--:--:--" else digestTimeFormat.format(Date(ts))

    private fun clip(s: String, max: Int): String {
        val one = s.replace('\n', ' ').trim()
        return if (one.length > max) one.take(max) + "…" else one
    }

    /**
     * 精简摘要：只保留「错误 + 设备基础信息 + 最慢的几个操作」，目标是一屏内看完、
     * 能直接粘进微信/ChatGPT 的输入框（默认上限 1500 字，实测一般 500~800 字）。
     *
     * 只扫最近两个日志文件，避免历史越久越慢；不影响磁盘上的完整日志，一条都不删。
     */
    fun digest(maxChars: Int = 2200): String {
        // Only count events whose event timestamp falls inside the same rolling 24-hour window.
        // Include both calendar days so midnight does not silently shorten the window.
        val now = System.currentTimeMillis()
        val cutoff = now - TimeUnit.HOURS.toMillis(24)
        val firstDay = dayFormat.format(Date(cutoff))
        val errors = mutableListOf<String>()
        val exits = mutableListOf<Pair<Long, String>>()
        val exitKeys = mutableSetOf<Pair<String, Long>>()
        val slowClicks = mutableListOf<Pair<Long, String>>()
        val slowQueries = mutableListOf<Pair<Long, String>>()
        var scanned = 0
        var errorCount = 0
        var fatalCount = 0
        var anrCount = 0
        var jankCount = 0
        var jankMax = 0L
        var gapCount = 0
        var clickCount = 0
        var clickSum = 0L
        var clickMax = 0L
        var queryCount = 0
        var querySum = 0L
        var queryMax = 0L
        var trimCount = 0
        var memorySamples = 0
        var pssSum = 0L
        var pssMax = 0L
        var heapMax = 0L
        var availMemMin = Long.MAX_VALUE
        var lastEventTs = 0L

        logFiles().filter { it.name.take(10) >= firstDay }.forEach { file ->
            runCatching {
                file.useLines(Charsets.UTF_8) { lines ->
                    lines.forEach lineLoop@{ raw ->
                        val o = runCatching { JSONObject(raw) }.getOrNull() ?: return@lineLoop
                        val ts = o.optLong("ts", 0L)
                        if (ts < cutoff || ts > now) return@lineLoop
                        scanned++
                        if (ts > lastEventTs) lastEventTs = ts
                        val lv = o.optString("lv", "")
                        val ev = o.optString("ev", "")
                        val mod = o.optString("mod", "")
                        val x = o.optJSONObject("x")

                        if (lv == "ERROR" || lv == "FATAL") {
                            errorCount++
                            if (lv == "FATAL") fatalCount++
                            if (ev == "MAIN_THREAD_BLOCKED" || mod == "ANR") anrCount++
                            if (errors.size >= 6) errors.removeAt(0)
                            errors.add("${shortTime(ts)} [$lv] $mod/$ev: ${clip(o.optString("msg"), 96)}")
                        }

                        when (ev) {
                            "EXIT_INFO" -> {
                                val reason = x?.optString("reasonName", "") ?: ""
                                val at = x?.optLong("timestamp", ts) ?: ts
                                if (reason.isNotEmpty() && reason !in benignExitReasons &&
                                    exitKeys.add(reason to at)
                                ) {
                                    val pss = x?.optInt("pssKb", 0) ?: 0
                                    exits.add(at to ("${shortTime(at)} $reason" +
                                        if (pss > 0) " · PSS ${pss / 1024}MB" else ""))
                                }
                            }
                            "JANK" -> {
                                jankCount++
                                val gap = x?.optLong("gapMs", 0L) ?: 0L
                                if (gap > jankMax) jankMax = gap
                            }
                            "FRAME_MONITOR_GAP" -> gapCount++
                            "CLICK_TO_FRAME" -> {
                                val cost = x?.optLong("costMs", 0L) ?: 0L
                                clickCount++
                                clickSum += cost
                                if (cost > clickMax) clickMax = cost
                                if (cost >= 100) {
                                    slowClicks.add(cost to "${shortTime(ts)} ${x?.optString("screen", o.optString("scr", "?"))}")
                                }
                            }
                            "QUERY_RESULT" -> {
                                val cost = x?.optLong("costMs", 0L) ?: 0L
                                queryCount++
                                querySum += cost
                                if (cost > queryMax) queryMax = cost
                                if (cost >= 80) {
                                    slowQueries.add(cost to "${shortTime(ts)} ${clip(o.optString("msg"), 64)}")
                                }
                            }
                            "SYS_SAMPLE" -> {
                                val pss = x?.optLong("pssMb", -1L) ?: -1L
                                val heap = x?.optLong("heapUsedMb", -1L) ?: -1L
                                val avail = x?.optLong("availMemMb", -1L) ?: -1L
                                if (pss >= 0) {
                                    memorySamples++
                                    pssSum += pss
                                    if (pss > pssMax) pssMax = pss
                                }
                                if (heap > heapMax) heapMax = heap
                                if (avail >= 0 && avail < availMemMin) availMemMin = avail
                            }
                            "TRIM_MEMORY" -> trimCount++
                        }
                    }
                }
            }
        }

        val version = runCatching { context.getAppVersion() }.getOrDefault("?")
        val device = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
        val header = buildString {
            append("MyScreenTime 诊断报告 · v").append(version).append('\n')
            append("设备: ").append(device).append(" · Android ").append(Build.VERSION.RELEASE)
                .append(" / SDK ").append(Build.VERSION.SDK_INT).append('\n')
            append("包名: ").append(context.packageName)
                .append(" · 使用情况访问: ").append(if (usageAccess() == "GRANTED") "已授权" else "未授权").append('\n')
            append("窗口: 最近 24 小时（").append(lineFormat.format(Date(cutoff)))
                .append(" 至 ").append(lineFormat.format(Date(now))).append("）\n")
            append("有效事件: ").append(scanned).append(" 条 · 最新记录: ")
                .append(if (lastEventTs > 0) lineFormat.format(Date(lastEventTs)) else "无")
                .append('\n')
        }

        val faults = buildString {
            append("【故障】ERROR/FATAL ").append(errorCount)
                .append("（FATAL ").append(fatalCount).append("） · 主线程阻塞/ANR ").append(anrCount).append('\n')
            if (errors.isEmpty()) append("  最近 24 小时未记录 ERROR/FATAL\n")
            else errors.forEach { append("  · ").append(it).append('\n') }
            if (exits.isNotEmpty()) {
                append("【系统记录的异常退出】（按原因+发生时间去重）\n")
                exits.sortedByDescending { it.first }.take(4).forEach {
                    append("  · ").append(it.second).append('\n')
                }
                append("  注：退出发生时间可能早于本报告窗口；上面只表示系统记录在本窗口被读取。\n")
            }
        }

        val performance = buildString {
            append("【交互性能】点击到首帧 ").append(clickCount)
            if (clickCount > 0) append(" 次 · 平均 ").append(clickSum / clickCount)
                .append("ms · 最差 ").append(clickMax).append("ms")
            append('\n')
            append("  交互掉帧 ").append(jankCount).append(" 次 · 最大间隔 ").append(jankMax).append("ms")
                .append(" · 监控中断间隔 ").append(gapCount).append(" 次\n")
            append("  查询 ").append(queryCount)
            if (queryCount > 0) append(" 次 · 平均 ").append(querySum / queryCount)
                .append("ms · 最差 ").append(queryMax).append("ms")
            append('\n')
            append("  内存采样 ").append(memorySamples).append(" 次")
            if (memorySamples > 0) append(" · PSS 平均/峰值 ").append(pssSum / memorySamples)
                .append("/").append(pssMax).append("MB")
            if (heapMax > 0) append(" · Heap 峰值 ").append(heapMax).append("MB")
            if (availMemMin != Long.MAX_VALUE) append(" · 可用内存最低 ").append(availMemMin).append("MB")
            append('\n')
            append("  内存回收事件 ").append(trimCount).append(" 次\n")
        }

        val hotspots = buildString {
            val clicksTop = slowClicks.sortedByDescending { it.first }.take(3)
            val queriesTop = slowQueries.sortedByDescending { it.first }.take(3)
            if (clicksTop.isNotEmpty() || queriesTop.isNotEmpty()) {
                append("【优先排查的慢操作】\n")
                clicksTop.forEach { append("  · 首帧 ").append(it.first).append("ms · ").append(it.second).append('\n') }
                queriesTop.forEach { append("  · 查询 ").append(it.first).append("ms · ").append(it.second).append('\n') }
            } else {
                append("【慢操作】最近 24 小时未达到记录阈值（首帧 100ms / 查询 80ms）\n")
            }
        }

        val guidance = "【下一步】请结合本报告窗口内的事件判断；若要定位具体调用链，再导出最近 500 行或完整日志。原始日志未删除。"
        val sections = listOf(header, faults, performance, hotspots, guidance)
        var report = sections.joinToString("\n")
        // Preserve device/window/fault context first; trim lower-priority detail before faults.
        if (report.length > maxChars) report = listOf(header, faults, performance, guidance).joinToString("\n")
        if (report.length > maxChars) report = listOf(header, faults, guidance).joinToString("\n")
        if (report.length > maxChars) report = report.take(maxChars - 16) + "\n…（报告过长，已截断）"
        return report.trimEnd()
    }

    /** 直接把摘要文本交给系统分享面板（微信/ChatGPT 等），不经过文件。 */
    fun shareDigest(): Boolean = try {
        val text = digest()
        event(
            "INFO", "AppLog", "EXPORT_DIGEST", "Shared compact digest",
            "chars" to text.length
        )
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.export_logs_subject))
        }
        context.startActivity(
            Intent.createChooser(send, context.getString(R.string.logs_digest_share))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        true
    } catch (e: Exception) {
        event("ERROR", "AppLog", "EXPORT_DIGEST", "Share digest failed", "err" to e.javaClass.simpleName)
        false
    }

    // ---- Export -------------------------------------------------------------

    private val aiPrompt = """
【给 AI 的提示 / Prompt】
这是一份 Android 应用「Screen Time（MyScreenTime）」的全量运行日志，由应用自动采集并永久保存，仅供开发者本人分析。
采集范围：生命周期/前后台、页面路径与停留时长、触摸坐标与手势、点击到首帧与查询耗时、掉帧、
系统聚合桶查询结果（条数/耗时/线程/范围）、内存与电量与进程状态、设备全量快照、崩溃与 ANR、手动标记。
请阅读以下内容并完成：
  1. 找出所有 ERROR / FATAL / CRASH / MAIN_THREAD_BLOCKED 记录，分析崩溃与异常的根本原因，给出可落地的修复方案
     （尽量指出应修改的模块、类或取数逻辑）。
  2. 检查 WARN、JANK、TRIM_MEMORY 与带耗时的记录，指出可能导致卡顿、被系统杀进程、或统计结果不准的地方。
     注意：JANK 只统计「交互中」（最近 1.2s 内有触摸）的帧间隔，是真实掉帧；
     IDLE_GAP 是界面静止时本来就没有新帧的间隔，不是卡顿，分析时请排除。
     判断体验好坏请优先看 CLICK_TO_FRAME（点击到下一帧的耗时）。
  3. 结合页面路径、触摸与响应时间，还原用户操作路径，提出功能与体验上的改进建议。
  4. 核对「采集统计」里的查询条数与耗时分布，判断各时间桶数据是否完整、排行与平均口径是否可信。
  5. 如果日志不足以判断，请列出还需要补充哪些埋点信息。
日志正文格式：时间 | 级别 | 模块 | [事件类型] 内容 | scr=当前页面 thr=线程 | 附加字段=值；
原始文件为每行一个 JSON 对象（ts/lv/mod/ev/msg/scr/thr/pid/fg/elapsed/x），便于脚本聚合。
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
        append(" MyScreenTime / Screen Time — 运行日志导出 (Full Runtime Log)\n")
        append(" 导出时间: ").append(lineFormat.format(Date())).append("\n")
        append("================================================================\n\n")
        append(aiPrompt).append("\n\n")
        append("【应用与设备信息 / Environment】\n")
        append(environment())
        append("Log files  : ").append(s.fileCount).append(" 个 (")
            .append(s.firstDay).append(" ~ ").append(s.lastDay).append("), ")
            .append(s.lineCount).append(" 行 / ")
            .append(String.format(Locale.US, "%.1f", s.totalBytes / 1024.0)).append(" KB\n")
        append("Retention  : 日志按天保存在应用私有目录 files/logs/，超过 4MB 自动分片，\n")
        append("             永久累积、绝不自动删除；只有卸载应用或清除应用数据才会清空。\n")
        append("Logcat     : ")
            .append(if (lastLogcatAvailable) "captured (see SYSTEM LOGCAT section)" else "UNAVAILABLE")
            .append('\n')
        append("StrictMode : enabled (violations appear in logcat); exit reasons from ApplicationExitInfo.\n\n")
        append("【采集统计 / CAPTURE SUMMARY】\n")
        append(summary()).append('\n')
        append("【设备全量快照 / DEVICE SNAPSHOT】\n")
        append(deviceSnapshotJson).append('\n')
        append("================================================================\n")
    }

    /** Builds one TXT containing everything, streaming the history without loading it all. */
    fun buildExportFile(): File {
        val dir = File(context.cacheDir, EXPORT_DIR).apply { mkdirs() }
        val out = File(dir, "MyScreenTime-logs-${fileStampFormat.format(Date())}.txt")
        // Read logcat first so the header can state whether it is actually present.
        val logcatText = readLogcat()
        out.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write(header())
            writer.write("\n===== 应用运行日志 / APP RUNTIME LOG (full history) =====\n")
            logFiles().forEach { file ->
                writer.write("\n----- ${file.name} -----\n")
                runCatching {
                    file.forEachLine(Charsets.UTF_8) { raw ->
                        writer.write(render(raw))
                        writer.newLine()
                    }
                }
            }
            writer.write("\n===== 系统 logcat（本进程，最近 600 行）/ SYSTEM LOGCAT =====\n")
            writer.write(logcatText)
        }
        return out
    }

    private fun readLogcat(): String = try {
        val process = Runtime.getRuntime().exec(
            arrayOf("logcat", "-d", "-t", "600", "--pid=${Process.myPid()}")
        )
        val text = process.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        process.destroy()
        lastLogcatAvailable = text.isNotBlank()
        when {
            text.isBlank() -> "(logcat 无输出或不可用)\n"
            text.endsWith("\n") -> text
            else -> text + "\n"
        }
    } catch (e: Exception) {
        lastLogcatAvailable = false
        "(无法读取 logcat: ${e.javaClass.simpleName})\n"
    }

    /** Shares the TXT through the system share sheet (no storage permission needed). */
    fun export(): Boolean = try {
        val started = SystemClock.uptimeMillis()
        val file = buildExportFile()
        val costMs = SystemClock.uptimeMillis() - started
        val s = stats()
        event(
            "INFO", "AppLog", "EXPORT", "Exported full runtime log",
            "file" to file.name,
            "bytes" to file.length(),
            "costMs" to costMs,
            "logFiles" to s.fileCount,
            "logLines" to s.lineCount,
            "hasLogcat" to lastLogcatAvailable
        )
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

    /** Short version for the clipboard: header + summary + the most recent lines. */
    fun clipboardText(): String =
        header() + "\n===== 最近日志 / RECENT LOG (last 500 lines) =====\n" + readRecent(500)

    /**
     * 只导出最近 [maxLines] 行：文件几十 KB，能直接发微信/邮件，
     * 不必把几 MB 的完整历史一股脑塞给对方。
     */
    fun exportRecent(maxLines: Int = 500): Boolean = try {
        val dir = File(context.cacheDir, EXPORT_DIR).apply { mkdirs() }
        val file = File(dir, "MyScreenTime-recent-${fileStampFormat.format(Date())}.txt")
        file.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write("================================================================\n")
            writer.write(" MyScreenTime — 最近 ${maxLines} 行日志 (Recent Runtime Log)\n")
            writer.write(" 导出时间: ${lineFormat.format(Date())}\n")
            writer.write("================================================================\n\n")
            writer.write("【应用与设备信息】\n")
            writer.write(environment())
            writer.write("\n【采集统计】\n")
            writer.write(summary())
            writer.write("\n===== 最近 ${maxLines} 行 / RECENT $maxLines LINES =====\n")
            writer.write(readRecent(maxLines))
            writer.newLine()
        }
        event(
            "INFO", "AppLog", "EXPORT_RECENT", "Exported recent lines",
            "lines" to maxLines, "bytes" to file.length()
        )
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.export_logs_subject))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(send, context.getString(R.string.export_logs))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        true
    } catch (e: Exception) {
        e("AppLog", "Failed to export recent log", e)
        false
    }
}
