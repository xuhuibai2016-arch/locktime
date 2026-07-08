package com.timelock.app.domain.usecase

import com.timelock.app.domain.model.AppLimitConfig
import com.timelock.app.domain.repository.AppLimitRepository
import com.timelock.app.domain.repository.UsageStatsRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * CheckAppLimitUseCase 单元测试。
 *
 * 使用 MockK 模拟两个 Repository，验证业务逻辑分支：
 * - 白名单 → Whitelisted
 * - 未配置 → NotConfigured
 * - 锁禁用 → Allowed
 * - 超时   → Locked
 * - 未超时 → Allowed
 */
class CheckAppLimitUseCaseTest {

    private val usageStatsRepo: UsageStatsRepository = mockk()
    private val appLimitRepo: AppLimitRepository = mockk()
    private lateinit var useCase: CheckAppLimitUseCase

    @Before
    fun setUp() {
        useCase = CheckAppLimitUseCase(usageStatsRepo, appLimitRepo)
    }

    // ── 白名单 ────────────────────────────────────────────────

    @Test
    fun `when app is whitelisted, returns Whitelisted`() = runTest {
        val pkg = "com.android.phone"
        coEvery { appLimitRepo.getConfig(pkg) } returns AppLimitConfig(
            packageName = pkg, appName = "Phone",
            isWhitelisted = true, isLockEnabled = false
        )

        val result = useCase(pkg)
        assertTrue(result is CheckResult.Whitelisted)
    }

    // ── 未配置 ────────────────────────────────────────────────

    @Test
    fun `when app is not configured, returns NotConfigured`() = runTest {
        val pkg = "com.unknown.app"
        coEvery { appLimitRepo.getConfig(pkg) } returns null

        val result = useCase(pkg)
        assertTrue(result is CheckResult.NotConfigured)
    }

    // ── 锁禁用 ────────────────────────────────────────────────

    @Test
    fun `when lock is disabled, returns Allowed regardless of usage`() = runTest {
        val pkg = "com.unlocked.app"
        coEvery { appLimitRepo.getConfig(pkg) } returns AppLimitConfig(
            packageName = pkg, appName = "Unlocked",
            dailyLimitMinutes = 30, isLockEnabled = false
        )
        coEvery { usageStatsRepo.getTodayUsage(pkg) } returns 3_600_000L // 60 min > 30 limit

        val result = useCase(pkg)
        assertTrue(result is CheckResult.Allowed)
    }

    // ── 超时触发锁机 ──────────────────────────────────────────

    @Test
    fun `when usage exceeds daily limit, returns Locked`() = runTest {
        val pkg = "com.locked.app"
        coEvery { appLimitRepo.getConfig(pkg) } returns AppLimitConfig(
            packageName = pkg, appName = "Locked",
            dailyLimitMinutes = 30, isLockEnabled = true
        )
        // 已用 31 分钟 = 1_860_000 ms > 30 min limit
        coEvery { usageStatsRepo.getTodayUsage(pkg) } returns 1_860_000L

        val result = useCase(pkg)
        assertTrue(result is CheckResult.Locked)
        val locked = result as CheckResult.Locked
        assertEquals(30, locked.limitMinutes)
        assertEquals(31L, locked.usedMinutes)
    }

    // ── 未超时 ────────────────────────────────────────────────

    @Test
    fun `when usage is under daily limit, returns Allowed with remaining`() = runTest {
        val pkg = "com.safe.app"
        coEvery { appLimitRepo.getConfig(pkg) } returns AppLimitConfig(
            packageName = pkg, appName = "Safe",
            dailyLimitMinutes = 60, isLockEnabled = true
        )
        // 已用 15 分钟 = 900_000 ms, 限额 60 min = 3_600_000 ms → 剩余 45 min
        coEvery { usageStatsRepo.getTodayUsage(pkg) } returns 900_000L

        val result = useCase(pkg)
        assertTrue(result is CheckResult.Allowed)
        val allowed = result as CheckResult.Allowed
        assertEquals(2_700_000L, allowed.remainingMillis) // 45 min
    }

    // ── 边界: 恰好等于限额 ────────────────────────────────────

    @Test
    fun `when usage equals limit exactly, returns Locked`() = runTest {
        val pkg = "com.exact.app"
        coEvery { appLimitRepo.getConfig(pkg) } returns AppLimitConfig(
            packageName = pkg, appName = "Exact",
            dailyLimitMinutes = 10, isLockEnabled = true
        )
        coEvery { usageStatsRepo.getTodayUsage(pkg) } returns 600_000L // exactly 10 min

        val result = useCase(pkg)
        assertTrue(result is CheckResult.Locked)
    }
}
