package com.timelock.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 应用限额配置表。
 *
 * 每一条记录对应一个被监控应用的限额策略。
 * - [dailyLimitMinutes] = 0 表示不限时（仅监控，不锁机）
 * - [isLockEnabled] 控制是否启用锁机
 * - [isWhitelisted] 标记电话/短信等永不被锁的白名单应用
 *
 * 交互粒度：分钟；存储时转换为分钟（int），避免浮点精度问题。
 */
@Entity(
    tableName = "app_limit_configs",
    indices = [
        Index(value = ["package_name"], unique = true)
    ]
)
data class AppLimitConfigEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** 应用包名 (唯一) */
    @ColumnInfo(name = "package_name")
    val packageName: String,

    /** 应用显示名称 */
    @ColumnInfo(name = "app_name")
    val appName: String,

    /** 每日限额 (分钟)；0 = 不限时 */
    @ColumnInfo(name = "daily_limit_minutes")
    val dailyLimitMinutes: Int = 0,

    /** 是否启用锁机 */
    @ColumnInfo(name = "is_lock_enabled")
    val isLockEnabled: Boolean = true,

    /** 是否为白名单应用 (电话/短信) —— 永不锁机 */
    @ColumnInfo(name = "is_whitelisted")
    val isWhitelisted: Boolean = false,

    /** 是否为系统保护应用 (电话/短信) —— 白名单中不可删除 */
    @ColumnInfo(name = "is_system_protected")
    val isSystemProtected: Boolean = false
)
