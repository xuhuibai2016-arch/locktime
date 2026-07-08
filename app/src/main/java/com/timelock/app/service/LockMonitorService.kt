package com.timelock.app.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import com.timelock.app.util.PermissionHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class LockMonitorService : Service() {

    companion object {
        const val TAG = "LockMonitorService"
        const val ACTION_CHECK = "com.timelock.app.ACTION_CHECK"
        const val ALARM_INTERVAL = 30_000L
        private const val ALARM_REQUEST_CODE = 2001

        fun scheduleNextAlarm(context: Context, count: Int) {
            val alarmMgr = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(context, LockCheckReceiver::class.java).apply {
                action = ACTION_CHECK
                putExtra("count", count)
            }
            val pending = PendingIntent.getBroadcast(
                context, ALARM_REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val triggerTime = SystemClock.elapsedRealtime() + ALARM_INTERVAL
            try {
                alarmMgr.setExactAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerTime, pending
                )
            } catch (_: SecurityException) {
                alarmMgr.setAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerTime, pending
                )
            }
        }
    }

    @Inject lateinit var engine: LockMonitorEngine
    @Inject lateinit var permissionHelper: PermissionHelper

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pollingJob: Job? = null
    private val screenStateReceiver = ScreenStateReceiver()

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")

        if (!permissionHelper.hasUsageStatsPermission()) {
            stopSelf()
            return
        }

        engine.onLockRequired = { pkg, _, limitMin, usedMin ->
            Log.d(TAG, "LOCK: $pkg (${usedMin}/${limitMin} min)")
            LockScreenDispatcher.show(this, pkg, limitMin, usedMin)
        }

        engine.createNotificationChannel()
        startForeground(LockMonitorEngine.NOTIFICATION_ID, engine.buildNotification())

        // 重新安排定时锁屏闹钟 (进程重启后恢复)
        ScheduledLockReceiver.schedule(this)

        // 注册熄屏/亮屏广播，控制检测暂停/恢复
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        registerReceiver(screenStateReceiver, filter)

        // 轮询前台应用
        startPolling()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand")
        scheduleNextAlarm(this, 0)
        return START_STICKY
    }

    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = scope.launch {
            Log.d(TAG, "polling coroutine started")
            while (isActive) {
                delay(4_000L)
                try {
                    // 使用引擎统一的前台检测方法
                    val pkg = engine.getForegroundPackage()
                    if (pkg != null && pkg != packageName) {
                        Log.d(TAG, "polling: detected $pkg, checking...")
                        engine.checkApp(pkg)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "polling error", e)
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        try { unregisterReceiver(screenStateReceiver) } catch (_: Exception) {}
        pollingJob?.cancel()
        super.onDestroy()
    }
}
