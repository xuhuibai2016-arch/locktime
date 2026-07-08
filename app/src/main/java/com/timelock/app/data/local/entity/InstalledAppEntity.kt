package com.timelock.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 已安装应用缓存表。
 *
 * 将 PackageManager 查询结果持久化到 Room，避免每次打开配置页
 * 都遍历 150+ 个 APK 的资源文件读取应用名。
 *
 * 更新策略：
 * - 首次启动 / 缓存为空 → 全量同步
 * - PACKAGE_ADDED 广播 → 增量插入
 * - PACKAGE_REMOVED 广播 → 增量删除
 * - 用户设限额时 APP 不在缓存 → 单个刷新 + 插入
 */
@Entity(tableName = "installed_apps_cache")
data class InstalledAppEntity(
    @PrimaryKey
    @ColumnInfo(name = "package_name")
    val packageName: String,

    @ColumnInfo(name = "app_name")
    val appName: String
)
