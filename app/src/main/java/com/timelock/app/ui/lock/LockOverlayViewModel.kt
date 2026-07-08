package com.timelock.app.ui.lock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 锁机覆盖层 ViewModel。
 *
 * 管理长按 3 秒紧急解锁的状态机：
 * - [startUnlockPress] → 启动 3 秒倒计时协程，每 50ms 更新进度
 * - [resetUnlockPress] → 取消协程，归零进度（已解锁则保持）
 * - 3 秒倒计时完成 → [LockOverlayUiState.isUnlocked] = true
 */
@HiltViewModel
class LockOverlayViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(LockOverlayUiState())
    val uiState: StateFlow<LockOverlayUiState> = _uiState.asStateFlow()

    private var unlockJob: Job? = null

    /** 开始长按：启动 3 秒倒计时进度 */
    fun startUnlockPress() {
        // 已解锁则忽略重复按压
        if (_uiState.value.isUnlocked) return

        unlockJob?.cancel()
        unlockJob = viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            while (isActive) {
                val elapsed = System.currentTimeMillis() - startTime
                val progress = (elapsed / 3000f).coerceIn(0f, 1f)
                _uiState.update { it.copy(pressProgress = progress) }

                if (progress >= 1f) {
                    _uiState.update { it.copy(isUnlocked = true) }
                    return@launch
                }
                delay(100L)
            }
        }
    }

    /** 释放按压：取消倒计时，归零进度（已解锁状态保持不变） */
    fun resetUnlockPress() {
        unlockJob?.cancel()
        unlockJob = null
        _uiState.update {
            if (it.isUnlocked) {
                it.copy(pressProgress = 0f) // 保持 unlocked
            } else {
                it.copy(pressProgress = 0f, isUnlocked = false)
            }
        }
    }

    /** 设置锁机界面的应用信息 */
    fun setAppInfo(appName: String, limitMinutes: Int, usedMinutes: Long) {
        _uiState.update {
            it.copy(appName = appName, limitMinutes = limitMinutes, usedMinutes = usedMinutes)
        }
    }
}

/**
 * 锁机覆盖层 UI 状态。
 */
data class LockOverlayUiState(
    /** 按压进度 0f → 1f */
    val pressProgress: Float = 0f,
    /** 是否已紧急解锁 */
    val isUnlocked: Boolean = false,
    /** 被锁应用名称 */
    val appName: String = "",
    /** 每日限额 (分钟) */
    val limitMinutes: Int = 0,
    /** 已用时长 (分钟) */
    val usedMinutes: Long = 0
)
