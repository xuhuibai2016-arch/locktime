package com.timelock.app.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.timelock.app.di.ServiceEntryPoint
import com.timelock.app.domain.usecase.CheckResult
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 无障碍服务 — 实时监听前台 APP 切换 (MIUI 上唯一可靠的前台检测方案)。
 *
 * 触发锁屏时调用 [LockScreenDispatcher.show]，通过全屏通知 Intent 弹出锁屏。
 * 不再直接 startActivity 或创建 WindowManager 覆盖层。
 */
class LockAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "LockAccessibility"
        var instance: LockAccessibilityService? = null
            private set
        fun isEnabled(): Boolean = instance != null
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 100
        }
        Log.d(TAG, "AccessibilityService connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return

        Log.d(TAG, "foreground changed: $pkg")

        scope.launch {
            try {
                val entryPoint = EntryPointAccessors.fromApplication(applicationContext, ServiceEntryPoint::class.java)
                val useCase = entryPoint.checkAppLimitUseCase()
                val lockStateManager = entryPoint.lockStateManager()

                // ── 全局锁屏逃逸检测（最高优先级）──
                if (lockStateManager.isGlobalLockEngaged()) {
                    Log.w(TAG, "GLOBAL LOCK ESCAPE detected → re-locking immediately")
                    lockStateManager.reTriggerGlobalLock()
                    return@launch
                }

                when (val result = useCase(pkg)) {
                    is CheckResult.Locked -> {
                        Log.d(TAG, "LOCK: $pkg (${result.usedMinutes}/${result.limitMinutes}min)")
                        if (!lockStateManager.isInCooldown(pkg)) {
                            LockScreenDispatcher.show(applicationContext, pkg, result.limitMinutes, result.usedMinutes)
                        }
                    }
                    else -> {
                        LockScreenDispatcher.cancel(applicationContext)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "check error", e)
            }
        }
    }

    override fun onInterrupt() {}
    override fun onDestroy() {
        instance = null
        Log.d(TAG, "AccessibilityService destroyed")
        super.onDestroy()
    }
}
