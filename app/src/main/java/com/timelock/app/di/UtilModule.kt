package com.timelock.app.di

import com.timelock.app.util.AppInfoProvider
import com.timelock.app.util.AppInfoProviderImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt DI 模块 —— 工具类接口绑定。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class UtilModule {

    @Binds
    @Singleton
    abstract fun bindAppInfoProvider(impl: AppInfoProviderImpl): AppInfoProvider
}
