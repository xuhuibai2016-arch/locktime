package com.timelock.app.domain.usecase

import com.timelock.app.domain.model.AppLimitConfig
import com.timelock.app.domain.repository.AppLimitRepository
import com.timelock.app.util.SystemAppHelper
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 一键添加系统关键应用到白名单。
 *
 * 自动识别电话、短信等关键通信应用，写入白名单数据库，
 * 并标记为 [AppLimitConfig.isSystemProtected] = true，
 * 防止用户在设置中误删导致无法接听电话或短信。
 */
@Singleton
class AddSystemAppsToWhitelistUseCase @Inject constructor(
    private val appLimitRepository: AppLimitRepository,
    private val systemAppHelper: SystemAppHelper
) {
    /**
     * 执行：获取系统应用 → 写入白名单。
     *
     * 对已存在于白名单中的配置，保留原有限额设置，仅更新
     * isWhitelisted 和 isSystemProtected 标志。
     *
     * @return 本次成功添加/更新的系统应用包名列表
     */
    suspend fun execute(): List<String> {
        val systemPackages = systemAppHelper.getSystemAppPackages()
        if (systemPackages.isEmpty()) return emptyList()

        val updatedPackages = mutableListOf<String>()

        for (packageName in systemPackages) {
            val appName = systemAppHelper.getAppName(packageName)

            // 检查是否已有配置（可能用户手动设置过）
            val existing = appLimitRepository.getConfig(packageName)
            val config = if (existing != null) {
                // 保留用户原有限额设置，仅更新白名单和保护标志
                existing.copy(
                    appName = appName,
                    isWhitelisted = true,
                    isSystemProtected = true
                )
            } else {
                // 新建配置：不限时 + 白名单 + 系统保护
                AppLimitConfig(
                    packageName = packageName,
                    appName = appName,
                    dailyLimitMinutes = 0,       // 不限时
                    isLockEnabled = true,         // 保持监控记录
                    isWhitelisted = true,         // 永不锁机
                    isSystemProtected = true      // 不可删除
                )
            }
            appLimitRepository.saveConfig(config)
            updatedPackages.add(packageName)
        }

        return updatedPackages
    }
}
