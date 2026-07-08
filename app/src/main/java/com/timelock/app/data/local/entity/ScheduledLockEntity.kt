package com.timelock.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 定时锁屏配置表 (单行: id=1)。
 *
 * @property lockHour 锁屏开始小时 (0-23)
 * @property lockMinute 锁屏开始分钟 (0-59)
 * @property lockDurationMinutes 锁屏持续分钟数
 * @property enabled 是否启用定时锁屏
 */
@Entity(tableName = "scheduled_lock_config")
data class ScheduledLockEntity(
    @PrimaryKey
    val id: Int = 1,

    @ColumnInfo(name = "lock_hour")
    val lockHour: Int = 21,

    @ColumnInfo(name = "lock_minute")
    val lockMinute: Int = 30,

    @ColumnInfo(name = "lock_duration_minutes")
    val lockDurationMinutes: Int = 180,

    @ColumnInfo(name = "enabled")
    val enabled: Boolean = false
)
