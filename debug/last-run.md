# 测试结果

- commit: 103df24（Add diagnostics to pin why WeChat's PM query bypasses our interception；纯诊断，不改行为）
- 编译: 成功（:app，BUILD SUCCESSFUL；唯一警告 PackageManagerHook.kt:100 `getInstallerPackageName(String)` is deprecated）
- 设备: 小米 M2011K2C / Android 14，手机上**没有**真实微信；微信 8.0.78 的 APK 是电脑备份副本。
- 被测 APK: 微信 8.0.78（单个 base.apk，280614450 字节，arm64-v8a，非 split）
- 现象:
  1. **第一个进程（PID 32250）在我点"登录"之前就死了一次**（21:29:14.782，欢迎页阶段）：不是 native 崩溃，而是微信自己的未捕获异常处理器退出了进程（`Process 32250 exited cleanly (1)`）。**前几轮没有出现过这一次**，是否是偶发我不知道。
  2. 系统重启进程（PID 1137），回到欢迎页（截图确认：深蓝地球图 + 右上"语言" + 绿色"登录" + 白色"注册"）。
  3. 我点"登录"（只点一下，没输入任何内容）→ `MobileInputUI` 创建并显示（`Displayed ...StubActivity +663ms`）→ **同样的 native 崩溃**：`SIGTRAP @ libcronet.119.0.6045.214.so+0xcbfe4`（线程 `L.ThreadPoolSin`），进程 1137 被系统判死（`exited due to signal 11`，21:29:25.559）。
  4. 系统再重启进程（PID 3813），又回到启动流程。

## 本轮重点：两条诊断日志（本轮核心产物）

- `wrapped package binder: queryLocalInterface -> ourIpm`：**0 次**（整个过程一次都没有出现）。
- `intercept getInstallerPackageName(com.tencent.mm)`：**3 次**，每个进程在 Application 初始化阶段各 1 次，**点"登录"崩溃前后一次都没有**：

```
21:28:47.068 I/MultiOpen(32250): ServiceManager package binder wrapped
21:29:10.278 I/MultiOpen(32250): intercept getInstallerPackageName(com.tencent.mm)      ← 进程 A，set mInitialApplication(21:29:08.722) 后约 1.6 秒
21:29:15.148 I/MultiOpen(1137):  ServiceManager package binder wrapped
21:29:19.079 I/MultiOpen(1137):  intercept getInstallerPackageName(com.tencent.mm)      ← 进程 B，set mInitialApplication(21:29:17.680) 后约 1.4 秒
21:29:25.920 I/MultiOpen(3813):  ServiceManager package binder wrapped
21:29:29.792 I/MultiOpen(3813):  intercept getInstallerPackageName(com.tencent.mm)      ← 进程 C，同样在 Application 初始化阶段
```
- `Unknown package: com.tencent.mm` 仍然出现：**1 次**，在进程 B 的登录崩溃那一刻（`21:29:24.803 W/System.err(1137)`），栈与前两轮逐帧相同
  （`ig5.n1.invoke → Method.invoke → IPackageManager$Stub$Proxy.getInstallerPackageName`，`Caused by RemoteException: Remote stack trace ... ComputerEngine.getInstallerPackageName`，即真实 system_server 回的）。

**有/无组合（明确结论，按 next-step 里的四种）：**
- 不是"都没有"：我们的拦截（`intercept getInstallerPackageName`）**被调用到了**，3 次。
- 也不是标准的"有 queryLocalInterface、没有 intercept"：`queryLocalInterface` 是 **0**，`intercept` 是 **3**。
- 也不是"两条都有但还崩"。
- **实际情况是：`queryLocalInterface` 为 0（微信没有通过我们包装的 binder 的 `queryLocalInterface` 拿接口），`intercept` 在每个进程的 Application 初始化阶段被调到 1 次；但登录页崩溃时那次真正出问题的调用（Cronet `BuildInfo` → `ig5.n1` 代理 → 真实 `IPackageManager$Stub$Proxy`）没有经过我们的拦截**（崩溃前后 `intercept` 日志是 0，而 `Unknown package` 异常来自真实系统）。
- 换句话说：**Application 阶段有一条查询走了我们的 hook；登录页阶段 Cronet 那条走的是另一条（直连真实 binder 的）路径。**

## 其它关键 logcat（MultiOpen + 系统；30 条 provider installed、20 条 receiver registered 已折叠）

### 进程 A（PID 32250）——欢迎页阶段死亡（本轮新现象）

```
21:29:06.730 START u0 {cmp=com.example.multiopen/.StubActivity (has extras)} ... result code=0          ← 打开微信实例
21:29:07.838 resources built / self-check OK；fake process name -> com.tencent.mm
21:29:08.722 set mInitialApplication -> com.tencent.mm.app.Application
21:29:10.278 intercept getInstallerPackageName(com.tencent.mm)
21:29:11.541 E plugin Application.onCreate failed（同前：Scene Activity process mismatch ... declared=null）
21:29:11.595 newActivity: stub -> com.tencent.mm.ui.LauncherUI
21:29:12.037 rewriteIntent: ...WelcomeActivity -> stub (flags=0x20000000)   → START StubActivity1 code=0
21:29:12.383 rewriteIntent: ...WelcomeActivity -> stub (flags=0x20000000)   → START StubActivity2 code=0
21:29:12.581 W ActivityTaskManager: Activity top resumed state loss timeout / pause timeout for ...StubActivity
21:29:14.111 newActivity: stub -> com.tencent.mm.plugin.account.ui.WelcomeActivity
21:29:14.450 Displayed com.example.multiopen/.StubActivity2 for user 0: +7s726ms
21:29:14.658 E/MultiOpen: startService com.tencent.mm.sandbox.monitor.ExceptionMonitorService 失败
21:29:14.658 E/MultiOpen: java.lang.ExceptionInInitializerError
    at com.tencent.mm.sandbox.monitor.ExceptionMonitorService.onCreate(Unknown Source:11)
    at com.example.multiopen.VirtualServices.create(VirtualServices.kt:97)
    at com.example.multiopen.VirtualServices.start(VirtualServices.kt:37)
    at com.example.multiopen.VirtualContext.startService(VirtualContext.kt:46)
    at com.tencent.mm.plugin.sandbox.SubCoreSandBox.dj(Unknown Source:277)
    at com.tencent.mm.app.q3.l(Unknown Source:397)
    at com.tencent.mm.app.q3.m(Unknown Source:189)
    at com.tencent.mm.app.l3.uncaughtException(Unknown Source:44)        ← 微信的未捕获异常处理器
    at kotlinx.coroutines.t0.a / u0.a / r3.Q / c3.I / c3.f0 / c3.V / a.resumeWith … ← 协程里
    at q36.l.run / j36.c.run（线程池）
Caused by: java.lang.RuntimeException: looper and serial is null!
    at com.tencent.mm.sdk.platformtools.q3.a(Unknown Source:72)
    at com.tencent.mm.sdk.platformtools.q3.<init>(SourceFile:3)
    at com.tencent.mm.sandbox.monitor.k.<clinit>(Unknown Source:2)
21:29:14.782 I/ActivityManager: Process com.example.multiopen (pid 32250) has died: fg  TOP
21:29:14.783 I/Zygote: Process 32250 exited cleanly (1)
```
- 这是**微信自己的未捕获异常处理器**（`l3.uncaughtException`）在处理一个协程线程里的异常时，想启动 `ExceptionMonitorService`（经过宿主的 `VirtualContext.startService`），
  该 Service 的 `<clinit>` 里 `q3.<init>` 抛 `RuntimeException: looper and serial is null!`，随后进程以退出码 1 干净退出（不是信号杀死）。
- **触发这个处理器的原始异常（"t"）日志里没有打出来**，所以我不知道它是什么。（本轮这个进程里没有 `Unknown package`，也没有 NativeCrash。）

### 进程 B（PID 1137）——点"登录"后（与前两轮同一个崩溃）

```
21:29:23.963 I/MultiOpen: rewriteIntent: com.tencent.mm.plugin.account.ui.MobileInputUI -> stub (flags=0x0)
21:29:24.071 START u0 {cmp=com.example.multiopen/.StubActivity (has extras)} ... result code=0
21:29:24.133 newActivity: stub -> com.tencent.mm.plugin.account.ui.MobileInputUI
21:29:24.560 virtual Service created: com.tencent.mm.service.ProcessService$SupportProcessService
21:29:24.728 Displayed com.example.multiopen/.StubActivity for user 0: +663ms
21:29:24.802 W/System.err(1137): java.lang.ExceptionInInitializerError   ← org.chromium.base.BuildInfo.getInstance / getAll
21:29:24.803 W/System.err(1137): Caused by: java.lang.IllegalArgumentException: Unknown package: com.tencent.mm   （栈同前两轮）
21:29:24.851 I/NativeCrash(6645): get threads total:134
21:29:24.917 E/NativeCrash(1137): Dumper process exited with status -11
21:29:25.559 I/Zygote: Process 1137 exited due to signal 11 (Segmentation fault)
21:29:25.563 W/ActivityTaskManager: Force removing ActivityRecord{... StubActivity}、{... StubActivity1}: app died, no saved state
```

native 转储（读自宿主私有目录里微信 NativeCrash 的 `.dmp`，只摘崩溃信息）：

```
Process: (1137) com.example.multiopen
Thread: (6631) L.ThreadPoolSin
Crash Time: 2026-10-01 21:29:24.808   Live Time: 7s
Signal: 5 (SIGTRAP), Code: 1 (TRAP_BRKPT)
Fault Address: 0000007cadc4dfe4
[Native Stack]
  #00 pc 00000000000cbfe4 /data/data/com.example.multiopen/files/virtual/1790857733355/lib/libcronet.119.0.6045.214.so (BuildId: df72137ab124b4dfe40adc11c9721ff3205d85e2)
[Java Stack]
```
**与上一轮同位置同偏移**（`libcronet.119.0.6045.214.so + 0xcbfe4`），线程名与上一轮相同（`L.ThreadPoolSin`）。Java 异常（24.802）到 SIGTRAP（24.808）约 6 毫秒。

### 进程 C（PID 3813）——系统再次重启后（观察到约 21:29:35 为止，未继续）

```
21:29:30.901 newActivity: stub -> com.tencent.mm.ui.LauncherUI
21:29:31.432 START u0 {cmp=com.example.multiopen/.StubActivity ...} ... result code=3
21:29:31.678 START StubActivity1 code=0；21:29:33.149 START StubActivity2 code=0
21:29:33.245 / 21:29:33.676 newActivity: stub -> WelcomeActivity（两次）
```
（这一段只是顺带记录，没有做分析；其中 `StubActivity ... result code=3`（START_DELIVERED_TO_TOP）出现了 1 次。）

## 本轮云端 Claude 要求的其余项

- `Unknown package` 还在不在: 在（1 次，进程 B 登录崩溃时）。
- 还崩不崩: 崩。native 摘要：**同上轮**（SIGTRAP @ libcronet.119.0.6045.214.so+0xcbfe4，线程 `L.ThreadPoolSin`）。
- 登录页能不能停住: 不能（显示约 0.1 秒内就崩了）。登录页没稳住，没有继续点"扫码登录"。

### 本地 AI 的分析（未改源码，未验证）

- **拦截日志的位置能解释为什么没挡住**：`intercept getInstallerPackageName(com.tencent.mm)` 在 3 个进程里都**只在 Application 初始化阶段**出现 1 次（`set mInitialApplication` 后约 1.5 秒），**不在** Cronet `BuildInfo` 触发的那次查询附近。
  所以"Application 阶段的查询经过了我们的 `sPackageManager` hook"和"Cronet BuildInfo 的查询没经过"是**两条不同的路径**。
- Cronet 那条的调用栈里有微信自己的代理 `ig5.n1`，它 `Method.invoke` 的目标是一个真实的 `IPackageManager$Stub$Proxy`；
  `queryLocalInterface -> ourIpm` 为 0，说明这个 `Stub$Proxy` 不是通过我们包装 binder 的 `queryLocalInterface` 得到的。
  可能的来源（**推测，我没有证据**）：微信在我们包装之前就缓存了真实的 IPackageManager；或者它用 `Proxy`/反射直接对真实 binder 做了 `asInterface`；或者我们的包装只替换了 ServiceManager 的缓存，但微信拿到接口的方式没有走缓存。
- 如果要确认，可以让云端在 `ig5.n1` 被安装时（例如 `ActivityThread.sPackageManager` 被再次替换的时刻）打印替换前后的对象类名，或者在包装层的 `transact` 里打印 transaction code，看登录页崩溃那一刻有没有经过包装层。
- 关于进程 A 的死亡：它和 libcronet 的 SIGTRAP **不是同一种死法**（信号 vs 干净退出码 1），本轮只出现 1 次，我没有足够信息判断是偶发还是新引入。
  它的触发点是微信的 `l3.uncaughtException`，说明当时某个协程线程里已经有一个没处理的异常；该异常的内容没有在日志里。

## 异常计数
FATAL EXCEPTION（AndroidRuntime）: 0 次
native 崩溃: 1 次（进程 B，SIGTRAP @ libcronet.119.0.6045.214.so+0xcbfe4，线程 L.ThreadPoolSin）
进程干净退出（微信未捕获异常处理器）: 1 次（进程 A，exit code 1，欢迎页阶段）
`wrapped package binder: queryLocalInterface -> ourIpm`: 0 次
`intercept getInstallerPackageName(com.tencent.mm)`: 3 次
`ServiceManager package binder wrapped`: 3 次
`Unknown package: com.tencent.mm`: 1 次
plugin Application.onCreate failed: 3 次（被捕获：Scene Activity process mismatch，3 个进程各一次）
rewriteIntent: 6 次；newActivity: stub: 7 次；START StubActivity*: 7 次（code=0 共 6 次，code=3 共 1 次）
mCoreAccount not initialized: 0 次
Resources$NotFoundException: 0 次
ACCESS_NETWORK_STATE: 0 次
baseRevision must not be null: 0 次
Skeleton not initialized: 0 次
UnsatisfiedLinkError: 0 次
ClassNotFoundException: 0 次
SecurityException: 0 次
NoClassDefFoundError: 0 次
