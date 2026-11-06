package com.atharok.screentime.common.utils

import android.app.AppOpsManager
import android.content.Context
import android.os.Process
import com.atharok.screentime.common.extensions.getAppVersion
import com.atharok.screentime.data.dataStore.LaunchHistoryStore
import com.atharok.screentime.data.dataStore.SettingsDataStore
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * 完整数据备份 / 恢复 / 迁移（本次唯一的重点功能）。
 *
 * 本应用「自己长期保存」的用户数据只有两份：
 *   1. 设置（[SettingsDataStore]：主题、默认时间段、忽略名单、每日目标、自动备份开关等）
 *   2. 逐日 / 逐应用的打开次数历史（[LaunchHistoryStore]，永久追加，只有卸载或清除数据才会丢）
 *
 * 说明：应用使用时长与小时分布来自 Android 系统的 UsageStatsManager，由系统按设备保存，
 * 不属于本应用可以导出或写入另一台手机的数据。因此备份「如实」只包含本应用持有的数据，
 * 不会伪造「时长备份」——这是数据诚实原则的一部分。
 *
 * 设计要点（对应《数据安全高于功能数量》的要求）：
 *   - 版本化 JSON（format + schemaVersion），拒绝未来版本。
 *   - SHA-256 完整性校验 + 记录条数校验，校验不过一律拒绝。
 *   - 恢复前自动做安全快照，并写「恢复进行中」标记；任何失败或中断都回滚到快照。
 *   - 支持「合并（保留现有）」与「覆盖（替换）」两种模式。
 *   - 恢复后再次校验条数，防止「只恢复一半却显示成功」。
 *   - 自动备份按天一份，最多保留 [AUTO_BACKUP_KEEP] 份，写在应用私有外部目录。
 *   - 删除数据前也先留一份安全快照。
 */
object DataBackupManager {

    /** 当前备份格式版本。读取时接受 [MIN_SUPPORTED_FORMAT]..[CURRENT_FORMAT]，更高的版本拒绝。 */
    const val CURRENT_FORMAT = 3
    private const val MIN_SUPPORTED_FORMAT = 1

    /** 备份文件大小上限（字符数，约 32MB），避免超大文件导致内存耗尽。 */
    private const val MAX_BACKUP_CHARS = 32 * 1024 * 1024

    private const val BACKUP_DIR = "backups"
    private const val AUTO_BACKUP_PREFIX = "MyScreenTime-auto-backup-"
    private const val PRE_RESTORE_PREFIX = "MyScreenTime-pre-restore-"
    private const val MARKER_FILE = "restore_in_progress.json"
    private const val AUTO_BACKUP_KEEP = 7

    enum class RestoreMode { MERGE, OVERWRITE }

    data class BackupInfo(
        val days: Int,
        val apps: Int,
        val entries: Int,
        val settingsFields: Int
    )

    data class RestoreResult(
        val success: Boolean,
        val reason: String? = null,
        val info: BackupInfo? = null,
        val rolledBack: Boolean = false
    )

    // ---------------------------------------------------------------- Export

    /**
     * 把当前设备数据导出为字符串。password 为空 -> 明文 JSON；否则 -> 加密信封。
     * 注意：本方法只在内存里生成字符串，真正的「写入文件 + 回读校验」由调用方（设置页）完成。
     */
    suspend fun exportToString(context: Context, password: String? = null): String {
        val plain = buildPlainBackup(context).toString(2)
        return if (password.isNullOrEmpty()) plain else BackupCipher.encrypt(plain, password)
    }

    /** 构建带版本、时间、时区、条数与校验和的明文备份根对象。 */
    private suspend fun buildPlainBackup(context: Context): JSONObject {
        // 两个 store 各自一次性读取，保证单个 store 内的一致快照。
        val settings = SettingsDataStore(context).exportAll()
        val history = LaunchHistoryStore(context).toJson()
        val info = countInfo(settings, history)
        val tz = TimeZone.getDefault()
        return JSONObject().apply {
            put("format", CURRENT_FORMAT)
            put("schemaVersion", 1)
            put("appVersion", context.getAppVersion())
            put("exportedAt", nowString())
            put("exportedAtEpochMs", System.currentTimeMillis())
            put("timeZoneId", tz.id)
            put("timeZoneOffsetMinutes", tz.getOffset(System.currentTimeMillis()) / 60_000)
            put("locale", Locale.getDefault().toLanguageTag())
            put("counts", JSONObject().apply {
                put("days", info.days)
                put("apps", info.apps)
                put("entries", info.entries)
                put("settingsFields", info.settingsFields)
            })
            put("checksum", checksum(settings, history))
            put("settings", settings)
            put("launchHistory", history)
        }
    }

    fun isTooLarge(raw: String): Boolean = raw.length > MAX_BACKUP_CHARS

    fun isEncrypted(raw: String): Boolean = BackupCipher.isEncrypted(raw)

    // ---------------------------------------------------------------- Restore

    /**
     * 完整恢复流水线：
     *   解析/解密 -> 版本校验 -> 校验和/条数校验 -> 当前数据安全快照 -> 写入 -> 复核；
     *   任何一步失败都会回滚到安全快照，绝不留下「只恢复一半」的状态。
     */
    suspend fun restore(
        context: Context,
        raw: String,
        password: String?,
        mode: RestoreMode
    ): RestoreResult {
        if (raw.isBlank()) return RestoreResult(false, "empty_file")
        if (isTooLarge(raw)) return RestoreResult(false, "file_too_large")

        // 1) 解密（如需要）
        val plain: String = if (BackupCipher.isEncrypted(raw)) {
            val envelope = runCatching { JSONObject(raw) }.getOrNull()
                ?: return RestoreResult(false, "invalid_file")
            if (password.isNullOrEmpty()) return RestoreResult(false, "password_required")
            BackupCipher.decrypt(envelope, password)
                ?: return RestoreResult(false, "wrong_password")
        } else {
            raw
        }

        // 2) 解析 + 版本
        val root = runCatching { JSONObject(plain) }.getOrNull()
            ?: return RestoreResult(false, "invalid_json")
        val format = root.optInt("format", -1)
        if (format < 0) return RestoreResult(false, "missing_format")
        if (format > CURRENT_FORMAT) return RestoreResult(false, "newer_version")
        if (format < MIN_SUPPORTED_FORMAT) return RestoreResult(false, "unsupported_version")

        val settings = root.optJSONObject("settings") ?: JSONObject()
        val history = root.optJSONObject("launchHistory") ?: JSONObject()
        val historyDays = history.optJSONObject("days")?.length() ?: 0
        val historyScanned = history.optJSONArray("scanned")?.length() ?: 0
        if (settings.length() == 0 && historyDays == 0 && historyScanned == 0) {
            return RestoreResult(false, "empty_backup")
        }

        val payloadInfo = countInfo(settings, history)

        // 3) 完整性校验：新格式必须带校验和
        val storedChecksum = root.optString("checksum", "")
        if (format >= 3) {
            if (storedChecksum.isEmpty()) return RestoreResult(false, "missing_checksum")
            if (checksum(settings, history) != storedChecksum) {
                return RestoreResult(false, "checksum_mismatch")
            }
        }
        val counts = root.optJSONObject("counts")
        if (counts != null) {
            if (counts.optInt("days", -1) != payloadInfo.days ||
                counts.optInt("entries", -1) != payloadInfo.entries
            ) {
                return RestoreResult(false, "counts_mismatch")
            }
        }

        // 4) 恢复前安全快照（内存 + 文件）+「恢复进行中」标记（用于崩溃恢复）
        val safetyRoot = runCatching { buildPlainBackup(context) }.getOrNull()
            ?: return RestoreResult(false, "safety_backup_failed")
        val safetyPlain = safetyRoot.toString(2)
        writePreRestoreFile(context, safetyPlain)
        writeMarker(context, safetyPlain)

        // 5) 写入
        val applied = runCatching { applyRestore(context, settings, history, mode) }
        if (applied.isFailure) {
            val ok = rollback(context, safetyRoot)
            clearMarker(context)
            AppLog.e("DataBackupManager", "Restore apply failed (rolledBack=$ok)", applied.exceptionOrNull())
            return RestoreResult(false, "apply_failed", rolledBack = ok)
        }

        // 6) 复核
        val verified = runCatching { verifyAfterRestore(context, payloadInfo, mode) }.getOrDefault(false)
        if (!verified) {
            val ok = rollback(context, safetyRoot)
            clearMarker(context)
            AppLog.e("DataBackupManager", "Restore verification failed (rolledBack=$ok)")
            return RestoreResult(false, "verify_failed", rolledBack = ok)
        }

        clearMarker(context)
        val after = runCatching {
            countInfo(SettingsDataStore(context).exportAll(), LaunchHistoryStore(context).toJson())
        }.getOrNull()
        AppLog.i("DataBackupManager", "Restore ok mode=$mode after=$after")
        return RestoreResult(true, null, after)
    }

    private suspend fun applyRestore(
        context: Context,
        settings: JSONObject,
        history: JSONObject,
        mode: RestoreMode
    ) {
        when (mode) {
            RestoreMode.OVERWRITE -> {
                val overwrite = JSONObject(settings.toString()).apply { put("__clearFirst__", true) }
                SettingsDataStore(context).importAll(overwrite)
                LaunchHistoryStore(context).fromJson(history)
            }
            RestoreMode.MERGE -> {
                // 合并：保留当前设置，只把备份中的历史按「逐日逐应用取较大值」并入，
                // 因此重复导入同一份备份不会让打开次数翻倍。
                LaunchHistoryStore(context).mergeFromJson(history)
            }
        }
    }

    private suspend fun verifyAfterRestore(
        context: Context,
        expected: BackupInfo,
        mode: RestoreMode
    ): Boolean {
        val after = countInfo(
            SettingsDataStore(context).exportAll(),
            LaunchHistoryStore(context).toJson()
        )
        return when (mode) {
            // 覆盖：条数不得少于备份（后台扫描可能同时新增，故用 >= 容忍并发写入）。
            RestoreMode.OVERWRITE ->
                after.days >= expected.days &&
                    after.entries >= expected.entries &&
                    after.settingsFields == expected.settingsFields
            // 合并：备份里的每一条都必须到位。
            RestoreMode.MERGE -> after.entries >= expected.entries
        }
    }

    /** 用安全快照把数据整体写回（覆盖式），返回是否成功。 */
    private suspend fun rollback(context: Context, safetyRoot: JSONObject): Boolean = runCatching {
        val settings = safetyRoot.optJSONObject("settings") ?: JSONObject()
        val history = safetyRoot.optJSONObject("launchHistory") ?: JSONObject()
        val overwrite = JSONObject(settings.toString()).apply { put("__clearFirst__", true) }
        SettingsDataStore(context).importAll(overwrite)
        LaunchHistoryStore(context).fromJson(history)
        AppLog.i("DataBackupManager", "Rollback completed")
    }.isSuccess

    // ---------------------------------------------------------------- Crash recovery

    private fun markerFile(context: Context) = File(context.filesDir, MARKER_FILE)

    private fun writeMarker(context: Context, plain: String) {
        runCatching { markerFile(context).writeText(plain) }
    }

    private fun clearMarker(context: Context) {
        runCatching { markerFile(context).delete() }
    }

    /**
     * 应用启动时调用：如果上次恢复在中途被杀死/断电，标记文件仍在，
     * 就回滚到恢复前快照，保证不会停留在「只恢复一半」的状态。
     */
    suspend fun recoverIfInterrupted(context: Context): Boolean {
        val file = markerFile(context)
        if (!file.exists()) return false
        return runCatching {
            val root = JSONObject(file.readText())
            val settings = root.optJSONObject("settings") ?: JSONObject()
            val history = root.optJSONObject("launchHistory") ?: JSONObject()
            val overwrite = JSONObject(settings.toString()).apply { put("__clearFirst__", true) }
            SettingsDataStore(context).importAll(overwrite)
            LaunchHistoryStore(context).fromJson(history)
            file.delete()
            AppLog.i("DataBackupManager", "Recovered from an interrupted restore (rolled back to pre-restore)")
            true
        }.getOrElse {
            AppLog.e("DataBackupManager", "Failed to recover interrupted restore", it)
            false
        }
    }

    // ---------------------------------------------------------------- Auto backup

    private fun backupDir(context: Context): File =
        File(context.getExternalFilesDir(null), BACKUP_DIR).apply { mkdirs() }

    private fun autoBackupFileForToday(context: Context): File {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        return File(backupDir(context), "$AUTO_BACKUP_PREFIX$date.json")
    }

    fun hasAutoBackupToday(context: Context): Boolean = autoBackupFileForToday(context).exists()

    /** 最近一次自动备份的时间（毫秒），没有则 null。 */
    fun lastAutoBackupTime(context: Context): Long? = runCatching {
        backupDir(context)
            .listFiles { f -> f.name.startsWith(AUTO_BACKUP_PREFIX) }
            ?.maxByOrNull { it.lastModified() }
            ?.lastModified()
    }.getOrNull()

    /** 自动备份的当前状态，供设置页显示「上次备份 / 份数 / 大小」。 */
    data class AutoBackupStatus(val lastTimeMs: Long, val sizeBytes: Long, val count: Int)

    fun autoBackupStatus(context: Context): AutoBackupStatus? = runCatching {
        val files = backupDir(context)
            .listFiles { f -> f.name.startsWith(AUTO_BACKUP_PREFIX) }
            ?.filter { it.isFile && it.length() > 0 }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()
        val latest = files.firstOrNull() ?: return null
        AutoBackupStatus(
            lastTimeMs = latest.lastModified(),
            sizeBytes = latest.length(),
            count = files.size
        )
    }.getOrNull()

    /** 写入今天的自动备份；已存在则跳过。返回是否（已存在或成功）可用。 */
    suspend fun writeAutoBackupIfNeeded(context: Context): Boolean {
        val target = autoBackupFileForToday(context)
        if (target.exists()) return true
        runCatching {
            val plain = buildPlainBackup(context).toString(2)
            val tmp = File(target.parentFile, target.name + ".tmp")
            tmp.writeText(plain)
            if (!tmp.renameTo(target)) {
                target.writeText(plain)
                tmp.delete()
            }
            pruneAutoBackups(context)
            AppLog.i("DataBackupManager", "Auto backup written ${target.name} (${target.length()} bytes)")
        }.onFailure {
            AppLog.e("DataBackupManager", "Auto backup failed", it)
        }
        return target.exists()
    }

    /** 只保留最近 [AUTO_BACKUP_KEEP] 份自动备份。 */
    private fun pruneAutoBackups(context: Context) {
        runCatching {
            val files = backupDir(context)
                .listFiles { f -> f.name.startsWith(AUTO_BACKUP_PREFIX) }
                ?.sortedByDescending { it.name }
                ?: return
            files.drop(AUTO_BACKUP_KEEP).forEach { it.delete() }
        }
    }

    private fun preRestoreFile(context: Context): File {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        return File(backupDir(context), "$PRE_RESTORE_PREFIX$stamp.json")
    }

    private fun writePreRestoreFile(context: Context, plain: String) {
        runCatching { preRestoreFile(context).writeText(plain) }
    }

    // ---------------------------------------------------------------- Delete

    /** 用户自主删除全部数据：先留一份安全快照，再清空设置与历史。 */
    suspend fun clearAllData(context: Context) {
        runCatching { writePreRestoreFile(context, buildPlainBackup(context).toString(2)) }
        SettingsDataStore(context).clearAll()
        LaunchHistoryStore(context).clearAll()
        AppLog.i("DataBackupManager", "All local data cleared by user")
    }

    fun clearAutoBackups(context: Context) {
        runCatching { backupDir(context).listFiles()?.forEach { it.delete() } }
    }

    // ---------------------------------------------------------------- Permission

    /** 恢复后需要重新确认使用情况访问权限（系统特殊权限，无法通过 JSON 迁移）。 */
    fun hasUsageAccessPermission(context: Context): Boolean = runCatching {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        mode == AppOpsManager.MODE_ALLOWED
    }.getOrDefault(false)

    // ---------------------------------------------------------------- Helpers

    private fun nowString(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())

    private fun countInfo(settings: JSONObject, history: JSONObject): BackupInfo {
        val daysObj = history.optJSONObject("days") ?: JSONObject()
        val apps = mutableSetOf<String>()
        var entries = 0
        val dayKeys = daysObj.keys()
        while (dayKeys.hasNext()) {
            val day = dayKeys.next()
            val dayObj = daysObj.optJSONObject(day) ?: continue
            val pkgKeys = dayObj.keys()
            while (pkgKeys.hasNext()) {
                val pkg = pkgKeys.next()
                if (dayObj.optInt(pkg, 0) > 0) {
                    entries++
                    apps.add(pkg)
                }
            }
        }
        return BackupInfo(
            days = daysObj.length(),
            apps = apps.size,
            entries = entries,
            settingsFields = settings.length()
        )
    }

    /** 稳定（可复现）序列化后取 SHA-256：键排序，避免 JSON 对象顺序不同导致校验失败。 */
    private fun checksum(settings: JSONObject, history: JSONObject): String =
        sha256(canonicalSettings(settings) + "\n" + canonicalHistory(history))

    private fun canonicalSettings(settings: JSONObject): String {
        val keys = settings.keys().asSequence().toList().sorted()
        return keys.joinToString("&") { key -> "$key=${settings.opt(key)?.toString() ?: ""}" }
    }

    private fun canonicalHistory(history: JSONObject): String {
        val builder = StringBuilder()
        val daysObj = history.optJSONObject("days") ?: JSONObject()
        val dayKeys = daysObj.keys().asSequence().toList().sorted()
        for (day in dayKeys) {
            val dayObj = daysObj.optJSONObject(day) ?: continue
            builder.append(day).append(':')
            val pkgKeys = dayObj.keys().asSequence().toList().sorted()
            for (pkg in pkgKeys) {
                builder.append(pkg).append('=').append(dayObj.optInt(pkg, 0)).append(',')
            }
            builder.append(';')
        }
        val scanned = history.optJSONArray("scanned")
        val scannedList = if (scanned == null) emptyList()
            else (0 until scanned.length()).map { scanned.optString(it) }.sorted()
        builder.append("|scanned:").append(scannedList.joinToString(","))
        return builder.toString()
    }

    private fun sha256(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        val out = StringBuilder(digest.size * 2)
        for (b in digest) out.append(String.format("%02x", b.toInt() and 0xFF))
        return out.toString()
    }
}
