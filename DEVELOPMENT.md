# TimeLock 开发文档

## 编译与安装

### 环境变量

```bash
export JAVA_HOME="D:/Program Files/Android/Android Studio/jbr"
export ANDROID_HOME="D:/software/android-sdk"
```

### 构建

```bash
cd "E:/tmp/lock"
./gradlew :app:assembleDebug
```

### 安装到设备

```bash
"D:/software/android-sdk/platform-tools/adb" install -r "E:/tmp/lock/app/build/outputs/apk/debug/app-debug.apk"
```

### 测试设备

- **型号**: Xiaomi 23054RA19C (pearl)
- **系统**: MIUI, Android 14

---

## 锁屏方案架构

### 整体数据流

```
                    ┌── LockAccessibilityService ──┐
                    │  TYPE_WINDOW_STATE_CHANGED    │
                    │  (实时, MIUI上受限)            │
                    └──────────┬────────────────────┘
                               │
                    ┌──────────▼────────────────────┐
                    │    LockMonitorService          │
                    │    UsageStats polling 每2秒     │
                    │    + startForeground() 前台服务 │
                    └──────────┬────────────────────┘
                               │
                    ┌──────────▼────────────────────┐
                    │    LockCheckReceiver            │
                    │    AlarmManager 每5秒           │
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
                    │  1. 解析 appName                │
                    │  2. 检查 cooldown/debounce      │
                    │  3. LockOverlayService.start()  │
                    └──────────┬────────────────────┘
                               │
                    ┌──────────▼────────────────────┐
                    │  LockOverlayService             │
                    │  TYPE_APPLICATION_OVERLAY 悬浮窗 │
                    │  直接覆盖在目标 app 之上          │
                    └────────────────────────────────┘
```

### 三层检测

| 层级 | 组件 | 机制 | 频率 | MIUI 兼容性 |
|------|------|------|------|-------------|
| 1 | `LockAccessibilityService` | `TYPE_WINDOW_STATE_CHANGED` | 实时 | ❌ 只收到 systemui/miui.home |
| 2 | `LockMonitorService` | `UsageStats.queryUsageStats()` | 每2秒 | ✅ 数据可用 |
| 3 | `LockCheckReceiver` | `AlarmManager` 精准唤醒 | 每5秒 | ✅ 进程死亡后仍唤醒 |

### 锁屏触发 — WindowManager 悬浮窗方案 (微信通话界面模式)

**原理**: 通过 `TYPE_APPLICATION_OVERLAY` 悬浮窗直接覆盖在目标应用之上，类似微信来电界面，不依赖通知系统。

**实现细节** (`LockScreenDispatcher.kt` + `LockOverlayService.kt`):

```kotlin
// 1. 解析应用显示名称 (通过 AppInfoProvider)
val appName = appInfoProvider.getAppName(packageName)

// 2. 启动悬浮窗服务 (检查 cooldown 和 debounce)
LockOverlayService.start(context, packageName, appName, limitMinutes, usedMinutes)

// 3. LockOverlayService 创建 TYPE_APPLICATION_OVERLAY 全屏悬浮窗
val params = WindowManager.LayoutParams(
    MATCH_PARENT, MATCH_PARENT,
    TYPE_APPLICATION_OVERLAY,
    FLAG_NOT_TOUCH_MODAL or FLAG_LAYOUT_IN_SCREEN or FLAG_FULLSCREEN,
    PixelFormat.OPAQUE
)
windowManager.addView(composeView, params)
```

**防抖机制**: 用户点“返回桌面”后 3 秒内不会重新弹出覆盖层，防止 UsageStats 延迟数据导致覆盖层被立即重建。

### 锁屏 UI — Compose 悬浮窗

`LockOverlayService` 通过 `ComposeView` 渲染 `LockOverlayScreen`:
- 全屏覆盖目标应用
- 显示应用名称、已用/限额时间
- 「返回桌面」按钮 → 通过 `HomeProxyActivity` 跳转
- 长按 3 秒紧急解锁 → 30 秒冷却

### 紧急解锁

- 锁屏界面长按 3 秒 → 覆盖层移除 + 30 秒冷却
- 冷却信息存储在 `SharedPreferences`，`LockOverlayService.isInCooldown()` 检查
- 「返回桌面」后记录 dismiss 时间，3 秒防抖窗口内不会重新弹出

---

## 测试操作流程

### 前置准备

确保以下权限已在系统设置中手动开启:

| 权限 | 设置路径 |
|------|----------|
| 使用情况访问权限 | 设置 → 应用 → 特殊权限 → 使用情况访问 |
| 悬浮窗权限 | 设置 → 应用 → 特殊权限 → 悬浮窗 |
| 无障碍服务 | 设置 → 更多设置 → 无障碍 → TimeLock |
| 通知权限 | 设置 → 应用 → TimeLock → 通知 |
| **省电策略** | **设置 → 应用管理 → TimeLock → 省电策略 → 无限制** |
| 自启动 | 设置 → 应用管理 → TimeLock → 自启动 |

> **重要**: MIUI 的 SmartPower 会在 app 进入后台 3 秒后冻结整个进程（包括前台服务、协程、AlarmManager）。
> 必须将省电策略设为「无限制」，否则轮询和闹钟都会被静默杀死。

### 测试步骤

**1. 启动并配置限制**

```bash
# 启动应用
"D:/software/android-sdk/platform-tools/adb" shell am start -n com.timelock.app/.MainActivity
```

在界面中为测试目标 APP（如 Telegram）设置一个极低的限额（如 1 分钟），然后正常使用该 APP 超过 1 分钟。

**2. 观察日志**

```bash
# 清空旧日志
"D:/software/android-sdk/platform-tools/adb" logcat -c

# 实时监控关键 tag
"D:/software/android-sdk/platform-tools/adb" logcat -s LockScreenDispatcher:LockMonitorService:LockAccessibility:LockCheckReceiver:LockMonitorEngine
```

**3. 验证检查点**

| 检查项 | 预期日志 | 命令 |
|--------|----------|------|
| 前台检测 | `polling: detected <pkg>, checking...` | 自动 |
| 超限判断 | `checkApp(<pkg>) → LOCKED` | 自动 |
| 触发锁屏 | `LockScreenDispatcher: show: <pkg>` | 自动 |
| 前台服务 | `LockMonitorService: onStartCommand` | 自动 |
| 权限状态 | 各 MIUIOP 显示 `allow` | `adb shell cmd appops get com.timelock.app` |

**4. 强制停止/清理**

```bash
# 强制停止（通知会消失）
"D:/software/android-sdk/platform-tools/adb" shell am force-stop com.timelock.app

# 注意: force-stop 后 AlarmManager 仍会在 5 秒后唤醒（manifest 注册的 receiver）
```

### 已知问题

**MIUI 后台进程冻结**: MIUI SmartPower 会在 app 进入后台几秒后冻结整个进程。必须在 MIUI 设置中将 TimeLock 的省电策略设为「无限制」，否则轮询协程、AlarmManager、无障碍服务全部停止工作。

**日志表现** (进程被冻结):
```
SmartPower: com.timelock.app: background→idle(3001ms)  ← MIUI 冻结进程
← 此后无任何 LockMonitorService / LockCheckReceiver 日志
```

**解决方法**:
1. 设置 → 应用管理 → TimeLock → 省电策略 → 无限制
2. 设置 → 应用管理 → TimeLock → 自启动 → 开启
3. `adb shell cmd appops set com.timelock.app RUN_ANY_IN_BACKGROUND allow`
4. `adb shell dumpsys deviceidle whitelist +com.timelock.app`

---

## 项目关键路径

```
app/src/main/java/com/timelock/app/
├── MainActivity.kt                          — 主 Activity（双模式: 正常/锁屏）
├── TimeLockApp.kt                           — Hilt Application
├── service/
│   ├── LockAccessibilityService.kt           — 无障碍服务（实时 APP 切换检测）
│   ├── LockMonitorService.kt                 — 前台服务 + UsageStats polling
│   ├── LockMonitorEngine.kt                  — 锁机检查引擎
│   ├── LockCheckReceiver.kt                  — AlarmManager 广播接收器
│   ├── LockOverlayService.kt                 — ★ WindowManager 悬浮窗锁屏 (主要方案)
│   └── LockScreenDispatcher.kt               — 统一锁屏调度器 (分发到 OverlayService)
├── domain/usecase/
│   └── CheckAppLimitUseCase.kt               — 超限判断逻辑
├── ui/
│   ├── HomeProxyActivity.kt                  — 透明代理（返回桌面用）
│   ├── lock/LockOverlayScreen.kt             — Compose 锁屏 UI
│   └── permission/PermissionGate.kt          — 4 项权限引导
└── util/
    └── PermissionHelper.kt                   — 权限检测与引导
```
