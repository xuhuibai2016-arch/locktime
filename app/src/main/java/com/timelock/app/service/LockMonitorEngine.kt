package com.timelock.app.service

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.timelock.app.MainActivity
import com.timelock.app.domain.usecase.CheckAppLimitUseCase
import com.timelock.app.domain.usecase.CheckResult
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 锁机监控引擎 (独立于 Service 生命周期，可单元测试)。
 *
 * 职责：
 * - 轮询检查前台应用是否超限
 * - 管理前台通知
 * - 触发锁机覆盖层
 */
@Singleton
class LockMonitorEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val checkAppLimitUseCase: CheckAppLimitUseCase
) {
    companion object {
        const val CHANNEL_ID = "lock_monitor_channel"
        const val CHANNEL_NAME = "应用时长监控"
        const val NOTIFICATION_ID = 1001

        /** 轮询间隔 (毫秒) */
        const val POLL_INTERVAL_MS = 5_000L

        /** 同包名检测去重窗口 (毫秒) — 避免三层检测在短时间内对同一 APP 重复检查 */
        private const val DEBOUNCE_MS = 3_000L
    }

    // 锁机回调 (由 Service 注册)
    var onLockRequired: ((packageName: String, appName: String, limitMinutes: Int, usedMinutes: Long) -> Unit)? = null

    /** 熄屏暂停标记：true 时跳过所有检测 */
    @Volatile
    var isPaused: Boolean = false

    // 检测去重缓存：同一包名在 DEBOUNCE_MS 内跳过完整检查
    private val lastCheckTimes = mutableMapOf<String, Long>()
    private val lastCheckDecisions = mutableMapOf<String, CheckDecision>()

    /** 检查应用是否需要锁机 (带 3s 去重)。熄屏时立即返回 [CheckDecision.SKIP_PAUSED]。 */
    suspend fun checkApp(packageName: String): CheckDecision {
        if (isPaused) return CheckDecision.SKIP_PAUSED

        // 去重：同一包名在 DEBOUNCE_MS 内直接返回上次结果
        val now = System.currentTimeMillis()
        lastCheckTimes[packageName]?.let { lastTime ->
            if (now - lastTime < DEBOUNCE_MS) {
                val cached = lastCheckDecisions[packageName]
                if (cached != null) {
                    Log.d("LockMonitorEngine", "checkApp($packageName) → cached (${now - lastTime}ms ago)")
                    return cached
                }
            }
        }

        val decision = when (val result = checkAppLimitUseCase(packageName)) {
            is CheckResult.Whitelisted -> {
                Log.d("LockMonitorEngine", "checkApp($packageName) → WHITELISTED")
                CheckDecision.SKIP_WHITELISTED
            }
            is CheckResult.NotConfigured -> {
                Log.d("LockMonitorEngine", "checkApp($packageName) → NOT_CONFIGURED")
                CheckDecision.SKIP_NOT_CONFIGURED
            }
            is CheckResult.Allowed -> {
                Log.d("LockMonitorEngine", "checkApp($packageName) → ALLOWED (remaining=${result.remainingMillis}ms)")
                CheckDecision.SKIP_UNDER_LIMIT
            }
            is CheckResult.Locked -> {
                Log.d("LockMonitorEngine", "checkApp($packageName) → LOCKED (${result.usedMinutes}/${result.limitMinutes}min)")
                onLockRequired?.invoke(
                    packageName, "", result.limitMinutes, result.usedMinutes
                )
                CheckDecision.LOCK(result.limitMinutes, result.usedMinutes)
            }
        }

        // 缓存本次检查结果，用于后续去重
        lastCheckTimes[packageName] = now
        lastCheckDecisions[packageName] = decision
        return decision
    }

    /** 创建通知渠道 */
    fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "TimeLock 后台监控服务"
                setShowBadge(false)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    /** 构建前台服务通知 */
    fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("TimeLock 监控中")
            .setContentText("正在监控应用使用时长")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    /**
     * 统一前台应用检测（合并自 LockCheckReceiver 和 LockMonitorService）。
     * 三级 fallback: UsageEvents → UsageStats → ActivityManager。
     */
    @Suppress("DEPRECATION")
    fun getForegroundPackage(): String? {
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
            )?.maxByOrNull { it.lastTimeUsed }?.packageName
            if (top != null && top != context.packageName) return top
        } catch (_: Exception) {}

        // 方法3: ActivityManager (MIUI 等设备上更可靠)
        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val processes = am?.runningAppProcesses
            val fg = processes?.firstOrNull {
                it.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
            }
            if (fg != null) {
                val pkgs = fg.pkgList
                if (pkgs.isNotEmpty()) return pkgs[0]
            }
        } catch (_: Exception) {}

        return null
    }

    /** 检查决策密封类 */
    sealed class CheckDecision {
        data object SKIP_WHITELISTED : CheckDecision()
        data object SKIP_NOT_CONFIGURED : CheckDecision()
        data object SKIP_UNDER_LIMIT : CheckDecision()
        data object SKIP_PAUSED : CheckDecision()
        data class LOCK(val limitMinutes: Int, val usedMinutes: Long) : CheckDecision()
    }
}
