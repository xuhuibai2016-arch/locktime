package com.timelock.app.ui.dashboard

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.timelock.app.data.local.dao.ScheduledLockDao
import com.timelock.app.data.local.entity.ScheduledLockEntity
import com.timelock.app.domain.repository.AppLimitRepository
import com.timelock.app.domain.repository.UsageStatsRepository
import com.timelock.app.service.LockStateManager
import com.timelock.app.service.ScheduledLockReceiver
import com.timelock.app.util.SystemAppHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * 仪表盘 ViewModel。
 *
 * 职责：
 * - 图表：当天 Top 5 使用时长分布
 * - 列表：近 7 天累计 Top 5 高频应用 + 限额状态
 *
 * 排除 TimeLock 自身及系统桌面/SystemUI 等非"使用"性质的应用。
 */
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val usageStatsRepository: UsageStatsRepository,
    private val appLimitRepository: AppLimitRepository,
    private val scheduledLockDao: ScheduledLockDao,
    private val lockStateManager: LockStateManager,
    private val systemAppHelper: SystemAppHelper,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        loadTopApps()
    }

    fun loadTopApps() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                // 同步最新数据
                usageStatsRepository.syncUsageStats()

                val today = dateFormat.format(Date())
                val cal = Calendar.getInstance()
                cal.add(Calendar.DAY_OF_YEAR, -6) // 含今天共 7 天
                val sevenDaysAgo = dateFormat.format(cal.time)

                // ── 当天数据 (图表用) → 过滤 → Top 5，含超限标记 ──
                val todayStats = usageStatsRepository.getDailyStats(today)
                val todayFiltered = todayStats
                    .filter { !isExcludedPackage(it.packageName) }
                val todayTop5 = todayFiltered.sortedByDescending { it.totalDuration }.take(5)
                val chartItems = todayTop5.map { stat ->
                    val config = appLimitRepository.getConfig(stat.packageName)
                    val limitMinutes = config?.dailyLimitMinutes?.takeIf { it > 0 }
                    val usedMinutes = stat.totalDuration / 60_000L
                    ChartItem(
                        packageName = stat.packageName,
                        appName = stat.appName,
                        usedMinutes = usedMinutes,
                        limitMinutes = limitMinutes,
                        isOverLimit = limitMinutes != null && usedMinutes >= limitMinutes
                    )
                }

                // ── 7 天累计数据 (列表用) → 过滤 → Top 5，不对比每日限额 ──
                val weekStats = usageStatsRepository.getStatsByDateRange(sevenDaysAgo, today)
                val weekFiltered = weekStats
                    .filter { !isExcludedPackage(it.packageName) }
                val weekTop5 = weekFiltered.sortedByDescending { it.totalDuration }.take(5)
                val items = weekTop5.map { stat ->
                    TopAppItem(
                        packageName = stat.packageName,
                        appName = stat.appName,
                        usedMinutes = stat.totalDuration / 60_000L,
                        limitMinutes = null,
                        isOverLimit = false
                    )
                }

                _uiState.update {
                    it.copy(isLoading = false, topApps = items, chartItems = chartItems)
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = e.message ?: "Unknown error")
                }
            }
        }
    }

    /** 判断是否应排除该包名（委托给 SystemAppHelper） */
    private fun isExcludedPackage(packageName: String): Boolean {
        return systemAppHelper.isExcludedPackage(packageName)
    }

    // ── 一键锁屏 ──────────────────────────────────────────────

    fun showLockDialog() { _uiState.update { it.copy(showLockDialog = true) } }
    fun dismissLockDialog() { _uiState.update { it.copy(showLockDialog = false) } }
    fun setLockMinutes(value: String) {
        // 仅允许数字输入
        if (value.all { it.isDigit() } && value.length <= 3) {
            _uiState.update { it.copy(lockMinutes = value) }
        }
    }

    /** 触发全局一键锁屏 */
    fun oneTapLock(context: android.content.Context) {
        val minutes = _uiState.value.lockMinutes.toIntOrNull()?.coerceIn(1, 999) ?: 30
        _uiState.update { it.copy(showLockDialog = false) }
        lockStateManager.startGlobalLock(minutes)
    }

    // ── 快捷设置限额 ──────────────────────────────────────────

    fun showQuickLimit(packageName: String, appName: String, currentLimit: Int?) {
        val limit = currentLimit ?: 0
        _uiState.update {
            it.copy(
                quickLimitTarget = QuickLimitTarget(packageName, appName, limit),
                quickLimitMinutes = if (limit > 0) limit.toString() else "30"
            )
        }
    }

    fun dismissQuickLimit() {
        _uiState.update { it.copy(quickLimitTarget = null, quickLimitMessage = null) }
    }

    fun setQuickLimitMinutes(value: String) {
        if (value.all { it.isDigit() } && value.length <= 3) {
            _uiState.update { it.copy(quickLimitMinutes = value) }
        }
    }

    fun saveQuickLimit() {
        val target = _uiState.value.quickLimitTarget ?: return
        val minutes = _uiState.value.quickLimitMinutes.toIntOrNull()?.coerceIn(1, 999) ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isQuickLimitSaving = true) }
            appLimitRepository.saveConfig(
                com.timelock.app.domain.model.AppLimitConfig(
                    packageName = target.packageName,
                    appName = target.appName,
                    dailyLimitMinutes = minutes,
                    isLockEnabled = true,
                    isWhitelisted = false
                )
            )
            loadTopApps()
            _uiState.update {
                it.copy(
                    isQuickLimitSaving = false,
                    quickLimitTarget = null,
                    quickLimitMessage = "已为 ${target.appName} 设置 ${minutes} 分钟限额"
                )
            }
        }
    }

    fun clearQuickLimitMessage() {
        _uiState.update { it.copy(quickLimitMessage = null) }
    }

    // ── 定时锁屏 ──────────────────────────────────────────────

    fun loadScheduledLock() {
        viewModelScope.launch {
            val config = scheduledLockDao.get()
            _uiState.update {
                val hour = config?.lockHour ?: 21
                val minute = config?.lockMinute ?: 30
                val duration = config?.lockDurationMinutes ?: 180
                it.copy(
                    scheduledLockEnabled = config?.enabled ?: false,
                    scheduledLockHour = hour,
                    scheduledLockMinute = minute,
                    scheduledLockDuration = duration,
                    scheduledLockHourText = "%02d".format(hour),
                    scheduledLockMinuteText = "%02d".format(minute),
                    scheduledLockDurationText = duration.toString()
                )
            }
        }
    }

    fun showScheduledLockSheet() {
        val state = _uiState.value
        // 距离定时锁屏触发时间不足 30 分钟时，禁止修改
        val now = java.util.Calendar.getInstance()
        val triggerCal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, state.scheduledLockHour)
            set(java.util.Calendar.MINUTE, state.scheduledLockMinute)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val minutesUntilTrigger = (triggerCal.timeInMillis - now.timeInMillis) / 60_000L
        // 如果今天时间已过，算到明天的触发时间
        val effectiveMinutes = if (minutesUntilTrigger < 0) {
            (triggerCal.timeInMillis + 24 * 60 * 60_000L - now.timeInMillis) / 60_000L
        } else minutesUntilTrigger

        val blocked = state.scheduledLockEnabled && effectiveMinutes in 0..29
        val reason = if (blocked) "距定时锁屏不足30分钟（${effectiveMinutes}分钟后触发），暂时无法修改" else null

        val hour = state.scheduledLockHour
        val minute = state.scheduledLockMinute
        val duration = state.scheduledLockDuration
        _uiState.update {
            it.copy(
                showScheduledLockSheet = true,
                scheduledLockBlocked = blocked,
                scheduledLockBlockReason = reason,
                scheduledLockHourText = "%02d".format(hour),
                scheduledLockMinuteText = "%02d".format(minute),
                scheduledLockDurationText = duration.toString()
            )
        }
    }

    fun dismissScheduledLockSheet() {
        _uiState.update { it.copy(showScheduledLockSheet = false) }
    }

    fun setScheduledLockHourText(text: String) {
        val filtered = text.filter { it.isDigit() }.takeLast(2)
        val parsed = filtered.toIntOrNull()
        val hour = parsed?.coerceIn(0, 23)
        _uiState.update { state ->
            state.copy(
                scheduledLockHourText = when {
                    parsed == null -> filtered
                    parsed > 23 -> hour.toString()
                    else -> filtered
                },
                scheduledLockHour = hour ?: state.scheduledLockHour
            )
        }
    }

    fun setScheduledLockMinuteText(text: String) {
        val filtered = text.filter { it.isDigit() }.takeLast(2)
        val parsed = filtered.toIntOrNull()
        val minute = parsed?.coerceIn(0, 59)
        _uiState.update { state ->
            state.copy(
                scheduledLockMinuteText = when {
                    parsed == null -> filtered
                    parsed > 59 -> minute.toString()
                    else -> filtered
                },
                scheduledLockMinute = minute ?: state.scheduledLockMinute
            )
        }
    }

    fun setScheduledLockDurationText(text: String) {
        val filtered = text.filter { it.isDigit() }.take(3)
        val parsed = filtered.toIntOrNull()
        val minutes = when {
            parsed == null -> null
            parsed < 1 -> 1
            parsed > 600 -> 600
            else -> parsed
        }
        _uiState.update { state ->
            state.copy(
                scheduledLockDurationText = when {
                    parsed == null -> filtered
                    parsed < 1 -> "1"
                    parsed > 600 -> "600"
                    else -> filtered
                },
                scheduledLockDuration = minutes ?: state.scheduledLockDuration
            )
        }
    }

    fun toggleScheduledLock(enabled: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(scheduledLockEnabled = enabled) }
            saveScheduledLock()
        }
    }

    fun saveScheduledLockConfig() {
        viewModelScope.launch {
            saveScheduledLock()
            _uiState.update {
                it.copy(
                    showScheduledLockSheet = false,
                    quickLimitMessage = "定时锁屏已保存"
                )
            }
        }
    }

    private suspend fun saveScheduledLock() {
        val state = _uiState.value
        val config = ScheduledLockEntity(
            id = 1,
            lockHour = state.scheduledLockHour,
            lockMinute = state.scheduledLockMinute,
            lockDurationMinutes = state.scheduledLockDuration,
            enabled = state.scheduledLockEnabled
        )
        scheduledLockDao.save(config)
        ScheduledLockReceiver.schedule(appContext)
    }

    /** 格式化定时锁屏时间为 "HH:MM" */
    fun formatScheduledTime(): String {
        val state = _uiState.value
        return "${"%02d".format(state.scheduledLockHour)}:${"%02d".format(state.scheduledLockMinute)}"
    }
}

/** 快捷设置限额的 App (null = 未选中) */
data class QuickLimitTarget(
    val packageName: String,
    val appName: String,
    val currentLimit: Int
)

/**
 * 仪表盘 UI 状态。
 */
data class DashboardUiState(
    val isLoading: Boolean = true,
    val topApps: List<TopAppItem> = emptyList(),
    val chartItems: List<ChartItem> = emptyList(),
    val error: String? = null,
    // ── 一键锁屏 ──
    val showLockDialog: Boolean = false,
    val lockMinutes: String = "30",
    // ── 快捷设置限额 ──
    val quickLimitTarget: QuickLimitTarget? = null,
    val quickLimitMinutes: String = "",
    val isQuickLimitSaving: Boolean = false,
    val quickLimitMessage: String? = null,
    // ── 定时锁屏 ──
    val scheduledLockEnabled: Boolean = false,
    val scheduledLockHour: Int = 21,
    val scheduledLockMinute: Int = 30,
    val scheduledLockDuration: Int = 180,
    val scheduledLockDurationText: String = "180",
    val scheduledLockHourText: String = "21",
    val scheduledLockMinuteText: String = "30",
    val showScheduledLockSheet: Boolean = false,
    val scheduledLockBlocked: Boolean = false,
    val scheduledLockBlockReason: String? = null
)

/**
 * 图表数据项，表示一个 App 的今日使用时长。
 */
data class ChartItem(
    val packageName: String,
    val appName: String,
    val usedMinutes: Long,
    val limitMinutes: Int? = null,
    val isOverLimit: Boolean = false
)

/**
 * Top 应用列表项。
 */
data class TopAppItem(
    val packageName: String,
    val appName: String,
    val usedMinutes: Long,
    val limitMinutes: Int? = null,
    val isOverLimit: Boolean = false
)
