package com.timelock.app.domain.model

/**
 * 单应用单日使用统计，用于趋势折线图等需要按天拆分的数据场景。
 *
 * @property packageName 应用包名
 * @property appName 应用显示名称（从 Room 记录中读取，非实时 PackageManager 解析）
 * @property date 日期字符串 "yyyy-MM-dd"
 * @property totalDuration 当日使用总时长 (毫秒)
 */
data class AppDailyUsage(
    val packageName: String,
    val appName: String,
    val date: String,
    val totalDuration: Long
)
