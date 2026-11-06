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

    /**
     * 批量写入多天的结果，只在全部合并完成后落盘一次。
     *
     * 逐日 [mergeDay] 每写一天都要序列化并重写整个 JSON 文件；补算 365 天就是 365 次全量写盘，
     * 这是补算慢、内存占用高的主要原因。批量接口把 IO 次数从 N 次降到 1 次。
     */
    fun mergeDays(results: Map<Long, Map<String, Int>>) {
        if (results.isEmpty()) return
        synchronized(lock) {
            results.forEach { (day, counts) ->
                val key = day.toString()
                val target = days.getOrPut(key) { mutableMapOf() }
                counts.forEach { (pkg, count) ->
                    if (count > 0) target[pkg] = maxOf(target[pkg] ?: 0, count)
                }
                scanned.add(key)
            }
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

    /** 把全部永久存档序列化为 JSON，供「完整数据备份」复用。 */
    fun toJson(): JSONObject = synchronized(lock) {
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
        root
    }

    /** 用备份里的 JSON 覆盖本地存档（先清空再写入），失败抛异常交由上层处理。 */
    fun fromJson(json: JSONObject) {
        val newDays = mutableMapOf<String, MutableMap<String, Int>>()
        val newScanned = mutableSetOf<String>()
        val daysObj = json.optJSONObject("days") ?: JSONObject()
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
            newDays[day] = counts
            newScanned.add(day)
        }
        val arr = json.optJSONArray("scanned")
        if (arr != null) {
            for (i in 0 until arr.length()) newScanned.add(arr.optString(i))
        } else {
            newScanned.addAll(newDays.keys)
        }
        synchronized(lock) {
            days.clear()
            days.putAll(newDays)
            scanned.clear()
            scanned.addAll(newScanned)
            persist()
        }
    }

    /**
     * 合并备份里的历史（「合并恢复」用）：逐日逐应用取较大值，并集 scanned。
     *
     * 取 max 而不是求和，保证重复导入同一份备份不会让打开次数翻倍。
     */
    fun mergeFromJson(json: JSONObject) = synchronized(lock) {
        val daysObj = json.optJSONObject("days")
        if (daysObj != null) {
            val dayKeys = daysObj.keys()
            while (dayKeys.hasNext()) {
                val day = dayKeys.next()
                val obj = daysObj.optJSONObject(day) ?: continue
                val target = days.getOrPut(day) { mutableMapOf() }
                val pkgKeys = obj.keys()
                while (pkgKeys.hasNext()) {
                    val pkg = pkgKeys.next()
                    val count = obj.optInt(pkg, 0)
                    if (count > 0) target[pkg] = maxOf(target[pkg] ?: 0, count)
                }
            }
        }
        val arr = json.optJSONArray("scanned")
        if (arr != null) {
            for (i in 0 until arr.length()) scanned.add(arr.optString(i))
        } else {
            daysObj?.let { obj ->
                val keys = obj.keys()
                while (keys.hasNext()) scanned.add(keys.next())
            }
        }
        persist()
    }

    /** 清空全部打开次数历史（用户自主删除数据）。 */
    fun clearAll() = synchronized(lock) {
        days.clear()
        scanned.clear()
        persist()
    }

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
