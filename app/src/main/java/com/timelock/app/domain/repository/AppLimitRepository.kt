package com.timelock.app.domain.repository

import com.timelock.app.domain.model.AppLimitConfig
import kotlinx.coroutines.flow.Flow

/**
 * 应用限额配置仓库接口 (Domain Layer)。
 *
 * 定义限额配置的 CRUD 和业务查询契约。
 * 实现类位于 data 层，通过 Hilt 注入。
 */
interface AppLimitRepository {

    /** 按包名查询单个应用配置，不存在返回 null */
    suspend fun getConfig(packageName: String): AppLimitConfig?

    /** 获取全部配置 */
    suspend fun getAllConfigs(): List<AppLimitConfig>

    /** 获取已启用锁机的应用配置列表 */
    suspend fun getLockedConfigs(): List<AppLimitConfig>

    /** 获取白名单包名列表 (仅返回 packageName) */
    suspend fun getWhitelistedPackages(): List<String>

    /** 插入或更新配置 (按 packageName 唯一约束 REPLACE) */
    suspend fun saveConfig(config: AppLimitConfig)

    /** 按包名删除配置 */
    suspend fun deleteConfig(packageName: String)

    /** Flow: 观察单个应用配置变化 */
    fun observeConfig(packageName: String): Flow<AppLimitConfig?>

    /** Flow: 观察全部配置变化 */
    fun observeAllConfigs(): Flow<List<AppLimitConfig>>
}
