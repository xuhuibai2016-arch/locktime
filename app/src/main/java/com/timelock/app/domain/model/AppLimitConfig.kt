package com.timelock.app.domain.model

/**
 * 应用限额配置领域模型。
 *
 * 与 [AppLimitConfigEntity] 的区别：不含 Room 注解和数据库 id，
 * 其位于 domain 层，不依赖 data 层。
 *
 * @property packageName 应用包名 (唯一标识)
 * @property appName 应用显示名称
 * @property dailyLimitMinutes 每日限额 (分钟)；0 = 不限时
 * @property isLockEnabled 是否启用锁机
 * @property isWhitelisted 是否为白名单应用 (电话/短信) —— 永不锁机
 * @property isSystemProtected 是否为系统保护应用 —— 白名单中不可删除
 */
data class AppLimitConfig(
    val packageName: String,
    val appName: String,
    val dailyLimitMinutes: Int = 0,
    val isLockEnabled: Boolean = true,
    val isWhitelisted: Boolean = false,
    val isSystemProtected: Boolean = false
)
