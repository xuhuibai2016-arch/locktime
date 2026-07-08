package com.timelock.app.service

import android.app.ActivityManager
import android.app.ActivityManager.RunningAppProcessInfo
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.timelock.app.di.ServiceEntryPoint
import com.timelock.app.domain.usecase.CheckAppLimitUseCase
import com.timelock.app.domain.usecase.CheckResult
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Manifest 注册接收器 —— 进程死亡后仍被 AlarmManager 唤醒。
 * 不使用 @AndroidEntryPoint（manifest 注册时不一定触发 Hilt 注入），
 * 改用 EntryPointAccessors 手动获取依赖。
 *
 * 使用 goAsync() + CoroutineScope 避免在主线程使用 runBlocking 导致 ANR。
 */
class LockCheckReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "LockCheckReceiver"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "onReceive: ${intent.action}")

        if (intent.action != LockMonitorService.ACTION_CHECK) return

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        val count = intent.getIntExtra("count", 0)

        scope.launch {
            try {
                // 手动从 Hilt 获取依赖
                val entryPoint = EntryPointAccessors.fromApplication(
                    appContext, ServiceEntryPoint::class.java
                )
                val useCase = entryPoint.checkAppLimitUseCase()
                val usageStatsRepo = entryPoint.usageStatsRepository()

                // 检查所有已配置应用的最近使用时间
                checkConfiguredApps(appContext, useCase)

                // 前台检测（作为补充）
                val pkg = getForegroundPackage(appContext)
                Log.d(TAG, "fg=$pkg")
                if (pkg != null && pkg != appContext.packageName) {
                    checkAndLock(appContext, pkg, useCase)
                }

                // 每 12 次同步一次数据
                if (count % 12 == 0) {
                    try {
                        usageStatsRepo.syncUsageStats()
                    } catch (_: Exception) {}
                }
            } catch (e: Exception) {
                Log.e(TAG, "onReceive error", e)
            } finally {
                // 设置下一次闹钟（必须在 pendingResult.finish() 之前，因为需要 Context）
                LockMonitorService.scheduleNextAlarm(appContext, count + 1)
                pendingResult.finish()
            }
        }
    }

    /** 记录最近活跃应用 (不触发锁屏)
     *  锁屏只由前台检测触发, 因为 UsageStats 的 lastTimeUsed 有延迟,
     *  用户可能已离开该 app 但数据尚未刷新, 此时触发锁屏会导致
     *  覆盖层在用户已返回桌面后仍然出现 */
    private suspend fun checkConfiguredApps(context: Context, useCase: CheckAppLimitUseCase) {
        try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return
            val now = System.currentTimeMillis()
            val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_BEST, now - 60_000, now)
            Log.d(TAG, "checkConfiguredApps: found ${stats.size} apps in last 60s")
            for (stat in stats) {
                if (stat.lastTimeUsed > now - 10_000) {
                    Log.d(TAG, "  recent: ${stat.packageName} lastUsed=${now - stat.lastTimeUsed}ms ago totalFg=${stat.totalTimeInForeground}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "checkConfiguredApps error", e)
        }
    }

    /** 检查单个应用是否超限，若超限则启动锁机覆盖层 */
    private suspend fun checkAndLock(
        context: Context,
        pkg: String,
        useCase: CheckAppLimitUseCase
    ) {
        val result = useCase(pkg)
        when (result) {
            is CheckResult.Locked -> {
                Log.d(TAG, "LOCK: $pkg (${result.usedMinutes}/${result.limitMinutes}min)")
                LockScreenDispatcher.show(context, pkg, result.limitMinutes, result.usedMinutes)
            }
            is CheckResult.Whitelisted -> Log.d(TAG, "SKIP $pkg: whitelisted")
            is CheckResult.NotConfigured -> Log.d(TAG, "SKIP $pkg: not configured (no limit set)")
            is CheckResult.Allowed -> Log.d(TAG, "SKIP $pkg: under limit (remaining=${result.remainingMillis}ms)")
        }
    }

    @Suppress("DEPRECATION")
    private fun getForegroundPackage(context: Context): String? {
        // 方法1: UsageEvents (最可靠，能检测任何前台切换)
        try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            val now = System.currentTimeMillis()
            val events = usm?.queryEvents(now - 10_000, now)
            if (events != null) {
                var lastPkg: String? = null
                var lastTime = 0L
                val event = UsageEvents.Event()
                while (events.hasNextEvent()) {
                    events.getNextEvent(event)
                    if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                        event.eventType == UsageEvents.Event.ACTIVITY_RESUMED
                    ) {
                        if (event.timeStamp > lastTime && event.packageName != context.packageName) {
                            lastTime = event.timeStamp
                            lastPkg = event.packageName
                        }
                    }
                }
                if (lastPkg != null) return lastPkg
            }
        } catch (_: Exception) {}

        // 方法2: UsageStats (后备)
        try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            val now = System.currentTimeMillis()
            val top = usm?.queryUsageStats(
                UsageStatsManager.INTERVAL_BEST, now - 30_000, now
            )
                ?.maxByOrNull { it.lastTimeUsed }?.packageName
            if (top != null) return top
        } catch (_: Exception) {}

        // 方法3: ActivityManager (MIUI 等设备上更可靠)
        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val processes = am?.runningAppProcesses
            val fg = processes?.firstOrNull {
                it.importance == RunningAppProcessInfo.IMPORTANCE_FOREGROUND
            }
            if (fg != null) {
                val pkgs = fg.pkgList
                if (pkgs.isNotEmpty()) {
                    Log.d(TAG, "  ActivityManager fg=${pkgs[0]} (importance=${fg.importance})")
                    return pkgs[0]
                }
            }
        } catch (_: Exception) {}

        return null
    }
}
