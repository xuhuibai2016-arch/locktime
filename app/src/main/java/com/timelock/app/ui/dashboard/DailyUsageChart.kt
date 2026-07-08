package com.timelock.app.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 图表柱状颜色调色板 —— 与浅紫色主题协调 */
private val chartColors = listOf(
    Color(0xFF7C4DFF),
    Color(0xFFB388FF),
    Color(0xFF651FFF),
    Color(0xFF536DFE),
    Color(0xFF448AFF),
    Color(0xFF26C6DA),
    Color(0xFF66BB6A),
    Color(0xFFFFA726),
    Color(0xFFEF5350),
    Color(0xFFAB47BC),
)

/** 单条柱状高度 */
private val BAR_HEIGHT = 28.dp
/** 柱状间距 */
private val BAR_SPACING = 8.dp
/** 柱状圆角半径 */
private val BAR_RADIUS = 6.dp

/**
 * 今日使用时长分布水平柱状图。
 *
 * 使用 Compose Canvas 手绘，零额外依赖，完美适配项目
 * Material 3 浅紫色卡片风格。每行一条水平柱状，
 * 显示 App 名称（截断 8 字）和时长标签。
 *
 * @param items 按使用时长降序排列的图表数据
 * @param modifier 外部修饰符
 */
@Composable
fun DailyUsageChart(
    items: List<ChartItem>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFE8DEF8)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // ── 标题行 ──
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.BarChart,
                    contentDescription = null,
                    tint = Color(0xFF5E35B1),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "今日使用分布",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF4A148C)
                )
            }

            if (items.isEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "今日暂无使用记录",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF7E57C2)
                    )
                }
            } else {
                // ── 总计摘要 ──
                Spacer(modifier = Modifier.height(4.dp))
                val totalMinutes = items.sumOf { it.usedMinutes }
                Text(
                    text = "总计 ${formatMinutes(totalMinutes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF7E57C2)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // ── 图例：App 名称 + 水平柱状条 ──
                val maxMinutes = items.maxOf { it.usedMinutes }.coerceAtLeast(1)
                items.forEachIndexed { index, item ->
                    val color = chartColors[index % chartColors.size]
                    BarRow(
                        appName = item.appName,
                        usedMinutes = item.usedMinutes,
                        maxMinutes = maxMinutes,
                        color = color,
                        isOverLimit = item.isOverLimit
                    )
                    if (index < items.lastIndex) {
                        Spacer(modifier = Modifier.height(BAR_SPACING))
                    }
                }
            }
        }
    }
}

/**
 * 单行柱状条：App 名称 → 柱状条 → 时长标签。
 *
 * @param appName 应用显示名
 * @param usedMinutes 使用分钟数
 * @param maxMinutes 所有 App 中最大分钟数（用于归一化柱状宽度）
 * @param color 柱状填充色
 */
@Composable
private fun BarRow(
    appName: String,
    usedMinutes: Long,
    maxMinutes: Long,
    color: Color,
    isOverLimit: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左侧 App 名称（固定宽度，截断）
        Text(
            text = appName,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF4A148C),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(72.dp)
        )

        // 超限警告图标
        if (isOverLimit) {
            Icon(
                Icons.Default.Warning,
                contentDescription = "已超限",
                tint = Color(0xFFEF5350),
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
        }

        Spacer(modifier = Modifier.width(8.dp))

        // 中间柱状条（Canvas 绘制圆角矩形）
        Canvas(
            modifier = Modifier
                .weight(1f)
                .height(BAR_HEIGHT)
        ) {
            val fraction = (usedMinutes.toFloat() / maxMinutes).coerceIn(0.05f, 1f)
            val barWidth = size.width * fraction
            val barHeight = size.height

            // 背景轨道（浅灰紫）
            drawRoundRect(
                color = Color(0xFFD1C4E9),
                topLeft = Offset.Zero,
                size = Size(size.width, barHeight),
                cornerRadius = CornerRadius(BAR_RADIUS.toPx())
            )

            // 前景柱状（主题色）
            drawRoundRect(
                color = color,
                topLeft = Offset.Zero,
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(BAR_RADIUS.toPx())
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // 右侧时长标签
        Text(
            text = formatMinutesCompact(usedMinutes),
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF5E35B1),
            modifier = Modifier.width(48.dp),
            fontSize = 11.sp
        )
    }
}

/**
 * 完整格式化分钟数（用于摘要行）。
 *
 * @param minutes 分钟数
 * @return "X分钟" 或 "X小时Y分钟"
 */
private fun formatMinutes(minutes: Long): String {
    return if (minutes < 60) {
        "${minutes}分钟"
    } else {
        val h = minutes / 60
        val m = minutes % 60
        if (m == 0L) "${h}小时" else "${h}小时${m}分钟"
    }
}

/**
 * 紧凑格式化（用于柱状图标签）。
 *
 * @param minutes 分钟数
 * @return "Xm" 或 "X.Xh"
 */
private fun formatMinutesCompact(minutes: Long): String {
    return if (minutes < 60) {
        "${minutes}分钟"
    } else {
        val h = minutes / 60
        val m = minutes % 60
        if (m == 0L) "${h}h" else "${h}h${m}"
    }
}
