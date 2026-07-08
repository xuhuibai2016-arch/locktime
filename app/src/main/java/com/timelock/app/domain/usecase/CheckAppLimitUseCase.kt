package com.timelock.app.domain.usecase

import com.timelock.app.domain.repository.AppLimitRepository
import com.timelock.app.domain.repository.UsageStatsRepository
import javax.inject.Inject

/**
 * 检查应用是否达到每日使用限额。
 *
 * 判断逻辑：
 * 1. 白名单应用 → [CheckResult.Whitelisted] (永不锁)
 * 2. 未配置限额 → [CheckResult.NotConfigured] (不监控)
 * 3. 锁已禁用   → [CheckResult.Allowed] (仅监控不锁)
 * 4. 已超限额   → [CheckResult.Locked] (触发锁机)
 * 5. 未超限额   → [CheckResult.Allowed] (正常使用)
 */
class CheckAppLimitUseCase @Inject constructor(
    private val usageStatsRepository: UsageStatsRepository,
    private val appLimitRepository: AppLimitRepository
) {
    suspend operator fun invoke(packageName: String): CheckResult {
        val config = appLimitRepository.getConfig(packageName) ?: return CheckResult.NotConfigured

        // 白名单应用永不锁
        if (config.isWhitelisted) return CheckResult.Whitelisted

        // 锁已禁用，不限时
        if (!config.isLockEnabled) return CheckResult.Allowed(
            remainingMillis = Long.MAX_VALUE
        )

        val usedMillis = usageStatsRepository.getTodayUsage(packageName)
        val limitMillis = config.dailyLimitMinutes.toLong() * 60_000L
        val usedMinutes = usedMillis / 60_000L

        return if (usedMillis >= limitMillis) {
            CheckResult.Locked(
                limitMinutes = config.dailyLimitMinutes,
                usedMinutes = usedMinutes
            )
        } else {
            CheckResult.Allowed(remainingMillis = limitMillis - usedMillis)
        }
    }
}

/**
 * 限额检查结果密封类。
 */
sealed class CheckResult {
    /** 白名单 — 永不锁机 */
    data object Whitelisted : CheckResult()

    /** 未配置限额 — 不监控 */
    data object NotConfigured : CheckResult()

    /** 允许使用 — [remainingMillis] 为剩余可用时长 */
    data class Allowed(val remainingMillis: Long) : CheckResult()

    /** 已超限 — [limitMinutes] 限额, [usedMinutes] 已用时长(分钟) */
    data class Locked(val limitMinutes: Int, val usedMinutes: Long) : CheckResult()
}
