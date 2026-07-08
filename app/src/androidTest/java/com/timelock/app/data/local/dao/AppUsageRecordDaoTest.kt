package com.timelock.app.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.timelock.app.data.local.AppDatabase
import com.timelock.app.data.local.entity.AppUsageRecordEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/**
 * AppUsageRecordDao 集成测试 (Room in-memory)。
 *
 * 覆盖:
 * - 单条/批量插入
 * - 按日期、按日期范围查询
 * - 聚合统计 (日/周/月)
 * - Flow 响应式查询
 * - 数据清理
 */
@RunWith(AndroidJUnit4::class)
class AppUsageRecordDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: AppUsageRecordDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).build()
        dao = db.appUsageRecordDao()
    }

    @After
    @Throws(IOException::class)
    fun tearDown() {
        db.close()
    }

    // ── 插入 & 查询 ───────────────────────────────────────────

    @Test
    fun insertAndGetByDate_returnsCorrectRecords() = runTest {
        val record = AppUsageRecordEntity(
            packageName = "com.test.app",
            appName = "Test App",
            startTime = 1700000000000L,
            endTime = 1700000360000L,
            duration = 360_000L, // 6 minutes
            date = "2024-11-15"
        )
        dao.insert(record)

        val results = dao.getRecordsByDate("2024-11-15")
        assertEquals(1, results.size)
        assertEquals("com.test.app", results[0].packageName)
        assertEquals(360_000L, results[0].duration)
    }

    @Test
    fun insertAll_batchInsert_succeeds() = runTest {
        val records = listOf(
            AppUsageRecordEntity(
                packageName = "com.app.a",
                appName = "App A",
                startTime = 1700000000000L,
                endTime = 1700000600000L,
                duration = 600_000L,
                date = "2024-11-15"
            ),
            AppUsageRecordEntity(
                packageName = "com.app.b",
                appName = "App B",
                startTime = 1700001000000L,
                endTime = 1700001300000L,
                duration = 300_000L,
                date = "2024-11-15"
            ),
            AppUsageRecordEntity(
                packageName = "com.app.a",
                appName = "App A",
                startTime = 1700002000000L,
                endTime = 1700002300000L,
                duration = 300_000L,
                date = "2024-11-15"
            )
        )
        dao.insertAll(records)

        val results = dao.getRecordsByDate("2024-11-15")
        assertEquals(3, results.size)
    }

    @Test
    fun getRecordsByDateRange_returnsRecordsInRange() = runTest {
        dao.insert(
            AppUsageRecordEntity(
                packageName = "com.day1.app",
                appName = "Day1",
                startTime = 1700000000000L,
                endTime = 1700000300000L,
                duration = 300_000L,
                date = "2024-11-13"
            )
        )
        dao.insert(
            AppUsageRecordEntity(
                packageName = "com.day2.app",
                appName = "Day2",
                startTime = 1700086400000L,
                endTime = 1700089400000L,
                duration = 300_000L,
                date = "2024-11-14"
            )
        )
        dao.insert(
            AppUsageRecordEntity(
                packageName = "com.day3.app",
                appName = "Day3",
                startTime = 1700172800000L,
                endTime = 1700175800000L,
                duration = 300_000L,
                date = "2024-11-15"
            )
        )

        val range = dao.getRecordsByDateRange("2024-11-13", "2024-11-14")
        assertEquals(2, range.size)
    }

    // ── 聚合统计 ──────────────────────────────────────────────

    @Test
    fun getDailyStats_aggregatesCorrectly() = runTest {
        // App A: 两次会话 = 600_000 + 300_000 = 900_000
        // App B: 一次会话 = 300_000
        dao.insertAll(
            listOf(
                AppUsageRecordEntity(
                    packageName = "com.app.a",
                    appName = "App A",
                    startTime = 1700000000000L,
                    endTime = 1700000600000L,
                    duration = 600_000L,
                    date = "2024-11-15"
                ),
                AppUsageRecordEntity(
                    packageName = "com.app.b",
                    appName = "App B",
                    startTime = 1700001000000L,
                    endTime = 1700001300000L,
                    duration = 300_000L,
                    date = "2024-11-15"
                ),
                AppUsageRecordEntity(
                    packageName = "com.app.a",
                    appName = "App A",
                    startTime = 1700002000000L,
                    endTime = 1700002300000L,
                    duration = 300_000L,
                    date = "2024-11-15"
                )
            )
        )

        val stats = dao.getDailyStats("2024-11-15")
        assertEquals(2, stats.size)

        // 按 totalDuration DESC 排序，App A 排第一
        val appA = stats[0]
        assertEquals("com.app.a", appA.packageName)
        assertEquals(900_000L, appA.totalDuration)

        val appB = stats[1]
        assertEquals("com.app.b", appB.packageName)
        assertEquals(300_000L, appB.totalDuration)
    }

    @Test
    fun getStatsByDateRange_aggregatesAcrossDays() = runTest {
        // App A: Day1=600k, Day2=300k, Day3=100k → total=1_000_000
        dao.insertAll(
            listOf(
                AppUsageRecordEntity(
                    packageName = "com.app.a",
                    appName = "App A",
                    duration = 600_000L,
                    date = "2024-11-13",
                    startTime = 1700000000000L,
                    endTime = 1700000600000L
                ),
                AppUsageRecordEntity(
                    packageName = "com.app.a",
                    appName = "App A",
                    duration = 300_000L,
                    date = "2024-11-14",
                    startTime = 1700086400000L,
                    endTime = 1700086700000L
                ),
                AppUsageRecordEntity(
                    packageName = "com.app.a",
                    appName = "App A",
                    duration = 100_000L,
                    date = "2024-11-15",
                    startTime = 1700172800000L,
                    endTime = 1700172900000L
                )
            )
        )

        val stats = dao.getStatsByDateRange("2024-11-13", "2024-11-15")
        assertEquals(1, stats.size)
        assertEquals("com.app.a", stats[0].packageName)
        // 跨三天总和
        assertEquals(1_000_000L, stats[0].totalDuration)
    }

    @Test
    fun getDailyStats_emptyDay_returnsEmptyList() = runTest {
        val stats = dao.getDailyStats("2024-12-25")
        assertTrue(stats.isEmpty())
    }

    // ── Flow 响应式查询 ───────────────────────────────────────

    @Test
    fun observeRecordsByDate_emitsInsertedRecords() = runTest {
        val record = AppUsageRecordEntity(
            packageName = "com.flow.app",
            appName = "Flow App",
            startTime = 1700000000000L,
            endTime = 1700000300000L,
            duration = 300_000L,
            date = "2024-11-15"
        )
        dao.insert(record)

        val emitted = dao.observeRecordsByDate("2024-11-15").first()
        assertEquals(1, emitted.size)
        assertEquals("com.flow.app", emitted[0].packageName)
    }

    // ── 数据清理 ──────────────────────────────────────────────

    @Test
    fun deleteRecordsBefore_removesOldRecords() = runTest {
        dao.insert(
            AppUsageRecordEntity(
                packageName = "com.old.app",
                appName = "Old",
                duration = 300_000L,
                date = "2024-08-01", // 旧数据
                startTime = 1722470400000L,
                endTime = 1722470700000L
            )
        )
        dao.insert(
            AppUsageRecordEntity(
                packageName = "com.new.app",
                appName = "New",
                duration = 300_000L,
                date = "2024-11-15", // 新数据
                startTime = 1700000000000L,
                endTime = 1700000300000L
            )
        )

        val deleted = dao.deleteRecordsBefore("2024-09-01")
        assertEquals(1, deleted)

        val remaining = dao.getRecordsByDateRange("2024-01-01", "2024-12-31")
        assertEquals(1, remaining.size)
        assertEquals("com.new.app", remaining[0].packageName)
    }
}
