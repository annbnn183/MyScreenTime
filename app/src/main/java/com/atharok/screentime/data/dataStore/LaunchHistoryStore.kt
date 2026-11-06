package com.atharok.screentime.data.dataStore

import android.content.Context
import com.atharok.screentime.common.utils.AppLog
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 永久保存「每天 / 每个应用被打开的次数」。
 *
 * Android 的 WEEKLY / MONTHLY / YEARLY 预聚合桶只保存前台时长，不保存启动次数，
 * 因此只有逐日累积的本地记录才能给出 4 周 / 半年 / 一年真实的点击量。
 * 本文件只追加、永不删除；只有卸载应用或清除数据才会清掉。
 */
class LaunchHistoryStore(private val context: Context) {

    private val file = File(context.filesDir, "launch_history.json")
    private val lock = Any()

    /** day timestamp (String) -> packageName -> launches */
    private val days: MutableMap<String, MutableMap<String, Int>> = mutableMapOf()
    /** 已经检查过的天（即使当天没有任何点击，也记录下来，避免反复扫描）。 */
    private val scanned: MutableSet<String> = mutableSetOf()

    init {
        load()
    }

    private fun load() {
        synchronized(lock) {
            if (!file.exists()) return@synchronized
            try {
                val root = JSONObject(file.readText())
                val daysObj = root.optJSONObject("days") ?: return@synchronized
                val dayKeys = daysObj.keys()
                while (dayKeys.hasNext()) {
                    val day = dayKeys.next()
                    val obj = daysObj.optJSONObject(day) ?: continue
                    val counts = mutableMapOf<String, Int>()
                    val pkgKeys = obj.keys()
                    while (pkgKeys.hasNext()) {
                        val pkg = pkgKeys.next()
                        counts[pkg] = obj.optInt(pkg, 0)
                    }
                    days[day] = counts
                }
                val arr = root.optJSONArray("scanned")
                if (arr != null) {
                    for (i in 0 until arr.length()) scanned.add(arr.optString(i))
                } else {
                    scanned.addAll(days.keys)
                }
            } catch (e: Exception) {
                AppLog.e("LaunchHistoryStore", "Failed to read launch history", e)
            }
        }
    }

    /** 合并写入某一天的结果，并把该天标记为已扫描。取 max，避免较小结果覆盖已有数据。 */
    fun mergeDay(day: Long, counts: Map<String, Int>) {
        synchronized(lock) {
            val key = day.toString()
            val target = days.getOrPut(key) { mutableMapOf() }
            counts.forEach { (pkg, count) ->
                if (count > 0) target[pkg] = maxOf(target[pkg] ?: 0, count)
            }
            scanned.add(key)
            persist()
        }
    }

    fun isScanned(day: Long): Boolean = synchronized(lock) { day.toString() in scanned }

    /** 累加区间内所有已记录天的点击量。 */
    fun sumRange(start: Long, end: Long): Map<String, Int> = synchronized(lock) {
        val result = mutableMapOf<String, Int>()
        days.forEach { (day, counts) ->
            val timestamp = day.toLongOrNull() ?: return@forEach
            if (timestamp < start || timestamp > end) return@forEach
            counts.forEach { (pkg, count) ->
                result[pkg] = (result[pkg] ?: 0) + count
            }
        }
        result
    }

    /** 区间内已扫描（已有点击量记录）的天数。 */
    fun countScannedInRange(start: Long, end: Long): Int = synchronized(lock) {
        scanned.count { key ->
            val timestamp = key.toLongOrNull() ?: return@count false
            timestamp in start..end
        }
    }

    /** 最早已扫描的一天；尚无记录返回 null。 */
    fun earliestScannedDay(): Long? = synchronized(lock) {
        scanned.mapNotNull { it.toLongOrNull() }.minOrNull()
    }

    fun scannedDays(): Int = synchronized(lock) { scanned.size }

    private fun persist() {
        try {
            val root = JSONObject()
            val daysObj = JSONObject()
            days.forEach { (day, counts) ->
                val obj = JSONObject()
                counts.forEach { (pkg, count) -> obj.put(pkg, count) }
                daysObj.put(day, obj)
            }
            root.put("version", 1)
            root.put("days", daysObj)
            root.put("scanned", JSONArray(scanned.toList()))
            val tmp = File(file.parentFile, "launch_history.json.tmp")
            tmp.writeText(root.toString())
            if (!tmp.renameTo(file)) {
                file.writeText(root.toString())
                tmp.delete()
            }
        } catch (e: Exception) {
            AppLog.e("LaunchHistoryStore", "Failed to persist launch history", e)
        }
    }
}
