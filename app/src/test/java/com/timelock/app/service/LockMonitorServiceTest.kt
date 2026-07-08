package com.timelock.app.service

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.timelock.app.domain.usecase.CheckAppLimitUseCase
import com.timelock.app.domain.usecase.CheckResult
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * LockMonitorService 触发逻辑单元测试 (RED — 先写测试，确认失败后实现)。
 *
 * 测试 LockMonitorEngine 核心逻辑（独立于 Android Service 生命周期）：
 * 1. 前台应用检查 → 白名单跳过
 * 2. 前台应用检查 → 超限触发锁机
 * 3. 前台应用检查 → 未超限不锁
 * 4. 前台应用检查 → 未配置不锁
 * 5. 通知渠道创建
 * 6. 轮询停止/恢复
 */
class LockMonitorServiceTest {

    private lateinit var context: Context
    private lateinit var checkAppLimitUseCase: CheckAppLimitUseCase
    private lateinit var engine: LockMonitorEngine

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        checkAppLimitUseCase = mockk()
        every { context.getSystemService(Context.NOTIFICATION_SERVICE) } returns mockk<NotificationManager>()
        engine = LockMonitorEngine(context, checkAppLimitUseCase)
    }

    // ── 1. 白名单跳过 ────────────────────────────────────────

    @Test
    fun `whitelisted app does not trigger lock`() = runTest {
        coEvery { checkAppLimitUseCase("com.android.phone") } returns CheckResult.Whitelisted

        val result = engine.checkApp("com.android.phone")
        assertEquals(LockMonitorEngine.CheckDecision.SKIP_WHITELISTED, result)
    }

    // ── 2. 超限触发锁机 ──────────────────────────────────────

    @Test
    fun `over-limit app triggers lock`() = runTest {
        coEvery { checkAppLimitUseCase("com.game.app") } returns CheckResult.Locked(
            limitMinutes = 30, usedMinutes = 31
        )

        val result = engine.checkApp("com.game.app")
        assertTrue(result is LockMonitorEngine.CheckDecision.LOCK)
        val lock = result as LockMonitorEngine.CheckDecision.LOCK
        assertEquals(30, lock.limitMinutes)
        assertEquals(31L, lock.usedMinutes)
    }

    // ── 3. 未超限不锁 ────────────────────────────────────────

    @Test
    fun `under-limit app does not trigger lock`() = runTest {
        coEvery { checkAppLimitUseCase("com.safe.app") } returns CheckResult.Allowed(2_700_000L)

        val result = engine.checkApp("com.safe.app")
        assertEquals(LockMonitorEngine.CheckDecision.SKIP_UNDER_LIMIT, result)
    }

    // ── 4. 未配置不锁 ────────────────────────────────────────

    @Test
    fun `unconfigured app does not trigger lock`() = runTest {
        coEvery { checkAppLimitUseCase("com.unknown.app") } returns CheckResult.NotConfigured

        val result = engine.checkApp("com.unknown.app")
        assertEquals(LockMonitorEngine.CheckDecision.SKIP_NOT_CONFIGURED, result)
    }

    // ── 5. 通知渠道创建 ──────────────────────────────────────

    @Test
    fun `createNotificationChannel creates channel on Android O plus`() {
        mockkStatic(Build.VERSION::class)
        every { Build.VERSION.SDK_INT } returns Build.VERSION_CODES.O

        val manager: NotificationManager = mockk(relaxed = true)
        every { context.getSystemService(Context.NOTIFICATION_SERVICE) } returns manager

        engine.createNotificationChannel()

        verify(exactly = 1) {
            manager.createNotificationChannel(any())
        }
    }

    // ── 6. 前台通知构建 ──────────────────────────────────────

    @Test
    fun `buildNotification returns a non-null notification`() {
        mockkStatic(Build.VERSION::class)
        every { Build.VERSION.SDK_INT } returns Build.VERSION_CODES.O

        engine.createNotificationChannel()
        val notification = engine.buildNotification()

        assertNotNull(notification)
    }
}
