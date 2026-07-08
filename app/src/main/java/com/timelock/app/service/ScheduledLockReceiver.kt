package com.timelock.app.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import com.timelock.app.di.ServiceEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * 定时锁屏 AlarmManager 接收器。
 * 到指定时间后自动触发全局锁屏。
 */
class ScheduledLockReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ScheduledLockReceiver"
        private const val ACTION_TRIGGER = "com.timelock.app.ACTION_SCHEDULED_LOCK"
        private const val REQUEST_CODE = 3001

        /** 安排下一次定时锁屏闹钟。从 Room 读取配置，计算下一次触发时间。 */
        fun schedule(context: Context) {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            scope.launch {
                try {
                    val entryPoint = EntryPointAccessors.fromApplication(
                        context.applicationContext, ServiceEntryPoint::class.java
                    )
                    val dao = entryPoint.scheduledLockDao()
                    val config = dao.get() ?: return@launch
                    if (!config.enabled) {
                        cancel(context)
                        return@launch
                    }

                    val alarmMgr = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return@launch
                    val intent = Intent(context, ScheduledLockReceiver::class.java).apply {
                        action = ACTION_TRIGGER
                    }
                    val pending = PendingIntent.getBroadcast(
                        context, REQUEST_CODE, intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )

                    // 计算距离下一次触发时间的毫秒数
                    val now = Calendar.getInstance()
                    val triggerCal = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, config.lockHour)
                        set(Calendar.MINUTE, config.lockMinute)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    // 如果今天的时间已过，改为明天
                    if (triggerCal.timeInMillis <= now.timeInMillis) {
                        triggerCal.add(Calendar.DAY_OF_YEAR, 1)
                    }

                    val triggerAtElapsed = SystemClock.elapsedRealtime() +
                        (triggerCal.timeInMillis - now.timeInMillis)

                    alarmMgr.setExactAndAllowWhileIdle(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        triggerAtElapsed,
                        pending
                    )
                    Log.d(TAG, "Scheduled for ${triggerCal.time}")
                } catch (e: Exception) {
                    Log.e(TAG, "schedule failed", e)
                }
            }
        }

        /** 取消定时锁屏闹钟 */
        fun cancel(context: Context) {
            val alarmMgr = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(context, ScheduledLockReceiver::class.java).apply {
                action = ACTION_TRIGGER
            }
            val pending = PendingIntent.getBroadcast(
                context, REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmMgr.cancel(pending)
            Log.d(TAG, "Cancelled")
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_TRIGGER) return

        Log.d(TAG, "Triggered! Starting scheduled lock")

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val pendingResult = goAsync()

        scope.launch {
            try {
                val entryPoint = EntryPointAccessors.fromApplication(
                    context.applicationContext, ServiceEntryPoint::class.java
                )
                val dao = entryPoint.scheduledLockDao()
                val config = dao.get()

                if (config != null && config.enabled) {
                    Log.d(TAG, "Locking for ${config.lockDurationMinutes} min")
                    entryPoint.lockStateManager().startGlobalLock(config.lockDurationMinutes)
                }

                // 安排明天的闹钟
                schedule(context.applicationContext)
            } catch (e: Exception) {
                Log.e(TAG, "onReceive error", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
