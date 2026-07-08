package com.timelock.app.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 国产手机保活设置跳转辅助类。
 *
 * 针对主流品牌（华为、小米、OPPO、vivo）提供对应的
 * 系统设置页跳转 Intent，以及当前设备品牌检测。
 */
@Singleton
class OemGuideHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /** 当前设备品牌名称（用于 UI 高亮） */
    val currentBrand: String
        get() = Build.BRAND.lowercase()

    /** 当前设备制造商名称 */
    val currentManufacturer: String
        get() = Build.MANUFACTURER.lowercase()

    /** 是否为主流国产 ROM */
    fun isChineseOem(): Boolean {
        val brand = currentBrand
        return brand in listOf("xiaomi", "redmi", "huawei", "honor", "oppo", "vivo", "iqoo", "oneplus", "realme")
    }

    /**
     * 跳转到应用详情页（通用兜底）。
     * 所有品牌均支持此入口，用户可在此页面手动开启自启动/权限。
     */
    fun openAppSettings(): Intent {
        return Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}")
        )
    }

    /**
     * 跳转到电池优化白名单页（通用兜底）。
     * Android 6+ 原生支持，但国产 ROM 可能被替换。
     */
    fun openBatteryOptimization(): Intent {
        return Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
    }

    // ── 小米 (MIUI / HyperOS) ──────────────────────────────────

    /** 小米自启动管理页 */
    fun openXiaomiAutoStart(): Intent? {
        return tryOpenIntent("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
    }

    /** 小米省电策略/后台管理页 */
    fun openXiaomiPowerKeeper(): Intent? {
        return tryOpenIntent("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsContainerManagementActivity")
            ?: tryOpenIntent("com.miui.securitycenter", "com.miui.powerkeeper.PowerKeeperActivity")
    }

    // ── 华为 (EMUI / HarmonyOS) ────────────────────────────────

    /** 华为自启动管理页 */
    fun openHuaweiAutoStart(): Intent? {
        return tryOpenIntent("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity")
            ?: tryOpenIntent("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")
    }

    /** 华为应用启动管理页 */
    fun openHuaweiLaunch(): Intent? {
        return tryOpenIntent("com.huawei.systemmanager", "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity")
    }

    // ── OPPO (ColorOS) ─────────────────────────────────────────

    /** OPPO 自启动管理页 */
    fun openOppoAutoStart(): Intent? {
        return tryOpenIntent("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity")
            ?: tryOpenIntent("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity")
    }

    /** OPPO 后台冻结管理页 */
    fun openOppoBackgroundFreeze(): Intent? {
        return tryOpenIntent("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.FakeActivity")
            ?: tryOpenIntent("com.oppo.safe", "com.oppo.safe.permission.startup.FakeActivity")
    }

    // ── vivo (Funtouch OS / OriginOS) ──────────────────────────

    /** vivo 自启动/i管家 */
    fun openVivoAutoStart(): Intent? {
        return tryOpenIntent("com.iqoo.secure", "com.iqoo.secure.MainActivity")
            ?: tryOpenIntent("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")
    }

    /** vivo 后台高耗电管理 */
    fun openVivoPower(): Intent? {
        return tryOpenIntent("com.vivo.abe", "com.vivo.abe.powersaver.PowerSaverActivity")
            ?: tryOpenIntent("com.iqoo.powersaving", "com.iqoo.powersaving.PowerSavingActivity")
    }

    // ── 内部工具 ────────────────────────────────────────────────

    /**
     * 尝试创建跳转到指定 Activity 的 Intent。
     * 如果目标 Activity 不存在（未安装该 ROM 组件），返回 null。
     */
    private fun tryOpenIntent(packageName: String, className: String): Intent? {
        val intent = Intent().apply {
            setClassName(packageName, className)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return if (intent.resolveActivity(context.packageManager) != null) {
            intent
        } else {
            null
        }
    }
}
