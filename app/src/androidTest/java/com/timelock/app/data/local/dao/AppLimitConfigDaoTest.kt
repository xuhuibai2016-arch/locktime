package com.timelock.app.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.timelock.app.data.local.AppDatabase
import com.timelock.app.data.local.entity.AppLimitConfigEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/**
 * AppLimitConfigDao 集成测试 (Room in-memory)。
 *
 * 覆盖:
 * - 增/删/改/查
 * - 唯一索引冲突 (package_name)
 * - 白名单 & 锁机业务查询
 * - Flow 响应式查询
 */
@RunWith(AndroidJUnit4::class)
class AppLimitConfigDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: AppLimitConfigDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).build()
        dao = db.appLimitConfigDao()
    }

    @After
    @Throws(IOException::class)
    fun tearDown() {
        db.close()
    }

    // ── 插入 & 查询 ───────────────────────────────────────────

    @Test
    fun insertAndGetAll_returnsInsertedConfig() = runTest {
        val config = AppLimitConfigEntity(
            packageName = "com.test.app",
            appName = "Test App",
            dailyLimitMinutes = 60,
            isLockEnabled = true,
            isWhitelisted = false
        )
        dao.insert(config)

        val all = dao.getAll()
        assertEquals(1, all.size)
        assertEquals("com.test.app", all[0].packageName)
        assertEquals(60, all[0].dailyLimitMinutes)
    }

    @Test
    fun getByPackageName_returnsCorrectConfig() = runTest {
        val config = AppLimitConfigEntity(
            packageName = "com.unique.app",
            appName = "Unique",
            dailyLimitMinutes = 30
        )
        dao.insert(config)

        val result = dao.getByPackageName("com.unique.app")
        assertNotNull(result)
        assertEquals("Unique", result!!.appName)
    }

    @Test
    fun getByPackageName_notFound_returnsNull() = runTest {
        val result = dao.getByPackageName("com.nonexistent.app")
        assertNull(result)
    }

    // ── 更新 ──────────────────────────────────────────────────

    @Test
    fun update_modifiesExistingConfig() = runTest {
        val config = AppLimitConfigEntity(
            packageName = "com.update.app",
            appName = "Before Update",
            dailyLimitMinutes = 30
        )
        val id = dao.insert(config)

        val updated = config.copy(id = id, dailyLimitMinutes = 90, appName = "After Update")
        dao.update(updated)

        val result = dao.getByPackageName("com.update.app")
        assertNotNull(result)
        assertEquals(90, result!!.dailyLimitMinutes)
        assertEquals("After Update", result.appName)
    }

    // ── 删除 ──────────────────────────────────────────────────

    @Test
    fun delete_removesConfig() = runTest {
        val config = AppLimitConfigEntity(
            packageName = "com.delete.app",
            appName = "To Delete",
            dailyLimitMinutes = 15
        )
        val id = dao.insert(config)

        dao.delete(config.copy(id = id))

        val all = dao.getAll()
        assertTrue(all.isEmpty())
    }

    // ── 唯一索引冲突 ──────────────────────────────────────────

    @Test
    fun insert_duplicatePackageName_replacesExisting() = runTest {
        val first = AppLimitConfigEntity(
            packageName = "com.duplicate.app",
            appName = "First",
            dailyLimitMinutes = 10
        )
        dao.insert(first)

        val second = AppLimitConfigEntity(
            packageName = "com.duplicate.app",
            appName = "Second",
            dailyLimitMinutes = 20
        )
        dao.insert(second)

        // OnConflictStrategy.REPLACE: 第二条覆盖第一条
        val all = dao.getAll()
        assertEquals(1, all.size)
        assertEquals("Second", all[0].appName)
        assertEquals(20, all[0].dailyLimitMinutes)
    }

    // ── 业务查询: 白名单 ──────────────────────────────────────

    @Test
    fun getWhitelistedApps_returnsOnlyWhitelisted() = runTest {
        dao.insert(
            AppLimitConfigEntity(
                packageName = "com.android.phone",
                appName = "Phone",
                isWhitelisted = true
            )
        )
        dao.insert(
            AppLimitConfigEntity(
                packageName = "com.android.mms",
                appName = "Messages",
                isWhitelisted = true
            )
        )
        dao.insert(
            AppLimitConfigEntity(
                packageName = "com.game.app",
                appName = "Game",
                isWhitelisted = false
            )
        )

        val whitelisted = dao.getWhitelistedApps()
        assertEquals(2, whitelisted.size)
    }

    // ── 业务查询: 已启用锁机 ──────────────────────────────────

    @Test
    fun getLockedApps_returnsOnlyLockEnabled() = runTest {
        dao.insert(
            AppLimitConfigEntity(
                packageName = "com.locked.app",
                appName = "Locked",
                dailyLimitMinutes = 30,
                isLockEnabled = true
            )
        )
        dao.insert(
            AppLimitConfigEntity(
                packageName = "com.unlocked.app",
                appName = "Unlocked",
                dailyLimitMinutes = 60,
                isLockEnabled = false
            )
        )

        val locked = dao.getLockedApps()
        assertEquals(1, locked.size)
        assertEquals("com.locked.app", locked[0].packageName)
    }

    @Test
    fun getLockedApps_sortedByLimitAscending() = runTest {
        dao.insert(
            AppLimitConfigEntity(
                packageName = "com.high.app",
                appName = "High",
                dailyLimitMinutes = 120,
                isLockEnabled = true
            )
        )
        dao.insert(
            AppLimitConfigEntity(
                packageName = "com.low.app",
                appName = "Low",
                dailyLimitMinutes = 15,
                isLockEnabled = true
            )
        )

        val locked = dao.getLockedApps()
        assertEquals(2, locked.size)
        // 限额从低到高排序
        assertEquals(15, locked[0].dailyLimitMinutes)
        assertEquals(120, locked[1].dailyLimitMinutes)
    }

    // ── Flow 响应式查询 ───────────────────────────────────────

    @Test
    fun observeAll_emitsInsertedConfigs() = runTest {
        dao.insert(
            AppLimitConfigEntity(
                packageName = "com.flow.app",
                appName = "Flow App",
                dailyLimitMinutes = 45
            )
        )

        val emitted = dao.observeAll().first()
        assertEquals(1, emitted.size)
        assertEquals("com.flow.app", emitted[0].packageName)
    }

    @Test
    fun observeByPackageName_emitsNullWhenNotFound() = runTest {
        val emitted = dao.observeByPackageName("com.nonexistent.app").first()
        assertNull(emitted)
    }
}
