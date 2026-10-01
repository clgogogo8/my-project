# 测试结果

- commit: 35e13d8（Add main-thread watchdog to diagnose WeChat onCreate ANR）
- 编译: 成功（:app，BUILD SUCCESSFUL）
- 设备: 小米 M2011K2C / Android 14
- 被测 APK: 微信 8.0.78（手机上已装的单个 base.apk，280614450 字节，arm64-v8a，非 split）
- 现象: 和上一轮（bea49c7）一样**不崩溃但卡死**：黑屏/无焦点，没有任何微信界面；单个进程 PID 9011 一直存活，CPU 0.0%。
  看门狗按预期打出了 3 条主线程栈，**三条逐行完全相同**，主线程一直卡在微信 `Application.onCreate` 里等一个 ForkJoinTask 完成。
  （这一轮观察到约 +40 秒时屏幕是黑底、没有 ANR 弹窗，焦点为空；上一轮同样的阶段曾出现过"MultiOpen没有响应"弹窗。）

## 关键 logcat（只含 MultiOpen 标签 + 崩溃栈）

看门狗之外的全部 MultiOpen 行（30 条 provider installed 已折叠；没有 receiver registered、没有 virtual Application created、没有 newActivity: stub 日志行；没有任何 E 级行）：

```
19:55:31.329 I/MultiOpen( 9011): IActivityManager hooked
19:55:31.332 I/MultiOpen( 9011): IPackageManager hooked
19:55:41.279 I/MultiOpen( 9011): extracted 209 so (arm64-v8a) -> /data/user/0/com.example.multiopen/files/virtual/1790852137552/lib
19:55:55.358 I/MultiOpen( 9011): resources built: cookie=15, apk=/data/user/0/com.example.multiopen/files/virtual/1790852137552/base.apk (280614450 bytes)
19:55:55.358 I/MultiOpen( 9011): resources self-check OK: 0x7f1202a7 -> com.tencent.mm:style/lc
19:55:56.519 I/MultiOpen( 9011): resources built: cookie=15, apk=/data/user/0/com.example.multiopen/files/virtual/1790852137552/base.apk (280614450 bytes)
19:55:56.519 I/MultiOpen( 9011): resources self-check OK: 0x7f1202a7 -> com.tencent.mm:style/lc
19:55:56.519 I/MultiOpen( 9011): fake process name -> com.tencent.mm
19:55:57.436 I/MultiOpen( 9011): set mInitialApplication -> com.tencent.mm.app.Application
   …（30 条 provider installed）
```

没有崩溃栈（没有 FATAL EXCEPTION、没有 AndroidRuntime 输出）。

## 本轮云端 Claude 要求的额外诊断

### 三条 main-thread stack（原样，核心产物）

`W/MultiOpen` 级别，PID 9011，每条 6 秒一次。三条栈的内容逐行完全一致（我把三段去掉时间戳后做了 md5，三个值相同）。
下面三条都贴出来；#2、#3 为了可读性在 `HookInstrumentation.newActivity` 帧之后省略了 13 条 Android 框架帧（与 #1 的末尾完全相同）。

```
19:56:02.521 W/MultiOpen( 9011): main-thread stack #1 (state=WAITING):
    at jdk.internal.misc.Unsafe.park(Native Method)
    at java.util.concurrent.locks.LockSupport.park(LockSupport.java:341)
    at java.util.concurrent.ForkJoinTask.awaitDone(ForkJoinTask.java:472)
    at java.util.concurrent.ForkJoinTask.get(ForkJoinTask.java:983)
    at ph5.n0.l(Unknown Source:18)
    at ph5.n0.a(Unknown Source:73)
    at gp0.q1.call(Unknown Source:47)
    at yu5.f.run(Unknown Source:44)
    at yu5.h.a(Unknown Source:80)
    at xu5.q.d(Unknown Source:124)
    at xu5.q.r(Unknown Source:41)
    at xu5.q.e(Unknown Source:3)
    at com.tencent.mm.legacy.app.WeChatSplashStartup.a(Unknown Source:432)
    at com.tencent.mm.legacy.app.WeChatSplash.a(Unknown Source:123)
    at re5.p.b(Unknown Source:362)
    at com.tencent.mm.app.MMApplicationLike.onCreate(Unknown Source:145)
    at com.tencent.tinker.entry.TinkerApplicationInlineFence.handleMessageImpl(Unknown Source:152)
    at com.tencent.tinker.entry.TinkerApplicationInlineFence.handleMessage_$noinline$(Unknown Source:3)
    at com.tencent.tinker.entry.TinkerApplicationInlineFence.handleMessage(Unknown Source:0)
    at com.tencent.tinker.loader.app.TinkerInlineFenceAction.callOnCreate(Unknown Source:5)
    at com.tencent.tinker.loader.app.TinkerApplication.onCreate(Unknown Source:8)
    at com.example.multiopen.VirtualApplications.ensure(VirtualApplications.kt:45)
    at com.example.multiopen.HookInstrumentation.newActivity(HookInstrumentation.kt:35)
    at android.app.ActivityThread.performLaunchActivity(ActivityThread.java:3902)
    at android.app.ActivityThread.handleLaunchActivity(ActivityThread.java:4164)
    at android.app.servertransaction.LaunchActivityItem.execute(LaunchActivityItem.java:103)
    at android.app.servertransaction.TransactionExecutor.executeCallbacks(TransactionExecutor.java:149)
    at android.app.servertransaction.TransactionExecutor.execute(TransactionExecutor.java:99)
    at android.app.ActivityThread$H.handleMessage(ActivityThread.java:2604)
    at android.os.Handler.dispatchMessage(Handler.java:106)
    at android.os.Looper.loopOnce(Looper.java:222)
    at android.os.Looper.loop(Looper.java:314)
    at android.app.ActivityThread.main(ActivityThread.java:8857)
    at java.lang.reflect.Method.invoke(Native Method)
    at com.android.internal.os.RuntimeInit$MethodAndArgsCaller.run(RuntimeInit.java:565)
    at com.android.internal.os.ZygoteInit.main(ZygoteInit.java:1013)

19:56:08.523 W/MultiOpen( 9011): main-thread stack #2 (state=WAITING):
    at jdk.internal.misc.Unsafe.park(Native Method)
    at java.util.concurrent.locks.LockSupport.park(LockSupport.java:341)
    at java.util.concurrent.ForkJoinTask.awaitDone(ForkJoinTask.java:472)
    at java.util.concurrent.ForkJoinTask.get(ForkJoinTask.java:983)
    at ph5.n0.l(Unknown Source:18)
    at ph5.n0.a(Unknown Source:73)
    at gp0.q1.call(Unknown Source:47)
    at yu5.f.run(Unknown Source:44)
    at yu5.h.a(Unknown Source:80)
    at xu5.q.d(Unknown Source:124)
    at xu5.q.r(Unknown Source:41)
    at xu5.q.e(Unknown Source:3)
    at com.tencent.mm.legacy.app.WeChatSplashStartup.a(Unknown Source:432)
    at com.tencent.mm.legacy.app.WeChatSplash.a(Unknown Source:123)
    at re5.p.b(Unknown Source:362)
    at com.tencent.mm.app.MMApplicationLike.onCreate(Unknown Source:145)
    at com.tencent.tinker.entry.TinkerApplicationInlineFence.handleMessageImpl(Unknown Source:152)
    at com.tencent.tinker.entry.TinkerApplicationInlineFence.handleMessage_$noinline$(Unknown Source:3)
    at com.tencent.tinker.entry.TinkerApplicationInlineFence.handleMessage(Unknown Source:0)
    at com.tencent.tinker.loader.app.TinkerInlineFenceAction.callOnCreate(Unknown Source:5)
    at com.tencent.tinker.loader.app.TinkerApplication.onCreate(Unknown Source:8)
    at com.example.multiopen.VirtualApplications.ensure(VirtualApplications.kt:45)
    at com.example.multiopen.HookInstrumentation.newActivity(HookInstrumentation.kt:35)
    … （其余 13 帧为 Android 框架帧，与 #1 相同）

19:56:14.526 W/MultiOpen( 9011): main-thread stack #3 (state=WAITING):
    at jdk.internal.misc.Unsafe.park(Native Method)
    at java.util.concurrent.locks.LockSupport.park(LockSupport.java:341)
    at java.util.concurrent.ForkJoinTask.awaitDone(ForkJoinTask.java:472)
    at java.util.concurrent.ForkJoinTask.get(ForkJoinTask.java:983)
    at ph5.n0.l(Unknown Source:18)
    at ph5.n0.a(Unknown Source:73)
    at gp0.q1.call(Unknown Source:47)
    at yu5.f.run(Unknown Source:44)
    at yu5.h.a(Unknown Source:80)
    at xu5.q.d(Unknown Source:124)
    at xu5.q.r(Unknown Source:41)
    at xu5.q.e(Unknown Source:3)
    at com.tencent.mm.legacy.app.WeChatSplashStartup.a(Unknown Source:432)
    at com.tencent.mm.legacy.app.WeChatSplash.a(Unknown Source:123)
    at re5.p.b(Unknown Source:362)
    at com.tencent.mm.app.MMApplicationLike.onCreate(Unknown Source:145)
    at com.tencent.tinker.entry.TinkerApplicationInlineFence.handleMessageImpl(Unknown Source:152)
    at com.tencent.tinker.entry.TinkerApplicationInlineFence.handleMessage_$noinline$(Unknown Source:3)
    at com.tencent.tinker.entry.TinkerApplicationInlineFence.handleMessage(Unknown Source:0)
    at com.tencent.tinker.loader.app.TinkerInlineFenceAction.callOnCreate(Unknown Source:5)
    at com.tencent.tinker.loader.app.TinkerApplication.onCreate(Unknown Source:8)
    at com.example.multiopen.VirtualApplications.ensure(VirtualApplications.kt:45)
    at com.example.multiopen.HookInstrumentation.newActivity(HookInstrumentation.kt:35)
    … （其余 13 帧为 Android 框架帧，与 #1 相同）
```

### 对比与标注

- 三次栈是否一样: **完全一样**（真卡死，不是在慢慢推进）。state 三次都是 `WAITING`。
- 栈顶: `Unsafe.park ← LockSupport.park ← ForkJoinTask.awaitDone ← ForkJoinTask.get`，即主线程在**阻塞等待一个 ForkJoinTask 的结果**。
- 紧挨着的微信自己的帧: `ph5.n0.l(Unknown Source:18)` ← `ph5.n0.a(:73)` ← `gp0.q1.call(:47)` ← `yu5.f.run` ← `yu5.h.a` ← `xu5.q.d/r/e` ←
  `com.tencent.mm.legacy.app.WeChatSplashStartup.a(:432)` ← `WeChatSplash.a(:123)` ← `re5.p.b(:362)` ← `MMApplicationLike.onCreate(:145)`。
  没有 CountDownLatch、Binder、nativePollOnce、Service 绑定之类的帧；等待点就是 `ph5.n0.l` 里的 `ForkJoinTask.get()`。
- 这条调用链（`ph5.n0.a ← gp0.q1.call ← yu5.f.run ← yu5.h.a ← xu5.q.d/r/e ← WeChatSplashStartup.a`）和之前 mCoreAccount 崩溃那一轮里
  `plugin Application.onCreate failed` 栈上 "Stacktraces.below.indicate.where.the.transit.task.being.submitted" 分隔线下面的那几帧是同一条；
  当时那个 ForkJoinTask 里面的任务抛了 mCoreAccount 异常，现在 mCoreAccount 异常没了，但任务**没有完成**，主线程一直等。
- 本地 AI 的观察（未看宿主代码，未验证）:
  - 进程里**没有任何名字含 ForkJoin / commonPool 的线程**；这个 ForkJoinTask 看起来是由微信自己的线程池（`[GT]ColdPool#N` 17 个、`[GT]HotPool#N` 8 个等）在执行。
  - 进程 PID 9011 里共有约 104 个线程（本轮重新数过，线程名集合和上一轮完全一致），**全部是 S（睡眠）状态**，CPU 0.0%。
    也就是说没有任何线程在跑，主线程在等的那个任务既没完成也没在运行，更像是它依赖的另一件事没发生（等一个永远不会来的回调/通知/条件），而不是慢。
  - 我没有看到被等待任务具体是什么，这需要看 `ph5.n0`、`gp0.q1` 的逻辑；Java 线程栈只有主线程的，没有后台线程的栈。
    如果云端要继续定位，可以让看门狗同时把 `[GT]ColdPool` / `[GT]HotPool` / `wc_srvinit_N` 等线程的栈也打出来（Thread.getAllStackTraces()）。

## 异常计数
FATAL EXCEPTION: 0 次
mCoreAccount not initialized: 0 次
plugin Application.onCreate failed: 0 次
Resources$NotFoundException: 0 次
ACCESS_NETWORK_STATE: 0 次
baseRevision must not be null: 0 次
Skeleton not initialized: 0 次
UnsatisfiedLinkError: 0 次
ClassNotFoundException: 0 次
SecurityException: 0 次
NoClassDefFoundError: 0 次
main-thread stack 日志: 3 条（#1 19:56:02.521 / #2 19:56:08.523 / #3 19:56:14.526，全部 state=WAITING，内容相同）
