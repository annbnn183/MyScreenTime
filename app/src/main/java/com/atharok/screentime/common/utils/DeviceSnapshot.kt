package com.atharok.screentime.common.utils

import android.app.ActivityManager
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.hardware.Sensor
import android.hardware.SensorManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.net.wifi.WifiManager
import android.nfc.NfcAdapter
import android.os.BatteryManager
import android.os.Build
import android.os.Debug
import android.os.Environment
import android.os.PowerManager
import android.os.Process
import android.os.StatFs
import android.os.SystemClock
import android.provider.Settings
import android.telephony.TelephonyManager
import android.view.WindowManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import java.util.TimeZone

/**
 * One-shot, full device / system / environment snapshot written at every cold start.
 *
 * Everything goes through [section] so a single unavailable API (hidden SystemProperties, a
 * permission-gated telephony field, a missing /proc node, ...) can never abort the snapshot;
 * the failure reason is recorded instead of silently dropping the field.
 */
object DeviceSnapshot {

    fun collect(context: Context): String {
        val root = JSONObject()
        root.put("capturedAtMs", System.currentTimeMillis())
        section(root, "device") { device() }
        section(root, "systemProperties") { rom() }
        section(root, "display") { display(context) }
        section(root, "batteryAndPower") { battery(context) }
        section(root, "memory") { memory(context) }
        section(root, "cpu") { cpu() }
        section(root, "storage") { storage(context) }
        section(root, "network") { network(context) }
        section(root, "sensors") { sensors(context) }
        section(root, "audioAndPeripherals") { audio(context) }
        section(root, "settings") { settings(context) }
        section(root, "process") { process(context) }
        section(root, "app") { app(context) }
        return root.toString(2)
    }

    private fun section(root: JSONObject, key: String, block: () -> JSONObject) {
        try {
            root.put(key, block())
        } catch (e: Throwable) {
            root.put(key, JSONObject().put("SNAPSHOT_FAIL", "${e.javaClass.simpleName}: ${e.message}"))
            AppLog.w("DeviceSnapshot", "section '$key' failed: ${e.javaClass.simpleName}: ${e.message}", e)
        }
    }

    // ---- device / system ----------------------------------------------------

    private fun device(): JSONObject = JSONObject().apply {
        put("manufacturer", Build.MANUFACTURER)
        put("brand", Build.BRAND)
        put("model", Build.MODEL)
        put("device", Build.DEVICE)
        put("product", Build.PRODUCT)
        put("hardware", Build.HARDWARE)
        put("board", Build.BOARD)
        put("bootloader", Build.BOOTLOADER)
        put("display", Build.DISPLAY)
        put("fingerprint", Build.FINGERPRINT)
        put("tags", Build.TAGS)
        put("type", Build.TYPE)
        put("host", Build.HOST)
        put("user", Build.USER)
        put("supportedAbis", Build.SUPPORTED_ABIS.joinToString(", "))
        put("sdkInt", Build.VERSION.SDK_INT)
        put("release", Build.VERSION.RELEASE)
        put("incremental", Build.VERSION.INCREMENTAL)
        put("securityPatch", Build.VERSION.SECURITY_PATCH)
        put("radioVersion", Build.getRadioVersion() ?: "?")
        put("osArch", System.getProperty("os.arch") ?: "?")
        put("vmVersion", System.getProperty("java.vm.version") ?: "?")
    }

    /** Vendor ROM identifiers are hidden API; read them reflectively, record any failure. */
    private fun rom(): JSONObject = JSONObject().apply {
        val keys = listOf(
            "ro.miui.ui.version.name", "ro.miui.ui.version.code",
            "ro.mi.os.version.name", "ro.mi.os.version.incremental",
            "ro.product.mod_device", "ro.build.version.oppo",
            "ro.vivo.os.version", "ro.build.fingerprint",
            "ro.build.characteristics", "ro.product.cpu.abilist"
        )
        try {
            val clazz = Class.forName("android.os.SystemProperties")
            val get = clazz.getMethod("get", String::class.java)
            for (key in keys) {
                val value = runCatching { get.invoke(null, key) as? String ?: "" }.getOrDefault("")
                if (value.isNotEmpty()) put(key, value)
                else put(key, "UNAVAILABLE")
                Unit
            }
        } catch (e: Throwable) {
            put("SNAPSHOT_FAIL", "SystemProperties reflection: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    @Suppress("DEPRECATION")
    private fun display(context: Context): JSONObject = JSONObject().apply {
        val dm = context.resources.displayMetrics
        put("widthPx", dm.widthPixels)
        put("heightPx", dm.heightPixels)
        put("density", dm.density.toDouble())
        put("densityDpi", dm.densityDpi)
        put("scaledDensity", dm.scaledDensity.toDouble())
        put("xdpi", dm.xdpi.toDouble())
        put("ydpi", dm.ydpi.toDouble())
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        val d = wm?.defaultDisplay
        if (d != null) {
            put("refreshRateHz", d.refreshRate.toDouble())
            val mode = d.mode
            put("modePhysicalWidth", mode.physicalWidth)
            put("modePhysicalHeight", mode.physicalHeight)
            put("modeRefreshRate", mode.refreshRate.toDouble())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                put("isHdr", d.isHdr)
                put("isWideColorGamut", d.isWideColorGamut)
            }
        }
        val cfg: Configuration = context.resources.configuration
        put("uiMode", cfg.uiMode)
        put("orientation", cfg.orientation)
        put("screenLayout", cfg.screenLayout)
        put("smallestScreenWidthDp", cfg.smallestScreenWidthDp)
        val cr = context.contentResolver
        put("screenBrightness", runCatching {
            Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS, -1)
        }.getOrDefault(-1))
        put("screenOffTimeoutMs", runCatching {
            Settings.System.getString(cr, Settings.System.SCREEN_OFF_TIMEOUT) ?: "?"
        }.getOrDefault("UNAVAILABLE"))
        put("fontScale", runCatching {
            Settings.System.getFloat(cr, Settings.System.FONT_SCALE, 1f).toDouble()
        }.getOrDefault(1.0))
    }

    @Suppress("DEPRECATION")
    private fun battery(context: Context): JSONObject = JSONObject().apply {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        bm?.let {
            put("capacityPct", it.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY))
            put("chargeCounterUah", it.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER))
            put("currentNowUa", it.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW))
            put("energyCounterNwh", it.getLongProperty(BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER))
        }
        val sticky = runCatching {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }.getOrNull()
        if (sticky != null) {
            put("level", sticky.getIntExtra("level", -1))
            put("scale", sticky.getIntExtra("scale", -1))
            put("temperatureDeciC", sticky.getIntExtra("temperature", -1))
            put("voltageMv", sticky.getIntExtra("voltage", -1))
            put("health", sticky.getIntExtra("health", -1))
            put("status", sticky.getIntExtra("status", -1))
            put("plugged", sticky.getIntExtra("plugged", -1))
            put("technology", sticky.getStringExtra("technology") ?: "?")
        } else {
            put("BATTERY_CHANGED", "UNAVAILABLE(registerReceiver failed)")
        }
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        pm?.let {
            put("powerSaveMode", it.isPowerSaveMode)
            put("deviceIdleMode", it.isDeviceIdleMode)
            put("interactive", it.isInteractive)
            put("ignoringBatteryOptimizations", it.isIgnoringBatteryOptimizations(context.packageName))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put("thermalStatus", it.currentThermalStatus)
            }
        }
    }

    private fun memory(context: Context): JSONObject = JSONObject().apply {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(mi)
        put("totalMemBytes", mi.totalMem)
        put("availMemBytes", mi.availMem)
        put("lowMemory", mi.lowMemory)
        put("thresholdBytes", mi.threshold)
        val dbg = Debug.MemoryInfo()
        Debug.getMemoryInfo(dbg)
        put("pssKb", dbg.totalPss)
        put("privateDirtyKb", dbg.totalPrivateDirty)
        put("sharedDirtyKb", dbg.totalSharedDirty)
        val r = Runtime.getRuntime()
        put("maxHeapBytes", r.maxMemory())
        put("heapTotalBytes", r.totalMemory())
        put("heapFreeBytes", r.freeMemory())
        put("procMeminfo", procSummary("/proc/meminfo", listOf("MemTotal", "MemAvailable", "SwapTotal", "SwapFree")))
    }

    private fun cpu(): JSONObject = JSONObject().apply {
        put("cores", Runtime.getRuntime().availableProcessors())
        put("loadavg", firstLine("/proc/loadavg"))
        put("stat", firstLine("/proc/stat"))
        put("cpuinfo", procSummary("/proc/cpuinfo", listOf("Hardware", "Processor", "BogoMIPS")))
    }

    private fun storage(context: Context): JSONObject = JSONObject().apply {
        val stat = StatFs(Environment.getDataDirectory().path)
        put("dataTotalBytes", stat.totalBytes)
        put("dataAvailBytes", stat.availableBytes)
        put("dataFreeBytes", stat.freeBytes)
        put("externalStorageState", Environment.getExternalStorageState())
        put("filesDirUsableBytes", File(context.filesDir.path).usableSpace)
        put("cacheDirUsableBytes", File(context.cacheDir.path).usableSpace)
    }

    @Suppress("MissingPermission", "DEPRECATION")
    private fun network(context: Context): JSONObject = JSONObject().apply {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val net = cm?.activeNetwork
        put("hasActiveNetwork", net != null)
        val caps = if (net != null) cm?.getNetworkCapabilities(net) else null
        caps?.let {
            put("downKbps", it.linkDownstreamBandwidthKbps)
            put("upKbps", it.linkUpstreamBandwidthKbps)
            put("metered", !it.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED))
            put("vpn", it.hasTransport(NetworkCapabilities.TRANSPORT_VPN))
            put("wifi", it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))
            put("cellular", it.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR))
            put("validated", it.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))
        }
        val lp = if (net != null) cm?.getLinkProperties(net) else null
        lp?.let {
            put("iface", it.interfaceName ?: "?")
            put("dnsServers", it.dnsServers.joinToString(", ") { d -> d.hostAddress ?: "?" })
        }
        put("totalRxBytes", TrafficStats.getTotalRxBytes())
        put("totalTxBytes", TrafficStats.getTotalTxBytes())
        put("uidRxBytes", TrafficStats.getUidRxBytes(Process.myUid()))
        put("uidTxBytes", TrafficStats.getUidTxBytes(Process.myUid()))
        val wm = context.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        wm?.let { put("wifiEnabled", it.isWifiEnabled) }
        val info = wm?.connectionInfo
        info?.let {
            put("bssid", it.bssid ?: "?")
            put("rssi", it.rssi)
            put("linkSpeedMbps", it.linkSpeed)
            put("frequencyMHz", if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) it.frequency else -1)
        }
        // SSID requires location permission on modern Android; record the reason instead.
        put("ssid", runCatching { info?.ssid ?: "?" }.getOrDefault("UNAVAILABLE(permission)"))
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        tm?.let {
            put("networkOperatorName", runCatching { it.networkOperatorName ?: "?" }.getOrDefault("UNAVAILABLE(permission)"))
            put("simCountryIso", runCatching { it.simCountryIso ?: "?" }.getOrDefault("UNAVAILABLE(permission)"))
            put("phoneType", it.phoneType)
        }
    }

    private fun sensors(context: Context): JSONObject = JSONObject().apply {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val arr = JSONArray()
        sm?.getSensorList(Sensor.TYPE_ALL)?.forEach { s ->
            arr.put(JSONObject().apply {
                put("name", s.name)
                put("vendor", s.vendor)
                put("type", s.type)
                put("version", s.version)
                put("maxRange", s.maximumRange.toDouble())
                put("resolution", s.resolution.toDouble())
                put("powerMa", s.power.toDouble())
                put("minDelayUs", s.minDelay)
            })
        }
        put("count", arr.length())
        put("list", arr)
    }

    private fun audio(context: Context): JSONObject = JSONObject().apply {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        am?.let {
            put("musicVolume", it.getStreamVolume(AudioManager.STREAM_MUSIC))
            put("musicMaxVolume", it.getStreamMaxVolume(AudioManager.STREAM_MUSIC))
            put("musicActive", it.isMusicActive)
            val devices = it.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            put("outputDevices", devices.joinToString(", ") { d -> "${d.type}:${d.productName}" })
        }
        put("nfcEnabled", runCatching {
            NfcAdapter.getDefaultAdapter(context)?.isEnabled?.toString() ?: "no_nfc"
        }.getOrDefault("UNAVAILABLE"))
        put("bluetoothEnabled", runCatching {
            (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)
                ?.adapter?.isEnabled?.toString() ?: "no_bluetooth"
        }.getOrDefault("UNAVAILABLE(permission)"))
    }

    private fun settings(context: Context): JSONObject = JSONObject().apply {
        val cr = context.contentResolver
        fun global(name: String) = runCatching {
            Settings.Global.getString(cr, name) ?: "null"
        }.getOrDefault("UNAVAILABLE")
        fun secure(name: String) = runCatching {
            Settings.Secure.getString(cr, name) ?: "null"
        }.getOrDefault("UNAVAILABLE")
        put("developmentSettingsEnabled", global(Settings.Global.DEVELOPMENT_SETTINGS_ENABLED))
        put("adbEnabled", global(Settings.Global.ADB_ENABLED))
        put("airplaneModeOn", global(Settings.Global.AIRPLANE_MODE_ON))
        put("accessibilityEnabled", secure(Settings.Secure.ACCESSIBILITY_ENABLED))
        put("enabledAccessibilityServices", secure(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES))
        put("androidId", secure(Settings.Secure.ANDROID_ID))
        put("locale", Locale.getDefault().toString())
        put("timeZone", TimeZone.getDefault().id)
        put("wallClockMs", System.currentTimeMillis())
        put("elapsedRealtimeMs", SystemClock.elapsedRealtime())
        put("uptimeMs", SystemClock.uptimeMillis())
    }

    private fun process(context: Context): JSONObject = JSONObject().apply {
        put("pid", Process.myPid())
        put("uid", Process.myUid())
        put("tid", Process.myTid())
        put("elapsedCpuMs", Process.getElapsedCpuTime())
        put("is64Bit", if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Process.is64Bit() else false)
        val state = ActivityManager.RunningAppProcessInfo()
        ActivityManager.getMyMemoryState(state)
        put("importance", state.importance)
        put("lru", state.lru)
        put("lastTrimLevel", state.lastTrimLevel)
        put("debuggerConnected", Debug.isDebuggerConnected())
        put("waitingForDebugger", Debug.waitingForDebugger())
        put("oomScoreAdj", firstLine("/proc/self/oom_score_adj"))
        put("threadCount", Thread.activeCount())
    }

    @Suppress("DEPRECATION")
    private fun app(context: Context): JSONObject = JSONObject().apply {
        val pm = context.packageManager
        val pi = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
        put("packageName", context.packageName)
        put("versionName", pi.versionName ?: "?")
        put("versionCode", if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) pi.longVersionCode else pi.versionCode.toLong())
        put("firstInstallTime", pi.firstInstallTime)
        put("lastUpdateTime", pi.lastUpdateTime)
        put("targetSdk", pi.applicationInfo?.targetSdkVersion ?: -1)
        put("minSdk", pi.applicationInfo?.minSdkVersion ?: -1)
        put("signatureSha1", pi.signatures?.joinToString(", ") { sha1(it.toByteArray()) } ?: "none")
        put("installerPackage", runCatching {
            pm.getInstallerPackageName(context.packageName) ?: "?"
        }.getOrDefault("UNAVAILABLE"))
        put("debuggable", (pi.applicationInfo?.flags ?: 0) and ApplicationInfo.FLAG_DEBUGGABLE != 0)
    }

    // ---- helpers ------------------------------------------------------------

    private fun firstLine(path: String): String = runCatching {
        File(path).bufferedReader().use { it.readLine() ?: "" }
    }.getOrDefault("UNAVAILABLE($path)")

    private fun procSummary(path: String, keys: List<String>): JSONObject = JSONObject().apply {
        runCatching {
            File(path).forEachLine { line ->
                val key = line.substringBefore(':').trim()
                if (keys.any { key == it }) put(key, line.substringAfter(':').trim())
            }
        }
    }

    private fun sha1(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-1").digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
