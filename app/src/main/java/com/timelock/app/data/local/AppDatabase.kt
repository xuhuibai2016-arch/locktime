package com.timelock.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.timelock.app.data.local.dao.AppLimitConfigDao
import com.timelock.app.data.local.dao.AppUsageRecordDao
import com.timelock.app.data.local.dao.InstalledAppDao
import com.timelock.app.data.local.dao.ScheduledLockDao
import com.timelock.app.data.local.entity.AppLimitConfigEntity
import com.timelock.app.data.local.entity.AppUsageRecordEntity
import com.timelock.app.data.local.entity.InstalledAppEntity
import com.timelock.app.data.local.entity.ScheduledLockEntity

@Database(
    entities = [
        AppUsageRecordEntity::class,
        AppLimitConfigEntity::class,
        ScheduledLockEntity::class,
        InstalledAppEntity::class
    ],
    version = 5,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun appUsageRecordDao(): AppUsageRecordDao
    abstract fun appLimitConfigDao(): AppLimitConfigDao
    abstract fun scheduledLockDao(): ScheduledLockDao
    abstract fun installedAppDao(): InstalledAppDao

    companion object {
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS installed_apps_cache (
                        package_name TEXT NOT NULL PRIMARY KEY,
                        app_name TEXT NOT NULL
                    )
                """)
            }
        }
    }
}
