# 测试结果

- commit: 69e0f7e（Log Intent flags in rewriteIntent to diagnose WelcomeActivity launch loop）
- 编译: 成功（:app，BUILD SUCCESSFUL）
- 设备: 小米 M2011K2C / Android 14
- 被测 APK: 微信 8.0.78（手机上已装的单个 base.apk，280614450 字节，arm64-v8a，非 split）
- 现象: 与上一轮一致：Application 创建成功、LauncherUI 起来了，随后 LauncherUI 反复 `startActivity(WelcomeActivity)`，被宿主重写成 StubActivity，
  但 WelcomeActivity 从未被创建；屏幕只有 StubActivity 的**空白白屏**，没有任何微信界面。没有 FATAL EXCEPTION，进程存活。
  这一轮采样（点击后约 +8 / +17 / +25 秒的三次）**没有看到 ANR 弹窗**（上一轮在第二次采样时就出现了）；MIUI 的 APP_SCOUT 记录了 3 次 `APP_SCOUT_WARNING/HANG`（20:20:05、20:20:24、20:20:27）。
  **本轮拿到了系统侧证据：系统对每一次启动都返回 `result code=3`（START_DELIVERED_TO_TOP）——不是新建 Activity，而是把 Intent 送给了已在栈顶的 StubActivity。**

## 关键 logcat（只含 MultiOpen 标签 + 崩溃栈 + 系统 ActivityTaskManager 的 StubActivity 行）

### 1. rewriteIntent 的 flags（本轮重点 1）

```
20:20:31.398 I/MultiOpen: rewriteIntent: com.tencent.mm.plugin.account.ui.WelcomeActivity -> stub (flags=0x20000000)
20:20:31.590 I/MultiOpen: rewriteIntent: com.tencent.mm.plugin.account.ui.WelcomeActivity -> stub (flags=0x20000000)
20:20:31.894 I/MultiOpen: rewriteIntent: com.tencent.mm.plugin.account.ui.WelcomeActivity -> stub (flags=0x20000000)
20:20:32.010 I/MultiOpen: rewriteIntent: com.tencent.mm.plugin.account.ui.WelcomeActivity -> stub (flags=0x20000000)
20:20:32.159 I/MultiOpen: rewriteIntent: com.tencent.mm.plugin.account.ui.WelcomeActivity -> stub (flags=0x20000000)
20:20:32.258 I/MultiOpen: rewriteIntent: com.tencent.mm.plugin.account.ui.WelcomeActivity -> stub (flags=0x20000000)
   …（共 319 条，最后一条 20:20:53.541；所有 319 条的 flags 都是同一个值）
```

**flags 值: 0x20000000（= `FLAG_ACTIVITY_SINGLE_TOP`），319 次全部相同，没有别的值。**
（rewriteIntent 全部都是 WelcomeActivity，没有其他目标。）

### 2. 系统 ActivityTaskManager 的决策（本轮重点 2）

`ActivityTaskManager` 里和 `StubActivity` 有关的 `START` 行只有两种。第一种（1 条，启动 LauncherUI 那次，没有 flags）：

```
20:20:22.079 I/ActivityTaskManager( 1985): START u0 {cmp=com.example.multiopen/.StubActivity (has extras)} with LAUNCH_MULTIPLE from uid 10251 from pid 12235 callingPackage com.example.multiopen (BAL_ALLOW_VISIBLE_WINDOW) result code=0
```

第二种（**319 条，与 rewriteIntent 的 319 条一一对应**，开头/结尾各一条）：

```
20:20:31.401 I/ActivityTaskManager( 1985): START u0 {flg=0x20000000 cmp=com.example.multiopen/.StubActivity (has extras)} with LAUNCH_MULTIPLE from uid 10251 from pid 12235 callingPackage com.example.multiopen (BAL_ALLOW_VISIBLE_WINDOW) result code=3
20:20:53.545 I/ActivityTaskManager( 1985): START u0 {flg=0x20000000 cmp=com.example.multiopen/.StubActivity (has extras)} with LAUNCH_MULTIPLE from uid 10251 from pid 12235 callingPackage com.example.multiopen (BAL_ALLOW_VISIBLE_WINDOW) result code=3
```

`result code` 的分布（只统计 StubActivity 的 START）：`code=0` 1 条，**`code=3` 319 条**。
`3` = `ActivityManager.START_DELIVERED_TO_TOP`：目标 Activity 已经在任务栈顶，系统**没有创建新实例**，而是把新 Intent 通过 `onNewIntent` 投递给栈顶实例。
本轮系统侧日志里**没有** `deliverNewIntent` / `Warning: Activity not started` / 复用任务的显式文字，只有 `result code=3`；
其余 ActivityTaskManager 行都与此无关（MainActivity 的 `Displayed ... +373ms`、DocumentsUI 选择器的 START/Displayed、"The Process ... Already Exists in BG"）。
另外 WindowManager 一共 6 条 W 级行，其中只有 1 条提到本应用：`onSyncReparent ... ActivityRecord{... com.example.multiopen/.StubActivity t-1}`；其余 5 条我没有逐条核对内容。

### 3. 清单里 WelcomeActivity / LauncherUI 的 launchMode / taskAffinity（本轮重点 3，aapt2 34.0.0 `dump xmltree --file AndroidManifest.xml`）

```
WelcomeActivity  (E: activity line=4272)
    android:theme=@0x7f1202b2
    android:name="com.tencent.mm.plugin.account.ui.WelcomeActivity"
    android:screenOrientation=1
    android:configChanges=0x000004a0
  → launchMode: 未声明（= standard, 0）；taskAffinity: 未声明（= 默认，应用包名）

LauncherUI  (E: activity line=541)
    android:theme=@0x7f1202b2
    android:label=@0x7f100fe8
    android:name="com.tencent.mm.ui.LauncherUI"
    android:exported=true
    android:launchMode=1          ← singleTop
    android:configChanges=0x00000da0
    android:windowSoftInputMode=0x00000032
  → launchMode: 1（singleTop）；taskAffinity: 未声明（= 默认）
```

补充：整个清单里显式声明了 launchMode 的 activity 共 400 个：`=0`（standard）5 个、`=1`（singleTop）242 个、`=2`（singleTask）130 个、`=3`（singleInstance）23 个。
有 taskAffinity 的 activity 很少（例如 `com.tencent.mm.notification`、`com.tencent.mm.finder`、`.AppBrandUI`），WelcomeActivity 和 LauncherUI 都没有。

### 4. 其他 MultiOpen 行（折叠 provider ×30 / receiver ×20）

```
20:19:56.541 I/MultiOpen: IActivityManager hooked
20:19:56.545 I/MultiOpen: IPackageManager hooked
20:20:06.646 I/MultiOpen: extracted 209 so (arm64-v8a) -> /data/user/0/com.example.multiopen/files/virtual/1790853602811/lib
20:20:22.059 I/MultiOpen: resources built: cookie=15, apk=/data/user/0/com.example.multiopen/files/virtual/1790853602811/base.apk (280614450 bytes)
20:20:22.059 I/MultiOpen: resources self-check OK: 0x7f1202a7 -> com.tencent.mm:style/lc
20:20:23.234 I/MultiOpen: resources built: ...（同上，第 2 次）
20:20:23.234 I/MultiOpen: resources self-check OK: 0x7f1202a7 -> com.tencent.mm:style/lc
20:20:23.234 I/MultiOpen: fake process name -> com.tencent.mm
20:20:24.158 I/MultiOpen: set mInitialApplication -> com.tencent.mm.app.Application
20:20:30.070 I/MultiOpen: virtual Service created: com.tencent.mm.service.ProcessService$MMProcessService
20:20:30.942 E/MultiOpen: plugin Application.onCreate failed
20:20:30.942 E/MultiOpen: java.lang.IllegalStateException: java.lang.IllegalStateException: Scene Activity process mismatch: component=com.tencent.wxpay.internal.presentation.MainProcessPaySceneActivity declared=null current=com.tencent.mm
20:20:30.943 I/MultiOpen: virtual Application created: com.tencent.mm.app.Application
20:20:31.000 I/MultiOpen: newActivity: stub -> com.tencent.mm.ui.LauncherUI
20:20:31.470 I/MultiOpen: virtual Service created: com.tencent.mm.ipcinvoker.wx_extension.service.PushProcessIPCService
20:20:31.771 I/MultiOpen: virtual Service created: androidx.work.impl.background.systemalarm.SystemAlarmService
```

（`Scene Activity process mismatch ... declared=null` 与上一轮完全相同：被宿主捕获，没有崩溃。）

整个期间只有 **1 次** `newActivity: stub`（LauncherUI），WelcomeActivity 一次都没被创建。

看门狗主线程栈（3 条）:
- #1（20:20:30.159，state=WAITING）：还在 Application.onCreate 里，栈顶 `ForkJoinTask.get ← ph5.n0.l ← ph5.n0.a ← gp0.q1.call …`（死锁修复后这一段能正常走完）。
- #2（20:20:36.160，state=RUNNABLE）：`com.tencent.mm.plugin.lite.logic.u0.getQualifierAttribute ← qs.g.e ← qs.g.d ← qs.b.a ← qs.f.hasNext ← com.tencent.mm.sdk.event.d.d ← IEvent.e`（在微信事件分发里）。
- #3（20:20:42.173，state=RUNNABLE）：`BinderProxy.transactNative ← … IActivityClientController$Stub$Proxy.overridePendingTransition ← Activity.overridePendingTransition ← kj5.f.h(:12)`。
主线程在 #2、#3 里都不是被锁住，是在忙（RUNNABLE），与上一轮的"主线程在 LauncherUI.onResume → D7 → startActivity 里循环"一致。

## 本地 AI 的分析（未改源码，未验证修复）

- 系统侧已经确认了机制：**不是系统拒绝启动，而是 `START_DELIVERED_TO_TOP`（`result code=3`）**。
  原因链（有日志/清单证据）:
  1. 宿主把 `WelcomeActivity` 重写成同一个 `com.example.multiopen/.StubActivity`（所有虚拟 Activity 共用一个桩）。
  2. 此时栈顶就是 StubActivity（承载 LauncherUI 的那个实例）。
  3. 微信发起的 Intent 带 `FLAG_ACTIVITY_SINGLE_TOP`（`flags=0x20000000`，319 次全是它）。
  4. 系统看到"目标 StubActivity == 栈顶且带 SINGLE_TOP"，不创建新实例，直接 `onNewIntent` 投递给栈顶的 StubActivity，返回 3。
  5. 所以宿主的 `newActivity` 不会再被调用，WelcomeActivity 永远不会被创建；LauncherUI 的循环等不到它出现，就一直重试。
- 说明：这里是**单桩**造成的——所有目标都映射到同一个桩，系统无法区分。WelcomeActivity 本身是 standard（launchMode 未声明），LauncherUI 是 singleTop(1)，
  这两条清单信息说明问题**不是来自微信清单里的 launchMode**，而是来自**微信发出的 Intent 自带的 SINGLE_TOP flag + 栈顶恰好是同一个桩**。
- 可能的修法思路（供云端决定，我没有试）:
  - 做"桩池"：准备多个 StubActivity 子类（Stub1、Stub2 …），每个被打开的虚拟 Activity 占一个，重写目标时选一个当前未占用的桩；这样栈顶与目标不同，系统会创建新实例。
  - 或者在重写 Intent 时清掉 `FLAG_ACTIVITY_SINGLE_TOP`（0x20000000）。这只能避免"投递到栈顶"，但仍然是单桩，系统要创建同一个桩的第二个实例，
    具体行为（standard 桩会新建实例）我没有验证。
  - 如果桩要对应微信里 launchMode 为 singleTask/singleInstance 的 Activity（清单里有 130 个 singleTask、23 个 singleInstance），也需要桩池里有对应 launchMode 的桩。

## 异常计数
FATAL EXCEPTION: 0 次
plugin Application.onCreate failed: 1 次（被捕获：Scene Activity process mismatch，与上一轮相同）
rewriteIntent(WelcomeActivity -> stub): 319 次，flags 全部是 0x20000000
ActivityTaskManager START StubActivity: result code=3 共 319 次，result code=0 共 1 次
virtual Application created: 1 次
newActivity: stub: 1 次（LauncherUI）
main-thread stack: 3 条（#1 WAITING，#2/#3 RUNNABLE）
mCoreAccount not initialized: 0 次
Resources$NotFoundException: 0 次
ACCESS_NETWORK_STATE: 0 次
baseRevision must not be null: 0 次
Skeleton not initialized: 0 次
UnsatisfiedLinkError: 0 次
ClassNotFoundException: 0 次
SecurityException: 0 次
NoClassDefFoundError: 0 次
ANR 弹窗: 本轮采样里没有看到（上一轮有）
