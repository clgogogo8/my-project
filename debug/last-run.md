# 测试结果（启动 ANR 二次启动验证，无代码改动）

- commit: af0b0f9（Note content:// in-process resolution landed; queue ANR cold-start probe）。与上一轮 a11ea77/984f1c5 相比 app 源码没有改动（`git diff` 对 app/、settings.gradle.kts、build.gradle.kts 为空）。
- 编译: 成功（:app，BUILD SUCCESSFUL，宿主重装）
- 设备: 小米 M2011K2C / Android 14，手机上没有真实微信；微信 8.0.78 的 APK 是电脑备份副本（单个 base.apk，280614450 字节，arm64-v8a，非 split）。
- 做法: `pm clear` 一次制造冷启动 → 添加实例 → 第 1 次打开（冷启动）→ 用返回键退出实例（宿主进程没杀，PID 8781 全程不变，没有 pm clear）→ 第 2 次打开同一个实例 → 退出 → 第 3 次打开。每次点击后观察约 24 秒（每 2 秒查一次有没有 ANR 弹窗）。**没有登录、没有输入任何东西。**
- 现象: **ANR 只出现在第 1 次（冷启动）**；第 2、3 次打开都是 0.43～0.45 秒就显示出欢迎页，没有 ANR。没有崩溃，进程 PID 8781 全程存活。

## 三次打开对比（本轮重点 1）

| 次数 | 类型 | `Displayed ...` | ANR 弹窗 |
|---|---|---|---|
| 第 1 次 | 冷启动（宿主刚装、`pm clear` 后） | `22:29:30.401 Displayed com.example.multiopen/.StubActivity2 for user 0: +8s769ms` | **有**（点击后约 12 秒的采样里发现；之前几轮是约 8～17 秒内出现；点系统弹窗"等待"后消失） |
| 第 2 次 | 热（进程已存活，Application 已建好） | `22:29:58.537 Displayed com.example.multiopen/.StubActivity7 for user 0: +450ms` | **无** |
| 第 3 次 | 热 | `22:30:30.232 Displayed com.example.multiopen/.StubActivity4 for user 0: +427ms` | **无** |

- 第 2、3 次明显变快：从约 **8.8 秒降到约 0.43～0.45 秒**，ANR 不再出现。三次的画面都是微信欢迎页（我截图确认了第 3 次：深蓝地球图、右上"语言"、底部绿色"登录"和白色"注册"）。
- 第 2、3 次的日志里**没有** `resources built` / `fake process name` / `set mInitialApplication` / `virtual Application created` / `provider installed` / `receiver registered`，只有 `rewriteIntent`/`newActivity: stub -> LauncherUI` → 两次 `rewriteIntent ...WelcomeActivity` → `newActivity: stub -> WelcomeActivity` → `Displayed`。
  也就是说二次打开只重新创建 Activity，不再重走 Application/provider 初始化。

## 冷启动 8.77 秒拆分（本轮重点 2，第 1 次，设备时钟）

`Displayed +8s769ms` 的起点约是 22:29:21.632（点击实例的时刻），终点 22:29:30.401。

```
22:28:48.824  IActivityManager hooked           （宿主进程启动，早于点击；与本次耗时无关）
22:29:21.576  resources built（第 1 次）         ← 点击实例，开始加载
22:29:22.736  resources built（第 2 次）
22:29:22.737  fake process name -> com.tencent.mm
22:29:23.672  set mInitialApplication
22:29:23.768 ～ 22:29:27.459  provider installed ×30（首条 ～ 末条，共约 3.7 秒）
22:29:28.202  virtual Service created: ...ProcessService$MMProcessService
22:29:28.915  plugin Application.onCreate failed（被捕获） / virtual Application created
22:29:28.967 ～ 28.987  receiver registered ×20
22:29:28.987  newActivity: stub -> com.tencent.mm.ui.LauncherUI
22:29:29.557  rewriteIntent: ...WelcomeActivity -> stub (flags=0x20000000)
22:29:29.796  rewriteIntent: ...WelcomeActivity -> stub (flags=0x20000000)
22:29:30.149  newActivity: stub -> com.tencent.mm.plugin.account.ui.WelcomeActivity
22:29:30.401  Displayed ...StubActivity2 +8s769ms
```

各段耗时（由上面的时间戳相减）：

| 阶段 | 起止 | 耗时 |
|---|---|---|
| 资源 / 进程名 / Application 构造（到 `set mInitialApplication`） | 21.632 → 23.672 | 约 **2.0 秒** |
| **30 个 ContentProvider 安装** | 23.768 → 27.459 | 约 **3.7 秒** |
| 微信 Application.onCreate 其余部分（到 `virtual Application created`） | 27.459 → 28.915 | 约 **1.5 秒** |
| LauncherUI 创建 → WelcomeActivity 创建 → 首帧 Displayed | 28.915 → 30.401 | 约 **1.5 秒** |
| 合计 | 21.632 → 30.401 | 约 8.8 秒 |

- **最大的一块是 provider 安装（约 3.7 秒，约占 42%）**；其次是启动前的资源/Application 构造（约 2.0 秒）、Application.onCreate（约 1.5 秒）、从 LauncherUI 到首帧（约 1.5 秒）。
- 我无法从日志里区分每一段里"dex 加载"和"微信自己的初始化"各占多少（日志只有这些点），所以上面是按日志点划分的，不是按"dex / onCreate / 资源"划分的。

## 其余回归（本轮重点 3）

- 新崩溃: 没有。FATAL EXCEPTION 0 次；NativeCrash 0 次；`Unknown package` 0 次。
- 日志里唯一一条"进程被杀"的记录是 `22:29:22.474 I/Zygote: Process 17569 exited due to signal 9 (Killed)`——**17569 是 `com.miui.mishare.connectivity`（MIUI 的系统服务），不是宿主（宿主始终是 PID 8781）**，与本测试无关。
- `plugin Application.onCreate failed`（Scene Activity process mismatch ... declared=null）仍是 1 次，被捕获，只发生在第 1 次（冷启动）。

## 本地 AI 的观察（未验证）

1. **用返回键退出微信实例会触发微信重新拉起 LauncherUI**：日志里每次我按返回键，都出现 `rewriteIntent: com.tencent.mm.ui.LauncherUI -> stub (flags=0x4000000)`（`FLAG_ACTIVITY_CLEAR_TOP`）+ `newActivity: stub -> LauncherUI`（第 1 次退出：22:29:53.345 和 22:29:55.616；第 2 次退出：22:30:25.091 和 22:30:27.365，共 4 次），
   并且第一次按返回后还额外创建了一个 WelcomeActivity（22:29:53.580）。我按了 2 次返回才回到 MultiOpen 列表。所以"退出实例"在这个容器里并不干净：返回键会让微信在欢迎页上重新拉起 LauncherUI。
2. **桩池是轮换分配，并且已经回绕**：`START` 的桩依次是 `StubActivity, 1, 2, 3, 4, 5, 6, 7`（8 个），然后又回到 `StubActivity, 1, 2, 3, 4`。
   第 2 次打开显示在 `StubActivity7`，第 3 次显示在 `StubActivity4`。本轮回绕时没有出问题（STARTs 全部 result code=0），但池只有 8 个，长时间使用时旧实例可能被新实例占用同一个桩，我没有测这种情况。
3. 冷启动的主要耗时在 30 个 provider 的安装（约 3.7 秒）。如果要缩短 ANR 前的主线程阻塞，这一段可能是最值得优化（例如放到后台线程或按需安装）的地方——这是我的推断，没有试过。

## 异常计数
FATAL EXCEPTION: 0 次
native 崩溃 / NativeCrash: 0 次
宿主进程死亡: 0 次（PID 8781 全程存活）；系统里被杀的 `com.miui.mishare.connectivity`（PID 17569）与我们无关
`Unknown package: com.tencent.mm`: 0 次
plugin Application.onCreate failed: 1 次（被捕获，仅冷启动）
`newActivity: stub -> LauncherUI`: 7 次；`-> WelcomeActivity`: 5 次；`rewriteIntent: LauncherUI -> stub (flags=0x4000000)`: 4 次
START StubActivity*: 13 次，全部 result code=0
mCoreAccount not initialized: 0 次
Resources$NotFoundException: 0 次
ACCESS_NETWORK_STATE: 0 次
baseRevision must not be null: 0 次
Skeleton not initialized: 0 次
UnsatisfiedLinkError: 0 次
ClassNotFoundException: 0 次
SecurityException: 0 次
NoClassDefFoundError: 0 次
ANR 弹窗: 第 1 次（冷启动）有 1 次；第 2、3 次 0 次
