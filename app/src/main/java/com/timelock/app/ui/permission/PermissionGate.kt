package com.timelock.app.ui.permission

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.timelock.app.service.LockMonitorService
import com.timelock.app.util.PermissionHelper

/**
 * 权限引导门 —— 在 ON_RESUME 时重新检查权限。
 * 全部权限就绪后才显示 [content]。
 * MIUI 设备会额外显示小米特有权限检查项。
 */
@Composable
fun PermissionGate(
    content: @Composable () -> Unit,
    permissionHelper: PermissionHelper = hiltViewModel<PermissionGateViewModel>().permissionHelper
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val isMiui = remember { permissionHelper.isMiui() }

    // 响应式权限状态
    var notificationOk by remember { mutableStateOf(checkNotification(permissionHelper)) }
    var usageOk by remember { mutableStateOf(permissionHelper.hasUsageStatsPermission()) }
    var overlayOk by remember { mutableStateOf(permissionHelper.canDrawOverlays()) }
    var accessibilityOk by remember { mutableStateOf(com.timelock.app.service.LockAccessibilityService.isEnabled()) }
    var exactAlarmOk by remember { mutableStateOf(permissionHelper.hasExactAlarmPermission()) }
    var batteryOk by remember { mutableStateOf(permissionHelper.isIgnoringBatteryOptimizations()) }

    // MIUI 特有权限状态 (无法自动检测，始终显示为手动确认项)
    val miuiManualCheckNeeded = isMiui  // MIUI 设备始终显示手动确认提醒

    // ON_RESUME 时重新检查（用户从设置页返回时触发）
    remember(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationOk = checkNotification(permissionHelper)
                usageOk = permissionHelper.hasUsageStatsPermission()
                overlayOk = permissionHelper.canDrawOverlays()
                accessibilityOk = com.timelock.app.service.LockAccessibilityService.isEnabled()
                exactAlarmOk = permissionHelper.hasExactAlarmPermission()
                batteryOk = permissionHelper.isIgnoringBatteryOptimizations()
            }
        }
        lifecycle.addObserver(observer)
        lifecycle
    }

    // 通知权限请求 launcher
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationOk = granted
    }

    val miuiGranted = true  // MIUI 特有权限无法自动检测，不阻断进入
    val allGranted = notificationOk && usageOk && overlayOk && accessibilityOk
            && exactAlarmOk && batteryOk
    val context = LocalContext.current

    // 权限全部就绪后启动监控服务
    LaunchedEffect(allGranted) {
        if (allGranted) {
            val intent = Intent(context, LockMonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    if (allGranted) {
        content()
    } else {
        PermissionGuideScreen(
            notificationOk = notificationOk,
            usageOk = usageOk,
            overlayOk = overlayOk,
            accessibilityOk = accessibilityOk,
            exactAlarmOk = exactAlarmOk,
            batteryOk = batteryOk,
            isMiui = isMiui,
            miuiManualCheckNeeded = miuiManualCheckNeeded,
            onRequestNotification = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            },
            onRequestUsage = { permissionHelper.openUsageAccessSettings() },
            onRequestOverlay = { permissionHelper.openOverlaySettings() },
            onRequestAccessibility = { permissionHelper.openAccessibilitySettings() },
            onRequestExactAlarm = { permissionHelper.openExactAlarmSettings() },
            onRequestBattery = { permissionHelper.openBatteryOptimizationSettings() },
            onRequestMiuiSettings = { permissionHelper.openMiuiAppSettings() }
        )
    }
}

private fun checkNotification(helper: PermissionHelper): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        helper.hasNotificationPermission()
    } else true
}

@Composable
private fun PermissionGuideScreen(
    notificationOk: Boolean,
    usageOk: Boolean,
    overlayOk: Boolean,
    accessibilityOk: Boolean,
    exactAlarmOk: Boolean,
    batteryOk: Boolean,
    isMiui: Boolean,
    miuiManualCheckNeeded: Boolean,
    onRequestNotification: () -> Unit,
    onRequestUsage: () -> Unit,
    onRequestOverlay: () -> Unit,
    onRequestAccessibility: () -> Unit,
    onRequestExactAlarm: () -> Unit,
    onRequestBattery: () -> Unit,
    onRequestMiuiSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Security,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "TimeLock 需要以下权限",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "用于监控应用使用时长和触发锁机保护",
            color = MaterialTheme.colorScheme.secondary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))

        // ── 标准权限 ──
        PermissionCard("通知权限", "在后台持续监控需要显示通知", notificationOk, onRequestNotification)
        Spacer(modifier = Modifier.height(12.dp))
        PermissionCard("使用情况访问权限", "获取当前前台运行的应用信息", usageOk, onRequestUsage)
        Spacer(modifier = Modifier.height(12.dp))
        PermissionCard("悬浮窗权限", "在应用超时时显示全屏锁机界面", overlayOk, onRequestOverlay)
        Spacer(modifier = Modifier.height(12.dp))
        PermissionCard("无障碍服务", "实时监控前台应用切换(小米设备必需)", accessibilityOk, onRequestAccessibility)
        Spacer(modifier = Modifier.height(12.dp))
        PermissionCard("精准闹钟", "确保每5秒检测一次前台应用变化", exactAlarmOk, onRequestExactAlarm)
        Spacer(modifier = Modifier.height(12.dp))
        PermissionCard("电池优化(无限制)", "防止系统在后台冻结进程，影响监控", batteryOk, onRequestBattery)

        // ── MIUI 特有权限 (无法自动检测，作为手动确认项) ──
        if (isMiui) {
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                "小米特有设置 (请手动确认)",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "以下设置无法自动检测，请进入应用管理页手动确认开启",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            MiuiManualCheckCard(
                "锁屏提醒",
                "确保通知在锁屏/灭屏时也能弹出",
                onRequestMiuiSettings
            )
            Spacer(modifier = Modifier.height(12.dp))
            MiuiManualCheckCard(
                "后台弹出界面",
                "确保应用在后台时能弹出悬浮窗",
                onRequestMiuiSettings
            )
            Spacer(modifier = Modifier.height(12.dp))
            MiuiManualCheckCard(
                "省电策略 = 无限制",
                "防止系统在后台冻结进程",
                onRequestMiuiSettings
            )
        }
    }
}

/**
 * MIUI 手动确认卡片 —— 显示提醒并提供跳转设置页按钮。
 * 由于 MIUI 特有权限无法通过标准 API 检测，始终显示提醒。
 */
@Composable
private fun MiuiManualCheckCard(
    title: String,
    desc: String,
    onOpenSettings: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(title, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = onOpenSettings) { Text("打开应用管理页") }
        }
    }
}

@Composable
private fun PermissionCard(
    title: String,
    desc: String,
    granted: Boolean,
    onGrant: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (granted) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (granted) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(title, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
            Spacer(modifier = Modifier.height(8.dp))
            if (granted) {
                Text("已授权 ✓", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            } else {
                Button(onClick = onGrant) { Text("授予权限") }
            }
        }
    }
}
