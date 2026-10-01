# 测试结果

- commit: 489a3a8（Fix cross-thread deadlock: run Application.onCreate outside the lock）
- 编译: 成功（:app，BUILD SUCCESSFUL）
- 设备: 小米 M2011K2C / Android 14
- 被测 APK: 微信 8.0.78（手机上已装的单个 base.apk，280614450 字节，arm64-v8a，非 split）
- 现象: **死锁解开了，往前走了一大步，但出现了新的卡死（ANR）**。
  `virtual Application created` 和 `newActivity: stub -> com.tencent.mm.ui.LauncherUI` 都出现了，StubActivity 的窗口也建起来了，
  但屏幕上**没有画出任何微信界面**（先是空白的白屏，之后变成灰底，点击后约 10~17 秒内弹出系统"MultiOpen没有响应"ANR 弹窗；弹窗一直在，我没点"确定"，观察到约 +50 秒）。
  这次主线程不再 WAITING，而是 **RUNNABLE，反复调用 startActivity**：`LauncherUI.onResume → D7 → startActivity`，
  日志里出现 **735 次** `rewriteIntent: com.tencent.mm.plugin.account.ui.WelcomeActivity -> stub`（20:10:46.675 ～ 20:11:38.362，约 80 毫秒一次），
  但整个期间只有 **1 次** `newActivity: stub`（就是 LauncherUI），**WelcomeActivity 从来没有被真正创建出来**。
  没有 FATAL EXCEPTION，进程 1 个，一直存活。

## 关键 logcat（只含 MultiOpen 标签 + 崩溃栈）

看门狗和重复的 rewriteIntent 之外的 MultiOpen 行（30 条 provider installed、20 条 receiver registered 已折叠；rewriteIntent 只列开头几条）：

```
20:10:15.945 I/MultiOpen: IActivityManager hooked
20:10:15.948 I/MultiOpen: IPackageManager hooked
20:10:26.068 I/MultiOpen: extracted 209 so (arm64-v8a) -> /data/user/0/com.example.multiopen/files/virtual/1790853022182/lib
20:10:40.141 I/MultiOpen: resources built: cookie=15, apk=/data/user/0/com.example.multiopen/files/virtual/1790853022182/base.apk (280614450 bytes)
20:10:40.141 I/MultiOpen: resources self-check OK: 0x7f1202a7 -> com.tencent.mm:style/lc
20:10:41.249 I/MultiOpen: resources built: ...（同上，第 2 次）
20:10:41.249 I/MultiOpen: resources self-check OK: 0x7f1202a7 -> com.tencent.mm:style/lc
20:10:41.249 I/MultiOpen: fake process name -> com.tencent.mm
20:10:42.214 I/MultiOpen: set mInitialApplication -> com.tencent.mm.app.Application
   …（provider installed ×30）
20:10:44.845 I/MultiOpen: virtual Service created: com.tencent.mm.service.ProcessService$MMProcessService
20:10:45.575 E/MultiOpen: plugin Application.onCreate failed
20:10:45.575 E/MultiOpen: java.lang.IllegalStateException: java.lang.IllegalStateException: Scene Activity process mismatch: component=com.tencent.wxpay.internal.presentation.MainProcessPaySceneActivity declared=null current=com.tencent.mm
20:10:45.575 I/MultiOpen: virtual Application created: com.tencent.mm.app.Application
   …（receiver registered ×20）
20:10:45.652 I/MultiOpen: newActivity: stub -> com.tencent.mm.ui.LauncherUI
20:10:46.675 I/MultiOpen: rewriteIntent: com.tencent.mm.plugin.account.ui.WelcomeActivity -> stub
20:10:46.908 I/MultiOpen: virtual Service created: com.tencent.mm.ipcinvoker.wx_extension.service.PushProcessIPCService
20:10:46.939 I/MultiOpen: rewriteIntent: com.tencent.mm.plugin.account.ui.WelcomeActivity -> stub
20:10:47.181 I/MultiOpen: virtual Service created: androidx.work.impl.background.systemalarm.SystemAlarmService
20:10:47.389 I/MultiOpen: rewriteIntent: com.tencent.mm.plugin.account.ui.WelcomeActivity -> stub
   … 之后每约 80 毫秒一条，共 735 条，最后一条 20:11:38.362（全部是同一个 WelcomeActivity，没有任何别的 rewriteIntent）
```

`plugin Application.onCreate failed`（被宿主捕获，没有崩溃）的 Caused by：

```
Caused by: java.lang.IllegalStateException: Scene Activity process mismatch: component=com.tencent.wxpay.internal.presentation.MainProcessPaySceneActivity declared=null current=com.tencent.mm
    at b76.b.c(Unknown Source:84)
    at r66.l0.b(SourceFile:9)
    at com.tencent.mm.plugin.pay.kit.x2.d(Unknown Source:152)
    at com.tencent.mm.plugin.pay.kit.f5.onCreate(Unknown Source:27)
    at ph5.w.access$1000(Unknown Source:16) ← ph5.u.compute ← ph5.v.compute ← ph5.w.transitLifecycleStatusOnDemand ← ph5.n0.j ← ph5.g0.run
    …（ForkJoin worker 帧）
外层: ph5.n0.l(:18) ← ph5.n0.a(:73) ← gp0.q1.call ← yu5.f.run ← yu5.h.a ← xu5.q.d/r/e ← WeChatSplashStartup.a(:432) ← WeChatSplash.a(:123)
      ← re5.p.b(:362) ← MMApplicationLike.onCreate(:145) ← Tinker… ← VirtualApplications.ensure(VirtualApplications.kt:48) ← HookInstrumentation.newActivity(HookInstrumentation.kt:35)
```

## 本轮云端 Claude 要求的额外诊断

- **死锁有没有解: 解了。** `virtual Application created: com.tencent.mm.app.Application`（20:10:45.575）出现；随后 `newActivity: stub -> com.tencent.mm.ui.LauncherUI`（20:10:45.652）出现。
  主线程栈也不再是 `ForkJoinTask.get()` 的 WAITING。
- **微信有没有显示出任何界面: 没有。** 我每 8 秒截一次屏：点击后约 +8 秒（第一次采样）是**空白的白屏**（dumpsys 窗口列表里能看到 `com.example.multiopen/.StubActivity` 窗口，但没有任何内容画出来），
  第二次采样（约 +17 秒）起变成**灰底 + "MultiOpen没有响应" ANR 弹窗**，之后一直如此。没有闪屏、启动页、隐私弹窗或登录页（我没点任何东西，也没点 ANR 的"确定"）。
- 是否还 ANR / 还白屏: **还有**（见上）。
- watchdog 主线程栈（**不再是 ForkJoinTask.get() 了**）：三条（20:10:48.215 / 20:10:54.216 / 20:11:00.235）都是 `state=RUNNABLE`，三条**不相同**（在变，说明主线程在循环干活，不是被锁住），
  但三条里都包含同一条链（我只核对了这些帧都出现，没有逐帧比对整条栈）：
  `LauncherUI.onResume(Unknown Source:3115) → LauncherUI.D7(Unknown Source:314) → … Activity.startActivity → … LauncherUI.startActivityForResult → VASLauncher.startActivityForResult → …`
  栈顶（最上面几帧）分别停在不同的 Binder 调用上：
  - #1（20:10:48.215）：`BinderProxy.transactNative ← … IActivityClientController$Stub$Proxy.getTaskForActivity ← ActivityClient.getTaskForActivity ← Activity.getTaskId ← qu1.i.S(:40) ← qu1.i.s(:23) ← ou1.i2.callback(:47) ← com.tencent.mm.sdk.event.d.d ← IEvent.e ← ia2.h.a ← xm0.a.g ← HellActivity.startActivityForResult ← … ← MMFragmentActivity.startActivity ← LauncherUI.D7(:314) ← LauncherUI.onResume(:3115) ← Instrumentation.callActivityOnResume ← Activity.performResume ← ActivityThread.handleResumeActivity`
  - #2（20:10:54.216）：`BinderProxy.transactNative ← IActivityTaskManager$Stub$Proxy.startActivity ← Instrumentation.execStartActivity ← java.lang.reflect.Method.invoke ← com.example.multiopen.ExecHookInstrumentation.callBase(ExecHookInstrumentation.java:39) ← ExecHookInstrumentation.execStartActivity(ExecHookInstrumentation.java:25) ← Activity.startActivityForResult ← HellActivity.startActivityForResult ← … ← VASLauncher.startActivityForResult ← LauncherUI.startActivityForResult`
  - #3（20:11:00.235）：`BinderProxy.transactNative ← IActivityClientController$Stub$Proxy.overridePendingTransition ← ActivityClient.overridePendingTransition ← Activity.overridePendingTransition ← kj5.f.i(:15) ← MMFragmentActivity.initActivityOpenAnimation(:200) ← MMFragmentActivity.startActivityForResult(SourceFile:14) ← VASLauncher.startActivityForResult ← LauncherUI.startActivityForResult ← …`
  （三条栈的 md5 各不相同；#1/#2/#3 里 `LauncherUI.onResume` 和 `LauncherUI.D7` 都各出现 1 次。）
- **bg thread 栈**（20:10:54.2 采样，共 24 个线程，10 种不同的栈；**没有任何 BLOCKED 线程了**）：
  - 22 个是空闲 WAITING/TIMED_WAITING：`[GT]HotPool#0/1/2/3/5/6/7`（7 个，`LinkedBlockingQueue.take`）、`matrix_x_0/1/2`（3 个，TIMED_WAITING）、
    `wc_srvinit_0…4`（5 个，空闲的 ForkJoin worker）、`wc_srvinit_5`（1 个，TIMED_WAITING 的空闲 ForkJoin worker）、
    `MMCrashANRThread-0/1`（2 个）、`IPCThreadPool#Thread-0/1`（2 个）、`pool-4-thread-1`、`Recovery.LogWriter`、`Zidl Java DestructorThread`。
  - **唯一 RUNNABLE 的后台线程：`[GT]HotPool#4`**，在解析 protobuf（`vc6.a.a ← com.tencent.mm.protobuf.f.getNextFieldNumber ← pc5.hv6.op ← f.parseFrom ← pc5.c2.op ← f.populateBuilderWithField ← … ← pc5.d2.op ← f.parseFrom ← ha2.b.a ← ha2.c.c ← ua2.b.f(:818) ← ia2.d.h(:202) …`，共 20 帧）。
  - 没有任何后台线程在等主线程，也没有线程卡在锁上。**卡住的是主线程自己在反复 startActivity。**
  - 上一轮的 `wc_srvinit_3` 死锁线程这次是空闲的（组 3 里，WAITING）。
- 新的第一个异常/`Caused by`: 没有 FATAL。唯一的 E 级是上面被捕获的 `Scene Activity process mismatch ... declared=null current=com.tencent.mm`（见上）。

### 本地 AI 的观察（未验证，供云端参考）

- **WelcomeActivity 的 735 次重写 + 只有 1 次 newActivity**：`ExecHookInstrumentation.execStartActivity` 一直在被调用并把目标重写成 stub，
  但系统并没有真正创建出新的 Activity（没有第二个 `newActivity: stub`）。主线程在 `LauncherUI.onResume → D7` 里反复发起 `startActivity`，
  而真正启动 stub 需要系统通过 Binder 回调主线程的 `handleLaunchActivity`——但主线程自己一直在忙着发 startActivity，没有机会处理。
  这是我的推测：LauncherUI 的 `D7` 可能在循环里"发起启动 WelcomeActivity → 检查任务/Activity 状态（`getTaskForActivity`）→ 没起来就再发起"，
  而启动请求需要主线程消息循环才能完成，所以永远等不到。**这个循环到底在 `D7` 里还是被 `onResume` 反复触发，我无法从栈里判断。**
- **`Scene Activity process mismatch ... declared=null`**：微信的 Pay 模块在 Application 启动期检查"Activity 声明的 process"是否等于当前进程，
  它读到的 declared 是 `null`（当前进程名已经伪装成 com.tencent.mm）。可能是宿主对 PackageManager 的 `getActivityInfo` 返回的 `ActivityInfo.processName` 为 null，
  我没有看宿主那部分代码，也没有验证。目前被宿主捕获，没有直接造成崩溃，但可能让 Application 启动流程少走一段。
- 进展小结: `mCoreAccount` 修好 → 死锁修好 → 现在到了 **LauncherUI 想跳 WelcomeActivity（首次未登录的欢迎页）但跳不过去**，说明微信已经走到"未登录，进欢迎页"这一步了。

## 异常计数
FATAL EXCEPTION: 0 次
plugin Application.onCreate failed: 1 次（被捕获：Scene Activity process mismatch）
mCoreAccount not initialized: 0 次
Resources$NotFoundException: 0 次
ACCESS_NETWORK_STATE: 0 次
baseRevision must not be null: 0 次
Skeleton not initialized: 0 次
UnsatisfiedLinkError: 0 次
ClassNotFoundException: 0 次
SecurityException: 0 次
NoClassDefFoundError: 0 次
virtual Application created: 1 次
newActivity: stub: 1 次（LauncherUI）
rewriteIntent(WelcomeActivity -> stub): 735 次
main-thread stack: 3 条（全部 RUNNABLE，内容不同，都在 LauncherUI.onResume → D7 → startActivity 链上）
bg thread 栈: 24 条（10 种不同的栈；0 个 BLOCKED，1 个 RUNNABLE：`[GT]HotPool#4`）
ANR 弹窗: 有
