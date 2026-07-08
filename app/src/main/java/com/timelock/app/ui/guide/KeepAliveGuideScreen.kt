package com.timelock.app.ui.guide

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 品牌保活指引数据。
 *
 * @param brandName 品牌显示名
 * @param brandKey 品牌标识（用于匹配设备）
 * @param steps 操作步骤列表（纯文本，通俗易懂）
 * @param intents 可跳转的设置页入口列表（null 表示不可用）
 */
private data class BrandGuide(
    val brandName: String,
    val brandKey: List<String>,
    val steps: List<String>,
    val intents: List<GuideIntent>
)

private data class GuideIntent(
    val label: String,
    val intentProvider: () -> Intent?
)

/**
 * 保活指南页面。
 *
 * 折叠面板形式展示华为、小米、OPPO、vivo 四大主流品牌
 * 的保活设置步骤，提供一键跳转到对应系统设置页的按钮。
 * 当前设备品牌自动展开并高亮。
 *
 * @param onBack 返回上一页
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun KeepAliveGuideScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("保活指南") },
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── 顶部说明卡片 ──
            item {
                InfoHeaderCard()
            }

            // ── 各品牌折叠面板 ──
            val deviceBrand = android.os.Build.BRAND.lowercase()
            val guides = buildBrandGuides()
            items(guides.size) { index ->
                val guide = guides[index]
                val isCurrentDevice = guide.brandKey.any { deviceBrand.contains(it) }
                BrandGuidePanel(
                    guide = guide,
                    isCurrentDevice = isCurrentDevice,
                    initiallyExpanded = isCurrentDevice,
                    onOpenIntent = { intent ->
                        intent?.let {
                            try {
                                context.startActivity(it)
                            } catch (_: Exception) {
                                // 无法跳转，静默忽略
                            }
                        }
                    }
                )
            }

            // ── 底部通用提示 ──
            item {
                GenericTipsCard()
            }
        }
    }
}

@Composable
private fun InfoHeaderCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE8DEF8)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = Color(0xFF5E35B1),
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "为什么要设置保活？",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF4A148C)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "国产手机系统会限制后台应用运行，导致 TimeLock 锁屏服务被杀死。\n" +
                            "请根据你的手机品牌，按下方步骤开启自启动和后台运行权限。\n" +
                            "你的设备品牌：${android.os.Build.BRAND} ${android.os.Build.MODEL}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF4A148C),
                    lineHeight = 20.sp
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BrandGuidePanel(
    guide: BrandGuide,
    isCurrentDevice: Boolean,
    initiallyExpanded: Boolean,
    onOpenIntent: (Intent?) -> Unit
) {
    var isExpanded by remember { mutableStateOf(initiallyExpanded) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrentDevice) Color(0xFFEDE7F6) else Color(0xFFF5F5F5)
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isCurrentDevice) 3.dp else 1.dp
        )
    ) {
        Column {
            // ── 标题行（可点击展开/折叠）──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = guide.brandName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isCurrentDevice) Color(0xFF4A148C)
                            else Color(0xFF424242)
                        )
                        if (isCurrentDevice) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "当前设备",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF7C4DFF),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess
                    else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "收起" else "展开",
                    tint = Color(0xFF757575)
                )
            }

            // ── 展开内容 ──
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                ) {
                    // 步骤列表
                    guide.steps.forEachIndexed { index, step ->
                        Row(
                            modifier = Modifier.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = "${index + 1}.",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF7C4DFF),
                                modifier = Modifier.width(20.dp)
                            )
                            Text(
                                text = step,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF424242),
                                lineHeight = 18.sp,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // 跳转按钮
                    if (guide.intents.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            guide.intents.forEach { guideIntent ->
                                val intent = remember { guideIntent.intentProvider() }
                                FilledTonalButton(
                                    onClick = { onOpenIntent(intent) },
                                    enabled = intent != null
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.OpenInNew,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = guideIntent.label,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GenericTipsCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE8DEF8)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF4CAF50),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "通用技巧",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF4A148C)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "1. 将 TimeLock 图标长按 → 应用信息 → 开启「自启动」和「后台活动」\n" +
                        "2. 在多任务界面将 TimeLock 下拉锁定，防止被一键清理\n" +
                        "3. 关闭省电模式，或将 TimeLock 设为「不限制」\n" +
                        "4. 确保已在系统设置中授予「使用情况访问权限」",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF4A148C),
                lineHeight = 20.sp
            )
        }
    }
}

/**
 * 构建四大品牌保活指引数据。
 */
private fun buildBrandGuides(): List<BrandGuide> = listOf(
    BrandGuide(
        brandName = "小米 / Redmi (MIUI / HyperOS)",
        brandKey = listOf("xiaomi", "redmi"),
        steps = listOf(
            "打开「手机管家」App",
            "点击「应用管理」→「权限」→「自启动管理」",
            "找到 TimeLock → 开启「自启动」",
            "返回「设置」→「应用管理」→ TimeLock",
            "「省电策略」→ 选择「无限制」",
            "在「最近任务」中，长按 TimeLock 卡片 → 点击锁定图标"
        ),
        intents = listOf(
            GuideIntent("自启动管理") {
                // 使用 OEM helper 尝试
                tryPackageIntent("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
            }
        )
    ),
    BrandGuide(
        brandName = "华为 / 荣耀 (EMUI / HarmonyOS)",
        brandKey = listOf("huawei", "honor"),
        steps = listOf(
            "打开「手机管家」App",
            "点击「应用启动管理」",
            "找到 TimeLock → 关闭「自动管理」→ 手动三项全开",
            "返回「设置」→「应用」→「应用启动管理」→ TimeLock",
            "开启「自启动」「关联启动」「后台活动」",
            "「设置」→「电池」→「应用耗电管理」→ 取消 TimeLock 的优化"
        ),
        intents = listOf(
            GuideIntent("启动管理") {
                tryPackageIntent("com.huawei.systemmanager", "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity")
            }
        )
    ),
    BrandGuide(
        brandName = "OPPO / 一加 (ColorOS / OxygenOS)",
        brandKey = listOf("oppo", "oneplus", "realme"),
        steps = listOf(
            "打开「设置」→「应用」→「自启动管理」",
            "找到 TimeLock → 开启「允许自启动」",
            "返回「设置」→「电池」→「应用耗电管理」",
            "找到 TimeLock → 选择「允许后台运行」",
            "在「最近任务」中，长按 TimeLock → 点击锁头图标",
            "「设置」→「关于手机」→ 连续点击版本号 → 开发者选项中关闭「不保留活动」"
        ),
        intents = listOf(
            GuideIntent("自启动管理") {
                tryPackageIntent("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity")
            }
        )
    ),
    BrandGuide(
        brandName = "vivo / iQOO (Funtouch OS / OriginOS)",
        brandKey = listOf("vivo", "iqoo"),
        steps = listOf(
            "打开「i管家」App",
            "点击「实用工具」→「自启动管理」",
            "找到 TimeLock → 开启「允许自启动」",
            "返回「设置」→「电池」→「后台高耗电」",
            "找到 TimeLock → 开启「允许高耗电时继续运行」",
            "在「最近任务」中，下拉 TimeLock 卡片锁定"
        ),
        intents = listOf(
            GuideIntent("i管家（自启动）") {
                tryPackageIntent("com.iqoo.secure", "com.iqoo.secure.MainActivity")
            }
        )
    )
)

/**
 * 安全地尝试创建跳转 Intent，不存在则返回 null。
 */
private fun tryPackageIntent(packageName: String, className: String): Intent? {
    return try {
        val intent = Intent().apply {
            setClassName(packageName, className)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        intent
    } catch (_: Exception) {
        null
    }
}
