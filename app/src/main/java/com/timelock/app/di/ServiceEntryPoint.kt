package com.timelock.app.di

import com.timelock.app.data.local.dao.ScheduledLockDao
import com.timelock.app.domain.repository.UsageStatsRepository
import com.timelock.app.domain.usecase.CheckAppLimitUseCase
import com.timelock.app.service.LockMonitorEngine
import com.timelock.app.service.LockStateManager
import com.timelock.app.util.AppInfoProvider
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * 统一 Service 层 Hilt EntryPoint。
 *
 * 所有 Service / BroadcastReceiver 通过此入口获取依赖，
 * 替代之前分散在 4 个文件中的独立 [EntryPoint] 接口。
 */
@InstallIn(SingletonComponent::class)
@EntryPoint
interface ServiceEntryPoint {
    fun checkAppLimitUseCase(): CheckAppLimitUseCase
    fun usageStatsRepository(): UsageStatsRepository
    fun appInfoProvider(): AppInfoProvider
    fun scheduledLockDao(): ScheduledLockDao
    fun lockStateManager(): LockStateManager
    fun lockMonitorEngine(): LockMonitorEngine
}
