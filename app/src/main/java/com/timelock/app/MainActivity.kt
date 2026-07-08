package com.timelock.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timelock.app.service.LockOverlayService
import com.timelock.app.service.LockStateManager
import com.timelock.app.ui.navigation.TimeLockNavHost
import com.timelock.app.ui.theme.TimeLockTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_LOCK_PACKAGE = "lock_package"
        const val EXTRA_LOCK_LIMIT_MINUTES = "lock_limit_minutes"
        const val EXTRA_LOCK_USED_MINUTES = "lock_used_minutes"
    }

    @Inject lateinit var lockStateManager: LockStateManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 检查是否以锁机模式启动
        val lockPackage = intent?.getStringExtra(EXTRA_LOCK_PACKAGE)
        val lockLimit = intent?.getIntExtra(EXTRA_LOCK_LIMIT_MINUTES, 0) ?: 0
        val lockUsed = intent?.getLongExtra(EXTRA_LOCK_USED_MINUTES, 0) ?: 0L

        if (!lockPackage.isNullOrEmpty()) {
            // 锁机模式
            val remainingUnlocks = lockStateManager.getRemainingUnlockCount(lockPackage)
            setContent {
                TimeLockTheme {
                    LockScreen(
                        appName = lockPackage,
                        limitMinutes = lockLimit,
                        usedMinutes = lockUsed,
                        remainingUnlocks = remainingUnlocks,
                        onReturnToDesktop = {
                            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                                addCategory(Intent.CATEGORY_HOME)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            startActivity(homeIntent)
                            finish()
                        },
                        onEmergencyUnlock = {
                            lockStateManager.recordEmergencyUnlock(lockPackage, lockUsed)
                            finish()
                        }
                    )
                }
            }
        } else {
            // 正常模式
            setContent {
                TimeLockTheme {
                    TimeLockNavHost()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // 处理新的锁机 intent (当 Activity 已在运行时)
        val lockPackage = intent.getStringExtra(EXTRA_LOCK_PACKAGE)
        if (!lockPackage.isNullOrEmpty()) {
            setIntent(intent)
            val lockLimit = intent.getIntExtra(EXTRA_LOCK_LIMIT_MINUTES, 0)
            val lockUsed = intent.getLongExtra(EXTRA_LOCK_USED_MINUTES, 0)
            val remainingUnlocks = lockStateManager.getRemainingUnlockCount(lockPackage)
            // 直接重新渲染锁屏 (比 recreate 更快更流畅)
            setContent {
                TimeLockTheme {
                    LockScreen(
                        appName = lockPackage,
                        limitMinutes = lockLimit,
                        usedMinutes = lockUsed,
                        remainingUnlocks = remainingUnlocks,
                        onReturnToDesktop = {
                            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                                addCategory(Intent.CATEGORY_HOME)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            startActivity(homeIntent)
                            finish()
                        },
                        onEmergencyUnlock = {
                            lockStateManager.recordEmergencyUnlock(lockPackage, lockUsed)
                            finish()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun LockScreen(
    appName: String = "",
    limitMinutes: Int = 0,
    usedMinutes: Long = 0,
    remainingUnlocks: Int = 2,
    isGlobalLock: Boolean = false,
    lockStartTimeMs: Long = System.currentTimeMillis(),
    onReturnToDesktop: () -> Unit,
    onEmergencyUnlock: () -> Unit,
    onLockExpired: () -> Unit = {}
) {
    var pressProgress by remember { mutableFloatStateOf(0f) }
    var isUnlocked by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val canUnlock = remainingUnlocks > 0

    // ── 全局锁屏倒计时 ──
    var countdown by remember { mutableStateOf(limitMinutes * 60) }
    LaunchedEffect(isGlobalLock, lockStartTimeMs) {
        if (isGlobalLock && limitMinutes > 0) {
            while (countdown > 0) {
                delay(1000L)
                val elapsed = (System.currentTimeMillis() - lockStartTimeMs) / 1000L
                countdown = (limitMinutes * 60L - elapsed).coerceAtLeast(0L).toInt()
            }
            onLockExpired()
        }
    }

    // 解锁后自动退出
    LaunchedEffect(isUnlocked) {
        if (isUnlocked) {
            delay(500)
            onEmergencyUnlock()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1A1A2E),
                        Color(0xFF16213E),
                        Color(0xFF0F3460)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (isUnlocked) {
                Text(
                    text = "✓",
                    fontSize = 72.sp,
                    color = Color(0xFF4CAF50),
                    fontWeight = FontWeight.Bold
                )
            } else {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = "已锁定",
                    modifier = Modifier.size(72.dp),
                    tint = Color(0xFFE94560)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── 标题 + 倒计时 / 限额信息 ──
            if (isGlobalLock) {
                Text(
                    text = if (isUnlocked) "已解锁" else "一键锁屏",
                    fontSize = 24.sp, fontWeight = FontWeight.Bold,
                    color = Color.White, textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
                if (!isUnlocked) {
                    val mins = countdown / 60
                    val secs = countdown % 60
                    Text(
                        text = "剩余 ${"%02d:%02d".format(mins, secs)}",
                        fontSize = 36.sp, fontWeight = FontWeight.Bold,
                        color = if (countdown < 60) Color(0xFFE94560) else Color(0xFF64B5F6),
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                Text(
                    text = if (isUnlocked) "已解锁"
                    else if (appName.isNotEmpty()) "$appName 已超限额"
                    else "应用已锁定",
                    fontSize = 24.sp, fontWeight = FontWeight.Bold,
                    color = Color.White, textAlign = TextAlign.Center
                )
                if (limitMinutes > 0 && !isUnlocked) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "今日限额 $limitMinutes 分钟 / 已用 $usedMinutes 分钟",
                        fontSize = 14.sp, color = Color.White.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            // 返回桌面按钮 (仅 App 限额锁屏)
            if (!isGlobalLock && !isUnlocked) {
                Button(
                    onClick = onReturnToDesktop,
                    modifier = Modifier.fillMaxWidth(0.7f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F3460))
                ) {
                    Icon(Icons.Default.Home, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("返回桌面", fontSize = 16.sp)
                }

                Spacer(modifier = Modifier.height(64.dp))
            }

            // ── 紧急解锁区域 (带次数限制) ──
            if (canUnlock || isUnlocked) {
                Text(
                    text = if (isUnlocked) "锁已解除"
                    else if (isGlobalLock) "紧急解锁（仅 1 次机会 → 5 分钟宽限期）"
                    else "紧急解锁 (今日剩余 $remainingUnlocks 次)",
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.5f)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    val job = scope.launch {
                                        val start = System.currentTimeMillis()
                                        while (isActive) {
                                            val elapsed = System.currentTimeMillis() - start
                                            val progress = (elapsed / 3000f).coerceIn(0f, 1f)
                                            pressProgress = progress
                                            if (progress >= 1f) {
                                                isUnlocked = true
                                                return@launch
                                            }
                                            delay(100L)
                                        }
                                    }
                                    tryAwaitRelease()
                                    job.cancel()
                                    pressProgress = 0f
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.1f))
                    )
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.sweepGradient(
                                    colors = listOf(
                                        Color(0xFF4CAF50).copy(alpha = pressProgress),
                                        Color.Transparent
                                    )
                                )
                            )
                    )
                    Text(
                        text = if (isUnlocked) "✓"
                        else if (pressProgress > 0f) "${(pressProgress * 100).toInt()}%"
                        else "长按\n3秒",
                        fontSize = if (pressProgress > 0f) 16.sp else 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        lineHeight = 14.sp
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = if (isUnlocked) "已解锁 — 即将关闭"
                    else if (pressProgress > 0f) "请保持按压..."
                    else "长按 3 秒紧急解锁",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.5f)
                )
            } else {
                // ── 解锁次数已用完 ──
                Text(
                    text = "今日解锁次数已用完",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFF6B6B)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (isGlobalLock) "已超过解锁次数限制\n请专注当下"
                    else "已超过每日 ${LockOverlayService.MAX_UNLOCK_COUNT} 次紧急解锁限制\n请返回桌面休息一下",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )
            }
        }
    }
}
