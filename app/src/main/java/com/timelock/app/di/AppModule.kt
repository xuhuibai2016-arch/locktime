package com.timelock.app.di

import android.content.Context
import androidx.room.Room
import com.timelock.app.data.local.AppDatabase
import com.timelock.app.data.local.dao.AppLimitConfigDao
import com.timelock.app.data.local.dao.AppUsageRecordDao
import com.timelock.app.data.local.dao.InstalledAppDao
import com.timelock.app.data.local.dao.ScheduledLockDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt DI 模块 —— 提供 Room Database 及其 DAO 实例。
 *
 * 所有依赖均为 [Singleton] 作用域，确保全局唯一 Database 实例。
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "timelock.db"
        )
            .addMigrations(AppDatabase.MIGRATION_4_5)
            .build()
    }

    @Provides
    @Singleton
    fun provideAppUsageRecordDao(db: AppDatabase): AppUsageRecordDao {
        return db.appUsageRecordDao()
    }

    @Provides
    @Singleton
    fun provideAppLimitConfigDao(db: AppDatabase): AppLimitConfigDao {
        return db.appLimitConfigDao()
    }

    @Provides
    @Singleton
    fun provideScheduledLockDao(db: AppDatabase): ScheduledLockDao {
        return db.scheduledLockDao()
    }

    @Provides
    @Singleton
    fun provideInstalledAppDao(db: AppDatabase): InstalledAppDao {
        return db.installedAppDao()
    }
}
