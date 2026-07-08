package com.timelock.app.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.timelock.app.ui.lock.LockOverlayScreen
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class LockOverlayService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    companion object {
        private const val TAG = "LockOverlayService"
        private const val EXTRA_PACKAGE_NAME = "package_name"
        private const val EXTRA_APP_NAME = "app_name"
        private const val EXTRA_LIMIT_MINUTES = "limit_minutes"
        private const val EXTRA_USED_MINUTES = "used_minutes"
        private const val EXTRA_IS_GLOBAL = "is_global"
        private const val EXTRA_GLOBAL_UNLOCK_EXHAUSTED = "global_unlock_exhausted"
        private const val EXTRA_LOCK_START_TIME = "lock_start_time"

        /** 全局一键锁屏使用的虚拟包名 */
        const val GLOBAL_LOCK_PACKAGE = "__global_lock__"

        /** 每日最大紧急解锁次数 (App 限额锁屏) */
        const val MAX_UNLOCK_COUNT = 2

        /** App 限额解锁后宽限分钟数 */
        const val GRACE_MINUTES = 5L

        /** 全局锁屏解锁后宽限期 (毫秒) */
        const val GLOBAL_GRACE_MS = 5 * 60 * 1000L

        fun stop(context: Context) {
            context.stopService(Intent(context, LockOverlayService::class.java))
        }
    }

    @Inject lateinit var lockStateManager: LockStateManager

    // ── Service 实例字段 ──

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val viewModelStore = ViewModelStore()
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    private var windowManager: WindowManager? = null
    private var overlayView: FrameLayout? = null
    private var isGlobalLock = false

    // ── 全局锁屏哨兵机制 ──
    // MIUI 手势可绕过 AccessibilityService 事件，因此用主动轮询守卫
    private val watchdogHandler = Handler(Looper.getMainLooper())
    private var watchdogRunning = false
    private var savedGlobalLockMinutes = 0
    private var savedGlobalLockStartTime = 0L
    private var savedLastParams: WindowManager.LayoutParams? = null

    override fun onCreate() {
        super.onCreate()
        lockStateManager.activeInstance = this
        savedStateRegistryController.performAttach()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        if (!Settings.canDrawOverlays(this)) {
            Log.w(TAG, "No overlay permission")
            stopSelf()
            return START_NOT_STICKY
        }

        val pkg = intent?.getStringExtra(EXTRA_PACKAGE_NAME) ?: ""
        if (pkg.isEmpty()) {
            Log.d(TAG, "empty intent — stopping")
            stopSelf()
            return START_NOT_STICKY
        }

        val appName = intent?.getStringExtra(EXTRA_APP_NAME) ?: ""
        val limitMin = intent?.getIntExtra(EXTRA_LIMIT_MINUTES, 0) ?: 0
        val usedMin = intent?.getLongExtra(EXTRA_USED_MINUTES, 0) ?: 0L
        isGlobalLock = intent?.getBooleanExtra(EXTRA_IS_GLOBAL, false) ?: false
        val lockStartTime = intent?.getLongExtra(EXTRA_LOCK_START_TIME, System.currentTimeMillis()) ?: System.currentTimeMillis()
        val unlockExhausted = intent?.getBooleanExtra(EXTRA_GLOBAL_UNLOCK_EXHAUSTED, false) ?: false

        val remainingUnlocks = if (isGlobalLock) {
            if (unlockExhausted) 0 else 1
        } else {
            lockStateManager.getRemainingUnlockCount(pkg)
        }

        // 保存全局锁屏参数，供 watchdog 使用
        if (isGlobalLock) {
            savedGlobalLockMinutes = limitMin
            savedGlobalLockStartTime = lockStartTime
        }

        showOverlay(
            packageName = pkg,
            appName = appName,
            limitMinutes = limitMin,
            usedMinutes = usedMin,
            remainingUnlocks = remainingUnlocks,
            isGlobalLock = isGlobalLock,
            lockStartTime = lockStartTime
        )
        return START_NOT_STICKY
    }

    @Suppress("DEPRECATION")
    private fun showOverlay(
        packageName: String, appName: String, limitMinutes: Int, usedMinutes: Long,
        remainingUnlocks: Int, isGlobalLock: Boolean, lockStartTime: Long
    ) {
        Log.d(TAG, "showOverlay — pkg=$packageName, global=$isGlobalLock, remaining=$remainingUnlocks")
        if (overlayView != null) return

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

        // 基础 flag：全屏 + 拦截外部触摸 + 覆盖系统栏
        var flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_FULLSCREEN

        // 全局锁屏：额外 flag 防止手势导航上滑 / 状态栏下拉
        if (isGlobalLock) {
            flags = flags or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            layoutType, flags, PixelFormat.OPAQUE
        ).apply {
            gravity = Gravity.CENTER
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@LockOverlayService)
            setViewTreeViewModelStoreOwner(this@LockOverlayService)
            setViewTreeSavedStateRegistryOwner(this@LockOverlayService)
            setContent {
                LockOverlayScreen(
                    appName = appName,
                    limitMinutes = limitMinutes,
                    usedMinutes = usedMinutes,
                    remainingUnlocks = remainingUnlocks,
                    isGlobalLock = isGlobalLock,
                    lockStartTimeMs = lockStartTime,
                    onReturnToDesktop = {
                        if (!isGlobalLock) {
                            lockStateManager.recordDismiss(packageName)
                            try {
                                val proxyIntent = Intent(this@LockOverlayService, com.timelock.app.ui.HomeProxyActivity::class.java)
                                proxyIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                startActivity(proxyIntent)
                                removeOverlay()
                            } catch (e: Exception) {
                                Log.e(TAG, "Return to desktop failed", e)
                            }
                        }
                    },
                    onEmergencyUnlock = {
                        if (isGlobalLock) {
                            handleGlobalUnlock(limitMinutes)
                        } else {
                            lockStateManager.recordEmergencyUnlock(packageName, usedMinutes)
                            removeOverlay()
                        }
                    },
                    onLockExpired = {
                        Log.d(TAG, "Global lock expired — removing overlay")
                        removeOverlay()
                    }
                )
            }
        }

        val container = object : FrameLayout(this) {
            override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
                if (event.keyCode == android.view.KeyEvent.KEYCODE_BACK) return true
                return super.dispatchKeyEvent(event)
            }

            @Suppress("DEPRECATION")
            override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
                if (isGlobalLock) {
                    applyImmersiveFlags()
                    return WindowInsets.CONSUMED
                }
                return super.onApplyWindowInsets(insets)
            }

            override fun onInterceptTouchEvent(ev: android.view.MotionEvent): Boolean {
                if (!isGlobalLock) return super.onInterceptTouchEvent(ev)
                // 只拦截顶部 80dp / 底部 80dp 的边缘触摸，阻断状态栏下拉和导航手势
                val density = resources.displayMetrics.density
                val edgePx = (80 * density).toInt()
                val y = ev.y.toInt()
                return (y < edgePx || y > height - edgePx).also { intercepted ->
                    if (intercepted && ev.action == android.view.MotionEvent.ACTION_DOWN) {
                        applyImmersiveFlags() // 边缘被触摸 → 立即加固
                    }
                }
            }

            override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
                super.onWindowFocusChanged(hasWindowFocus)
                if (isGlobalLock && hasWindowFocus) {
                    // 获得焦点 → 立即加固沉浸模式（MIUI 手势导致系统栏短暂出现后恢复时触发）
                    applyImmersiveFlags()
                }
            }
        }.apply {
            setViewTreeLifecycleOwner(this@LockOverlayService)
            setViewTreeViewModelStoreOwner(this@LockOverlayService)
            setViewTreeSavedStateRegistryOwner(this@LockOverlayService)
            addView(composeView)
            isFocusableInTouchMode = true
            isFocusable = true
            if (isGlobalLock) {
                // MIUI 沉浸模式下系统栏有 ~3s 自动隐藏定时器。
                // 在 3s 窗口内持续强制重设标记，确保用户来不及做第二次滑动。
                setOnSystemUiVisibilityChangeListener { _ ->
                    for (i in 0..10) {
                        postDelayed({ applyImmersiveFlags() }, i * 100L)
                    }
                }
            }
            requestApplyInsets()
        }

        overlayView = container
        savedLastParams = params
        try {
            windowManager?.addView(container, params)
            Log.d(TAG, "Overlay shown — $appName global=$isGlobalLock")
            // 全局锁屏：启动哨兵，每 300ms 检查覆盖层是否还在
            if (isGlobalLock) startWatchdog()
        } catch (e: SecurityException) {
            Log.e(TAG, "addView rejected", e)
            overlayView = null; stopSelf()
        } catch (e: Exception) {
            Log.e(TAG, "addView failed", e)
            overlayView = null; stopSelf()
        }
    }

    /** 全局锁屏解锁：记录状态 + 显示 Toast + 安排 5 分钟后重新锁屏 */
    private fun handleGlobalUnlock(originalLockMinutes: Int) {
        lockStateManager.recordGlobalUnlock()
        val graceEnd = System.currentTimeMillis() + GLOBAL_GRACE_MS

        Toast.makeText(this, "已解锁 — 5 分钟全局宽限期，之后将重新锁屏", Toast.LENGTH_LONG).show()
        Log.d(TAG, "Global unlock — grace ends at $graceEnd")

        // 安排 5 分钟后重新触发全局锁屏（无解锁机会）
        scheduleGlobalReLock(originalLockMinutes, graceEnd)
        removeOverlay()
    }

    /** 用 AlarmManager 安排 5 分钟后重新锁屏 */
    private fun scheduleGlobalReLock(lockMinutes: Int, triggerAtMs: Long) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(this, LockOverlayService::class.java).apply {
            putExtra(EXTRA_PACKAGE_NAME, GLOBAL_LOCK_PACKAGE)
            putExtra(EXTRA_APP_NAME, "一键锁屏")
            putExtra(EXTRA_LIMIT_MINUTES, lockMinutes)
            putExtra(EXTRA_USED_MINUTES, 0L)
            putExtra(EXTRA_IS_GLOBAL, true)
            putExtra(EXTRA_GLOBAL_UNLOCK_EXHAUSTED, true)
            putExtra(EXTRA_LOCK_START_TIME, System.currentTimeMillis())
        }
        val pending = PendingIntent.getService(
            this, 1001, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMs, pending)
        Log.d(TAG, "Global re-lock scheduled at $triggerAtMs")
    }

    /**
     * 强制清除旧 overlay 引用。
     * 被 [reTriggerGlobalLock] 调用，确保后续 showOverlay() 不会因
     * 「overlayView != null」守卫而跳过。
     */
    fun forceClearStaleOverlay() {
        overlayView?.let { view ->
            try {
                windowManager?.removeView(view)
            } catch (_: Exception) { /* 已被移除也没关系 */ }
        }
        overlayView = null
    }

    // ── Watchdog 哨兵：主动检测 overlay 是否被 MIUI 移除 ──

    private val watchdogRunnable = object : Runnable {
        override fun run() {
            if (!watchdogRunning) return
            if (!lockStateManager.isGlobalLockEngaged()) {
                Log.d(TAG, "watchdog: global lock no longer engaged, stopping")
                watchdogRunning = false
                return
            }

            val view = overlayView
            if (view != null && view.isAttachedToWindow) {
                // overlay 正常显示，无需操作
            } else if (view != null && !view.isAttachedToWindow) {
                // overlay 被 MIUI 从 WindowManager 分离，但引用还在 → 重新 addView
                Log.w(TAG, "watchdog: overlay detached but ref exists → re-adding")
                reAttachOverlay(view)
            } else {
                // overlay 引用为 null → 完全重建
                Log.w(TAG, "watchdog: overlay is null → recreating")
                recreateGlobalOverlay()
            }

            watchdogHandler.postDelayed(this, 5000L)
        }
    }

    private fun startWatchdog() {
        watchdogRunning = true
        watchdogHandler.removeCallbacks(watchdogRunnable)
        watchdogHandler.postDelayed(watchdogRunnable, 1000L)
        Log.d(TAG, "watchdog started (1s interval)")
    }

    private fun stopWatchdog() {
        watchdogRunning = false
        watchdogHandler.removeCallbacks(watchdogRunnable)
    }

    /** 将被分离的 overlay 重新 addView 到 WindowManager */
    private fun reAttachOverlay(view: FrameLayout) {
        val params = savedLastParams ?: return
        try {
            // 先移除（可能处于半分离状态）
            try { windowManager?.removeView(view) } catch (_: Exception) {}
            windowManager?.addView(view, params)
            Log.d(TAG, "watchdog: overlay re-attached")
        } catch (e: Exception) {
            Log.e(TAG, "watchdog: re-attach failed, recreating", e)
            overlayView = null
            recreateGlobalOverlay()
        }
    }

    /** 完全重建全局锁屏 overlay（保留原始倒计时） */
    private fun recreateGlobalOverlay() {
        if (savedGlobalLockMinutes <= 0) return
        forceClearStaleOverlay()

        showOverlay(
            packageName = GLOBAL_LOCK_PACKAGE,
            appName = "一键锁屏",
            limitMinutes = savedGlobalLockMinutes,
            usedMinutes = 0L,
            remainingUnlocks = lockStateManager.getGlobalUnlockRemaining(),
            isGlobalLock = true,
            lockStartTime = savedGlobalLockStartTime
        )
        Log.d(TAG, "watchdog: overlay recreated")
    }

    private fun removeOverlay() {
        stopWatchdog()
        overlayView?.let { view ->
            try { windowManager?.removeView(view) }
            catch (e: Exception) { Log.e(TAG, "removeOverlay", e) }
        }
        overlayView = null
        stopSelf()
    }

    /**
     * 强制隐藏系统状态栏和导航栏。
     * 使用 IMMERSIVE（非 STICKY）模式：滑动不会唤醒系统栏；
     * 即使被其他机制激活，[setOnSystemUiVisibilityChangeListener] 也会立即重隐藏。
     */
    /**
     * 强制隐藏系统栏。不用 IMMERSIVE（MIUI 上反而会给手势唤醒留入口），
     * 只用纯隐藏标记 + [setOnSystemUiVisibilityChangeListener] 持续守卫。
     */
    @Suppress("DEPRECATION")
    private fun View.applyImmersiveFlags() {
        systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            )
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() {
        lockStateManager.activeInstance = null
        stopWatchdog()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        viewModelStore.clear()
        removeOverlay()
        super.onDestroy()
    }
}
