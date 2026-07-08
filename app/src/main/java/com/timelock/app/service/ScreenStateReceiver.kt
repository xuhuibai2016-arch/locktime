package com.timelock.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * 监听熄屏/亮屏广播，控制 [LockMonitorEngine] 的暂停状态。
 *
 * 熄屏时暂停所有前台检测以省电，亮屏时恢复。
 * 由 [LockMonitorService] 动态注册。
 */
class ScreenStateReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ScreenStateReceiver"
    }

    @InstallIn(SingletonComponent::class)
    @EntryPoint
    interface ScreenStateEntryPoint {
        fun lockMonitorEngine(): LockMonitorEngine
    }

    override fun onReceive(context: Context, intent: Intent) {
        val engine = try {
            EntryPointAccessors.fromApplication(
                context.applicationContext, ScreenStateEntryPoint::class.java
            ).lockMonitorEngine()
        } catch (_: Exception) {
            return
        }

        when (intent.action) {
            Intent.ACTION_SCREEN_OFF -> {
                Log.d(TAG, "Screen OFF — pausing detection")
                engine.isPaused = true
            }
            Intent.ACTION_SCREEN_ON -> {
                Log.d(TAG, "Screen ON — resuming detection")
                engine.isPaused = false
            }
        }
    }
}
