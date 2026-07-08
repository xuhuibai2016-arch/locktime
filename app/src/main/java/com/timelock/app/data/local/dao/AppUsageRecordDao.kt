package com.timelock.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.timelock.app.data.local.AppDailyUsage
import com.timelock.app.data.local.AppUsageStat
import com.timelock.app.data.local.entity.AppUsageRecordEntity
import kotlinx.coroutines.flow.Flow

/**
 * 应用使用记录 DAO。
 *
 * 提供：
 * - 写入：单条/批量插入
 * - 查询：按日期、按日期范围
 * - 聚合：按天/周/月统计 (SUM + GROUP BY)
 * - 响应式：Flow 查询供 UI 层订阅
 */
@Dao
interface AppUsageRecordDao {

    /** 插入单条记录，返回自增 ID */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: AppUsageRecordEntity): Long

    /** 批量插入 (用于 UsageStats 同步) */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<AppUsageRecordEntity>)

    // ── 同步查询 ──────────────────────────────────────────────

    @Query("SELECT * FROM app_usage_records WHERE date = :date ORDER BY start_time DESC")
    suspend fun getRecordsByDate(date: String): List<AppUsageRecordEntity>

    @Query(
        """
        SELECT * FROM app_usage_records
        WHERE date BETWEEN :startDate AND :endDate
        ORDER BY date DESC, start_time DESC
        """
    )
    suspend fun getRecordsByDateRange(startDate: String, endDate: String): List<AppUsageRecordEntity>

    // ── 聚合统计查询 ──────────────────────────────────────────

    /** 单日统计: (package_name, date) 唯一约束下每 App 仅一行，无需聚合 */
    @Query(
        """
        SELECT package_name AS packageName,
               app_name AS appName,
               duration AS totalDuration
        FROM app_usage_records
        WHERE date = :date
        ORDER BY duration DESC
        """
    )
    suspend fun getDailyStats(date: String): List<AppUsageStat>

    /** 日期范围统计 (周/月): 按应用汇总时长 */
    @Query(
        """
        SELECT package_name AS packageName,
               app_name AS appName,
               SUM(duration) AS totalDuration
        FROM app_usage_records
        WHERE date BETWEEN :startDate AND :endDate
        GROUP BY package_name
        ORDER BY totalDuration DESC
        """
    )
    suspend fun getStatsByDateRange(startDate: String, endDate: String): List<AppUsageStat>

    /** 指定包名列表在日期范围内的逐日统计 (趋势图用) */
    @Query(
        """
        SELECT package_name AS packageName,
               app_name AS appName,
               date,
               SUM(duration) AS totalDuration
        FROM app_usage_records
        WHERE date BETWEEN :startDate AND :endDate
          AND package_name IN (:packages)
        GROUP BY package_name, date
        ORDER BY package_name, date
        """
    )
    suspend fun getDailyStatsForPackages(
        startDate: String, endDate: String, packages: List<String>
    ): List<AppDailyUsage>

    // ── Flow 响应式查询 ───────────────────────────────────────

    @Query("SELECT * FROM app_usage_records WHERE date = :date ORDER BY start_time DESC")
    fun observeRecordsByDate(date: String): Flow<List<AppUsageRecordEntity>>

    /** 事务性替换当天全部记录：先删后插，保证原子性 */
    @Transaction
    suspend fun replaceDailyRecords(date: String, records: List<AppUsageRecordEntity>) {
        deleteRecordsByDate(date)
        insertAll(records)
    }

    /** 删除指定日期的所有记录 (用于同步前清除旧数据) */
    @Query("DELETE FROM app_usage_records WHERE date = :date")
    suspend fun deleteRecordsByDate(date: String): Int

    /** 删除指定日期前的数据 (保留策略: 默认保留 90 天) */
    @Query("DELETE FROM app_usage_records WHERE date < :beforeDate")
    suspend fun deleteRecordsBefore(beforeDate: String): Int

    /** 删除指定日期中不在给定包名列表中的记录 (增量同步用) */
    @Query("DELETE FROM app_usage_records WHERE date = :date AND package_name NOT IN (:packages)")
    suspend fun deleteRecordsNotIn(date: String, packages: List<String>): Int
}
