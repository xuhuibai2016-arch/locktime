package com.timelock.app.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import com.timelock.app.data.local.dao.InstalledAppDao
import com.timelock.app.data.local.entity.InstalledAppEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 已安装应用信息。
 */
data class InstalledApp(
    val packageName: String,
    val appName: String
)

/**
 * 应用信息提供者接口。
 */
interface AppInfoProvider {

    /** 根据包名获取应用显示名称 */
    fun getAppName(packageName: String): String

    /** 获取所有可启动的已安装应用列表 (含自身) */
    fun getInstalledApps(): List<InstalledApp>
}

/**
 * 基于 Room 缓存 + PackageManager 的实现。
 *
 * 缓存策略：
 * - 首次启动 → 全量从 PackageManager 同步到 Room
 * - 后续启动 → 直接读 Room 缓存（毫秒级）
 * - [PackageChangeReceiver] → 增量更新
 * - 用户设限额时 APP 不在缓存 → 单个实时查询并回写缓存
 * - 每次打开配置页 → 后台异步对比，有差异则增量修正
 */
@Singleton
class AppInfoProviderImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: InstalledAppDao
) : AppInfoProvider {

    companion object {
        private const val TAG = "AppInfoProvider"
    }

    @Volatile
    private var memoryCache: List<InstalledApp>? = null

    override fun getAppName(packageName: String): String {
        // 优先查缓存
        val cached = memoryCache?.find { it.packageName == packageName }
        if (cached != null) return cached.appName

        // 缓存未命中 → 实时查询 PackageManager，结果回写
        val name = resolveAppName(packageName)
        kotlinx.coroutines.runBlocking {
            try { dao.insert(InstalledAppEntity(packageName, name)) } catch (_: Exception) {}
        }
        return name
    }

    override fun getInstalledApps(): List<InstalledApp> {
        // 内存缓存命中 → 直接返回
        memoryCache?.let { return it }

        // 读 Room 缓存
        val cached = kotlinx.coroutines.runBlocking { dao.getAll() }
        if (cached.isNotEmpty()) {
            val list = cached.map { InstalledApp(it.packageName, it.appName) }
            memoryCache = list
            Log.d(TAG, "Loaded ${list.size} apps from Room cache")
            // 后台异步刷新（检测新装/卸载变化）
            asyncRefresh(cached)
            return list
        }

        // 缓存为空（首次启动）→ 全量同步
        Log.d(TAG, "Cache empty, full sync from PackageManager")
        val list = loadFromPackageManager()
        memoryCache = list
        kotlinx.coroutines.runBlocking {
            dao.insertAll(list.map { InstalledAppEntity(it.packageName, it.appName) })
        }
        return list
    }

    /** 从 PackageManager 全量加载（慢操作，仅首次/兜底） */
    private fun loadFromPackageManager(): List<InstalledApp> {
        val pm = context.packageManager
        val launchIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        return pm.queryIntentActivities(launchIntent, 0)
            .map { it.activityInfo }
            .distinctBy { it.packageName }
            .map { info ->
                InstalledApp(
                    packageName = info.packageName,
                    appName = info.loadLabel(pm).toString()
                )
            }
            .sortedBy { it.appName.lowercase() }
    }

    /** 实时解析单个 APP 名称（兜底用） */
    fun resolveAppName(packageName: String): String {
        return try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            packageName
        }
    }

    /** 标记内存缓存过期（PackageChangeReceiver 调用） */
    fun markCacheStale() {
        memoryCache = null
    }

    /** 后台异步对比 Room 缓存与系统实际应用列表，增量修正差异 */
    private fun asyncRefresh(cachedEntities: List<InstalledAppEntity>) {
        Thread {
            try {
                val fresh = loadFromPackageManager()
                val freshPkgs = fresh.map { it.packageName }.toSet()
                val cachedPkgs = cachedEntities.map { it.packageName }.toSet()

                // 新增的 APP
                val added = fresh.filter { it.packageName !in cachedPkgs }
                // 已卸载的 APP
                val removed = cachedPkgs - freshPkgs

                if (added.isNotEmpty() || removed.isNotEmpty()) {
                    Log.d(TAG, "asyncRefresh: +${added.size} apps, -${removed.size} apps")
                    kotlinx.coroutines.runBlocking {
                        if (added.isNotEmpty()) {
                            dao.insertAll(added.map { InstalledAppEntity(it.packageName, it.appName) })
                        }
                        removed.forEach { dao.deleteByPackageName(it) }
                    }
                    // 更新内存缓存
                    memoryCache = fresh
                }
            } catch (e: Exception) {
                Log.e(TAG, "asyncRefresh failed", e)
            }
        }.apply { isDaemon = true }.start()
    }
}
