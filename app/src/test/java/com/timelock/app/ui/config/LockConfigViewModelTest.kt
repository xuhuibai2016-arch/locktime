package com.timelock.app.ui.config

import com.timelock.app.domain.model.AppLimitConfig
import com.timelock.app.domain.repository.AppLimitRepository
import com.timelock.app.domain.repository.UsageStatsRepository
import com.timelock.app.domain.usecase.AddSystemAppsToWhitelistUseCase
import com.timelock.app.util.AppInfoProvider
import com.timelock.app.util.InstalledApp
import com.timelock.app.util.SystemAppHelper
import io.mockk.coEvery
import io.mockk.coVerify
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
 * LockConfigViewModel 单元测试。
 *
 * 验证:
 * - 加载已安装应用 + 合并限额配置
 * - 选择应用 → 弹出 BottomSheet
 * - Stepper 调整限额
 * - 保存限额 → 更新 Repository
 * - 一键添加系统应用到白名单
 * - 关闭 BottomSheet
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LockConfigViewModelTest {

    private val appLimitRepo: AppLimitRepository = mockk()
    private val usageStatsRepo: com.timelock.app.domain.repository.UsageStatsRepository = mockk()
    private val appInfoProvider: AppInfoProvider = mockk()
    private val addSystemAppsUseCase: AddSystemAppsToWhitelistUseCase = mockk()
    private val systemAppHelper: SystemAppHelper = mockk(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var viewModel: LockConfigViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() {
        coEvery { usageStatsRepo.getTodayUsage(any()) } returns 0L
        viewModel = LockConfigViewModel(appLimitRepo, usageStatsRepo, appInfoProvider, addSystemAppsUseCase, systemAppHelper)
    }

    // ── 加载应用列表 ──────────────────────────────────────────

    @Test
    fun `loadApps merges installed apps with configs`() = runTest {
        val installed = listOf(
            InstalledApp("com.app.a", "App A"),
            InstalledApp("com.app.b", "App B"),
            InstalledApp("com.app.c", "App C"),
        )
        val configs = listOf(
            AppLimitConfig(
                packageName = "com.app.a", appName = "App A",
                dailyLimitMinutes = 60, isLockEnabled = true
            )
        )
        coEvery { appInfoProvider.getInstalledApps() } returns installed
        coEvery { appLimitRepo.getAllConfigs() } returns configs

        createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(3, state.apps.size)

        val appA = state.apps.find { it.packageName == "com.app.a" }!!
        assertTrue(appA.isConfigured)
        assertEquals(60, appA.dailyLimitMinutes)
        assertTrue(appA.isLockEnabled)

        val appB = state.apps.find { it.packageName == "com.app.b" }!!
        assertFalse(appB.isConfigured)
        assertEquals(0, appB.dailyLimitMinutes)
    }

    // ── 选择应用 → 弹出 BottomSheet ────────────────────────────

    @Test
    fun `selectApp opens bottom sheet with correct limit`() = runTest {
        val installed = listOf(InstalledApp("com.app.a", "App A"))
        val configs = listOf(
            AppLimitConfig(
                packageName = "com.app.a", appName = "App A",
                dailyLimitMinutes = 45, isLockEnabled = true
            )
        )
        coEvery { appInfoProvider.getInstalledApps() } returns installed
        coEvery { appLimitRepo.getAllConfigs() } returns configs

        createViewModel()
        advanceUntilIdle()

        viewModel.selectApp("com.app.a")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.showBottomSheet)
        assertNotNull(state.selectedApp)
        assertEquals("com.app.a", state.selectedApp!!.packageName)
        assertEquals(45, state.currentLimitMinutes)
    }

    @Test
    fun `selectApp without config uses default limit`() = runTest {
        val installed = listOf(InstalledApp("com.new.app", "New App"))
        coEvery { appInfoProvider.getInstalledApps() } returns installed
        coEvery { appLimitRepo.getAllConfigs() } returns emptyList()

        createViewModel()
        advanceUntilIdle()

        viewModel.selectApp("com.new.app")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.showBottomSheet)
        assertEquals(30, state.currentLimitMinutes)
    }

    // ── 限额输入调整 ──────────────────────────────────────────

    @Test
    fun `setLimitMinutes updates the limit value`() = runTest {
        val installed = listOf(InstalledApp("com.app.a", "App A"))
        coEvery { appInfoProvider.getInstalledApps() } returns installed
        coEvery { appLimitRepo.getAllConfigs() } returns emptyList()

        createViewModel()
        advanceUntilIdle()

        viewModel.selectApp("com.app.a")
        advanceUntilIdle()

        viewModel.setLimitMinutes("45")
        assertEquals(45, viewModel.uiState.value.currentLimitMinutes)
    }

    @Test
    fun `setLimitMinutes does not go below 1 minute`() = runTest {
        val installed = listOf(InstalledApp("com.app.a", "App A"))
        coEvery { appInfoProvider.getInstalledApps() } returns installed
        coEvery { appLimitRepo.getAllConfigs() } returns emptyList()

        createViewModel()
        advanceUntilIdle()

        viewModel.selectApp("com.app.a")
        advanceUntilIdle()

        viewModel.setLimitMinutes("0")
        assertEquals(1, viewModel.uiState.value.currentLimitMinutes)
    }

    // ── 保存限额 ──────────────────────────────────────────────

    @Test
    fun `saveLimit calls repository and closes sheet`() = runTest {
        val installed = listOf(InstalledApp("com.app.a", "App A"))
        coEvery { appInfoProvider.getInstalledApps() } returns installed
        coEvery { appLimitRepo.getAllConfigs() } returns emptyList()
        coEvery { appLimitRepo.saveConfig(any()) } returns Unit

        createViewModel()
        advanceUntilIdle()

        viewModel.selectApp("com.app.a")
        advanceUntilIdle()
        viewModel.setLimitMinutes("32")

        viewModel.saveLimit()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.showBottomSheet)
        assertFalse(state.isSaving)
        assertNotNull(state.snackbarMessage)

        coVerify(exactly = 1) {
            appLimitRepo.saveConfig(match { config ->
                config.packageName == "com.app.a" && config.dailyLimitMinutes == 32
            })
        }
    }

    // ── 一键添加系统应用到白名单 ──────────────────────────────

    @Test
    fun `addSystemAppsToWhitelist calls use case and shows snackbar`() = runTest {
        val installed = listOf(InstalledApp("com.app.a", "App A"))
        coEvery { appInfoProvider.getInstalledApps() } returns installed
        coEvery { appLimitRepo.getAllConfigs() } returns emptyList()
        coEvery { addSystemAppsUseCase.execute() } returns listOf("com.android.dialer", "com.android.mms")

        createViewModel()
        advanceUntilIdle()

        viewModel.addSystemAppsToWhitelist()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isAddingSystemApps)
        assertNotNull(state.snackbarMessage)
        assertTrue(state.snackbarMessage!!.contains("2"))
    }

    @Test
    fun `addSystemAppsToWhitelist handles empty result`() = runTest {
        val installed = listOf(InstalledApp("com.app.a", "App A"))
        coEvery { appInfoProvider.getInstalledApps() } returns installed
        coEvery { appLimitRepo.getAllConfigs() } returns emptyList()
        coEvery { addSystemAppsUseCase.execute() } returns emptyList()

        createViewModel()
        advanceUntilIdle()

        viewModel.addSystemAppsToWhitelist()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isAddingSystemApps)
        assertNotNull(state.snackbarMessage)
        assertTrue(state.snackbarMessage!!.contains("未检测到"))
    }

    // ── 关闭 BottomSheet ──────────────────────────────────────

    @Test
    fun `dismissSheet closes bottom sheet`() = runTest {
        val installed = listOf(InstalledApp("com.app.a", "App A"))
        coEvery { appInfoProvider.getInstalledApps() } returns installed
        coEvery { appLimitRepo.getAllConfigs() } returns emptyList()

        createViewModel()
        advanceUntilIdle()

        viewModel.selectApp("com.app.a")
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.showBottomSheet)

        viewModel.dismissSheet()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.showBottomSheet)
        assertNull(viewModel.uiState.value.selectedApp)
    }
}
