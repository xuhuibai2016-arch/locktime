package com.timelock.app.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.timelock.app.ui.components.AppIcon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToConfig: () -> Unit,
    onNavigateToTrend: () -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val quickLimitSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(Unit) {
        viewModel.loadScheduledLock()
    }

    LaunchedEffect(uiState.quickLimitMessage) {
        uiState.quickLimitMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearQuickLimitMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("TimeLock") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                actions = {
                    IconButton(onClick = onNavigateToTrend) {
                        Icon(Icons.Default.TrendingUp, contentDescription = "使用趋势", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                    IconButton(onClick = onNavigateToConfig) {
                        Icon(Icons.Default.Settings, contentDescription = "应用限额配置", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                    IconButton(onClick = { viewModel.loadTopApps() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.showLockDialog() },
                containerColor = Color(0xFFE94560),
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Lock, contentDescription = "一键锁屏", modifier = Modifier.size(24.dp))
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            when {
                uiState.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                uiState.error != null -> {
                    Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(16.dp))
                        Text(uiState.error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                uiState.topApps.isEmpty() -> {
                    Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Schedule, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(64.dp))
                        Spacer(Modifier.height(16.dp))
                        Text("今日暂无使用记录", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.secondary)
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            DailyUsageChart(items = uiState.chartItems, modifier = Modifier.padding(bottom = 4.dp))
                        }
                        item {
                            ScheduledLockCard(
                                enabled = uiState.scheduledLockEnabled,
                                timeText = viewModel.formatScheduledTime(),
                                durationMinutes = uiState.scheduledLockDuration,
                                onClick = { viewModel.showScheduledLockSheet() },
                                onToggle = { viewModel.toggleScheduledLock(it) }
                            )
                        }
                        item {
                            Text("7天 Top 5", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))
                        }
                        items(uiState.topApps) { app ->
                            TopAppCard(
                                item = app,
                                onSetLimit = {
                                    viewModel.showQuickLimit(app.packageName, app.appName, app.limitMinutes)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // ── 一键锁屏 Dialog ──
    if (uiState.showLockDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissLockDialog() },
            title = { Text("一键锁屏", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("设置锁屏分钟数，确认后手机将立即锁定", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = uiState.lockMinutes,
                        onValueChange = { viewModel.setLockMinutes(it) },
                        label = { Text("分钟数") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = { viewModel.oneTapLock(context) }) { Text("确认锁屏") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissLockDialog() }) { Text("取消") }
            }
        )
    }

    // ── 快捷限额 BottomSheet ──
    if (uiState.quickLimitTarget != null) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.dismissQuickLimit() },
            sheetState = quickLimitSheetState,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
        ) {
            QuickLimitSheet(
                appName = uiState.quickLimitTarget!!.appName,
                minutes = uiState.quickLimitMinutes,
                isSaving = uiState.isQuickLimitSaving,
                onMinutesChange = { viewModel.setQuickLimitMinutes(it) },
                onSave = { viewModel.saveQuickLimit() },
                onDismiss = { viewModel.dismissQuickLimit() }
            )
        }
    }

    // ── 定时锁屏 BottomSheet ──
    if (uiState.showScheduledLockSheet) {
        val scheduledSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { viewModel.dismissScheduledLockSheet() },
            sheetState = scheduledSheetState,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
        ) {
            ScheduledLockSheet(
                enabled = uiState.scheduledLockEnabled,
                hourText = uiState.scheduledLockHourText,
                minuteText = uiState.scheduledLockMinuteText,
                durationText = uiState.scheduledLockDurationText,
                durationMinutes = uiState.scheduledLockDuration,
                blocked = uiState.scheduledLockBlocked,
                blockReason = uiState.scheduledLockBlockReason,
                onToggle = { viewModel.toggleScheduledLock(it) },
                onHourChange = { viewModel.setScheduledLockHourText(it) },
                onMinuteChange = { viewModel.setScheduledLockMinuteText(it) },
                onDurationChange = { viewModel.setScheduledLockDurationText(it) },
                onSave = { viewModel.saveScheduledLockConfig() },
                onDismiss = { viewModel.dismissScheduledLockSheet() }
            )
        }
    }
}

@Composable
private fun TopAppCard(item: TopAppItem, onSetLimit: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIcon(packageName = item.packageName, size = 48.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.appName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Schedule, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "${item.usedMinutes} 分钟",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
            Text(
                "设置限额",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { onSetLimit() }
            )
        }
    }
}

/**
 * 快捷限额输入 BottomSheet。
 */
@Composable
private fun QuickLimitSheet(
    appName: String,
    minutes: String,
    isSaving: Boolean,
    onMinutesChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(appName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text("设置每日限额（分钟）", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            value = minutes,
            onValueChange = onMinutesChange,
            label = { Text("分钟数") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))
        Text("到达限额后将锁定应用", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onSave, modifier = Modifier.fillMaxWidth(), enabled = !isSaving) {
            if (isSaving) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                Spacer(Modifier.width(8.dp))
            }
            Text("保存")
        }
        Spacer(Modifier.height(12.dp))
    }
}

// ── 定时锁屏卡片 ──────────────────────────────────────────────

@Composable
private fun ScheduledLockCard(
    enabled: Boolean,
    timeText: String,
    durationMinutes: Int,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) Color(0xFFE8DEF8) else Color(0xFFF5F5F5)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (enabled) Icons.Default.Lock else Icons.Default.Schedule,
                contentDescription = null,
                tint = if (enabled) Color(0xFF5E35B1) else Color(0xFF9E9E9E),
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "定时锁屏",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (enabled) Color(0xFF4A148C) else Color(0xFF757575)
                )
                Text(
                    text = if (enabled) "每晚 $timeText 锁屏 ${formatDuration(durationMinutes)}"
                    else "设置每日自动锁屏时段",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (enabled) Color(0xFF7E57C2) else Color(0xFF9E9E9E)
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Color(0xFF7C4DFF)
                )
            )
        }
    }
}

// ── 定时锁屏配置 BottomSheet ──────────────────────────────────

@Composable
private fun ScheduledLockSheet(
    enabled: Boolean,
    hourText: String,
    minuteText: String,
    durationText: String,
    durationMinutes: Int,
    blocked: Boolean = false,
    blockReason: String? = null,
    onToggle: (Boolean) -> Unit,
    onHourChange: (String) -> Unit,
    onMinuteChange: (String) -> Unit,
    onDurationChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("定时锁屏", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text("每晚自动锁屏，帮助规律作息", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)

        // ── 临近触发时间不能修改的提示 ──
        if (blocked && blockReason != null) {
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color(0xFFFF6B6B),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = blockReason,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFFF6B6B)
                )
            }
        }

        Spacer(Modifier.height(24.dp))
        HorizontalDivider()

        // ── 启用开关 ──
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("启用定时锁屏", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Switch(
                checked = enabled,
                onCheckedChange = onToggle,
                enabled = !blocked,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Color(0xFF7C4DFF)
                )
            )
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider()

        if (enabled) {
            Spacer(Modifier.height(20.dp))

            // ── 锁屏开始时间 ──
            Text("锁屏开始时间", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 小时选择
                OutlinedTextField(
                    value = hourText,
                    onValueChange = onHourChange,
                    modifier = Modifier.width(80.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    enabled = !blocked,
                    textStyle = MaterialTheme.typography.titleLarge.copy(textAlign = TextAlign.Center)
                )
                Text(" : ", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                // 分钟选择
                OutlinedTextField(
                    value = minuteText,
                    onValueChange = onMinuteChange,
                    modifier = Modifier.width(80.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    enabled = !blocked,
                    textStyle = MaterialTheme.typography.titleLarge.copy(textAlign = TextAlign.Center)
                )
            }

            Spacer(Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(Modifier.height(20.dp))

            // ── 锁屏时长 ──
            Text("锁屏时长（分钟）", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = durationText,
                onValueChange = onDurationChange,
                label = { Text("分钟数") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                enabled = !blocked,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Text(formatDuration(durationMinutes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
        }

        Spacer(Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))

        Button(onClick = onSave, modifier = Modifier.fillMaxWidth(), enabled = !blocked) {
            Text("保存")
        }
        Spacer(Modifier.height(12.dp))
    }
}

private fun formatDuration(minutes: Int): String {
    return if (minutes < 60) "${minutes}分钟"
    else {
        val h = minutes / 60
        val m = minutes % 60
        if (m == 0) "${h}小时" else "${h}小时${m}分钟"
    }
}
