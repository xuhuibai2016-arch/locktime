package com.timelock.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * 开机广播接收器：设备重启后自行启动监控服务 + 重新安排定时锁屏闹钟。
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        Log.d(TAG, "BOOT_COMPLETED — auto-starting services")

        // 1. 重启前台监控服务
        try {
            val serviceIntent = Intent(context.applicationContext, LockMonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.applicationContext.startForegroundService(serviceIntent)
            } else {
                context.applicationContext.startService(serviceIntent)
            }
            Log.d(TAG, "LockMonitorService started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start LockMonitorService", e)
        }

        // 2. 重新安排定时锁屏闹钟
        ScheduledLockReceiver.schedule(context.applicationContext)
    }
}
