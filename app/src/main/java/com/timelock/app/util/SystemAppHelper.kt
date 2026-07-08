package com.timelock.app.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Telephony
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 系统应用识别工具类。
 *
 * 负责识别和过滤不需要监控的系统/关键应用：
 * - TimeLock 自身
 * - 系统桌面 (Launcher)
 * - 默认电话/短信应用
 * - SystemUI / 设置 / 输入法等系统组件
 */
@Singleton
class SystemAppHelper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appInfoProvider: AppInfoProvider
) {
    companion object {
        /** 自身包名 */
        private const val SELF_PACKAGE = "com.timelock.app"

        /** 固定排除的包名前缀 */
        private val EXCLUDED_PREFIXES = setOf(
            "com.android.systemui",
            "com.android.settings",
            "com.android.launcher",
            "com.android.dialer",
            "com.android.phone",
            "com.android.contacts",
            "com.android.mms",
            "com.android.camera",
            "com.android.deskclock",
            "com.android.calculator",
            "com.android.calendar",
            "com.android.providers",
            "com.android.packageinstaller",
            "com.android.documentsui",
            "com.android.inputmethod",
            "com.android.keychain",
            "com.android.wallpaper",
            "com.google.android.apps.nexuslauncher",
            "com.google.android.inputmethod",
            "com.miui.home",
            "com.miui.securitycenter",
            "com.miui.notes",
            "com.miui.gallery",
            "com.xiaomi",
            "android",
        )
    }

    /**
     * 判断一个包名是否应被排除（不监控、不排名、不显示在配置页）。
     *
     * 排除条件（任一满足即排除）：
     * 1. TimeLock 自身
     * 2. 命中已知系统应用前缀列表
     * 3. 是系统默认拨号器 / 短信 / 桌面
     * 4. 是通过 PackageManager 标记为系统应用
     */
    fun isExcludedPackage(packageName: String): Boolean {
        // 自身
        if (packageName == SELF_PACKAGE) return true

        // 已知系统前缀
        if (EXCLUDED_PREFIXES.any { packageName.startsWith(it) }) return true

        // 动态识别的系统关键应用（拨号/短信/桌面）
        if (packageName in getDynamicExcludedPackages()) return true

        // FLAG_SYSTEM 标记
        if (isSystemApp(packageName)) return true

        return false
    }

    /** 获取动态识别的排除包名集合（默认拨号器/短信/桌面，合并静态兜底列表） */
    private fun getDynamicExcludedPackages(): Set<String> {
        val pkgs = mutableSetOf<String>()
        getDefaultDialerPackage()?.let { pkgs.add(it) }
        getDefaultSmsPackage()?.let { pkgs.add(it) }
        getDefaultLauncherPackage()?.let { pkgs.add(it) }
        // 兜底：常见 ROM 自带系统包
        pkgs.addAll(
            listOf(
                "com.android.dialer",
                "com.google.android.dialer",
                "com.android.contacts",
                "com.android.mms",
                "com.google.android.apps.messaging",
                "com.android.incallui",
            )
        )
        return pkgs.filter { isPackageInstalled(it) }.toSet()
    }

    /** 获取系统默认电话应用包名 */
    fun getDefaultDialerPackage(): String? {
        val intent = Intent(Intent.ACTION_DIAL).apply {
            addCategory(Intent.CATEGORY_DEFAULT)
        }
        val resolveInfo = context.packageManager.resolveActivity(
            intent, PackageManager.MATCH_DEFAULT_ONLY
        )
        return resolveInfo?.activityInfo?.packageName
    }

    /** 获取系统默认短信应用包名 */
    fun getDefaultSmsPackage(): String? {
        return try {
            Telephony.Sms.getDefaultSmsPackage(context)
        } catch (_: Exception) { null }
    }

    /** 获取系统默认桌面（Launcher）包名 */
    fun getDefaultLauncherPackage(): String? {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
        }
        val resolveInfo = context.packageManager.resolveActivity(
            intent, PackageManager.MATCH_DEFAULT_ONLY
        )
        return resolveInfo?.activityInfo?.packageName
    }

    /** 通过 PackageManager 检测是否为系统应用 */
    private fun isSystemApp(packageName: String): Boolean {
        return try {
            val info = context.packageManager.getApplicationInfo(packageName, 0)
            (info.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    /**
     * 获取所有需要保护的系统关键应用包名列表（供一键白名单使用）。
     *
     * 包含：默认电话 + 默认短信 + 常见 ROM 自带拨号/短信包名
     */
    fun getSystemAppPackages(): List<String> {
        val packages = mutableListOf<String>()
        getDefaultDialerPackage()?.let { packages.add(it) }
        getDefaultSmsPackage()?.let { packages.add(it) }
        val fallbackPackages = listOf(
            "com.android.dialer",
            "com.google.android.dialer",
            "com.android.contacts",
            "com.android.mms",
            "com.google.android.apps.messaging",
            "com.android.incallui",
        )
        fallbackPackages.forEach { pkg ->
            if (isPackageInstalled(pkg)) packages.add(pkg)
        }
        return packages.distinct()
    }

    /** 根据包名获取应用显示名称（委托） */
    fun getAppName(packageName: String): String = appInfoProvider.getAppName(packageName)

    /** 检查应用是否已安装 */
    private fun isPackageInstalled(packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) { false }
    }
}
