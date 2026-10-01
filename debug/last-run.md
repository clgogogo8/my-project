# 测试结果

- commit: f70d002（Dump background-thread stacks to locate the stuck ForkJoinTask）
- 编译: 成功（:app，BUILD SUCCESSFUL）
- 设备: 小米 M2011K2C / Android 14
- 被测 APK: 微信 8.0.78（手机上已装的单个 base.apk，280614450 字节，arm64-v8a，非 split）
- 现象: 与上一轮一致：不崩溃、卡死；黑屏/无焦点；约 +10 秒出现系统 ANR 弹窗"MultiOpen没有响应"（我没点"确定"，观察到约 +40 秒，弹窗一直在）。
  单个进程一直存活，CPU 0.0%，没有任何微信界面。
  **本轮定位到了死锁**：一个微信后台线程 `wc_srvinit_3` 处于 `BLOCKED`，卡在宿主的 `VirtualApplications.ensure` 入口，
  而主线程正持有 `VirtualApplications` 这把锁在 `ensure` 里执行 `app.onCreate()`，且在等这个后台线程所属的任务完成。**两边互相等待。**

## 关键 logcat（只含 MultiOpen 标签 + 崩溃栈）

看门狗之外的全部 MultiOpen 行（30 条 provider installed 已折叠；没有 receiver registered、没有 virtual Application created、没有 newActivity: stub 日志行；没有任何 E 级行）：

```
20:01:37.679 I/MultiOpen: IActivityManager hooked
20:01:37.682 I/MultiOpen: IPackageManager hooked
20:01:47.977 I/MultiOpen: extracted 209 so (arm64-v8a) -> /data/user/0/com.example.multiopen/files/virtual/1790852503953/lib
20:02:17.619 I/MultiOpen: resources built: cookie=15, apk=/data/user/0/com.example.multiopen/files/virtual/1790852503953/base.apk (280614450 bytes)
20:02:17.619 I/MultiOpen: resources self-check OK: 0x7f1202a7 -> com.tencent.mm:style/lc
20:02:18.727 I/MultiOpen: resources built: ...（同上，第 2 次）
20:02:18.727 I/MultiOpen: resources self-check OK: 0x7f1202a7 -> com.tencent.mm:style/lc
20:02:18.728 I/MultiOpen: fake process name -> com.tencent.mm
20:02:19.641 I/MultiOpen: set mInitialApplication -> com.tencent.mm.app.Application
   …（30 条 provider installed）
```

没有崩溃栈（没有 FATAL EXCEPTION，没有 AndroidRuntime 输出）。

## 本轮云端 Claude 要求的额外诊断

### 主线程 #1/#2/#3

三条（20:02:24.729 / 20:02:30.732 / 20:02:36.759，state=WAITING）去掉时间戳后 md5 完全相同，并且**和上一轮（35e13d8）的三条栈逐行相同**。仍卡同一处：

```
at jdk.internal.misc.Unsafe.park(Native Method)
at java.util.concurrent.locks.LockSupport.park(LockSupport.java:341)
at java.util.concurrent.ForkJoinTask.awaitDone(ForkJoinTask.java:472)
at java.util.concurrent.ForkJoinTask.get(ForkJoinTask.java:983)
at ph5.n0.l(Unknown Source:18)
at ph5.n0.a(Unknown Source:73)
at gp0.q1.call(Unknown Source:47)
at yu5.f.run(Unknown Source:44)
at yu5.h.a(Unknown Source:80)
at xu5.q.d(Unknown Source:124) ← xu5.q.r(:41) ← xu5.q.e(:3)
at com.tencent.mm.legacy.app.WeChatSplashStartup.a(Unknown Source:432)
at com.tencent.mm.legacy.app.WeChatSplash.a(Unknown Source:123)
at re5.p.b(Unknown Source:362)
at com.tencent.mm.app.MMApplicationLike.onCreate(Unknown Source:145)
at com.tencent.tinker.entry.TinkerApplicationInlineFence.handleMessageImpl(Unknown Source:152) ← handleMessage_$noinline$ ← handleMessage
at com.tencent.tinker.loader.app.TinkerInlineFenceAction.callOnCreate(Unknown Source:5)
at com.tencent.tinker.loader.app.TinkerApplication.onCreate(Unknown Source:8)
at com.example.multiopen.VirtualApplications.ensure(VirtualApplications.kt:45)
at com.example.multiopen.HookInstrumentation.newActivity(HookInstrumentation.kt:35)
at android.app.ActivityThread.performLaunchActivity(ActivityThread.java:3902) … （其余为 Android 框架帧）
```

### 所有 bg thread 的栈（共 38 个线程，按"栈完全相同"分成 8 组；每组完整栈贴一次，组内线程名列全）

时间都是 20:02:30.757 前后（看门狗第 2 次采样）。

**组 8（最关键，1 个线程，BLOCKED）：`wc_srvinit_3`**

```
bg thread 'wc_srvinit_3' (BLOCKED):
    at com.example.multiopen.VirtualApplications.ensure(Unknown Source:0)
    at com.example.multiopen.VirtualServices.create(VirtualServices.kt:89)
    at com.example.multiopen.VirtualServices.bind(VirtualServices.kt:57)
    at com.example.multiopen.VirtualContext.bindService(VirtualContext.kt:50)
    at android.content.ContextWrapper.bindService(ContextWrapper.java:872)
    at qg5.m1.d(Unknown Source:66)
    at vu1.l1.onCreate(Unknown Source:1235)
    at ph5.w.access$1000(Unknown Source:16)
    at ph5.u.compute(Unknown Source:123)
    at java.util.concurrent.RecursiveAction.exec(RecursiveAction.java:194)
    at java.util.concurrent.ForkJoinTask.doExec(ForkJoinTask.java:377)
    at java.util.concurrent.ForkJoinTask.invoke(ForkJoinTask.java:690)
    at ph5.v.compute(Unknown Source:35)
    at java.util.concurrent.RecursiveAction.exec(RecursiveAction.java:194)
    at java.util.concurrent.ForkJoinTask.doExec(ForkJoinTask.java:377)
    at java.util.concurrent.ForkJoinTask.invoke(ForkJoinTask.java:690)
    at ph5.w.transitLifecycleStatusOnDemand(Unknown Source:254)
    at ph5.n0.j(Unknown Source:20)
    at ph5.g0.run(Unknown Source:23)
    at java.util.concurrent.ForkJoinTask$AdaptedRunnableAction.exec(ForkJoinTask.java:1380)
```

**组 2（5 个线程，WAITING，空闲的 ForkJoin worker）：`wc_srvinit_0`、`wc_srvinit_1`、`wc_srvinit_2`、`wc_srvinit_4`、`wc_srvinit_5`**

```
    at jdk.internal.misc.Unsafe.park(Native Method)
    at java.util.concurrent.locks.LockSupport.park(LockSupport.java:341)
    at java.util.concurrent.ForkJoinPool.awaitWork(ForkJoinPool.java:1727)
    at java.util.concurrent.ForkJoinPool.runWorker(ForkJoinPool.java:1626)
    at java.util.concurrent.ForkJoinWorkerThread.run(ForkJoinWorkerThread.java:165)
```

**组 1（24 个线程，WAITING，空闲的线程池 worker）：`[GT]ColdPool#0` ～ `#15`（16 个）和 `[GT]HotPool#0` ～ `#7`（8 个）**

```
    at jdk.internal.misc.Unsafe.park(Native Method)
    at java.util.concurrent.locks.LockSupport.park(LockSupport.java:341)
    at java.util.concurrent.locks.AbstractQueuedSynchronizer$ConditionNode.block(AbstractQueuedSynchronizer.java:506)
    at java.util.concurrent.ForkJoinPool.unmanagedBlock(ForkJoinPool.java:3466)
    at java.util.concurrent.ForkJoinPool.managedBlock(ForkJoinPool.java:3437)
    at java.util.concurrent.locks.AbstractQueuedSynchronizer$ConditionObject.await(AbstractQueuedSynchronizer.java:1623)
    at java.util.concurrent.LinkedBlockingQueue.take(LinkedBlockingQueue.java:435)
    at java.util.concurrent.ThreadPoolExecutor.getTask(ThreadPoolExecutor.java:1177)
    at java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1237)
    at java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:668)
    at j36.c.run(Unknown Source:2)
    at java.lang.Thread.run(Thread.java:1012)
```

**组 3（2 个线程，WAITING）：`MMCrashANRThread-0`、`MMCrashANRThread-1`**

```
    at jdk.internal.misc.Unsafe.park(Native Method)
    at java.util.concurrent.locks.LockSupport.park(LockSupport.java:341)
    at java.util.concurrent.locks.AbstractQueuedSynchronizer$ConditionNode.block(AbstractQueuedSynchronizer.java:506)
    at java.util.concurrent.ForkJoinPool.unmanagedBlock(ForkJoinPool.java:3466)
    at java.util.concurrent.ForkJoinPool.managedBlock(ForkJoinPool.java:3437)
    at java.util.concurrent.locks.AbstractQueuedSynchronizer$ConditionObject.await(AbstractQueuedSynchronizer.java:1623)
    at java.util.concurrent.ScheduledThreadPoolExecutor$DelayedWorkQueue.take(ScheduledThreadPoolExecutor.java:1176)
    at java.util.concurrent.ScheduledThreadPoolExecutor$DelayedWorkQueue.take(ScheduledThreadPoolExecutor.java:905)
    at java.util.concurrent.ThreadPoolExecutor.getTask(ThreadPoolExecutor.java:1177)
    at java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1237)
    at java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:668)
    at java.lang.Thread.run(Thread.java:1012)
```

**组 4（3 个线程，TIMED_WAITING）：`matrix_x_0`、`matrix_x_1`、`matrix_x_2`**

```
    at jdk.internal.misc.Unsafe.park(Native Method)
    at java.util.concurrent.locks.LockSupport.parkNanos(LockSupport.java:252)
    at java.util.concurrent.SynchronousQueue$TransferStack.transfer(SynchronousQueue.java:401)
    at java.util.concurrent.SynchronousQueue.poll(SynchronousQueue.java:903)
    at com.tencent.matrix.lifecycle.h0.poll(SourceFile:1)
    at java.util.concurrent.ThreadPoolExecutor.getTask(ThreadPoolExecutor.java:1176)
    at java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1237)
    at java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:668)
    at com.tencent.matrix.lifecycle.k0.run(Unknown Source:57)
    at java.lang.Thread.run(Thread.java:1012)
```

**组 5（1 个线程，WAITING）：`pool-4-thread-1`**（空闲线程池 worker：`LinkedBlockingQueue.take ← ThreadPoolExecutor.getTask ← runWorker ← Worker.run ← Thread.run`，同组 1 的前 10 帧，没有 `j36.c.run` 帧）

**组 6（1 个线程，WAITING）：`Recovery.LogWriter`**

```
    at jdk.internal.misc.Unsafe.park(Native Method)
    at java.util.concurrent.locks.LockSupport.park(LockSupport.java:341)
    at java.util.concurrent.locks.AbstractQueuedSynchronizer$ConditionNode.block(AbstractQueuedSynchronizer.java:506)
    at java.util.concurrent.ForkJoinPool.unmanagedBlock(ForkJoinPool.java:3466)
    at java.util.concurrent.ForkJoinPool.managedBlock(ForkJoinPool.java:3437)
    at java.util.concurrent.locks.AbstractQueuedSynchronizer$ConditionObject.await(AbstractQueuedSynchronizer.java:1623)
    at java.util.concurrent.LinkedBlockingQueue.take(LinkedBlockingQueue.java:435)
    at wc5.n0.run(Unknown Source:35)
    at java.lang.Thread.run(Thread.java:1012)
```

**组 7（1 个线程，WAITING）：`Zidl Java DestructorThread`**

```
    at java.lang.Object.wait(Native Method)
    at java.lang.Object.wait(Object.java:386)
    at java.lang.ref.ReferenceQueue.remove(ReferenceQueue.java:210)
    at java.lang.ref.ReferenceQueue.remove(ReferenceQueue.java:231)
    at com.tencent.wechat.zidl2.DestructorThread$1.run(Unknown Source:4)
```

### 直接回答 next-step 里的问题

- 有没有名字像 `ForkJoinPool-*-worker-*` / `ForkJoinPool.commonPool-worker-*` 的线程: **没有这种名字**。
  但 **`wc_srvinit_0…5` 就是 ForkJoin worker**（栈里是 `ForkJoinWorkerThread.run ← ForkJoinPool.runWorker`，只是微信给线程起了自己的名字，所以按名字找不到）。
  6 个里 5 个空闲（`ForkJoinPool.awaitWork`），**1 个 `wc_srvinit_3` 在执行任务但 BLOCKED**。
- `[GT]ColdPool#N` / `[GT]HotPool#N`（共 24 个）: 全部是空闲的普通线程池 worker（`LinkedBlockingQueue.take`），**不在等任何别的东西，没有卡在 com.tencent.* 调用上**。
- 等待链（有栈可证）:
  1. 主线程 `main`: 持有 `VirtualApplications` 的锁（`VirtualApplications.ensure` 是 `@Synchronized`，主线程在它里面第 45 行跑 `app.onCreate()`），
     并在 `ForkJoinTask.get()` 上等微信的启动任务（`ph5.n0.l`）完成。
  2. 那个任务由 ForkJoin worker `wc_srvinit_3` 执行，执行到 `vu1.l1.onCreate → qg5.m1.d → ContextWrapper.bindService`，
     走进宿主的 `VirtualContext.bindService → VirtualServices.bind → VirtualServices.create(:89) → VirtualApplications.ensure`。
  3. `wc_srvinit_3` 进不去 `VirtualApplications.ensure`（`BLOCKED` = 在等对象监视器），因为那把锁被主线程持有。
  4. 主线程在等 `wc_srvinit_3` 的任务 → `wc_srvinit_3` 在等主线程释放锁。**循环等待，死锁。**
- 附带: `VirtualServices.bind` 本身也是 `@Synchronized`（VirtualServices.kt:52 附近），所以 `wc_srvinit_3` 是在**持有 `VirtualServices` 的锁**的情况下去抢 `VirtualApplications` 的锁。
  （我读了源码确认这两处 `@Synchronized`：`VirtualApplications.kt` 第 11-16 行，`VirtualServices.kt` 的 `bind`。）

### 本地 AI 的分析和建议（未改源码，未验证修复）

- 根因（宿主代码问题，不是微信的）: `VirtualApplications.ensure` 在整个创建 Application 的过程中（包括耗时的 `app.onCreate()`）一直持有对象级的 `@Synchronized` 锁。
  微信的 `Application.onCreate` 会在主线程上**同步等待**后台 ForkJoin 任务，而这些后台任务又要调用宿主的 `bindService`/`startService`，反过来需要进同一个 `ensure`，就死锁了。
  （`Application.attachBaseContext` 阶段的同线程重入没问题，因为是可重入锁；问题是**跨线程**。）
- 可能的修法思路（供云端决定，我没有试）:
  - 不要在持锁状态下执行 `app.onCreate()`（以及 `VirtualProviders.ensure`）：先在锁内登记占位 Application，然后在锁外执行 provider 和 onCreate；
    其他线程再调用 `ensure` 时，如果发现该实例"正在初始化"，应直接返回已登记的占位 Application，不要去抢锁等待（或者只在很短的时间内持锁做登记）。
  - `VirtualServices.bind` 的 `@Synchronized` 也最好缩小范围，避免在持有它的同时调用可能阻塞的 `create`。
- 本轮之后的风险提示: 一旦死锁解掉，微信可能会继续往下跑，新的问题会在后面暴露，需要继续观察。

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
main-thread stack: 3 条（全部 WAITING，内容相同，与上一轮相同）
bg thread 栈: 38 条（8 种不同的栈；其中 1 个 BLOCKED：`wc_srvinit_3`）
ANR 弹窗: 有（约 +10 秒出现，之后一直在）
