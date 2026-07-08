package com.timelock.app.ui.config

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.timelock.app.domain.model.AppLimitConfig
import com.timelock.app.domain.repository.AppLimitRepository
import com.timelock.app.domain.repository.UsageStatsRepository
import com.timelock.app.domain.usecase.AddSystemAppsToWhitelistUseCase
import com.timelock.app.util.AppInfoProvider
import com.timelock.app.util.SystemAppHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 应用限额配置 ViewModel。
 *
 * 职责：
 * - 加载已安装应用列表 + 合并已有限额配置
 * - 管理 BottomSheet 交互 (选择 → 调整 → 保存)
 * - 一键添加系统应用到白名单
 */
@HiltViewModel
class LockConfigViewModel @Inject constructor(
    private val appLimitRepository: AppLimitRepository,
    private val usageStatsRepository: UsageStatsRepository,
    private val appInfoProvider: AppInfoProvider,
    private val addSystemAppsToWhitelistUseCase: AddSystemAppsToWhitelistUseCase,
    private val systemAppHelper: SystemAppHelper
) : ViewModel() {

    companion object {
        const val DEFAULT_LIMIT_MINUTES = 30
        const val MIN_LIMIT_MINUTES = 1
        /** 距离超限多少分钟内不允许修改限额 */
        const val LIMIT_MODIFY_BUFFER_MINUTES = 10
    }

    private val _uiState = MutableStateFlow(LockConfigUiState())
    val uiState: StateFlow<LockConfigUiState> = _uiState.asStateFlow()

    init {
        loadApps()
    }

    private fun loadApps() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val installed = appInfoProvider.getInstalledApps()
                    .filter { !systemAppHelper.isExcludedPackage(it.packageName) }
                val configs = appLimitRepository.getAllConfigs()
                val configMap = configs.associateBy { it.packageName }

                val items = installed.map { app ->
                    val config = configMap[app.packageName]
                    AppConfigItem(
                        packageName = app.packageName,
                        appName = app.appName,
                        dailyLimitMinutes = config?.dailyLimitMinutes ?: 0,
                        isLockEnabled = config?.isLockEnabled ?: true,
                        isConfigured = config != null
                    )
                }

                _uiState.update { it.copy(isLoading = false, apps = items) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    /** 选中应用 → 弹出 BottomSheet (含限额修改限制检查) */
    fun selectApp(packageName: String) {
        val app = _uiState.value.apps.find { it.packageName == packageName } ?: return
        val limit = if (app.isConfigured) app.dailyLimitMinutes else DEFAULT_LIMIT_MINUTES

        viewModelScope.launch {
            // 检查当天已用时长，决定是否允许修改限额
            val todayUsedMs = usageStatsRepository.getTodayUsage(packageName)
            val todayUsedMin = todayUsedMs / 60_000L
            val currentLimit = app.dailyLimitMinutes.toLong()

            val blocked: Boolean
            val blockReason: String?
            when {
                !app.isConfigured -> {
                    blocked = false; blockReason = null // 新配置，无限制
                }
                todayUsedMin >= currentLimit -> {
                    blocked = true
                    blockReason = "已超限（${todayUsedMin}分钟 / ${currentLimit}分钟），无法修改限额"
                }
                todayUsedMin >= currentLimit - LIMIT_MODIFY_BUFFER_MINUTES -> {
                    blocked = true
                    blockReason = "距超限不足${LIMIT_MODIFY_BUFFER_MINUTES}分钟（${todayUsedMin}分钟 / ${currentLimit}分钟），无法修改限额"
                }
                else -> {
                    blocked = false; blockReason = null
                }
            }

            _uiState.update {
                it.copy(
                    selectedApp = app,
                    currentLimitMinutes = limit,
                    showBottomSheet = true,
                    limitModifyBlocked = blocked,
                    limitModifyBlockReason = blockReason
                )
            }
        }
    }

    /** 手动输入限额分钟数 (仅允许数字，最多 3 位) */
    fun setLimitMinutes(value: String) {
        if (value.all { it.isDigit() } && value.length <= 3) {
            val minutes = value.toIntOrNull() ?: return
            _uiState.update { it.copy(currentLimitMinutes = minutes.coerceAtLeast(MIN_LIMIT_MINUTES)) }
        }
    }

    /** 保存限额配置 */
    fun saveLimit() {
        val state = _uiState.value
        val app = state.selectedApp ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            appLimitRepository.saveConfig(
                AppLimitConfig(
                    packageName = app.packageName,
                    appName = app.appName,
                    dailyLimitMinutes = state.currentLimitMinutes,
                    isLockEnabled = true,
                    isWhitelisted = false
                )
            )
            // 重新加载以刷新列表
            loadApps()
            _uiState.update {
                it.copy(
                    isSaving = false,
                    showBottomSheet = false,
                    selectedApp = null,
                    snackbarMessage = "已为 ${app.appName} 设置 ${state.currentLimitMinutes} 分钟限额"
                )
            }
        }
    }

    /** 关闭 BottomSheet */
    fun dismissSheet() {
        _uiState.update {
            it.copy(showBottomSheet = false, selectedApp = null)
        }
    }

    /** 一键添加系统应用到白名单 */
    fun addSystemAppsToWhitelist() {
        viewModelScope.launch {
            _uiState.update { it.copy(isAddingSystemApps = true) }
            try {
                val added = addSystemAppsToWhitelistUseCase.execute()
                loadApps() // 刷新列表显示白名单状态
                val msg = if (added.isEmpty()) {
                    "未检测到需要添加的系统应用"
                } else {
                    "已添加 ${added.size} 个系统应用到白名单（电话/短信）"
                }
                _uiState.update {
                    it.copy(isAddingSystemApps = false, snackbarMessage = msg)
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isAddingSystemApps = false,
                        snackbarMessage = "添加失败：${e.message}"
                    )
                }
            }
        }
    }

    /** 更新搜索关键词 */
    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    /** 获取过滤后的应用列表 */
    fun getFilteredApps(): List<AppConfigItem> {
        val state = _uiState.value
        val query = state.searchQuery.trim().lowercase()
        if (query.isEmpty()) return state.apps
        return state.apps.filter { it.appName.lowercase().contains(query) }
    }

    /** 清除 Snackbar 消息 */
    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }
}

/**
 * 限额配置 UI 状态。
 */
data class LockConfigUiState(
    val isLoading: Boolean = true,
    val apps: List<AppConfigItem> = emptyList(),
    val selectedApp: AppConfigItem? = null,
    val showBottomSheet: Boolean = false,
    val currentLimitMinutes: Int = LockConfigViewModel.DEFAULT_LIMIT_MINUTES,
    val isSaving: Boolean = false,
    val isAddingSystemApps: Boolean = false,
    val searchQuery: String = "",
    val snackbarMessage: String? = null,
    // ── 限额修改限制 ──
    val limitModifyBlocked: Boolean = false,
    val limitModifyBlockReason: String? = null
)

/**
 * 应用配置列表项。
 */
data class AppConfigItem(
    val packageName: String,
    val appName: String,
    val dailyLimitMinutes: Int = 0,
    val isLockEnabled: Boolean = true,
    val isConfigured: Boolean = false
)
