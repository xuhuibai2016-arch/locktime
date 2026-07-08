package com.timelock.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 应用使用记录表。
 *
 * 每一条记录代表一次前台使用会话（从进入前台到退出前台）。
 * 底层存储精度：毫秒 (epoch millis)；UI 交互精度：分钟。
 *
 * 索引策略：
 * - [date] 列上建索引以加速日/周/月聚合查询
 * - [package_name, start_time] 复合索引用于去重查询
 */
@Entity(
    tableName = "app_usage_records",
    indices = [
        Index(value = ["date"]),
        Index(value = ["package_name", "start_time"]),
        Index(value = ["package_name", "date"], unique = true)
    ]
)
data class AppUsageRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "package_name")
    val packageName: String,

    @ColumnInfo(name = "app_name")
    val appName: String,

    /** 会话开始时间 (epoch millis, UTC) */
    @ColumnInfo(name = "start_time")
    val startTime: Long,

    /** 会话结束时间 (epoch millis, UTC)；进行中的会话设为 [Long.MAX_VALUE] */
    @ColumnInfo(name = "end_time")
    val endTime: Long,

    /** 本次会话时长 (毫秒)；进行中的会话设为 0 */
    @ColumnInfo(name = "duration")
    val duration: Long,

    /** 日期键 (yyyy-MM-dd)，用于日聚合查询 */
    @ColumnInfo(name = "date")
    val date: String
)
