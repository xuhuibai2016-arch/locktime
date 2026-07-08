package com.timelock.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.timelock.app.data.local.entity.AppLimitConfigEntity
import kotlinx.coroutines.flow.Flow

/**
 * 应用限额配置 DAO。
 *
 * 提供：
 * - CRUD：增/删/改/查
 * - 业务查询：白名单、已启用锁机的应用
 * - 响应式：Flow 查询供 UI 层订阅
 */
@Dao
interface AppLimitConfigDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(config: AppLimitConfigEntity): Long

    @Update
    suspend fun update(config: AppLimitConfigEntity)

    @Delete
    suspend fun delete(config: AppLimitConfigEntity)

    // ── 同步查询 ──────────────────────────────────────────────

    @Query("SELECT * FROM app_limit_configs ORDER BY app_name ASC")
    suspend fun getAll(): List<AppLimitConfigEntity>

    @Query("SELECT * FROM app_limit_configs WHERE package_name = :packageName LIMIT 1")
    suspend fun getByPackageName(packageName: String): AppLimitConfigEntity?

    /** 查询已启用锁机的应用 (排序: 限额从低到高) */
    @Query(
        """
        SELECT * FROM app_limit_configs
        WHERE is_lock_enabled = 1
        ORDER BY daily_limit_minutes ASC
        """
    )
    suspend fun getLockedApps(): List<AppLimitConfigEntity>

    /** 查询白名单应用 (电话/短信) */
    @Query("SELECT * FROM app_limit_configs WHERE is_whitelisted = 1")
    suspend fun getWhitelistedApps(): List<AppLimitConfigEntity>

    // ── Flow 响应式查询 ───────────────────────────────────────

    @Query("SELECT * FROM app_limit_configs ORDER BY app_name ASC")
    fun observeAll(): Flow<List<AppLimitConfigEntity>>

    @Query("SELECT * FROM app_limit_configs WHERE package_name = :packageName LIMIT 1")
    fun observeByPackageName(packageName: String): Flow<AppLimitConfigEntity?>
}
