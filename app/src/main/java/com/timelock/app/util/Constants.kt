package com.timelock.app.util

/**
 * 全局常量定义 — 集中管理所有魔法数字。
 *
 * 按类别分组：检测、锁屏、通知、同步。
 */
object Constants {

    // ── 检测 ──────────────────────────────────────────────

    /** 前台检测去重窗口 (毫秒) */
    const val DETECTION_DEBOUNCE_MS = 3_000L

    /** AlarmManager 保底检测间隔 (毫秒) */
    const val ALARM_CHECK_INTERVAL_MS = 30_000L

    /** Foreground Service 轮询间隔 (毫秒) */
    const val POLLING_INTERVAL_MS = 4_000L

    /** UsageStats 查询窗口 (毫秒) */
    const val USAGE_STATS_QUERY_WINDOW_MS = 90_000L

    /** UsageEvents 查询窗口 (毫秒) */
    const val USAGE_EVENTS_QUERY_WINDOW_MS = 10_000L

    /** AccessibilityService 事件通知超时 (毫秒) */
    const val ACCESSIBILITY_NOTIFICATION_TIMEOUT_MS = 100L

    // ── 锁屏覆盖层 ────────────────────────────────────────

    /** 紧急解锁后冷却时间 (毫秒) */
    const val UNLOCK_COOLDOWN_MS = 30_000L

    /** 返回桌面防抖窗口 (毫秒) */
    const val DISMISS_DEBOUNCE_MS = 3_000L

    /** 每日最大紧急解锁次数 */
    const val MAX_UNLOCK_COUNT = 2

    /** 解锁后宽限分钟数 */
    const val GRACE_MINUTES = 5L

    /** 全局锁屏解锁后宽限期 (毫秒) */
    const val GLOBAL_GRACE_MS = 5 * 60 * 1000L

    /** Watchdog 哨兵检测间隔 (毫秒) */
    const val WATCHDOG_INTERVAL_MS = 5_000L

    /** 边缘触摸拦截高度 (dp) */
    const val EDGE_INTERCEPT_DP = 80

    // ── 同步 ──────────────────────────────────────────────

    /** 数据同步频率 (每 N 次 Alarm 触发同步一次) */
    const val SYNC_INTERVAL_COUNT = 12

    // ── 通知 ──────────────────────────────────────────────

    /** 前台服务通知 ID */
    const val MONITOR_NOTIFICATION_ID = 1001

    /** 监控通知渠道 ID */
    const val MONITOR_CHANNEL_ID = "lock_monitor_channel"
}
