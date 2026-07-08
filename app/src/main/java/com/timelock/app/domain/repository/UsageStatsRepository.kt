package com.timelock.app.domain.repository

import com.timelock.app.domain.model.AppDailyUsage
import com.timelock.app.domain.model.AppUsageStat

/**
 * 应用使用统计仓库接口 (Domain Layer)。
 *
 * 封装 UsageStatsManager 系统 API 与 Room 持久化逻辑。
 * 实现类位于 data 层，通过 Hilt 注入。
 */
interface UsageStatsRepository {

    /**
     * 查询指定应用今日使用总时长 (毫秒)。
     * 从 Room 聚合查询获取，避免直接调用系统 API 的性能开销。
     */
    suspend fun getTodayUsage(packageName: String): Long

    /**
     * 查询指定应用在日期范围内的使用总时长 (毫秒)。
     */
    suspend fun getUsageByDateRange(
        packageName: String,
        startDate: String,
        endDate: String
    ): Long

    /** 查询某天的按应用聚合统计 */
    suspend fun getDailyStats(date: String): List<AppUsageStat>

    /** 查询日期范围内的按应用聚合统计 */
    suspend fun getStatsByDateRange(startDate: String, endDate: String): List<AppUsageStat>

    /**
     * 从 UsageStatsManager 同步使用数据到 Room。
     * 后台定期调用 (后续 Phase 通过 WorkManager 调度)。
     */
    suspend fun syncUsageStats()

    /**
     * 查询指定包名列表在日期范围内的逐日统计 (趋势图用)。
     * 返回按 package_name + date 分组的每日使用时长。
     */
    suspend fun getDailyStatsForPackages(
        startDate: String, endDate: String, packages: List<String>
    ): List<AppDailyUsage>
}
