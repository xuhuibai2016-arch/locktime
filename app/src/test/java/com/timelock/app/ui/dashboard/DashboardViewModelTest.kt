package com.timelock.app.ui.dashboard

import com.timelock.app.data.local.AppUsageStat
import com.timelock.app.data.local.dao.ScheduledLockDao
import com.timelock.app.domain.repository.AppLimitRepository
import com.timelock.app.domain.repository.UsageStatsRepository
import com.timelock.app.service.LockStateManager
import com.timelock.app.util.SystemAppHelper
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * DashboardViewModel 单元测试。
 *
 * 验证：
 * - 图表数据 (chartItems)：当天 Top 5（排除系统应用）
 * - 列表数据 (topApps)：近 7 天累计 Top 5 + 限额信息
 * - 排除 TimeLock 自身 + 系统桌面/SystemUI
 * - 空数据 / 异常处理
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    private val usageStatsRepo: UsageStatsRepository = mockk()
    private val appLimitRepo: AppLimitRepository = mockk()
    private val scheduledLockDao: ScheduledLockDao = mockk()
    private val lockStateManager: LockStateManager = mockk()
    private val systemAppHelper: SystemAppHelper = mockk(relaxed = true)
    private val appContext: android.content.Context = mockk()
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var viewModel: DashboardViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { usageStatsRepo.syncUsageStats() } returns Unit
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() {
        viewModel = DashboardViewModel(usageStatsRepo, appLimitRepo, scheduledLockDao, lockStateManager, systemAppHelper, appContext)
    }

    // ── 加载成功 ─────────────────────────────────────────────

    @Test
    fun `chart shows today top 5 and list shows 7-day top 5`() = runTest {
        // 当天数据 (图表用) — 6 个 App，第 6 个被挤出
        val todayStats = listOf(
            AppUsageStat("com.app.a", "App A", 6_000_000L),  // 100 min
            AppUsageStat("com.app.b", "App B", 5_400_000L),  //  90 min
            AppUsageStat("com.app.c", "App C", 4_800_000L),  //  80 min
            AppUsageStat("com.app.d", "App D", 3_600_000L),  //  60 min
            AppUsageStat("com.app.e", "App E", 3_000_000L),  //  50 min
            AppUsageStat("com.app.f", "App F", 2_400_000L),  //  40 min (6th, excluded from chart top 5)
        )

        // 7 天累计 (列表用) — 不同排序
        val weekStats = listOf(
            AppUsageStat("com.app.b", "App B", 30_000_000L),  // 500 min — #1 in 7-day
            AppUsageStat("com.app.a", "App A", 24_000_000L),  // 400 min — #2
            AppUsageStat("com.app.c", "App C", 18_000_000L),  // 300 min — #3
            AppUsageStat("com.app.g", "App G", 12_000_000L),  // 200 min — #4 (not in today)
            AppUsageStat("com.app.h", "App H",  9_000_000L),  // 150 min — #5 (not in today)
            AppUsageStat("com.app.d", "App D",  6_000_000L),  // 100 min — #6
        )

        coEvery { usageStatsRepo.getDailyStats(any()) } returns todayStats
        coEvery { usageStatsRepo.getStatsByDateRange(any(), any()) } returns weekStats
        coEvery { appLimitRepo.getConfig(any()) } returns null

        createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.error)

        // ── 图表：当天 Top 5 ──
        assertEquals(5, state.chartItems.size)
        assertEquals("com.app.a", state.chartItems[0].packageName)
        assertEquals(100L, state.chartItems[0].usedMinutes)
        assertEquals("com.app.e", state.chartItems[4].packageName)
        // com.app.f (40 min) 应被挤出
        assertTrue(state.chartItems.none { it.packageName == "com.app.f" })

        // ── 列表：7 天累计 Top 5 ──
        assertEquals(5, state.topApps.size)
        // 7 天排序: B(500) > A(400) > C(300) > G(200) > H(150)
        assertEquals("com.app.b", state.topApps[0].packageName)
        assertEquals(500L, state.topApps[0].usedMinutes)
        assertEquals("com.app.h", state.topApps[4].packageName)
    }

    // ── 排除 TimeLock 自身 + 系统应用 ─────────────────────────

    @Test
    fun `excludes self package and system packages`() = runTest {
        val todayStats = listOf(
            AppUsageStat("com.timelock.app", "TimeLock", 1_000_000L),       // 自身
            AppUsageStat("com.android.systemui", "System UI", 2_000_000L),  // 系统 UI
            AppUsageStat("com.miui.home", "Mi Launcher", 3_000_000L),       // 桌面
            AppUsageStat("com.android.settings", "Settings", 4_000_000L),   // 系统设置
            AppUsageStat("com.app.a", "App A", 5_000_000L),
            AppUsageStat("com.app.b", "App B", 4_000_000L),
        )
        val weekStats = listOf(
            AppUsageStat("com.app.a", "App A", 20_000_000L),
            AppUsageStat("com.app.b", "App B", 15_000_000L),
        )

        // 系统/自身包 → 排除；用户包 → 不排除（relaxed mock 默认返回 false）
        every { systemAppHelper.isExcludedPackage("com.timelock.app") } returns true
        every { systemAppHelper.isExcludedPackage("com.android.systemui") } returns true
        every { systemAppHelper.isExcludedPackage("com.miui.home") } returns true
        every { systemAppHelper.isExcludedPackage("com.android.settings") } returns true

        coEvery { usageStatsRepo.getDailyStats(any()) } returns todayStats
        coEvery { usageStatsRepo.getStatsByDateRange(any(), any()) } returns weekStats
        coEvery { appLimitRepo.getConfig(any()) } returns null

        createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value

        // 图表：排除后只剩 2 个用户 App
        assertEquals(2, state.chartItems.size)
        assertTrue(state.chartItems.all { it.packageName in listOf("com.app.a", "com.app.b") })

        // 列表同理
        assertEquals(2, state.topApps.size)
    }

    // ── 空数据 ────────────────────────────────────────────────

    @Test
    fun `empty stats emit empty lists`() = runTest {
        coEvery { usageStatsRepo.getDailyStats(any()) } returns emptyList()
        coEvery { usageStatsRepo.getStatsByDateRange(any(), any()) } returns emptyList()

        createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.error)
        assertTrue(state.topApps.isEmpty())
        assertTrue(state.chartItems.isEmpty())
    }

    // ── 异常处理 ──────────────────────────────────────────────

    @Test
    fun `error in data loading emits error state`() = runTest {
        coEvery { usageStatsRepo.getDailyStats(any()) } throws RuntimeException("Permission denied")

        createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNotNull(state.error)
        assertTrue(state.error!!.contains("Permission"))
    }
}
