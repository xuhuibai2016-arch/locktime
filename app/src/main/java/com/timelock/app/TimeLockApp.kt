package com.timelock.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * TimeLock Application — Hilt 依赖注入入口。
 *
 * 职责：
 * - 初始化 Hilt DI 容器
 * - 后续 Phase 中在此注册 UsageStats 权限回调 & 锁机服务绑定
 */
@HiltAndroidApp
class TimeLockApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Phase 2+ 初始化:
        // - UsageStatsManager 权限检查
        // - LockOverlayService 预绑定
        // - 定时任务调度 (WorkManager)
    }
}
