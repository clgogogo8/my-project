# 测试结果

- commit: c0dc383（Run plugin ContentProviders before Application.onCreate (fixes mCoreAccount)）
- 编译: 成功（:app，BUILD SUCCESSFUL）
- 设备: 小米 M2011K2C / Android 14
- 被测 APK: 微信 8.0.78（手机上已装的单个 base.apk，280614450 字节，arm64-v8a，非 split）
- 现象: **仍然白屏**，没有出现任何微信界面（没有启动页/闪屏/隐私弹窗）；焦点全程为空；宿主进程反复崩溃重启，
  约 50 秒内 10 次 FATAL EXCEPTION（都在 main 线程，每次新进程）。没有 ANR 弹窗。
  **`mCoreAccount not initialized` 没有消失**，调用栈和上一轮（38f1cbd）完全一样；
  但 provider 顺序已经对了（见下）。

## 关键 logcat（只含 MultiOpen 标签 + 崩溃栈）

第 1 个进程（PID 32012）里 FATAL 之前的全部 MultiOpen 行；receiver registered 20 条、provider installed 30 条已折叠（都是微信类名，无异常）。

```
19:43:15.778 I/MultiOpen(32012): IActivityManager hooked
19:43:15.781 I/MultiOpen(32012): IPackageManager hooked
19:43:25.980 I/MultiOpen(32012): extracted 209 so (arm64-v8a) -> /data/user/0/com.example.multiopen/files/virtual/1790851402075/lib
19:43:51.065 I/MultiOpen(32012): resources built: cookie=15, apk=/data/user/0/com.example.multiopen/files/virtual/1790851402075/base.apk (280614450 bytes)
19:43:51.065 I/MultiOpen(32012): resources self-check OK: 0x7f1202a7 -> com.tencent.mm:style/lc
19:43:52.997 I/MultiOpen(32012): set mInitialApplication -> com.tencent.mm.app.Application
19:43:53.096 I/MultiOpen(32012): provider installed: com.tencent.mm.plugin.base.stub.MMPluginProvider [...]   ← 第 1 个 provider
   …（共 30 个 provider installed，含 WXCommProvider、androidx.startup.InitializationProvider、ExtControlProviderAccountSync 等）
19:43:54.714 I/MultiOpen(32012): provider installed: com.google.firebase.provider.FirebaseInitProvider [...]   ← 第 30 个 provider
19:43:55.388 E/MultiOpen(32012): plugin Application.onCreate failed
19:43:55.388 E/MultiOpen(32012): b96.b: mCoreAccount not initialized!
19:43:55.388 I/MultiOpen(32012): virtual Application created: com.tencent.mm.app.Application
19:43:55.493 I/MultiOpen(32012): receiver registered: ...（20 条，从这里开始）
19:43:55.504 I/MultiOpen(32012): newActivity: stub -> com.tencent.mm.ui.LauncherUI
19:43:55.843 I/MultiOpen(32012): virtual Service created: com.tencent.mm.ipcinvoker.wx_extension.service.MainProcessIPCService
```

顺序证据：30 条 `provider installed`（19:43:53.096 ～ 19:43:54.714）全部在 `plugin Application.onCreate failed`（19:43:55.388）之前；
`set mInitialApplication` 在第一个 provider 之前。也就是说 provider 先于 Application.onCreate 的顺序**已经对了**，
但 Application.onCreate 里仍然抛出同一个异常。

`plugin Application.onCreate failed` 的堆栈（与上一轮相同）：

```
b96.b: mCoreAccount not initialized!
    at b96.a.g(Unknown Source:10)
    at b96.a.f(Unknown Source:3)
    at b96.a.c(Unknown Source:5)
    at gp0.j1.b(Unknown Source:8)
    at gp0.m.t(Unknown Source:10)
    at hv1.a.onCreate(Unknown Source:3)
    at ph5.w.access$1000 / ph5.u.compute / ph5.v.compute / ph5.w.transitLifecycleStatusOnDemand / ph5.n0.j / ph5.g0.run
    at gp0.q1.call ← yu5.f.run ← yu5.h.a ← xu5.q.d / r / e
    at com.tencent.mm.legacy.app.WeChatSplashStartup.a(Unknown Source:432)
    at com.tencent.mm.legacy.app.WeChatSplash.a(Unknown Source:123)
    at re5.p.b(Unknown Source:362)
    at com.tencent.mm.app.MMApplicationLike.onCreate(Unknown Source:145)
    at com.tencent.tinker.entry.TinkerApplicationInlineFence.handleMessageImpl(Unknown Source:152)
```

第一个 FATAL（19:43:55.854，PID 32012，main 线程）：

```
FATAL EXCEPTION: main
Process: com.example.multiopen, PID: 32012
b96.b: mCoreAccount not initialized!
    at b96.a.g(Unknown Source:10)
    at b96.a.f(Unknown Source:3)
    at b96.a.c(Unknown Source:5)
    at gp0.j1.b(Unknown Source:8)
    at b41.h9.h(Unknown Source:0)
    at com.tencent.mm.ui.LauncherUI.onCreate(Unknown Source:1033)
    at com.example.multiopen.HookInstrumentation.callActivityOnCreate(HookInstrumentation.kt:44)
```

10 次 FATAL 的异常标题完全相同（`b96.b: mCoreAccount not initialized!`），PID 依次为 32012、32300、32556、1810、3140、3637、3960、4679、5053、5471，
间隔从约 9 秒逐渐缩到约 4 秒；每个进程都走了同一套流程（mInitialApplication → providers → Application.onCreate failed → LauncherUI.onCreate → 崩溃）。

## 本轮云端 Claude 要求的额外诊断

- `mCoreAccount not initialized` 还在不在: **还在**（每个进程 2 处：Application.onCreate 里被捕获 1 次 + LauncherUI.onCreate 里崩溃 1 次，共 20 行）。
- `plugin Application.onCreate failed` 还有没有: **还有**，10 次（每个进程 1 次）。
- `FATAL EXCEPTION` 次数: 10；仍白屏；仍反复重启。
- `provider installed` 是否出现在 `virtual Application created` 之前: **是**（见上方时间戳）。
  细节：receiver registered 现在是在 `virtual Application created` 之后才开始注册（19:43:55.493），不是之前。
- 新的第一个异常/`Caused by`: **没有新崩溃**，崩溃点和上一轮完全相同。
- 微信是否显示出任何界面: **没有**，全程白屏。
- 资源相关: `resources built: cookie=15, ... (280614450 bytes)`、`resources self-check OK: 0x7f1202a7 -> com.tencent.mm:style/lc`（无 FAILED）；Resources$NotFoundException 保持 0 次。
- 本地 AI 的观察（未看宿主代码，未验证）:
  - 日志里每个进程只在一处出现 `provider installed` 的整批输出，没有看到某个 provider 安装失败的 E 级日志，所以 30 个 provider 都"装上了"。
  - 调用栈里是 `gp0.j1.b → b96.a.c → b96.a.f → b96.a.g` 抛 `mCoreAccount not initialized`，即微信在 `WeChatSplashStartup` 流程里通过 `gp0.j1` 读取核心账号，但它还没被设置。
    本轮 provider 顺序已对而问题依旧，说明 mCoreAccount 的初始化大概率不在（这批）provider 的 onCreate 里，
    或者初始化依赖了别的前置条件（比如进程名判断 / 多进程 / 另一个组件先于它运行），我无法从日志判断。

## 异常计数
mCoreAccount not initialized: 20 行（10 个 FATAL 标题 + 10 条 `E/MultiOpen` 的 Application.onCreate failed 标题行）
FATAL EXCEPTION: 10 次
plugin Application.onCreate failed: 10 次
Resources$NotFoundException: 0 次
ACCESS_NETWORK_STATE: 0 次
baseRevision must not be null: 0 次
Skeleton not initialized: 0 次
UnsatisfiedLinkError: 0 次
ClassNotFoundException: 0 次
SecurityException: 0 次
NoClassDefFoundError: 0 次
