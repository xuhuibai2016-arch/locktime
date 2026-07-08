package com.timelock.app.data.repository

import android.app.usage.UsageStatsManager
import android.content.Context
import android.util.Log
import com.timelock.app.data.local.dao.AppUsageRecordDao
import com.timelock.app.data.local.entity.AppUsageRecordEntity
import com.timelock.app.domain.model.AppDailyUsage
import com.timelock.app.domain.model.AppUsageStat
import com.timelock.app.domain.repository.UsageStatsRepository
import com.timelock.app.util.AppInfoProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 应用使用统计仓库实现。
 *
 * 数据源：
 * - 读取：优先从 Room 聚合查询 (性能)
 * - 同步：从 [UsageStatsManager] 拉取系统数据 → 写入 Room
 */
@Singleton
class UsageStatsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: AppUsageRecordDao,
    private val appInfoProvider: AppInfoProvider
) : UsageStatsRepository {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    override suspend fun getTodayUsage(packageName: String): Long {
        val today = dateFormat.format(Date())
        val stats = dao.getDailyStats(today)
        return stats.find { it.packageName == packageName }?.totalDuration ?: 0L
    }

    override suspend fun getUsageByDateRange(
        packageName: String,
        startDate: String,
        endDate: String
    ): Long {
        val stats = dao.getStatsByDateRange(startDate, endDate)
        return stats.find { it.packageName == packageName }?.totalDuration ?: 0L
    }

    override suspend fun getDailyStats(date: String): List<AppUsageStat> {
        return dao.getDailyStats(date)
    }

    override suspend fun getStatsByDateRange(
        startDate: String,
        endDate: String
    ): List<AppUsageStat> {
        return dao.getStatsByDateRange(startDate, endDate)
    }

    override suspend fun syncUsageStats() {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE)
            as? UsageStatsManager ?: return

        val cal = Calendar.getInstance()
        val endTime = cal.timeInMillis
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val startTime = cal.timeInMillis
        val today = dateFormat.format(Date())

        // 使用 UsageEvents 逐事件计算精确时间范围内的前台时长
        // queryUsageStats 的 totalTimeInForeground 是桶全量，不受 beginTime/endTime 约束
        val events = usageStatsManager.queryEvents(startTime, endTime)

        if (events == null) return

        val fgStartTimes = mutableMapOf<String, Long>()
        val fgDurations = mutableMapOf<String, Long>()

        val event = android.app.usage.UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName ?: continue

            when (event.eventType) {
                android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND,
                android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED -> {
                    fgStartTimes[pkg] = event.timeStamp
                }
                android.app.usage.UsageEvents.Event.MOVE_TO_BACKGROUND,
                android.app.usage.UsageEvents.Event.ACTIVITY_PAUSED,
                android.app.usage.UsageEvents.Event.ACTIVITY_STOPPED -> {
                    val start = fgStartTimes.remove(pkg) ?: continue
                    val duration = event.timeStamp - start
                    if (duration > 0) {
                        fgDurations[pkg] = (fgDurations[pkg] ?: 0L) + duration
                    }
                }
            }
        }

        // 未结束的前台会话：计到 endTime
        for ((pkg, start) in fgStartTimes) {
            val duration = endTime - start
            if (duration > 0) {
                fgDurations[pkg] = (fgDurations[pkg] ?: 0L) + duration
            }
        }

        val records = fgDurations.map { (pkg, duration) ->
            val appName = appInfoProvider.getAppName(pkg)
            AppUsageRecordEntity(
                packageName = pkg,
                appName = appName,
                startTime = startTime,
                endTime = endTime,
                duration = duration,
                date = today
            )
        }
        // 事务性替换当天记录：先删后插，保证原子性
        dao.replaceDailyRecords(today, records)
    }

    override suspend fun getDailyStatsForPackages(
        startDate: String, endDate: String, packages: List<String>
    ): List<AppDailyUsage> {
        if (packages.isEmpty()) return emptyList()
        return dao.getDailyStatsForPackages(startDate, endDate, packages)
    }
}
