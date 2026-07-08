# TimeLock — Android App Usage Limiter

[![Android](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.22-blue.svg)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-BOM%202024.02-purple.svg)](https://developer.android.com/compose)
[![minSdk](https://img.shields.io/badge/minSdk-26-orange.svg)](https://developer.android.com)
[![License](https://img.shields.io/badge/License-MIT-lightgrey.svg)](LICENSE)

TimeLock 是一个 Android 应用使用时长限制工具。用户可以为任意 APP 设置每日使用限额，超限后自动弹出全屏锁屏界面阻止继续使用，帮助控制手机使用时间。

---

## 功能列表

### 📱 应用时长管理
- **每日限额设置** — 为每个 APP 单独设置每日使用时长限额（分钟）
- **实时监控** — 三层检测机制，实时追踪前台应用使用时长
- **白名单机制** — 支持将系统应用（电话、短信）等加入白名单，永不锁定
- **一键添加系统应用保护** — 自动将电话/短信等关键系统应用加入白名单

### 🔒 锁屏机制
- **全屏悬浮窗锁屏** — 超限后通过 WindowManager TYPE_APPLICATION_OVERLAY 全屏覆盖目标应用
- **一键锁屏** — 手动触发全局锁屏，设置 N 分钟后自动解锁
- **定时锁屏** — 每日定时自动锁屏（如每晚 23:00），帮助规律作息
- **紧急解锁** — 长按 3 秒紧急解锁（每日限 2 次），解锁后 5 分钟宽限期
- **返回桌面** — 超限后提供返回桌面按钮，防抖 3 秒避免重复弹出

### 📊 数据可视化
- **今日使用概览** — 圆形进度图展示今日各 APP 使用时长分布
- **7 天 Top 5** — 一周内使用时间最长的 5 个 APP 排行
- **使用趋势折线图** — 每个 APP 近 7 天每日使用时长折线图

### 🛡️ MIUI 深度适配
- **省电策略绕过** — 前台服务 + AlarmManager 精准闹钟保障后台存活
- **Watchdog 哨兵机制** — 全局锁屏模式下主动检测 overlay 是否被系统移除并自动重建
- **沉浸模式守���** — 全局锁屏强制隐藏状态栏/导航栏，防止手势绕过
- **开机自启** — 开机广播自动重新安排定时锁屏闹钟

### 🔧 其他
- **应用搜索** — 在配置页面搜索已安装应用
- **保活指南** — 内置各厂商（MIUI/OPPO/vivo 等）省电策略设置引导
- **APP 安装/卸载自动更新** — 监听 `PACKAGE_ADDED` / `PACKAGE_REMOVED` 广播增量更新缓存

---

## 架构

### 整体数据流

```
                    ┌── LockAccessibilityService ──┐
                    │  TYPE_WINDOW_STATE_CHANGED    │
                    │  (实时检测, MIUI上受限)        │
                    └──────────┬────────────────────┘
                               │
                    ┌──────────▼────────────────────┐
                    │    LockMonitorService          │
                    │    UsageStats polling 每4秒     │
                    │    + startForeground() 前台服务 │
                    └──────────┬────────────────────┘
                               │
                    ┌──────────▼────────────────────┐
                    │    LockCheckReceiver            │
                    │    AlarmManager 每30秒          │
                    │    UsageStats + UsageEvents     │
                    │    + ActivityManager            │
                    └──────────┬────────────────────┘
                               │
                    ┌──────────▼────────────────────┐
                    │    CheckAppLimitUseCase         │
                    │    → Locked / Allowed /         │
                    │      Whitelisted / NotConfigured │
                    └──────────┬────────────────────┘
                               │ (Locked)
                    ┌──────────▼────────────────────┐
                    │  LockScreenDispatcher.show()    │
                    │  1. 检查 cooldown / debounce    │
                    │  2. LockOverlayService.start()  │
                    └──────────┬────────────────────┘
                               │
                    ┌──────────▼────────────────────┐
                    │  LockOverlayService             │
                    │  TYPE_APPLICATION_OVERLAY 悬浮窗 │
                    │  直接覆盖在目标 app 之上          │
                    └────────────────────────────────┘
```

### 三层检测机制

| 层级 | 组件 | 机制 | 频率 | MIUI 兼容性 |
|------|------|------|------|-------------|
| 1 | `LockAccessibilityService` | `TYPE_WINDOW_STATE_CHANGED` | 实时 | ⚠️ 仅收到 systemui/miui.home |
| 2 | `LockMonitorService` | `UsageStats.queryUsageStats()` | 每 4 秒 | ✅ 数据可用 |
| 3 | `LockCheckReceiver` | `AlarmManager` 精准唤醒 | 每 30 秒 | ✅ 进程死亡后仍唤醒 |

### 设计模式

- **Clean Architecture** 分层结构：`data` → `domain` → `ui` / `service`
- **MVVM** — ViewModel + Compose UI
- **Hilt DI** — 依赖注入，包括 Service 层通过 `EntryPointAccessors` 注入
- **Repository Pattern** — 数据访问通过 Repository 接口隔离
- **UseCase Pattern** — 核心业务逻辑封装为独立 UseCase

---

## 技术栈

| 类型 | 技术 | 版本 |
|------|------|------|
| 语言 | Kotlin | 1.9.22 |
| UI | Jetpack Compose + Material 3 | BOM 2024.02 |
| DI | Hilt (Dagger) | 2.50 |
| 数据库 | Room | 2.6.1 |
| 持久化 | DataStore Preferences | 1.0.0 |
| 导航 | Navigation Compose | — |
| 异步 | Kotlin Coroutines | 1.7.3 |
| 构建 | Gradle + AGP | 8.2.2 |
| 测试 | JUnit + MockK | 4.13.2 / 1.13.9 |
| KSP | 注解处理 | 1.9.22-1.0.17 |

---

## 编译环境

### 系统要求

- **操作系统**: Windows / macOS / Linux
- **JDK**: 17+ (推荐 JetBrains Runtime 21，位于 Android Studio 安装目录 `jbr/`)
- **Android SDK**: API 34, Build Tools 34+
- **Gradle**: 8.14.5 (项目自带 wrapper，无需手动安装)

### 环境变量配置

```bash
# Windows (Git Bash)
export JAVA_HOME="D:/Program Files/Android/Android Studio/jbr"
export ANDROID_HOME="D:/software/android-sdk"

# macOS
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"

# Linux
export JAVA_HOME="/opt/android-studio/jbr"
export ANDROID_HOME="$HOME/Android/Sdk"
```

### 编译命令

```bash
# 克隆项目
git clone https://github.com/YOUR_USERNAME/TimeLock.git
cd TimeLock

# Debug 构建
./gradlew :app:assembleDebug

# Release 构建 (开启混淆)
./gradlew :app:assembleRelease

# 生成的 APK 位置
# Debug:  app/build/outputs/apk/debug/app-debug.apk
# Release: app/build/outputs/apk/release/app-release.apk
```

### 安装到设备

```bash
# 通过 ADB 安装
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 测试命令

```bash
# 运行单元测试
./gradlew :app:test

# 运行 Instrumented 测试
./gradlew :app:connectedAndroidTest
```

---

## 所需权限

安装后需要在系统设置中手动开启以下权限：

| 权限 | 用途 | 设置路径 |
|------|------|----------|
| 使用情况访问 | 读取 APP 使用时长 | 设置 → 应用 → 特殊权限 → 使用情况访问 |
| 悬浮窗 | 超限后全屏覆盖锁定 | 设置 → 应用 → 特殊权限 → 悬浮窗 |
| 无障碍服务 | 实时前台 APP 检测 | 设置 → 更多设置 → 无障碍 → TimeLock |
| 通知权限 | 前台监控持久通知 | 设置 → 应用 → TimeLock → 通知 |

> **⚠️ MIUI 用户必读**: 必须在「设置 → 应用管理 → TimeLock → 省电策略」中选择 **「无限制」**，否则 MIUI SmartPower 会在 APP 进入后台几秒后冻结进程，导致所有轮询和闹钟停止工作。

---

## 项目结构

```
app/src/main/java/com/timelock/app/
├── MainActivity.kt                    — 主 Activity（双模式: 正常锁屏）
├── TimeLockApp.kt                     — Hilt Application 入口
│
├── data/local/                        — ★ 数据层
│   ├── AppDatabase.kt                 — Room Database (v5, 4 张表)
│   ├── dao/
│   │   ├── AppLimitConfigDao.kt       — 限额配置 DAO
│   │   ├── AppUsageRecordDao.kt       — 使用记录 DAO
│   │   ├── InstalledAppDao.kt         — 已安装应用缓存 DAO
│   │   └── ScheduledLockDao.kt        — 定时锁屏配置 DAO
│   └── entity/                        — Room Entity 定义
│
├── data/repository/                   — Repository 实现
│   ├── AppLimitRepositoryImpl.kt
│   └── UsageStatsRepositoryImpl.kt
│
├── domain/                            — ★ 领域层
│   ├── model/                         — 领域模型
│   ├── repository/                    — Repository 接口
│   └── usecase/
│       ├── CheckAppLimitUseCase.kt    — 超限判断核心逻辑
│       └── AddSystemAppsToWhitelistUseCase.kt
│
├── di/                                — ★ 依赖注入
│   ├── AppModule.kt                   — Room Database + DAO 提供
│   ├── RepositoryModule.kt            — Repository 绑定
│   ├── ServiceEntryPoint.kt           — Service/BroadcastReceiver 统一入口
│   └── UtilModule.kt                  — 工具类提供
│
├── service/                           — ★ 锁屏服务层（核心）
│   ├── LockAccessibilityService.kt     — 无障碍服务（实时 APP 切换）
│   ├── LockMonitorService.kt          — 前台服务 + UsageStats polling
│   ├── LockMonitorEngine.kt           — 锁机检查引擎
│   ├── LockCheckReceiver.kt           — AlarmManager 广播接收器
│   ├── LockOverlayService.kt          — WindowManager 悬浮窗锁屏 ★
│   ├── LockScreenDispatcher.kt        — 统一锁屏调度器
│   ├── LockStateManager.kt            — 状态管理（冷却/防抖/解锁计数）
│   ├── ScheduledLockReceiver.kt       — 定时锁屏闹钟接收器
│   ├── BootReceiver.kt                — 开机广播接收器
│   └── ScreenStateReceiver.kt         — 熄屏/亮屏广播
│
├── ui/                                — ★ Compose UI
│   ├── navigation/TimeLockNavHost.kt  — 导航图
│   ├── components/AppIcon.kt          — 应用图标组件
│   ├── theme/Theme.kt                 — Material 3 主题
│   ├── dashboard/                     — 首页（使用概览/图表/一键锁屏）
│   ├── config/                        — 限额配置页（搜索/设置/白名单）
│   ├── lock/LockOverlayScreen.kt      — 全屏锁屏 UI（Compose）
│   ├── trend/TrendScreen.kt           — 7 天使用趋势折线图
│   ├── permission/PermissionGate.kt   — 4 项权限引导页
│   └── guide/KeepAliveGuideScreen.kt  — 保活指南
│
└── util/                              — 工具类
    ├── PermissionHelper.kt            — 权限检测与引导
    ├── AppInfoProvider.kt             — 应用名/图标解析
    ├── SystemAppHelper.kt             — 系统应用识别
    ├── OemGuideHelper.kt              — 各厂商省电策略引导
    ├── PackageChangeReceiver.kt       — APP 安装/卸载广播
    └── Constants.kt                   — 全局常量
```

---

## 已知问题

### MIUI 后台进程冻结

MIUI SmartPower 会在 APP 进入后台几秒后冻结整个进程，冻结后轮询协程、AlarmManager、无障碍服务全部停止。

**解决方法**:
1. 设置 → 应用管理 → TimeLock → 省电策略 → 无限制
2. 设置 → 应用管理 → TimeLock → 自启动 → 开启
3. 或者在 ADB 中执行以下命令：
```bash
adb shell cmd appops set com.timelock.app RUN_ANY_IN_BACKGROUND allow
adb shell dumpsys deviceidle whitelist +com.timelock.app
```

### AccessibilityService 在 MIUI 上受限

MIUI 的无障碍服务只会收到 `systemui` 和 `miui.home` 的窗口变化事件，无法检测第三方 APP 前台切换。已通过 UsageStats polling 和 AlarmManager 作为补充。

---

## 测试设备

| 型号 | 系统 | 备注 |
|------|------|------|
| Xiaomi 23054RA19C (pearl) | MIUI, Android 14 | 主要测试设备 |
| Android Emulator | Android 14 (API 34) | CI 测试 |

---

## License

MIT License — 详见 [LICENSE](LICENSE) 文件。
