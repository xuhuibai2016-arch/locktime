package com.timelock.app.ui.trend

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.timelock.app.domain.repository.UsageStatsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject

/**
 * 趋势页 ViewModel。
 *
 * 职责：
 * - 计算过去 7 天的日期范围
 * - 获取 7 天累计 Top 5 应用
 * - 逐日查询 Top 5 应用的使用时长（用于折线图）
 */
@HiltViewModel
class TrendViewModel @Inject constructor(
    private val usageStatsRepository: UsageStatsRepository
) : ViewModel() {

    companion object {
        private const val TREND_DAYS = 7
    }

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    private val _uiState = MutableStateFlow(TrendUiState())
    val uiState: StateFlow<TrendUiState> = _uiState.asStateFlow()

    init {
        loadTrend()
    }

    fun loadTrend() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                // 1. 同步最新数据
                usageStatsRepository.syncUsageStats()

                // 2. 计算 7 天日期列表
                val cal = Calendar.getInstance()
                val dates = (0 until TREND_DAYS).map { dayOffset ->
                    cal.apply {
                        timeInMillis = System.currentTimeMillis()
                        add(Calendar.DAY_OF_YEAR, -dayOffset)
                    }.let { dateFormat.format(it.time) }
                }.reversed() // 从最早到最近
                val startDate = dates.first()
                val endDate = dates.last()

                // 3. 获取 7 天累计 Top 5
                val weekStats = usageStatsRepository.getStatsByDateRange(startDate, endDate)
                val top5 = weekStats
                    .filter { it.totalDuration > 0 }
                    .sortedByDescending { it.totalDuration }
                    .take(5)

                if (top5.isEmpty()) {
                    _uiState.update { it.copy(isLoading = false, isEmpty = true) }
                    return@launch
                }

                // 4. 逐日查询 Top 5 的使用时长
                val top5Packages = top5.map { it.packageName }
                val dailyRecords = usageStatsRepository.getDailyStatsForPackages(
                    startDate, endDate, top5Packages
                )

                // 5. 按 APP 分组，补全缺失日期
                val appTrends = top5.map { app ->
                    val appDailyMap = dailyRecords
                        .filter { it.packageName == app.packageName }
                        .associateBy { it.date }

                    val dailyUsages = dates.map { date ->
                        appDailyMap[date]?.totalDuration ?: 0L
                    }

                    AppTrendItem(
                        packageName = app.packageName,
                        appName = app.appName,
                        dailyMinutes = dailyUsages.map { it / 60_000L },
                        totalMinutes = app.totalDuration / 60_000L,
                        dates = dates.map { formatDateShort(it) }
                    )
                }

                _uiState.update {
                    it.copy(isLoading = false, appTrends = appTrends)
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = e.message ?: "Unknown error")
                }
            }
        }
    }

    /** 将 "yyyy-MM-dd" 格式化为 "周一/二/…/日" */
    private fun formatDateShort(dateStr: String): String {
        return try {
            val date = dateFormat.parse(dateStr) ?: return dateStr
            val cal = Calendar.getInstance().apply { time = date }
            val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
            val labels = listOf("", "周日", "周一", "周二", "周三", "周四", "周五", "周六")
            labels.getOrElse(dayOfWeek) { dateStr }
        } catch (_: Exception) {
            dateStr
        }
    }
}

/**
 * 趋势页 UI 状态。
 */
data class TrendUiState(
    val isLoading: Boolean = true,
    val isEmpty: Boolean = false,
    val appTrends: List<AppTrendItem> = emptyList(),
    val error: String? = null
)

/**
 * 单个 APP 的 7 天趋势数据。
 */
data class AppTrendItem(
    val packageName: String,
    val appName: String,
    /** 每日使用分钟数 (长度 = 7)，与 [dates] 一一对应 */
    val dailyMinutes: List<Long>,
    /** 7 天累计总分钟数 */
    val totalMinutes: Long,
    /** 日期标签 (长度 = 7)，已格式化为 "周一/二/…/日" */
    val dates: List<String>
)
