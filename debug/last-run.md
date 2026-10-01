# 测试结果

- commit: 8ffd3ce（Apply plugin Activity screenOrientation on the stub；改动 ManifestParser.kt、HookInstrumentation.kt）
- 编译: 成功（:app，BUILD SUCCESSFUL；唯一警告 PluginRuntime.kt:29 `Resources(...)` is deprecated）
- 设备: 小米 M2011K2C / Android 14，手机上没有真实微信；微信 8.0.78 的 APK 是电脑备份副本（单个 base.apk，280614450 字节，arm64-v8a，非 split）。
- 现象: **回归通过。** 欢迎页 → 手机号登录页（MobileInputUI）→ 账号/密码登录页（LoginUI）全部稳定显示，**全程竖屏**，宿主进程 PID 17555 全程存活，无崩溃。
  欢迎页冷启动期有一次系统 ANR 弹窗（点系统弹窗"等待"后消失，与前几轮一致）。
  **没有输入任何账号/密码，没有点"同意并继续/同意并登录"，没有登录。**

## 关键 logcat（MultiOpen + ActivityTaskManager；系统 events 缓冲区）

页面时间线（桩分配与前几轮相同）：

```
START u0 {cmp=.../.StubActivity} ... result code=0                              ← 打开实例（LauncherUI）
rewriteIntent: ...WelcomeActivity -> stub (flags=0x20000000) ×2 → START StubActivity1 / StubActivity2，全部 result code=0
Displayed com.example.multiopen/.StubActivity2 for user 0: +8s26ms               ← 欢迎页（冷启动，伴随 1 次 ANR 弹窗）
rewriteIntent: ...MobileInputUI -> stub (flags=0x0) → START StubActivity3 result code=0
Displayed com.example.multiopen/.StubActivity3 for user 0: +542ms               ← 手机号登录页
rewriteIntent: ...LoginUI -> stub (flags=0x0)      → START StubActivity4 result code=0
Displayed com.example.multiopen/.StubActivity4 for user 0: +396ms               ← 账号/密码登录页
```
（30 条 provider installed、20 条 receiver registered 已折叠；`ART hook installed` 1 次；本轮 logcat 过滤没有包含 Pine 标签，所以没统计 `handleBridge`。）

### 方向（本轮重点 2、3）

系统 events 缓冲区里本轮 `wm_set_requested_orientation` 的记录（方括号里第一个值 1 = `SCREEN_ORIENTATION_PORTRAIT`）：

```
22:40:24.321 I/wm_set_requested_orientation: [1,com.example.multiopen/.StubActivity]
22:40:24.329 I/wm_set_requested_orientation: [1,com.example.multiopen/.StubActivity]
22:40:25.418 I/wm_set_requested_orientation: [1,com.example.multiopen/.StubActivity2]
22:40:39.648 I/wm_set_requested_orientation: [1,com.example.multiopen/.StubActivity3]
22:40:51.600 I/wm_set_requested_orientation: [1,com.example.multiopen/.StubActivity4]
22:40:51.788 I/wm_set_requested_orientation: [1,com.example.multiopen/.StubActivity4]
（每条后面各有一条对应的 wm_requested_orientation [1,com.example.multiopen]）
```
- 这些页面的方向都是竖屏（值 1）；**屏幕上也是竖屏**（我对账号/密码页截图确认：竖向布局，没有转成横屏，没有显示异常；欢迎页、手机号登录页之前的轮次一直是竖屏，本轮的 `dumpsys window` 里 `mLastOrientation=-1` 是系统未锁定的默认读数，我没有用它判断）。
- **我没有办法区分这些 `requested_orientation=1` 是新代码（`HookInstrumentation.patch` 里的 `setRequestedOrientation`）设置的，还是微信自己调用的**：这些页面在微信清单里本来就有 `screenOrientation=1`（前面 aapt2 查过 WelcomeActivity 是 1），而且更早的轮次（83bd5fc 那一轮）的 events 里就已经有 `wm_set_requested_orientation [1,...StubActivity3]`。
  所以这一轮的日志只能证明"方向是竖屏、没有异常"，**不能证明新代码本身起了作用**。
- 新代码的异常: **没有。** 日志里没有 `setRequestedOrientation` 相关的任何字样，也没有相关异常。

## 本轮云端 Claude 要求的重点

1. **回归: 通过。** 欢迎页 / 登录页 / 账号密码页稳定显示，无崩溃（FATAL 0、NativeCrash 0、`Unknown package` 0）。
2. **方向是否正常: 正常**，竖屏，没有被转成横屏或显示异常。
3. **`setRequestedOrientation` 引起的异常/崩溃: 没有。**
4. 没有登录、没有输入。

### 小注记

- 日志里有 1 条进程被杀记录：`Process 19699 exited due to signal 9 (Killed)`。**19699 不是宿主（宿主始终是 PID 17555）**；日志里也没找到这个 PID 的 `Start proc`，我不知道它是什么进程，与本测试无关（前一轮出现过的是 MIUI 系统服务 `com.miui.mishare.connectivity`）。
- 操作插曲（与被测应用无关）：这一轮的选择器第一次又没选中 `real.apk`（停在 `advanced` 子目录），按兜底流程回根目录再选后添加成功。

## 异常计数
FATAL EXCEPTION: 0 次
native 崩溃 / NativeCrash: 0 次
宿主进程死亡: 0 次（PID 17555 全程存活）；系统里被杀的 PID 19699 与宿主无关
`Unknown package: com.tencent.mm`: 0 次
plugin Application.onCreate failed: 1 次（被捕获：Scene Activity process mismatch）
`ART hook installed`: 1 次
rewriteIntent: 4 次（WelcomeActivity ×2，flags=0x20000000；MobileInputUI ×1、LoginUI ×1，flags=0x0）
START StubActivity*: 5 次（StubActivity、StubActivity1～4），全部 result code=0
`setRequestedOrientation` 相关日志/异常: 0 次
mCoreAccount not initialized: 0 次
Resources$NotFoundException: 0 次
ACCESS_NETWORK_STATE: 0 次
baseRevision must not be null: 0 次
Skeleton not initialized: 0 次
UnsatisfiedLinkError: 0 次
ClassNotFoundException: 0 次
SecurityException: 0 次
NoClassDefFoundError: 0 次
ANR 弹窗: 有 1 次（欢迎页冷启动期；点"等待"后消失）
