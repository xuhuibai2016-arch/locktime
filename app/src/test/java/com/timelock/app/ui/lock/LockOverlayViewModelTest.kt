package com.timelock.app.ui.lock

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * LockOverlayViewModel 单元测试 (RED — 先写测试，确认失败后实现)。
 *
 * 验证长按 3 秒紧急解锁的状态机流转：
 * 1. 初始状态 progress=0, unlocked=false
 * 2. 按下 → progress 递增
 * 3. 持续 3 秒 → unlocked=true
 * 4. 提前释放 → progress 归零
 * 5. 已解锁后释放 → 保持 unlocked
 * 6. 重复按压 → 从 0 重新开始
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LockOverlayViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── 1. 初始状态 ──────────────────────────────────────────

    @Test
    fun `initial state has zero progress and not unlocked`() {
        val vm = LockOverlayViewModel()
        val state = vm.uiState.value
        assertEquals(0f, state.pressProgress)
        assertFalse(state.isUnlocked)
    }

    // ── 2. 按下 → 进度递增 ──────────────────────────────────

    @Test
    fun `startUnlockPress begins progress increment`() = runTest {
        val vm = LockOverlayViewModel()
        vm.startUnlockPress()

        // 1 秒后进度约 0.33
        advanceTimeBy(1000L)
        testDispatcher.scheduler.advanceUntilIdle()
        val midProgress = vm.uiState.value.pressProgress
        assertTrue(midProgress in 0.2f..0.5f)

        // 2 秒后进度约 0.66
        advanceTimeBy(1000L)
        testDispatcher.scheduler.advanceUntilIdle()
        val lateProgress = vm.uiState.value.pressProgress
        assertTrue(lateProgress in 0.5f..0.8f)
    }

    // ── 3. 持续 3 秒 → 解锁 ──────────────────────────────────

    @Test
    fun `pressing for 3 seconds triggers unlock`() = runTest {
        val vm = LockOverlayViewModel()
        vm.startUnlockPress()

        // 快进 3.5 秒，确保触发
        advanceTimeBy(3500L)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(1f, state.pressProgress)
        assertTrue(state.isUnlocked)
    }

    // ── 4. 提前释放 → 归零 ──────────────────────────────────

    @Test
    fun `resetUnlockPress during press resets progress to zero`() = runTest {
        val vm = LockOverlayViewModel()
        vm.startUnlockPress()

        // 按下 1 秒
        advanceTimeBy(1000L)
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.uiState.value.pressProgress > 0f)

        // 提前释放
        vm.resetUnlockPress()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(0f, state.pressProgress)
        assertFalse(state.isUnlocked)
    }

    // ── 5. 已解锁后释放 → 保持 unlocked ──────────────────────

    @Test
    fun `resetUnlockPress after unlock keeps unlocked state`() = runTest {
        val vm = LockOverlayViewModel()
        vm.startUnlockPress()

        // 持续 3+ 秒解锁
        advanceTimeBy(3500L)
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.uiState.value.isUnlocked)

        // 释放
        vm.resetUnlockPress()
        testDispatcher.scheduler.advanceUntilIdle()

        // 已解锁状态应保持
        assertTrue(vm.uiState.value.isUnlocked)
        assertEquals(0f, vm.uiState.value.pressProgress)
    }

    // ── 6. 重复按压 ──────────────────────────────────────────

    @Test
    fun `second press restarts progress from zero`() = runTest {
        val vm = LockOverlayViewModel()

        // 第一次按下 → 释放
        vm.startUnlockPress()
        advanceTimeBy(1000L)
        testDispatcher.scheduler.advanceUntilIdle()
        vm.resetUnlockPress()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(0f, vm.uiState.value.pressProgress)

        // 第二次按下 → 进度重新从 0 开始
        vm.startUnlockPress()
        advanceTimeBy(500L)
        testDispatcher.scheduler.advanceUntilIdle()
        val progress = vm.uiState.value.pressProgress
        assertTrue(progress in 0.1f..0.25f)
    }

    // ── 7. 设置应用信息 ──────────────────────────────────────

    @Test
    fun `setAppInfo updates app name limit and usage`() {
        val vm = LockOverlayViewModel()
        vm.setAppInfo(
            appName = "Test App",
            limitMinutes = 30,
            usedMinutes = 45
        )
        val state = vm.uiState.value
        assertEquals("Test App", state.appName)
        assertEquals(30, state.limitMinutes)
        assertEquals(45L, state.usedMinutes)
    }
}
