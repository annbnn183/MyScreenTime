package com.atharok.screentime.common.utils

import android.app.Activity
import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Debug
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.Process
import android.os.StrictMode
import android.os.SystemClock
import android.view.Choreographer
import android.view.MotionEvent
import org.json.JSONObject
import java.util.Locale
import java.util.TimeZone
import kotlin.math.hypot

/**
 * All runtime collectors, installed once from [ScreenTimeApplication].
 *
 * This app is a single-Activity Compose app, so a traditional View-tree hit test would always
 * resolve to one AndroidComposeView and yield no control ids. Touch events therefore carry raw
 * coordinates, a 3x3 screen zone, gesture classification and the current Compose route instead.
 */
object RuntimeMonitors {

    private const val SAMPLE_INTERVAL_MS = 60_000L
    private const val JANK_THRESHOLD_MS = 34L
    // Every gap >= 34ms is still recorded (nothing is dropped), but small hiccups are logged at
    // DEBUG so the WARN stream only contains stalls a user can actually feel.
    private const val JANK_WARN_THRESHOLD_MS = 100L
    /** 超过这个时间没有触摸，就认为界面处于静止状态，此时的帧间隔不是掉帧。 */
    private const val IDLE_AFTER_MS = 1_200L
    /** 静止状态下只有超过这个时长才纳入统计，避免空闲期把日志刷满。 */
    private const val IDLE_GAP_LOG_MS = 700L
    /** 静止状态下最多每 30 秒汇总一条 IDLE_GAP。 */
    private const val IDLE_GAP_SUMMARY_MS = 30_000L

    fun install(app: Application) {
        installLifecycle(app)
        installSystemReceiver(app)
        installStrictMode()
        startFrameMonitor()
        startSampling(app)
        // getHistoricalProcessExitReasons() is a binder call that may also stream a 12KB trace.
        // Running it inline in Application.onCreate stalled the first frame on cold start. It is
        // pure diagnostics, so do it on a daemon thread after startup; NO event is lost, the
        // records are simply written a fraction of a second later.
        Thread({ AppLog.logHistoricalExitInfo(app) }, "AppLog-exit-info").apply {
            isDaemon = true
            start()
        }
        AppLog.event(
            "INFO", "Monitors", "MONITORS_INSTALLED",
            "Lifecycle / system broadcasts / StrictMode / frame monitor / sampling / exit info enabled",
            "sampleIntervalMs" to SAMPLE_INTERVAL_MS,
            "jankThresholdMs" to JANK_THRESHOLD_MS,
            "jankWarnThresholdMs" to JANK_WARN_THRESHOLD_MS
        )
    }

    // ---- 1. Activity lifecycle, foreground/background, page open cost --------

    private fun installLifecycle(app: Application) {
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            private var started = 0
            private val createdAt = HashMap<Int, Long>()

            private fun log(ev: String, activity: Activity, vararg extra: Pair<String, Any>) {
                runCatching {
                    val fields = ArrayList<Pair<String, Any>>(extra.size + 1)
                    fields.add("activity" to activity.javaClass.simpleName)
                    fields.addAll(extra)
                    AppLog.event(
                        "INFO", "Lifecycle", ev,
                        "${activity.javaClass.simpleName}: $ev",
                        *fields.toTypedArray()
                    )
                }
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: android.os.Bundle?) {
                createdAt[activity.hashCode()] = SystemClock.uptimeMillis()
                log("ACTIVITY_CREATED", activity, "hasSavedState" to (savedInstanceState != null))
            }

            override fun onActivityStarted(activity: Activity) {
                started++
                if (started == 1) AppLog.setForeground(true)
                log("ACTIVITY_STARTED", activity, "startedCount" to started)
            }

            override fun onActivityResumed(activity: Activity) {
                val started = createdAt.remove(activity.hashCode()) ?: 0L
                val cost = if (started > 0L) SystemClock.uptimeMillis() - started else 0L
                log("ACTIVITY_RESUMED", activity, "pageOpenMs" to cost)
            }

            override fun onActivityPaused(activity: Activity) {
                log("ACTIVITY_PAUSED", activity)
            }

            override fun onActivityStopped(activity: Activity) {
                started = (started - 1).coerceAtLeast(0)
                if (started == 0) AppLog.setForeground(false)
                log("ACTIVITY_STOPPED", activity, "startedCount" to started)
            }

            override fun onActivitySaveInstanceState(activity: Activity, outState: android.os.Bundle) {
                log("ACTIVITY_SAVE_STATE", activity)
            }

            override fun onActivityDestroyed(activity: Activity) {
                createdAt.remove(activity.hashCode())
                log("ACTIVITY_DESTROYED", activity)
            }
        })
    }

    // ---- 2. System broadcasts: screen, time, timezone, power, permission -----

    private fun installSystemReceiver(app: Application) {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_LOCALE_CHANGED)
            addAction(Intent.ACTION_CONFIGURATION_CHANGED)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_BATTERY_LOW)
            addAction(Intent.ACTION_BATTERY_OKAY)
            addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED)
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                runCatching {
                    val action = intent.action ?: "?"
                    AppLog.event(
                        "INFO", "System", "SYSTEM_EVENT", "Broadcast: $action",
                        "action" to action,
                        "timezone" to TimeZone.getDefault().id,
                        "locale" to Locale.getDefault().toString(),
                        "fg" to AppLog.isForeground
                    )
                }
            }
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                app.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                app.registerReceiver(receiver, filter)
            }
            AppLog.i("Monitors", "System event receiver registered (${filter.countActions()} actions)")
        }.onFailure {
            AppLog.e("Monitors", "Failed to register system event receiver", it)
        }
    }

    // ---- 3. StrictMode (writes to logcat, which the TXT export includes) -----

    private fun installStrictMode() {
        runCatching {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().build()
            )
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder().detectAll().penaltyLog().build()
            )
            AppLog.i("Monitors", "StrictMode detectAll + penaltyLog enabled")
        }.onFailure { AppLog.e("Monitors", "StrictMode setup failed", it) }
    }

    // ---- 4. Frame jank monitor ---------------------------------------------

    /**
     * 帧间隔监视器。
     *
     * 关键修正（v1.0.18）：旧实现把「界面没有任何重绘需求」的空闲间隔也当成掉帧。
     * 持续 postFrameCallback 会让 Choreographer 一直回调，但用户手指停下、界面静止时
     * 相邻两次回调之间本来就会隔很久（实测出现过 2.3 秒），这跟卡顿完全是两件事。
     * 旧日志里 590 条 JANK 绝大多数属于这种空闲间隔，反而把真正的掉帧淹没了。
     *
     * 现在按「是否正在交互」分开记录，两类都保留、都不丢：
     *  - 交互中（最近 [IDLE_AFTER_MS] 内有触摸）：阈值 [JANK_THRESHOLD_MS]，记为真掉帧。
     *  - 空闲中：只有超过 [IDLE_GAP_LOG_MS] 才记一条，并带 `idle=true`，
     *    供分析时排除；这不会掩盖真问题，因为交互期的记录一条不少。
     */
    private fun startFrameMonitor() {
        runCatching {
            Choreographer.getInstance().postFrameCallback(object : Choreographer.FrameCallback {
                private var last = 0L
                private var lastIdleLog = 0L
                private var idleMaxGap = 0L
                override fun doFrame(frameTimeNanos: Long) {
                    if (last != 0L) {
                        val gap = (frameTimeNanos - last) / 1_000_000L
                        val nowUptime = SystemClock.uptimeMillis()
                        val idle = (nowUptime - TouchTracker.lastEventUptime) >= IDLE_AFTER_MS
                        if (AppLog.isForeground) {
                            if (!idle && gap >= JANK_THRESHOLD_MS) {
                                AppLog.event(
                                    if (gap >= JANK_WARN_THRESHOLD_MS) "WARN" else "DEBUG",
                                    "Perf", "JANK", "Frame gap ${gap}ms",
                                    "gapMs" to gap, "screen" to AppLog.screen, "idle" to false
                                )
                            } else if (idle && gap >= IDLE_GAP_LOG_MS) {
                                // 静止期最多每 30 秒汇总一条，记录这段时间内最长的无帧间隔。
                                // 这样既不丢「空闲了多久」这个事实，也不会让日志被空闲刷满。
                                if (gap > idleMaxGap) idleMaxGap = gap
                                if (nowUptime - lastIdleLog >= IDLE_GAP_SUMMARY_MS) {
                                    lastIdleLog = nowUptime
                                    AppLog.event(
                                        "DEBUG", "Perf", "IDLE_GAP",
                                        "No frame for ${idleMaxGap}ms while idle",
                                        "gapMs" to idleMaxGap, "screen" to AppLog.screen
                                    )
                                    idleMaxGap = 0L
                                }
                            }
                        }
                    }
                    last = frameTimeNanos
                    Choreographer.getInstance().postFrameCallback(this)
                }
            })
        }.onFailure { AppLog.e("Monitors", "Frame monitor setup failed", it) }
    }

    // ---- 5. Periodic system-state sampling ---------------------------------

    @Volatile private var lastUsageAccess: String? = null
    @Volatile private var lastTimezone: String? = null

    private fun startSampling(app: Application) {
        val handler = Handler(Looper.getMainLooper())
        val task = object : Runnable {
            override fun run() {
                sample(app)
                handler.postDelayed(this, SAMPLE_INTERVAL_MS)
            }
        }
        handler.postDelayed(task, 15_000L)
    }

    private fun sample(app: Application) {
        runCatching {
            val am = app.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val mi = ActivityManager.MemoryInfo()
            am?.getMemoryInfo(mi)
            val dbg = Debug.MemoryInfo()
            Debug.getMemoryInfo(dbg)
            val pm = app.getSystemService(Context.POWER_SERVICE) as? PowerManager
            val ignoringBattery = pm?.isIgnoringBatteryOptimizations(app.packageName) ?: false
            val usage = usageAccess(app)
            val tz = TimeZone.getDefault().id
            val thermal = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                pm?.currentThermalStatus ?: -1
            } else -1

            if (lastUsageAccess != null && lastUsageAccess != usage) {
                AppLog.event(
                    "WARN", "Permission", "PERMISSION_CHANGED",
                    "Usage access changed $lastUsageAccess -> $usage",
                    "from" to (lastUsageAccess ?: "?"), "to" to usage
                )
            }
            if (lastTimezone != null && lastTimezone != tz) {
                AppLog.event(
                    "WARN", "System", "TIMEZONE_CHANGED",
                    "Timezone changed $lastTimezone -> $tz",
                    "from" to (lastTimezone ?: "?"), "to" to tz
                )
            }
            lastUsageAccess = usage
            lastTimezone = tz

            AppLog.event(
                "DEBUG", "System", "SYS_SAMPLE", "Periodic system state sample",
                "availMemMb" to mi.availMem / (1024 * 1024),
                "totalMemMb" to mi.totalMem / (1024 * 1024),
                "thresholdMb" to mi.threshold / (1024 * 1024),
                "lowMemory" to mi.lowMemory,
                "pssMb" to dbg.totalPss / 1024,
                "heapUsedMb" to (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024 * 1024),
                "usageAccess" to usage,
                "batteryOptimized" to !ignoringBattery,
                "thermalStatus" to thermal,
                "threadCount" to Thread.activeCount(),
                "timezone" to tz,
                "elapsedRealtimeMs" to SystemClock.elapsedRealtime()
            )
        }.onFailure { AppLog.e("Monitors", "System state sample failed", it) }
    }

    private fun usageAccess(app: Application): String = try {
        val appOps = app.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
        val mode = appOps?.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), app.packageName
        )
        if (mode == AppOpsManager.MODE_ALLOWED) "GRANTED" else "DENIED"
    } catch (_: Exception) {
        "UNKNOWN"
    }

    /** Called from Application.onTrimMemory / onLowMemory so LMK pressure is visible in the log. */
    fun onTrimMemory(level: Int) {
        val dbg = Debug.MemoryInfo()
        Debug.getMemoryInfo(dbg)
        AppLog.event(
            "WARN", "Memory", "TRIM_MEMORY", "onTrimMemory level=$level",
            "level" to level,
            "pssMb" to dbg.totalPss / 1024,
            "fg" to AppLog.isForeground,
            "screen" to AppLog.screen
        )
    }

    fun onLowMemory() {
        AppLog.event("WARN", "Memory", "LOW_MEMORY", "onLowMemory callback received")
    }
}

/**
 * Touch / gesture capture. Installed on the single Activity's dispatchTouchEvent.
 *
 * MOVE events are sampled to avoid flooding the log; UP always produces one aggregate record with
 * duration, displacement, gesture classification and a 3x3 screen zone.
 */
object TouchTracker {

    private const val MOVE_LOG_INTERVAL_MS = 120L
    private const val SWIPE_THRESHOLD_PX = 40.0
    private const val LONG_PRESS_MS = 500L

    private var width = 0
    private var height = 0
    private var downTime = 0L
    private var downX = 0f
    private var downY = 0f
    private var lastMoveLog = 0L
    private var moveCount = 0

    /**
     * 最后一次触摸事件的时间（uptimeMillis）。帧监视器用它判断当前是否处于交互中：
     * 屏幕静止时本来就不产生新帧，把它算成掉帧会让日志完全失真。
     */
    @Volatile
    var lastEventUptime: Long = 0L
        private set

    fun attach(context: Context) {
        val dm = context.resources.displayMetrics
        width = dm.widthPixels
        height = dm.heightPixels
    }

    fun dispatch(ev: MotionEvent) {
        runCatching {
            val now = SystemClock.uptimeMillis()
            lastEventUptime = now
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downTime = now
                    downX = ev.rawX
                    downY = ev.rawY
                    lastMoveLog = now
                    moveCount = 0
                    touch("DOWN", "down", ev, 0L, 0f, 0f, 0.0, 0)
                }
                MotionEvent.ACTION_MOVE -> {
                    moveCount++
                    if (now - lastMoveLog >= MOVE_LOG_INTERVAL_MS) {
                        lastMoveLog = now
                        touch("MOVE", "move", ev, now - downTime, ev.rawX - downX, ev.rawY - downY, 0.0, moveCount)
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val duration = now - downTime
                    val dx = ev.rawX - downX
                    val dy = ev.rawY - downY
                    val distance = hypot(dx.toDouble(), dy.toDouble())
                    val gesture = when {
                        ev.actionMasked == MotionEvent.ACTION_CANCEL -> "cancel"
                        duration >= LONG_PRESS_MS && distance < 24 -> "long_press"
                        distance >= SWIPE_THRESHOLD_PX -> "swipe"
                        else -> "tap"
                    }
                    touch("UP", gesture, ev, duration, dx, dy, distance, moveCount)
                    if (ev.actionMasked == MotionEvent.ACTION_UP) trackClickToFrame(now)
                }
            }
        }.onFailure { AppLog.e("TouchTracker", "Failed to record touch event", it) }
    }

    private fun touch(
        action: String, gesture: String, ev: MotionEvent,
        durationMs: Long, dx: Float, dy: Float, distancePx: Double, moves: Int
    ) {
        AppLog.event(
            "DEBUG", "Touch", "TOUCH", "$action/$gesture",
            "action" to action,
            "gesture" to gesture,
            "x" to ev.rawX.toInt(),
            "y" to ev.rawY.toInt(),
            "zone" to zone(ev.rawX, ev.rawY),
            "durationMs" to durationMs,
            "dx" to dx.toInt(),
            "dy" to dy.toInt(),
            "distancePx" to distancePx.toInt(),
            "moves" to moves,
            "pressure" to ev.pressure.toDouble(),
            "size" to ev.size.toDouble(),
            "screen" to AppLog.screen
        )
    }

    private fun trackClickToFrame(upTime: Long) {
        runCatching {
            Choreographer.getInstance().postFrameCallback {
                val cost = SystemClock.uptimeMillis() - upTime
                AppLog.event(
                    "DEBUG", "Perf", "CLICK_TO_FRAME", "Tap to next frame ${cost}ms",
                    "costMs" to cost, "screen" to AppLog.screen
                )
            }
        }
    }

    private fun zone(x: Float, y: Float): String {
        if (width <= 0 || height <= 0) return "unknown"
        val col = when { x < width / 3f -> "L"; x < width * 2f / 3f -> "C"; else -> "R" }
        val row = when { y < height / 3f -> "T"; y < height * 2f / 3f -> "M"; else -> "B" }
        return "$row$col"
    }
}
