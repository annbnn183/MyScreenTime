package com.atharok.screentime.presentation.viewmodel

import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atharok.screentime.common.utils.AppLog
import com.atharok.screentime.common.utils.NUMBER_OF_DAYS
import com.atharok.screentime.domain.entities.CentralTendency
import com.atharok.screentime.domain.entities.DailyGoalSettings
import com.atharok.screentime.domain.entities.Period
import com.atharok.screentime.domain.entities.usage.AppUsage
import com.atharok.screentime.domain.entities.usage.DeviceUsage
import com.atharok.screentime.domain.resources.Resource
import com.atharok.screentime.domain.usecases.DeviceUsageUseCase
import com.atharok.screentime.domain.usecases.SettingsUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class DeviceUsageViewModel(
    private val usageUseCase: DeviceUsageUseCase,
    private val settingsUseCase: SettingsUseCase
): ViewModel() {

    // Permission

    fun hasAppUsagePermission(): Boolean = usageUseCase.hasAppUsagePermission()

    // Usage calculation

    /**
     * 首帧先用与系统默认一致的 DAY 渲染，再异步读用户偏好。
     *
     * 旧实现是 `runBlocking { settingsUseCase.getDefaultPeriod().first() }`，在 ViewModel 构造
     * （即主线程、冷启动关键路径）上阻塞等待 DataStore 读盘 —— 导出日志里实测首帧出现
     * 971 ms 的掉帧，用户看到的就是「点开图标后白屏/卡住一秒」。改为先渲染、后修正。
     */
    val periodState: MutableState<Period> = mutableStateOf(Period.DAY)
    val dayIndexState: MutableIntState = mutableIntStateOf(NUMBER_OF_DAYS - 1) // We display 9 days max.

    private var periodChosenByUser = false

    init {
        viewModelScope.launch(Dispatchers.IO + AppLog.ceh) {
            val saved = runCatching { settingsUseCase.getDefaultPeriod().first() }.getOrNull()
                ?: return@launch
            if (!periodChosenByUser && periodState.value != saved) {
                periodState.value = saved
            }
        }
    }

    /** 用户手动切换周期：之后不再被异步读到的偏好覆盖。 */
    fun setPeriod(period: Period) {
        periodChosenByUser = true
        periodState.value = period
    }

    val selectedCentralTendency: MutableState<CentralTendency> = mutableStateOf(CentralTendency.AVERAGE)

    fun getDeviceUsageFlow(): StateFlow<Resource<DeviceUsage<out AppUsage>>> = usageUseCase.deviceUsageFlow

    /** Daily goal configuration, used by the month view's goal calendar. */
    fun getDailyGoalSettings(): Flow<DailyGoalSettings> = settingsUseCase.getDailyGoalSettings()

    /** 是否正在补算历史点击量。 */
    val backfillRunning: MutableState<Boolean> = mutableStateOf(false)
    /** 已推进的天数，用于显示「正在补算 x / 365 天」。 */
    val backfillProgress: MutableIntState = mutableIntStateOf(0)

    /** 一次补满一年：旧的实现每次只推进 31 天，用户要连点 12 次才能补完。 */
    private val backfillTargetDays = 365
    private var autoBackfillTriggered = false

    /** 进入主页后自动补一次（静默）。补过的天会被 [LaunchHistoryStore] 直接跳过，重复调用几乎无成本。 */
    fun backfillLaunchHistoryIfNeeded() {
        if (autoBackfillTriggered) return
        autoBackfillTriggered = true
        backfillLaunchHistory()
    }

    fun backfillLaunchHistory() = viewModelScope.launch(Dispatchers.Default + AppLog.ceh) {
        if (backfillRunning.value) return@launch
        backfillRunning.value = true
        backfillProgress.value = 0
        try {
            val ignoreSystemApps = settingsUseCase.shouldIgnoreSystemApps().first()
            val ignoredPackages = try {
                settingsUseCase.getIgnoredPackages().first()
            } catch (_: Exception) {
                emptyList()
            }
            val done = usageUseCase.backfillLaunchHistory(
                ignoreSystemApps = ignoreSystemApps,
                ignoredPackages = ignoredPackages,
                maxDays = backfillTargetDays
            ) { progress, _ ->
                backfillProgress.intValue = progress
            }
            AppLog.i("DeviceUsageViewModel", "Backfilled $done days of launch history")

            // 点击量变了、时长没变：只让周期缓存失效并重新组装，
            // 不再像旧实现那样跑一次 useCache=false 的 10 天事件全量扫描。
            usageUseCase.invalidatePeriodCaches()
            when (periodState.value) {
                Period.DAY -> calculateDayDeviceUsage(useCache = true)
                Period.WEEK -> calculateWeekDeviceUsage(useCache = true)
                Period.MONTH -> calculateMonthDeviceUsage(useCache = true)
                Period.YEAR -> calculateYearDeviceUsage(useCache = true)
            }
            calculateComparison()
        } catch (e: Exception) {
            AppLog.e("DeviceUsageViewModel", "Launch history backfill failed", e)
        } finally {
            backfillRunning.value = false
        }
    }

    fun calculateMonthDeviceUsage(useCache: Boolean = true) = viewModelScope.launch(Dispatchers.Default + AppLog.ceh) {
        usageUseCase.calculateMonthDeviceUsage(
            ignoreSystemApps = settingsUseCase.shouldIgnoreSystemApps().first(),
            ignoredPackages = try {
                settingsUseCase.getIgnoredPackages().first()
            } catch (_: Exception) {
                emptyList()
            },
            goalMillis = try {
                settingsUseCase.getDailyGoalSettings().first().goalMillis
            } catch (_: Exception) {
                0L
            },
            useCache = useCache
        )
    }

    fun calculateWeekDeviceUsage(useCache: Boolean = true) = viewModelScope.launch(Dispatchers.Default + AppLog.ceh) {
        usageUseCase.calculateWeekDeviceUsage(
            ignoreSystemApps = settingsUseCase.shouldIgnoreSystemApps().first(),
            ignoredPackages = try {
                settingsUseCase.getIgnoredPackages().first()
            } catch (_: Exception) {
                emptyList()
            },
            useCache = useCache
        )
    }

    fun calculateDayDeviceUsage(useCache: Boolean = true) = viewModelScope.launch(Dispatchers.Default + AppLog.ceh) {
        usageUseCase.calculateDayDeviceUsage(
            ignoreSystemApps = settingsUseCase.shouldIgnoreSystemApps().first(),
            ignoredPackages = try {
                settingsUseCase.getIgnoredPackages().first()
            } catch (_: Exception) {
                emptyList()
            },
            dayIndex = dayIndexState.intValue,
            useCache = useCache
        )
    }

    fun calculateYearDeviceUsage(useCache: Boolean = true) = viewModelScope.launch(Dispatchers.Default + AppLog.ceh) {
        usageUseCase.calculateYearDeviceUsage(
            ignoreSystemApps = settingsUseCase.shouldIgnoreSystemApps().first(),
            ignoredPackages = try {
                settingsUseCase.getIgnoredPackages().first()
            } catch (_: Exception) {
                emptyList()
            },
            goalMillis = try {
                settingsUseCase.getDailyGoalSettings().first().goalMillis
            } catch (_: Exception) {
                0L
            },
            useCache = useCache
        )
    }

    /** 计算「本周期 vs 上一周期」对比（DAY 周期为 null）。 */
    fun calculateComparison() = viewModelScope.launch(Dispatchers.Default + AppLog.ceh) {
        usageUseCase.calculateComparison(
            period = periodState.value,
            ignoreSystemApps = settingsUseCase.shouldIgnoreSystemApps().first(),
            ignoredPackages = try {
                settingsUseCase.getIgnoredPackages().first()
            } catch (_: Exception) {
                emptyList()
            }
        )
    }

    val comparisonFlow: kotlinx.coroutines.flow.StateFlow<com.atharok.screentime.domain.usecases.UsageComparison?> =
        usageUseCase.comparisonFlow
}
