package com.timelock.app.util

import android.Manifest
import android.app.AlarmManager
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 关键权限检测与引导工具。
 *
 * 标准权限：
 * - [hasUsageStatsPermission] — PACKAGE_USAGE_STATS，监控前台应用
 * - [canDrawOverlays]        — SYSTEM_ALERT_WINDOW，悬浮窗锁机
 * - [hasNotificationPermission] — POST_NOTIFICATIONS (Android 13+)
 * - [hasExactAlarmPermission] — SCHEDULE_EXACT_ALARM，精准闹钟唤醒
 *
 * MIUI 专项：
 * - [isMiui] — 检测是否为 MIUI 设备
 * - [hasMiuiLockScreenNotification] — MIUIOP(10021) 锁屏提醒
 * - [hasMiuiBackgroundPopup]        — MIUIOP(10008) 后台弹出界面
 * - [isIgnoringBatteryOptimizations] — 电池优化白名单
 */
@Singleton
class PermissionHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    // ─── 标准权限检测 ───

    /** UsageStats 权限 (需要用户在设置中手动开启) */
    fun hasUsageStatsPermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** 悬浮窗权限 */
    fun canDrawOverlays(): Boolean {
        return Settings.canDrawOverlays(context)
    }

    /** 通知权限 (Android 13+) */
    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    /** 精准闹钟权限 (Android 12+)，影响 LockCheckReceiver 能否每5秒精准唤醒 */
    fun hasExactAlarmPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmMgr = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            alarmMgr?.canScheduleExactAlarms() == true
        } else {
            true
        }
    }

    // ─── MIUI 专项检测 ───

    /** 检测是否为 MIUI/HyperOS 设备 */
    fun isMiui(): Boolean {
        // 方式 1: 检查 Build.MANUFACTURER
        if (Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true)) return true
        // 方式 2: 检查 ro.miui.ui.version.name 系统属性
        return try {
            val clazz = Class.forName("android.os.SystemProperties")
            val method = clazz.getMethod("get", String::class.java, String::class.java)
            val value = method.invoke(null, "ro.miui.ui.version.name", "") as? String ?: ""
            value.isNotEmpty()
        } catch (_: Exception) {
            false
        }
    }

    /**
     * 检查 MIUI 特有权限 (通过 AppOpsManager 反射调用)。
     * MIUI 扩展了标准 AppOpsManager，增加了 MIUIOP 系列权限码。
     *
     * 常用 opcode：
     * - 10008: 后台弹出界面 (background popup)
     * - 10021: 锁屏提醒 (lock screen notification)
     */
    private fun isMiuiOpAllowed(opCode: Int): Boolean {
        if (!isMiui()) return true  // 非 MIUI 设备默认放行
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        return try {
            // 尝试 checkMiuiOp(int op, int uid, String pkg) —— MIUI 内部方法
            val method = appOps.javaClass.getDeclaredMethod(
                "checkMiuiOp",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                String::class.java
            )
            method.isAccessible = true
            val result = method.invoke(appOps, opCode, Process.myUid(), context.packageName) as? Int ?: -1
            Log.d(TAG, "MIUIOP($opCode) checkMiuiOp result=$result")
            result == AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            Log.d(TAG, "checkMiuiOp($opCode) reflection failed: ${e.javaClass.simpleName}: ${e.message}")
            // 反射失败时尝试 checkOpNoThrow 方式 (MIUI 可能将 MIUIOP 注册为标准 op 字符串)
            try {
                val opStr = "miui_op_$opCode"
                val mode = appOps.checkOpNoThrow(opStr, Process.myUid(), context.packageName)
                Log.d(TAG, "MIUIOP($opCode) checkOpNoThrow($opStr) mode=$mode")
                mode == AppOpsManager.MODE_ALLOWED
            } catch (e2: Exception) {
                Log.d(TAG, "MIUIOP($opCode) fallback also failed: ${e2.message}")
                true  // 无法检测时默认放行，避免误报
            }
        }
    }

    /** MIUI 锁屏提醒权限 (MIUIOP 10021)，控制通知是否能在锁屏/灭屏时弹出 */
    fun hasMiuiLockScreenNotification(): Boolean = isMiuiOpAllowed(10021)

    /** MIUI 后台弹出界面权限 (MIUIOP 10008)，控制 app 在后台时能否弹出界面/悬浮窗 */
    fun hasMiuiBackgroundPopup(): Boolean = isMiuiOpAllowed(10008)

    /** 检查是否在电池优化白名单中（省电策略=无限制） */
    fun isIgnoringBatteryOptimizations(): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    // ─── 设置页跳转 ───

    /** 打开 UsageStats 权限设置页 */
    fun openUsageAccessSettings() {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /** 打开悬浮窗权限设置页 */
    fun openOverlaySettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        )
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /** 打开通知权限设置页 */
    fun openNotificationSettings() {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /** 打开无障碍设置页 */
    fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /** 打开精准闹钟权限设置页 */
    fun openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    /** 打开 MIUI 应用管理页 (包含锁屏提醒、后台弹出界面等 MIUI 特有开关) */
    fun openMiuiAppSettings() {
        val intent = Intent("miui.intent.action.APP_SETTINGS").apply {
            putExtra("package_name", context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            // fallback 到标准应用详情页
            val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallback)
        }
    }

    /** 打开电池优化设置页 (省电策略) */
    fun openBatteryOptimizationSettings() {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            // fallback 到电池优化列表页
            val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallback)
        }
    }

    companion object {
        private const val TAG = "PermissionHelper"
    }
}
