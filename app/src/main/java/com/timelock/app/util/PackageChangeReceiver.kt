package com.timelock.app.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.timelock.app.data.local.dao.InstalledAppDao
import com.timelock.app.data.local.entity.InstalledAppEntity
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 监听 APP 安装/卸载广播，增量更新 [InstalledAppDao] 缓存。
 *
 * 系统事件驱动，无主动唤醒，功耗几乎为零。
 */
class PackageChangeReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "PackageChangeReceiver"
    }

    @InstallIn(SingletonComponent::class)
    @EntryPoint
    interface PackageChangeEntryPoint {
        fun installedAppDao(): InstalledAppDao
        fun appInfoProvider(): AppInfoProvider
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val pkg = intent.data?.schemeSpecificPart ?: return
        val isReplacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
        if (pkg.isEmpty()) return

        val entryPoint = try {
            EntryPointAccessors.fromApplication(
                context.applicationContext, PackageChangeEntryPoint::class.java
            )
        } catch (_: Exception) {
            return
        }

        val dao = entryPoint.installedAppDao()
        val appInfoProvider = entryPoint.appInfoProvider()

        scope.launch {
            when (intent.action) {
                Intent.ACTION_PACKAGE_ADDED -> {
                    if (isReplacing) {
                        // 更新安装，刷新应用名
                        val name = (appInfoProvider as AppInfoProviderImpl).resolveAppName(pkg)
                        dao.insert(InstalledAppEntity(pkg, name))
                        Log.d(TAG, "UPDATED: $pkg ($name)")
                    } else {
                        val name = (appInfoProvider as AppInfoProviderImpl).resolveAppName(pkg)
                        dao.insert(InstalledAppEntity(pkg, name))
                        Log.d(TAG, "ADDED: $pkg ($name)")
                    }
                }
                Intent.ACTION_PACKAGE_REMOVED -> {
                    if (!isReplacing) {
                        dao.deleteByPackageName(pkg)
                        Log.d(TAG, "REMOVED: $pkg")
                    }
                }
            }
            // 通知 AppInfoProviderImpl 刷新内存缓存
            (appInfoProvider as AppInfoProviderImpl).markCacheStale()
        }
    }
}
