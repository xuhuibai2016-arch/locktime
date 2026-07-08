package com.timelock.app.data.repository

import com.timelock.app.data.local.dao.AppLimitConfigDao
import com.timelock.app.data.local.entity.AppLimitConfigEntity
import com.timelock.app.domain.model.AppLimitConfig
import com.timelock.app.domain.repository.AppLimitRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 应用限额配置仓库实现。
 *
 * 委托给 Room DAO，并在 Domain Model 与 Entity 之间做映射。
 */
@Singleton
class AppLimitRepositoryImpl @Inject constructor(
    private val dao: AppLimitConfigDao
) : AppLimitRepository {

    override suspend fun getConfig(packageName: String): AppLimitConfig? {
        return dao.getByPackageName(packageName)?.toDomain()
    }

    override suspend fun getAllConfigs(): List<AppLimitConfig> {
        return dao.getAll().map { it.toDomain() }
    }

    override suspend fun getLockedConfigs(): List<AppLimitConfig> {
        return dao.getLockedApps().map { it.toDomain() }
    }

    override suspend fun getWhitelistedPackages(): List<String> {
        return dao.getWhitelistedApps().map { it.packageName }
    }

    override suspend fun saveConfig(config: AppLimitConfig) {
        dao.insert(config.toEntity())
    }

    override suspend fun deleteConfig(packageName: String) {
        val config = dao.getByPackageName(packageName) ?: return
        dao.delete(config)
    }

    override fun observeConfig(packageName: String): Flow<AppLimitConfig?> {
        return dao.observeByPackageName(packageName).map { it?.toDomain() }
    }

    override fun observeAllConfigs(): Flow<List<AppLimitConfig>> {
        return dao.observeAll().map { list -> list.map { it.toDomain() } }
    }

    // ── 映射函数 ──────────────────────────────────────────────

    private fun AppLimitConfigEntity.toDomain() = AppLimitConfig(
        packageName = packageName,
        appName = appName,
        dailyLimitMinutes = dailyLimitMinutes,
        isLockEnabled = isLockEnabled,
        isWhitelisted = isWhitelisted,
        isSystemProtected = isSystemProtected
    )

    private fun AppLimitConfig.toEntity() = AppLimitConfigEntity(
        packageName = packageName,
        appName = appName,
        dailyLimitMinutes = dailyLimitMinutes,
        isLockEnabled = isLockEnabled,
        isWhitelisted = isWhitelisted,
        isSystemProtected = isSystemProtected
    )
}
