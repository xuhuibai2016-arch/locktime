package com.timelock.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.timelock.app.data.local.entity.InstalledAppEntity

@Dao
interface InstalledAppDao {

    @Query("SELECT * FROM installed_apps_cache ORDER BY app_name ASC")
    suspend fun getAll(): List<InstalledAppEntity>

    @Query("SELECT * FROM installed_apps_cache WHERE package_name = :pkg LIMIT 1")
    suspend fun getByPackageName(pkg: String): InstalledAppEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(app: InstalledAppEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(apps: List<InstalledAppEntity>)

    @Query("DELETE FROM installed_apps_cache WHERE package_name = :pkg")
    suspend fun deleteByPackageName(pkg: String)

    @Query("DELETE FROM installed_apps_cache")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM installed_apps_cache")
    suspend fun count(): Int
}
