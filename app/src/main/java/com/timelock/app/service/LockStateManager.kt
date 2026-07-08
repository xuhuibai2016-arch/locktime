package com.timelock.app.service

import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 锁屏状态管理器 —— 管理冷却期、防抖、解锁计数、宽限期、全局锁屏状态。
 *
 * 从 [LockOverlayService.companion] 中抽取，通过 Hilt 注入。
 * 状态持久化使用 SharedPreferences (后续可迁移至 Room)。
 */
@Singleton
class LockStateManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "LockStateManager"

        /** 紧急解锁后跳过锁机的冷却时间 (毫秒) */
        private const val UNLOCK_COOLDOWN_MS = 30_000L

        /** 返回桌面防抖窗口 (毫秒) */
        private const val DISMISS_DEBOUNCE_MS = 3_000L

        /** App 限额解锁后宽限分钟数 */
        private const val GRACE_MINUTES = 5L

        /** 全局锁屏解锁后宽限期 (毫秒) */
        private const val GLOBAL_GRACE_MS = 5 * 60 * 1000L

        private const val PREFS_NAME = "lock_overlay_prefs"
        private const val KEY_UNLOCK_TIME_PREFIX = "unlock_time_"
        private const val KEY_DISMISS_TIME_PREFIX = "dismiss_time_"
        private const val KEY_UNLOCK_COUNT_PREFIX = "unlock_count_"
        private const val KEY_UNLOCK_USED_MINUTES_PREFIX = "unlock_used_minutes_"

        private const val KEY_GLOBAL_UNLOCKED = "global_unlocked"
        private const val KEY_GLOBAL_GRACE_END = "global_grace_end"
        private const val KEY_GLOBAL_LOCK_START = "global_lock_start"
        private const val KEY_GLOBAL_LOCK_MINUTES = "global_lock_minutes"
    }

    // ── 内存缓存 ──
    private val cachedCooldowns = mutableMapOf<String, Long>()
    private val cachedDismisses = mutableMapOf<String, Long>()
    private val cachedUnlockCounts = mutableMapOf<String, Int>()
    private val cachedUnlockUsedMinutes = mutableMapOf<String, Long>()

    /** 当前活跃的 Service 实例 (由 LockOverlayService 在 onCreate/onDestroy 中设置) */
    @Volatile
    var activeInstance: LockOverlayService? = null

    // ═══════════════════════════════════════════════════════════
    // App 限额锁屏
    // ═══════════════════════════════════════════════════════════

    fun startAppLock(packageName: String, appName: String, limitMinutes: Int, usedMinutes: Long) {
        if (isInCooldown(packageName)) {
            Log.d(TAG, "skip lock for $packageName: in cooldown")
            return
        }
        if (isInDismissDebounce(packageName)) {
            Log.d(TAG, "skip lock for $packageName: in dismiss debounce")
            return
        }
        val intent = Intent(context, LockOverlayService::class.java).apply {
            putExtra("package_name", packageName)
            putExtra("app_name", appName)
            putExtra("limit_minutes", limitMinutes)
            putExtra("used_minutes", usedMinutes)
        }
        context.startService(intent)
    }

    fun isInCooldown(packageName: String): Boolean {
        val unlockTime = cachedCooldowns.getOrPut(packageName) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getLong(KEY_UNLOCK_TIME_PREFIX + packageName, 0L)
        }
        if (unlockTime == 0L) return false
        return (System.currentTimeMillis() - unlockTime) < UNLOCK_COOLDOWN_MS
    }

    fun recordDismiss(packageName: String) {
        val now = System.currentTimeMillis()
        cachedDismisses[packageName] = now
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putLong(KEY_DISMISS_TIME_PREFIX + packageName, now)
            .apply()
    }

    fun isInDismissDebounce(packageName: String): Boolean {
        val t = cachedDismisses.getOrPut(packageName) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getLong(KEY_DISMISS_TIME_PREFIX + packageName, 0L)
        }
        if (t == 0L) return false
        return (System.currentTimeMillis() - t) < DISMISS_DEBOUNCE_MS
    }

    fun getRemainingUnlockCount(packageName: String): Int {
        return (LockOverlayService.MAX_UNLOCK_COUNT - getTodayUnlockCount(packageName)).coerceAtLeast(0)
    }

    fun isUnlockAllowed(packageName: String): Boolean {
        return getRemainingUnlockCount(packageName) > 0
    }

    fun getTodayUnlockCount(packageName: String): Int {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val key = KEY_UNLOCK_COUNT_PREFIX + packageName + "_" + today
        return cachedUnlockCounts.getOrPut(key) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(key, 0)
        }
    }

    fun recordEmergencyUnlock(packageName: String, usedMinutes: Long) {
        val now = System.currentTimeMillis()
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val countKey = KEY_UNLOCK_COUNT_PREFIX + packageName + "_" + today
        val currentCount = getTodayUnlockCount(packageName)
        cachedCooldowns[packageName] = now
        cachedUnlockUsedMinutes[packageName] = usedMinutes
        cachedUnlockCounts[countKey] = currentCount + 1
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putLong(KEY_UNLOCK_TIME_PREFIX + packageName, now)
            .putLong(KEY_UNLOCK_USED_MINUTES_PREFIX + packageName, usedMinutes)
            .putInt(countKey, currentCount + 1)
            .apply()
    }

    fun isInGracePeriod(packageName: String, currentUsedMinutes: Long): Boolean {
        if (getTodayUnlockCount(packageName) == 0) return false
        val unlockUsedMinutes = cachedUnlockUsedMinutes.getOrPut(packageName) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getLong(KEY_UNLOCK_USED_MINUTES_PREFIX + packageName, Long.MAX_VALUE)
        }
        if (unlockUsedMinutes == Long.MAX_VALUE) return false
        return (currentUsedMinutes - unlockUsedMinutes) < GRACE_MINUTES
    }

    // ═══════════════════════════════════════════════════════════
    // 全局锁屏
    // ═══════════════════════════════════════════════════════════

    fun startGlobalLock(lockMinutes: Int) {
        activeInstance?.forceClearStaleOverlay()

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        prefs.edit()
            .putBoolean(KEY_GLOBAL_UNLOCKED, false)
            .putLong(KEY_GLOBAL_GRACE_END, 0L)
            .putLong(KEY_GLOBAL_LOCK_START, now)
            .putInt(KEY_GLOBAL_LOCK_MINUTES, lockMinutes)
            .apply()

        val intent = Intent(context, LockOverlayService::class.java).apply {
            putExtra("package_name", LockOverlayService.GLOBAL_LOCK_PACKAGE)
            putExtra("app_name", "一键锁屏")
            putExtra("limit_minutes", lockMinutes)
            putExtra("used_minutes", 0L)
            putExtra("is_global", true)
            putExtra("lock_start_time", now)
        }
        context.startService(intent)
    }

    /** 是否处于全局锁屏宽限期 */
    fun isGlobalLockInGrace(): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val graceEnd = prefs.getLong(KEY_GLOBAL_GRACE_END, 0L)
        return graceEnd > 0L && System.currentTimeMillis() < graceEnd
    }

    /** 全局锁屏是否应该正在展示（已触发 + 未解锁 + 非宽限期） */
    fun isGlobalLockEngaged(): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lockStart = prefs.getLong(KEY_GLOBAL_LOCK_START, 0L)
        if (lockStart == 0L) return false
        if (isGlobalLockInGrace()) return false
        val unlocked = prefs.getBoolean(KEY_GLOBAL_UNLOCKED, false)
        if (unlocked) return false
        val lockMinutes = prefs.getInt(KEY_GLOBAL_LOCK_MINUTES, 0)
        val elapsed = System.currentTimeMillis() - lockStart
        return elapsed < lockMinutes * 60_000L
    }

    /** 重新触发全局锁屏覆盖层（保留原始倒计时，不计为新的锁屏） */
    fun reTriggerGlobalLock() {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lockStart = prefs.getLong(KEY_GLOBAL_LOCK_START, System.currentTimeMillis())
        val lockMinutes = prefs.getInt(KEY_GLOBAL_LOCK_MINUTES, 0)
        val unlocked = prefs.getBoolean(KEY_GLOBAL_UNLOCKED, false)
        if (lockMinutes <= 0) return

        Log.d(TAG, "reTriggerGlobalLock — preserving startTime=$lockStart, unlocked=$unlocked")

        activeInstance?.forceClearStaleOverlay()

        val intent = Intent(context, LockOverlayService::class.java).apply {
            putExtra("package_name", LockOverlayService.GLOBAL_LOCK_PACKAGE)
            putExtra("app_name", "一键锁屏")
            putExtra("limit_minutes", lockMinutes)
            putExtra("used_minutes", 0L)
            putExtra("is_global", true)
            putExtra("lock_start_time", lockStart)
            putExtra("global_unlock_exhausted", unlocked)
        }
        context.startService(intent)
    }

    /** 获取全局锁屏剩余解锁次数 (0 或 1) */
    fun getGlobalUnlockRemaining(): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return if (prefs.getBoolean(KEY_GLOBAL_UNLOCKED, false)) 0 else 1
    }

    /** 记录全局锁屏解锁状态 */
    fun recordGlobalUnlock() {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val graceEnd = System.currentTimeMillis() + GLOBAL_GRACE_MS
        prefs.edit()
            .putBoolean(KEY_GLOBAL_UNLOCKED, true)
            .putLong(KEY_GLOBAL_GRACE_END, graceEnd)
            .apply()
    }

    /** 停止锁屏服务 */
    fun stopService() {
        context.stopService(Intent(context, LockOverlayService::class.java))
    }
}
