package com.timelock.app.ui.lock

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.timelock.app.service.LockOverlayService
import kotlinx.coroutines.delay

/**
 * 锁机覆盖层 UI。
 *
 * 支持两种模式：
 * - App 限额锁屏：显示 App 名称 + 限额信息 + 返回桌面 + 紧急解锁
 * - 全局一键锁屏：显示倒计时 + 仅 1 次解锁机会
 */
@Composable
fun LockOverlayScreen(
    appName: String = "",
    limitMinutes: Int = 0,
    usedMinutes: Long = 0,
    remainingUnlocks: Int = 2,
    isGlobalLock: Boolean = false,
    lockStartTimeMs: Long = System.currentTimeMillis(),
    onReturnToDesktop: () -> Unit,
    onEmergencyUnlock: () -> Unit,
    onLockExpired: () -> Unit = {},
    viewModel: LockOverlayViewModel = hiltViewModel()
) {
    viewModel.setAppInfo(appName, limitMinutes, usedMinutes)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // ── 全局锁屏倒计时 ──
    var countdown by remember { mutableStateOf(limitMinutes * 60) }
    LaunchedEffect(isGlobalLock, lockStartTimeMs) {
        if (isGlobalLock && limitMinutes > 0) {
            while (countdown > 0) {
                delay(1000L)
                val elapsed = (System.currentTimeMillis() - lockStartTimeMs) / 1000L
                countdown = (limitMinutes * 60L - elapsed).coerceAtLeast(0L).toInt()
            }
            // 倒计时归零 → 自动解除锁屏（不计入解锁次数，不触发宽限期）
            onLockExpired()
        }
    }

    LaunchedEffect(uiState.isUnlocked) {
        if (uiState.isUnlocked) onEmergencyUnlock()
    }

    val animatedProgress by animateFloatAsState(uiState.pressProgress, tween(100), label = "unlock-progress")

    Box(
        modifier = Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFF1A1A2E), Color(0xFF16213E), Color(0xFF0F3460)))
        )
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // ── 锁图标 ──
            if (uiState.isUnlocked) {
                Text("✓", fontSize = 72.sp, color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
            } else {
                Icon(Icons.Default.Lock, contentDescription = "已锁定", modifier = Modifier.size(72.dp), tint = Color(0xFFE94560))
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── 标题 ──
            if (isGlobalLock) {
                Text(
                    text = if (uiState.isUnlocked) "已解锁" else "一键锁屏",
                    fontSize = 24.sp, fontWeight = FontWeight.Bold,
                    color = Color.White, textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
                // 倒计时
                if (!uiState.isUnlocked) {
                    val mins = countdown / 60
                    val secs = countdown % 60
                    Text(
                        text = "剩余 ${"%02d:%02d".format(mins, secs)}",
                        fontSize = 36.sp, fontWeight = FontWeight.Bold,
                        color = if (countdown < 60) Color(0xFFE94560) else Color(0xFF64B5F6),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "手机已锁定，无法进行任何操作",
                        fontSize = 13.sp, color = Color.White.copy(alpha = 0.5f)
                    )
                }
            } else {
                Text(
                    text = if (uiState.isUnlocked) "已解锁"
                    else if (appName.isNotEmpty()) "$appName 已锁定"
                    else "应用已锁定",
                    fontSize = 24.sp, fontWeight = FontWeight.Bold,
                    color = Color.White, textAlign = TextAlign.Center
                )
                if (limitMinutes > 0 && !uiState.isUnlocked) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "今日限额 $limitMinutes 分钟 / 已用 $usedMinutes 分钟",
                        fontSize = 14.sp, color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            // ── 返回桌面 (仅 App 限额锁屏) ──
            if (!isGlobalLock && !uiState.isUnlocked) {
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

            // ── 紧急解锁 ──
            if (remainingUnlocks > 0 || uiState.isUnlocked) {
                val label = when {
                    uiState.isUnlocked -> "锁已解除"
                    isGlobalLock -> "紧急解锁（仅 1 次机会 → 5 分钟宽限期）"
                    else -> "紧急解锁 (今日剩余 $remainingUnlocks 次)"
                }
                Text(label, fontSize = 13.sp, color = Color.White.copy(alpha = 0.5f), textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(12.dp))

                Box(
                    modifier = Modifier.size(80.dp).pointerInput(Unit) {
                        detectTapGestures(onPress = {
                            viewModel.startUnlockPress()
                            tryAwaitRelease()
                            viewModel.resetUnlockPress()
                        })
                    },
                    contentAlignment = Alignment.Center
                ) {
                    Box(Modifier.size(80.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.1f)))
                    Box(Modifier.size(80.dp).clip(CircleShape).background(
                        Brush.sweepGradient(listOf(Color(0xFF4CAF50).copy(alpha = animatedProgress), Color.Transparent))
                    ))
                    Text(
                        text = if (uiState.isUnlocked) "✓"
                        else if (uiState.pressProgress > 0f) "${(animatedProgress * 100).toInt()}%"
                        else "长按\n3秒",
                        fontSize = if (uiState.pressProgress > 0f) 16.sp else 11.sp,
                        fontWeight = FontWeight.Bold, color = Color.White,
                        textAlign = TextAlign.Center, lineHeight = 14.sp
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (uiState.isUnlocked) "已解锁 — 覆盖层将关闭"
                    else if (uiState.pressProgress > 0f) "请保持按压..."
                    else "长按 3 秒紧急解锁",
                    fontSize = 12.sp, color = Color.White.copy(alpha = 0.5f)
                )
            } else {
                Text("今日解锁次数已用完", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF6B6B))
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (isGlobalLock) "已超过解锁次数限制\n请专注当下" else "已超过每日 ${LockOverlayService.MAX_UNLOCK_COUNT} 次限制\n请返回桌面休息一下",
                    fontSize = 12.sp, color = Color.White.copy(alpha = 0.5f), textAlign = TextAlign.Center, lineHeight = 18.sp
                )
            }
        }
    }
}
