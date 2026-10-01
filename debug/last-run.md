# 测试结果

- commit: 38f1cbd（本地 HEAD 为 44bc91b，只比 38f1cbd 多了 debug 目录下的说明文件，代码相同）
- 编译: 成功（:app，BUILD SUCCESSFUL）
- 设备: 小米 M2011K2C / Android 14
- 被测 APK: 微信 8.0.78（手机上已装的单个 base.apk，280614450 字节，arm64-v8a，非 split）
- 现象: 仍然白屏，焦点一直为空；宿主进程反复崩溃重启，约 25 秒内 9 次 FATAL EXCEPTION（都在 main 线程，每次新进程，约每 4 秒一次）。
  **Resources$NotFoundException 已经消失（0 次）**，换成了新的崩溃 `b96.b: mCoreAccount not initialized!`。本轮没有出现 ANR 弹窗。

## 关键 logcat（只含 MultiOpen 标签 + 崩溃栈）

第 1 个进程（PID 23302）里 FATAL 之前的全部 MultiOpen 行；receiver registered 20 条、provider installed 30 条已折叠（都是微信类名）。

```
19:34:34.980 I/MultiOpen(23302): IActivityManager hooked
19:34:34.983 I/MultiOpen(23302): IPackageManager hooked
19:34:45.081 I/MultiOpen(23302): extracted 209 so (arm64-v8a) -> /data/user/0/com.example.multiopen/files/virtual/1790850881238/lib
19:35:09.933 I/MultiOpen(23302): resources built: cookie=15, apk=/data/user/0/com.example.multiopen/files/virtual/1790850881238/base.apk (280614450 bytes)
19:35:09.933 I/MultiOpen(23302): resources self-check OK: 0x7f1202a7 -> com.tencent.mm:style/lc
19:35:11.886 I/MultiOpen(23302): set mInitialApplication -> com.tencent.mm.app.Application
19:35:12.351 E/MultiOpen(23302): plugin Application.onCreate failed
19:35:12.351 E/MultiOpen(23302): b96.b: mCoreAccount not initialized!
19:35:12.351 I/MultiOpen(23302): virtual Application created: com.tencent.mm.app.Application
19:35:18.140 I/MultiOpen(23302): newActivity: stub -> com.tencent.mm.ui.LauncherUI
19:35:18.183 I/MultiOpen(23302): virtual Service created: com.tencent.mm.ipcinvoker.wx_extension.service.MainProcessIPCService
```

`plugin Application.onCreate failed` 的堆栈（19:35:12，微信 Application.onCreate 里）：

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

第一个 FATAL（19:35:18.461，PID 23302，main 线程，与 `newActivity: stub -> LauncherUI` 同一时刻）：

```
FATAL EXCEPTION: main
Process: com.example.multiopen, PID: 23302
b96.b: mCoreAccount not initialized!
    at b96.a.g(Unknown Source:10)
    at b96.a.f(Unknown Source:3)
    at b96.a.c(Unknown Source:5)
    at gp0.j1.b(Unknown Source:8)
    at b41.h9.h(Unknown Source:0)
    at com.tencent.mm.ui.LauncherUI.onCreate(Unknown Source:1033)
    at com.example.multiopen.HookInstrumentation.callActivityOnCreate(HookInstrumentation.kt:44)
```

其余 8 次 FATAL 的异常标题完全相同（都是 `b96.b: mCoreAccount not initialized!`），每次都是新进程重启后重复同样的流程。

## 本轮云端 Claude 要求的额外诊断

- 是否出现 `set mInitialApplication -> com.tencent.mm.app.Application`: **出现了**（每个新进程各 1 次，共 10 次：
  19:35:11.886 / 19:35:22.087 / 19:35:28.619 / 19:35:36.714 / 19:35:42.554 / 19:35:46.487 / 19:35:50.367 / 19:35:54.449 / 19:35:58.387 / 19:36:02.130）。
- `le5.j` 的 Resources$NotFoundException（0x7f0e06ad / 0x7f110838 / 0x7f11028f）: **全部消失**，日志里 0 次。
- 资源自检: `resources built: cookie=15, ... (280614450 bytes)`、`resources self-check OK: 0x7f1202a7 -> com.tencent.mm:style/lc`，第 1 个进程各出现 1 次，没有 FAILED。
- FATAL EXCEPTION 次数: 9；仍然白屏，没有 ANR 弹窗；每个进程都走到了 `newActivity: stub -> com.tencent.mm.ui.LauncherUI`（9 次）。
- 新的第一个 `Caused by`: 本轮 FATAL 没有 `Caused by`，异常本身就是 `b96.b: mCoreAccount not initialized!`（完整栈见上）。
- 本地 AI 的观察（未看宿主代码，未验证）:
  - 这个新异常和之前的 `b96.b: Skeleton not initialized!`（commit e0908cb 之前出现过）是同一个异常类 `b96.b`、同一条调用链起点 `b96.a.g ← f ← c`，只是消息变了。
  - 它先在 Application.onCreate 里出现一次（被 `plugin Application.onCreate failed` 捕获，没有崩溃），然后在 `LauncherUI.onCreate` 里又出现一次，这次没人捕获，进程崩溃。
  - 所以微信 Application.onCreate 没有走完（在 `WeChatSplashStartup` 启动流程里抛了异常），导致微信内部的核心账号/Kernel 对象没有初始化。

## 异常计数
Resources$NotFoundException: 0 次
mCoreAccount not initialized: 19 行（9 个 FATAL 标题 + 10 条 `E/MultiOpen` 的 Application.onCreate failed 标题行）
FATAL EXCEPTION: 9 次
plugin Application.onCreate failed: 10 次
ACCESS_NETWORK_STATE: 0 次
baseRevision must not be null: 0 次
Skeleton not initialized: 0 次
UnsatisfiedLinkError: 0 次
ClassNotFoundException: 0 次
SecurityException: 0 次
NoClassDefFoundError: 0 次
