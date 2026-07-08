package com.timelock.app.ui.trend

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.timelock.app.ui.components.AppIcon

/** 折线图颜色调色板 */
private val trendColors = listOf(
    Color(0xFF7C4DFF),
    Color(0xFFEF5350),
    Color(0xFF26C6DA),
    Color(0xFFFFA726),
    Color(0xFF66BB6A),
)

// ── 图表常量 ──
private val CHART_HEIGHT = 160.dp
private val CHART_PADDING_H = 40.dp   // 左右留白给 Y 轴标签
private val CHART_PADDING_TOP = 16.dp  // 上方留白
private val CHART_PADDING_BOTTOM = 28.dp // 下方留白给 X 轴标签

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrendScreen(
    onBack: () -> Unit,
    viewModel: TrendViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("使用趋势") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                uiState.error != null -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(16.dp))
                        Text(uiState.error!!, color = MaterialTheme.colorScheme.error)
                    }
                }
                uiState.isEmpty -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.Schedule, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(64.dp))
                        Spacer(Modifier.height(16.dp))
                        Text("暂无 7 天使用数据", color = MaterialTheme.colorScheme.secondary)
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        itemsIndexed(uiState.appTrends) { index, trend ->
                            TrendCard(trend = trend, colorIndex = index)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrendCard(trend: AppTrendItem, colorIndex: Int) {
    val color = trendColors[colorIndex % trendColors.size]
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE8DEF8)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // ── 标题行：App 图标 + 名称 + 总时长 ──
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(packageName = trend.packageName, size = 32.dp)
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = trend.appName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4A148C)
                    )
                    Text(
                        text = "7 天合计 ${formatTrendMinutes(trend.totalMinutes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF7E57C2)
                    )
                }
                Icon(
                    Icons.Default.TrendingUp,
                    contentDescription = null,
                    tint = Color(0xFF5E35B1),
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(Modifier.height(12.dp))

            // ── 折线图 ──
            LineChart(
                dailyMinutes = trend.dailyMinutes,
                dates = trend.dates,
                color = color,
                modifier = Modifier.fillMaxWidth().height(CHART_HEIGHT)
            )
        }
    }
}

/**
 * Compose Canvas 手绘折线图。
 *
 * - X 轴：7 天日期标签
 * - Y 轴：分钟数，自适应刻度
 * - 数据点用小圆点标记，连线平滑
 */
@Composable
private fun LineChart(
    dailyMinutes: List<Long>,
    dates: List<String>,
    color: Color,
    modifier: Modifier = Modifier
) {
    if (dailyMinutes.isEmpty()) return

    val maxVal = dailyMinutes.max().coerceAtLeast(1).toFloat()
    val ySteps = listOf(0f, maxVal * 0.5f, maxVal) // 3 条 Y 轴参考线

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val padLeft = CHART_PADDING_H.toPx()
        val padRight = 8.dp.toPx()
        val padTop = CHART_PADDING_TOP.toPx()
        val padBottom = CHART_PADDING_BOTTOM.toPx()
        val chartW = w - padLeft - padRight
        val chartH = h - padTop - padBottom

        // ── Y 轴参考线 + 标签 ──
        val textPaint = android.graphics.Paint().apply {
            setColor(0xFF7E57C2.toInt())
            textSize = 10.sp.toPx()
            isAntiAlias = true
        }
        ySteps.forEach { yVal ->
            val y = padTop + chartH * (1f - yVal / maxVal)
            drawLine(
                color = Color(0xFFD1C4E9),
                start = Offset(padLeft, y),
                end = Offset(w - padRight, y),
                strokeWidth = 1.dp.toPx()
            )
            val label = if (yVal >= 60) "${(yVal / 60).toInt()}h" else "${yVal.toInt()}m"
            drawContext.canvas.nativeCanvas.drawText(
                label, 2.dp.toPx(), y + 4.dp.toPx(), textPaint
            )
        }

        if (dailyMinutes.size <= 1) return@Canvas

        // ── X 轴标签 ──
        val xTextPaint = android.graphics.Paint().apply {
            setColor(0xFF7E57C2.toInt())
            textSize = 9.sp.toPx()
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
        }
        dates.forEachIndexed { i, label ->
            val x = padLeft + chartW * i / (dailyMinutes.size - 1)
            drawContext.canvas.nativeCanvas.drawText(
                label, x, h - 2.dp.toPx(), xTextPaint
            )
        }

        // ── 数据点坐标 ──
        val points = dailyMinutes.mapIndexed { i, min ->
            val x = padLeft + chartW * i / (dailyMinutes.size - 1)
            val y = padTop + chartH * (1f - min.toFloat() / maxVal)
            Offset(x, y)
        }

        // ── 折线 ──
        val path = Path().apply {
            moveTo(points.first().x, points.first().y)
            points.drop(1).forEach { pt -> lineTo(pt.x, pt.y) }
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // ── 数据点圆点 ──
        points.forEach { pt ->
            drawCircle(color = color, radius = 4.dp.toPx(), center = pt)
            drawCircle(color = Color.White, radius = 2.dp.toPx(), center = pt)
        }

        // ── 渐变填充区域 (浅色) ──
        if (points.size >= 2) {
            val fillPath = Path().apply {
                moveTo(points.first().x, padTop + chartH)
                points.forEach { pt -> lineTo(pt.x, pt.y) }
                lineTo(points.last().x, padTop + chartH)
                close()
            }
            drawPath(
                path = fillPath,
                color = color.copy(alpha = 0.12f)
            )
        }
    }
}

private fun formatTrendMinutes(minutes: Long): String {
    return if (minutes < 60) "${minutes}分钟"
    else {
        val h = minutes / 60
        val m = minutes % 60
        if (m == 0L) "${h}小时" else "${h}小时${m}分钟"
    }
}
