package com.timelock.app.service

import android.content.Context
import android.util.Log
import com.timelock.app.di.ServiceEntryPoint
import dagger.hilt.android.EntryPointAccessors

/**
 * 统一锁屏调度器 —— WindowManager 悬浮窗方案 (微信通话界面模式)。
 *
 * 原理:
 *   通过 LockOverlayService 启动 TYPE_APPLICATION_OVERLAY 悬浮窗，
 *   直接覆盖在当前前台应用之上，不依赖通知系统的 fullScreenIntent。
 *   MIUI 兼容性好，无需「后台弹出界面」权限。
 *
 * 使用:
 *   LockScreenDispatcher.show(context, pkg, limitMin, usedMin)
 */
object LockScreenDispatcher {

    private const val TAG = "LockScreenDispatcher"

    fun show(context: Context, packageName: String, limitMinutes: Int, usedMinutes: Long) {
        Log.d(TAG, "show: $packageName (${usedMinutes}min / ${limitMinutes}min limit)")

        val ep = EntryPointAccessors.fromApplication(
            context.applicationContext, ServiceEntryPoint::class.java
        )
        val lockStateManager = ep.lockStateManager()

        // 检查是否处于解锁宽限期内 (解锁后 5 分钟额外使用)
        if (lockStateManager.isInGracePeriod(packageName, usedMinutes)) {
            Log.d(TAG, "skip lock for $packageName: in grace period after unlock")
            return
        }

        // 解析应用显示名称
        val appName = try {
            ep.appInfoProvider().getAppName(packageName)
        } catch (_: Exception) {
            packageName
        }

        // 使用悬浮窗覆盖层锁屏 (微信通话界面方案，MIUI 兼容)
        lockStateManager.startAppLock(
            packageName = packageName,
            appName = appName,
            limitMinutes = limitMinutes,
            usedMinutes = usedMinutes
        )
    }

    /** 取消锁屏 (紧急解锁后调用) */
    fun cancel(context: Context) {
        LockOverlayService.stop(context)
    }
}
