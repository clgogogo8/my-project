# 测试结果

- commit: 83bd5fc（launchMode-aware stub pool (singleTask/singleInstance stubs)；含 2198c40 外部存储重定向）
- 编译: 成功（:app，BUILD SUCCESSFUL）
- 设备: 小米 M2011K2C / Android 14（**手机上同时装着真正的微信 com.tencent.mm，uid 10260**；被测的是它的 APK 副本放进多开容器）
- 被测 APK: 微信 8.0.78（手机上已装的单个 base.apk，280614450 字节，arm64-v8a，非 split）
- 现象:
  1. **回归通过**：欢迎页仍然正常显示（深蓝地球图 + 右上"语言" + 底部绿色"登录"、白色"注册"）；没有 FATAL；启动期 ANR 弹窗和上一轮一样出现，我点系统弹窗上的"等待"后消失。
  2. **点"登录"（只点了这一下，没有输入任何内容，没有登录）→ 新的崩溃**：登录页 `MobileInputUI` 被创建、系统记录 `Displayed ...StubActivity3 +569ms`，
     约 **0.46 秒后宿主进程被 SIGSEGV 杀死**（`Process 25414 exited due to signal 11 (Segmentation fault)`）；系统随后重启进程，回到欢迎页。
     **AndroidRuntime 里没有 FATAL EXCEPTION**（native 崩溃，不是 Java 异常）。
     我的截图是点击后 3 秒才截的，那时已经是重启后的欢迎页，所以**我没有亲眼看到登录页的画面**，只有系统日志证明它被创建并显示过。

## 关键 logcat（MultiOpen + 系统对本进程的行；全部是这一次点击"登录"前后的）

### 点击"登录"的时间线（PID 25414，设备时钟）

```
21:04:58.974 I/MultiOpen: rewriteIntent: com.tencent.mm.plugin.account.ui.MobileInputUI -> stub (flags=0x0)
21:04:58.984 I/ActivityTaskManager: START u0 {cmp=com.example.multiopen/.StubActivity3 (has extras)} ... result code=0
21:04:59.019 I/MultiOpen: newActivity: stub -> com.tencent.mm.plugin.account.ui.MobileInputUI
21:04:59.353 I/MultiOpen: virtual Service created: com.tencent.mm.service.ProcessService$SupportProcessService
21:04:59.355 I/wm_on_create_called: ...StubActivity3,performCreate
21:04:59.395 I/wm_on_resume_called: ...StubActivity3,RESUME_ACTIVITY
21:04:59.555 I/ActivityTaskManager: Displayed com.example.multiopen/.StubActivity3 for user 0: +569ms
21:04:59.601 I/chromium: [...mm_cronet_network_change_notify.cc(59)] remain size: 1
21:04:59.603 I/AppsFilter: interaction: PackageSetting{... com.example.multiopen/10251} -> PackageSetting{... com.tencent.mm/10260} BLOCKED
21:04:59.614 W/System.err: java.lang.ExceptionInInitializerError
21:04:59.614 W/System.err:     at org.chromium.base.BuildInfo.getInstance(Unknown Source:10)
21:04:59.614 W/System.err:     at org.chromium.base.BuildInfo.getAll(Unknown Source:0)
21:04:59.614 W/System.err: Caused by: java.lang.IllegalArgumentException: Unknown package: com.tencent.mm
21:04:59.614 W/System.err:     at android.content.pm.IPackageManager$Stub$Proxy.getInstallerPackageName(IPackageManager.java:5422)
21:04:59.614 W/System.err:     at java.lang.reflect.Method.invoke(Native Method)
21:04:59.614 W/System.err:     at ig5.n1.invoke(Unknown Source:202)            ← 一个 IPackageManager 的动态代理（`ig5.n1` 是混淆后的类名，看起来是微信自己的类，不是宿主 `com.example.multiopen` 包下的类；我没有进一步确认）
21:04:59.614 W/System.err:     at java.lang.reflect.Proxy.invoke(Proxy.java:1006)
21:04:59.614 W/System.err:     at $Proxy11.getInstallerPackageName(Unknown Source)
21:04:59.614 W/System.err:     at android.app.ApplicationPackageManager.getInstallerPackageName(ApplicationPackageManager.java:2582)
21:04:59.614 W/System.err:     at org.chromium.base.BuildInfo.<init>(SourceFile:45)
21:04:59.614 W/System.err:     at org.chromium.base.BuildInfo.<init>(SourceFile:1)
21:04:59.614 W/System.err:     at org.chromium.base.BuildInfo$Holder.<clinit>(Unknown Source:3)
21:04:59.614 W/System.err: Caused by: android.os.RemoteException: Remote stack trace:
21:04:59.614 W/System.err:     at com.android.server.pm.ComputerEngine.getInstallerPackageName(ComputerEngine.java:5069) ...
21:04:59.757 V/NativeCrash(29303): Dump Java in cloned process
21:04:59.785 E/NativeCrash(25414): Dumper process exited with status -11
21:04:59.785 V/NativeCrash(25414): Call crash dump callback.
21:05:00.019 I/ActivityManager: Process com.example.multiopen (pid 25414) has died: fg  TOP
21:05:00.020 I/am_proc_died: [0,25414,com.example.multiopen,0,2]
21:05:00.021 I/Zygote: Process 25414 exited due to signal 11 (Segmentation fault)
21:05:00.035 W/ActivityTaskManager: Force removing ActivityRecord{... com.example.multiopen/.StubActivity3 t86}: app died, no saved state
21:05:00.053 I/ActivityManager: Start proc 26515:com.example.multiopen/u0a251 for top-activity {com.example.multiopen/com.example.multiopen.StubActivity2} caller=com.example.multiopen
```

系统重启进程后（PID 26515）的 MultiOpen 行（系统自动恢复栈顶的 StubActivity2，微信 Application 再创建一遍，回到欢迎页）：

```
21:05:00.385 I/MultiOpen: IActivityManager hooked
21:05:00.387 I/MultiOpen: IPackageManager hooked
21:05:00.531 I/MultiOpen: resources built: cookie=15, apk=.../virtual/1790856202165/base.apk (280614450 bytes)
21:05:00.531 I/MultiOpen: resources self-check OK: 0x7f1202a7 -> com.tencent.mm:style/lc
21:05:00.532 I/MultiOpen: fake process name -> com.tencent.mm
21:05:01.266 I/MultiOpen: set mInitialApplication -> com.tencent.mm.app.Application
21:05:02.783 I/MultiOpen: virtual Service created: com.tencent.mm.service.ProcessService$MMProcessService
21:05:03.036 E/MultiOpen: plugin Application.onCreate failed     （同前：Scene Activity process mismatch ... declared=null current=com.tencent.mm）
21:05:03.037 I/MultiOpen: virtual Application created: com.tencent.mm.app.Application
21:05:03.105 I/MultiOpen: newActivity: stub -> com.tencent.mm.plugin.account.ui.WelcomeActivity
```

### 欢迎页阶段（点"登录"之前，进程 25414）

```
rewriteIntent: ...WelcomeActivity -> stub (flags=0x20000000)  ×2
newActivity: stub -> LauncherUI；newActivity: stub -> WelcomeActivity
START StubActivity code=0、StubActivity1 code=0、StubActivity2 code=0（与上一轮相同）
```
（30 条 provider installed、20 条 receiver registered 已折叠；这一阶段没有 FATAL。）

## 本轮云端 Claude 要求的重点

1. **回归: 欢迎页仍正常显示，没有新的 FATAL。** ✔
2. **桩分配（点"登录"后）**: `rewriteIntent: MobileInputUI -> stub (flags=0x0)`，分配到的是 **`StubActivity3`（通用池，standard/singleTop 那一组）**，**不是** StubTask*/StubInstance*。
   这一次点击没有走到 singleTask/singleInstance 专用桩（因为崩溃发生在 MobileInputUI 显示之后）。
   `newActivity: stub -> com.tencent.mm.plugin.account.ui.MobileInputUI` 出现了。
3. **result code**: 登录页那条 `START ... StubActivity3 ... result code=0`（新建成功）。本轮 StubActivity* 的 START 共 4 条，全部 `code=0`。
4. **点进去崩了**: 是。登录页创建并显示后，进程在约 0.46 秒内收到 SIGSEGV 死亡。**没有刷屏循环**（rewriteIntent 总共 3 次）。

## 本地 AI 的观察（未验证，没有 tombstone 可读）

- 死因证据只有这些：`Zygote: Process 25414 exited due to signal 11 (Segmentation fault)`，`am_proc_died`，MIUI 的 `NativeCrash: Dumper process exited with status -11`。
  **我读不到 native 的栈**：`/data/tombstones` 权限不够，logcat 里没有 `DEBUG`/`F/libc` 的 backtrace，AndroidRuntime 也没有 FATAL。
- 进程死亡前约 0.4 秒（21:04:59.614；NativeCrash 的记录在约 170 毫秒之后）有一个 **Cronet 相关的 Java 异常**：`org.chromium.base.BuildInfo$Holder.<clinit>` 在 `getInstallerPackageName("com.tencent.mm")` 里抛
  `IllegalArgumentException: Unknown package: com.tencent.mm`，变成 `ExceptionInInitializerError`（打到 System.err）。
  - 这次调用经过一个 IPackageManager 的动态代理（`ig5.n1.invoke` → `$Proxy11`，类名混淆，看起来是微信自己的），最终到了系统 PackageManagerService，系统回"Unknown package"。
  - 同一时刻系统日志有 `AppsFilter: interaction: ... com.example.multiopen/10251 -> ... com.tencent.mm/10260 BLOCKED`：**包可见性过滤**把宿主看真实微信包的权限挡住了
    （手机上真的装着 com.tencent.mm，但宿主没有 `<queries>`/`QUERY_ALL_PACKAGES` 能看见它）。
  - 虚拟微信的包名正好等于手机上真实微信的包名，所以像 `getInstallerPackageName` 这类按包名查询的调用，看起来没有被宿主的 PackageManager 钩子拦住，直接到了真实系统（这是我的推断，没有核对宿主钩子的覆盖范围）。
  - **这是推测**：Cronet 的 native 初始化可能通过 JNI 调 `BuildInfo.getAll()`，类初始化失败后 native 拿到 null / 异常没处理，触发 SIGSEGV。我没有证据证明这两件事有因果关系，只是时间上相邻（约 170 毫秒内）。
- 环境因素提醒：**这台手机装着真实的微信**，这可能让上面的"Unknown package"和 AppsFilter 的行为与"手机上没装微信"的情况不同；如果云端想排除这个干扰，可以在没装真实微信的设备上对比，或者在宿主里对 `com.tencent.mm` 的 `getInstallerPackageName` 等包名查询返回虚拟包信息。
- 我没有点"注册"，也没有测 singleTask / singleInstance 桩（这一步需要登录页稳定显示后才能继续点下去）。

## 异常计数
FATAL EXCEPTION（AndroidRuntime）: 0 次
native 崩溃: 1 次（SIGSEGV，PID 25414，点"登录"后约 0.46 秒；无 tombstone 可读）
plugin Application.onCreate failed: 2 次（被捕获：Scene Activity process mismatch；第 1 次启动一次 + 崩溃重启后一次）
rewriteIntent: 3 次（WelcomeActivity ×2，flags=0x20000000；MobileInputUI ×1，flags=0x0）
newActivity: stub: 4 次（LauncherUI、WelcomeActivity、MobileInputUI、重启后再一次 WelcomeActivity）
ActivityTaskManager START StubActivity*: 4 次，全部 result code=0
mCoreAccount not initialized: 0 次
Resources$NotFoundException: 0 次
ACCESS_NETWORK_STATE: 0 次
baseRevision must not be null: 0 次
Skeleton not initialized: 0 次
UnsatisfiedLinkError: 0 次
ClassNotFoundException: 0 次
SecurityException: 0 次
NoClassDefFoundError: 0 次
ANR 弹窗: 有（启动期，点系统弹窗"等待"后消失）；点"登录"之后没有再出现 ANR
