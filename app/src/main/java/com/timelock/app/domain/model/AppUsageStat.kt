package com.timelock.app.domain.model

/**
 * Room 聚合查询结果 —— 用于日/周/月统计图表。
 *
 * @property packageName 应用包名
 * @property appName 应用显示名
 * @property totalDuration 聚合总时长 (毫秒)
 */
data class AppUsageStat(
    val packageName: String,
    val appName: String,
    val totalDuration: Long
)
