package com.timelock.app.di

import com.timelock.app.data.repository.AppLimitRepositoryImpl
import com.timelock.app.data.repository.UsageStatsRepositoryImpl
import com.timelock.app.domain.repository.AppLimitRepository
import com.timelock.app.domain.repository.UsageStatsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt DI 模块 —— Domain ↔ Data 层的接口绑定。
 *
 * 将 Domain 层接口绑定到 Data 层的具体实现，
 * 确保 ViewModel / UseCase 只依赖抽象接口。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAppLimitRepository(impl: AppLimitRepositoryImpl): AppLimitRepository

    @Binds
    @Singleton
    abstract fun bindUsageStatsRepository(impl: UsageStatsRepositoryImpl): UsageStatsRepository
}
