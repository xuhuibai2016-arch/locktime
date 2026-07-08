package com.timelock.app.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.timelock.app.data.local.AppDatabase
import com.timelock.app.data.local.dao.AppLimitConfigDao
import com.timelock.app.data.local.entity.AppLimitConfigEntity
import com.timelock.app.domain.repository.AppLimitRepository
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

/**
 * AppLimitRepositoryImpl 集成测试 (RED — 先写测试，确认失败后再实现)。
 *
 * 直接对接 Room DAO，确保 Repository → DAO 链路正确。
 */
@RunWith(AndroidJUnit4::class)
class AppLimitRepositoryImplTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: AppLimitConfigDao
    private lateinit var repository: AppLimitRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).build()
        dao = db.appLimitConfigDao()
        repository = AppLimitRepositoryImpl(dao)
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ── getConfig ─────────────────────────────────────────────

    @Test
    fun `getConfig returns entity when exists`() = runTest {
        dao.insert(
            AppLimitConfigEntity(
                packageName = "com.test.app",
                appName = "Test",
                dailyLimitMinutes = 30
            )
        )
        val config = repository.getConfig("com.test.app")
        assertNotNull(config)
        assertEquals("Test", config!!.appName)
        assertEquals(30, config.dailyLimitMinutes)
    }

    @Test
    fun `getConfig returns null when not found`() = runTest {
        val config = repository.getConfig("com.missing.app")
        assertNull(config)
    }

    // ── getAllConfigs ─────────────────────────────────────────

    @Test
    fun `getAllConfigs returns all saved configs`() = runTest {
        dao.insert(AppLimitConfigEntity(packageName = "com.a", appName = "A"))
        dao.insert(AppLimitConfigEntity(packageName = "com.b", appName = "B"))

        val all = repository.getAllConfigs()
        assertEquals(2, all.size)
    }

    // ── getLockedConfigs ──────────────────────────────────────

    @Test
    fun `getLockedConfigs returns only lock-enabled apps`() = runTest {
        dao.insert(
            AppLimitConfigEntity(
                packageName = "com.locked", appName = "L",
                isLockEnabled = true
            )
        )
        dao.insert(
            AppLimitConfigEntity(
                packageName = "com.unlocked", appName = "U",
                isLockEnabled = false
            )
        )

        val locked = repository.getLockedConfigs()
        assertEquals(1, locked.size)
        assertEquals("com.locked", locked[0].packageName)
    }

    // ── getWhitelistedPackages ────────────────────────────────

    @Test
    fun `getWhitelistedPackages returns only package names`() = runTest {
        dao.insert(
            AppLimitConfigEntity(
                packageName = "com.android.phone", appName = "Phone",
                isWhitelisted = true
            )
        )
        dao.insert(
            AppLimitConfigEntity(
                packageName = "com.game", appName = "Game",
                isWhitelisted = false
            )
        )

        val whitelist = repository.getWhitelistedPackages()
        assertEquals(listOf("com.android.phone"), whitelist)
    }

    // ── saveConfig ────────────────────────────────────────────

    @Test
    fun `saveConfig inserts new config`() = runTest {
        repository.saveConfig(
            AppLimitConfigEntity(
                packageName = "com.new.app", appName = "New",
                dailyLimitMinutes = 45
            )
        )

        val saved = dao.getByPackageName("com.new.app")
        assertNotNull(saved)
        assertEquals(45, saved!!.dailyLimitMinutes)
    }

    @Test
    fun `saveConfig updates existing config via replace`() = runTest {
        repository.saveConfig(
            AppLimitConfigEntity(
                packageName = "com.update.app", appName = "Old",
                dailyLimitMinutes = 20
            )
        )
        repository.saveConfig(
            AppLimitConfigEntity(
                packageName = "com.update.app", appName = "Updated",
                dailyLimitMinutes = 60
            )
        )

        val updated = dao.getByPackageName("com.update.app")
        assertNotNull(updated)
        assertEquals("Updated", updated!!.appName)
        assertEquals(60, updated.dailyLimitMinutes)
    }

    // ── deleteConfig ──────────────────────────────────────────

    @Test
    fun `deleteConfig removes config by package name`() = runTest {
        dao.insert(
            AppLimitConfigEntity(
                packageName = "com.delete.app", appName = "DeleteMe"
            )
        )
        repository.deleteConfig("com.delete.app")

        val deleted = dao.getByPackageName("com.delete.app")
        assertNull(deleted)
    }

    // ── observeConfig ─────────────────────────────────────────

    @Test
    fun `observeConfig emits saved entity`() = runTest {
        dao.insert(
            AppLimitConfigEntity(
                packageName = "com.flow.app", appName = "Flow",
                dailyLimitMinutes = 10
            )
        )

        val emitted = repository.observeConfig("com.flow.app").first()
        assertNotNull(emitted)
        assertEquals("Flow", emitted!!.appName)
    }

    // ── observeAllConfigs ─────────────────────────────────────

    @Test
    fun `observeAllConfigs emits all saved entities`() = runTest {
        dao.insert(AppLimitConfigEntity(packageName = "com.x", appName = "X"))
        dao.insert(AppLimitConfigEntity(packageName = "com.y", appName = "Y"))

        val emitted = repository.observeAllConfigs().first()
        assertEquals(2, emitted.size)
    }
}
