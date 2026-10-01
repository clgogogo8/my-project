# 测试结果

- commit: 3574170（Wrap ServiceManager package binder so WeChat's own PM proxy hits our hook）
- 编译: 成功（:app，BUILD SUCCESSFUL；唯一警告 PackageManagerHook.kt:99 `getInstallerPackageName(String)` is deprecated）
- 设备: 小米 M2011K2C / Android 14，手机上**没有**真实微信（上一轮已卸载）；微信 8.0.78 的 APK 是电脑备份副本，推到 /sdcard/Download 后放进多开容器。
- 被测 APK: 微信 8.0.78（单个 base.apk，280614450 字节，arm64-v8a，非 split）
- 现象:
  1. **回归通过**：`ServiceManager package binder wrapped` 出现了；欢迎页正常显示（有 ANR 弹窗，点系统弹窗上的"等待"后消失）；没有 FATAL；没有新的黑屏/崩溃。
  2. **点"登录"后仍然崩溃，和上一轮完全一样**：`MobileInputUI` 创建并显示（`Displayed ...StubActivity3 +559ms`），随后进程被 native 崩溃杀死，系统重启进程回到欢迎页。
     登录页没能稳定停住，我没有看到它的画面（截图是点击后约 2 秒才截的，已是重启后的欢迎页）。我只点了"登录"一下，没有输入任何内容，也没有点别的。

## 关键 logcat（MultiOpen + 系统）

### ServiceManager 包装（本轮重点 1）

```
21:21:39.135 I/MultiOpen: ServiceManager package binder wrapped      ← 第 1 个进程（PID 29750）
21:22:28.096 I/MultiOpen: ServiceManager package binder wrapped      ← 崩溃后重启的进程（PID 30942）
```
（每个新进程各 1 次；紧跟在 `IActivityManager hooked` / `IPackageManager hooked` 之后。）

### 点击"登录"的时间线（PID 29750）

```
21:22:26.546 I/MultiOpen: rewriteIntent: com.tencent.mm.plugin.account.ui.MobileInputUI -> stub (flags=0x0)
21:22:26.558 I/ActivityTaskManager: START u0 {cmp=com.example.multiopen/.StubActivity3 (has extras)} ... result code=0
21:22:26.595 I/MultiOpen: newActivity: stub -> com.tencent.mm.plugin.account.ui.MobileInputUI
21:22:26.968 I/MultiOpen: virtual Service created: com.tencent.mm.service.ProcessService$SupportProcessService
21:22:27.121 I/ActivityTaskManager: Displayed com.example.multiopen/.StubActivity3 for user 0: +559ms
21:22:27.173 W/System.err: java.lang.ExceptionInInitializerError          ← 与上一轮同一个异常
21:22:27.173 W/System.err:   at org.chromium.base.BuildInfo.getInstance(Unknown Source:10)
21:22:27.173 W/System.err:   at org.chromium.base.BuildInfo.getAll(Unknown Source:0)
21:22:27.173 W/System.err: Caused by: java.lang.IllegalArgumentException: Unknown package: com.tencent.mm
21:22:27.174 W/System.err:   at android.content.pm.IPackageManager$Stub$Proxy.getInstallerPackageName(IPackageManager.java:5422)
21:22:27.174 W/System.err:   at java.lang.reflect.Method.invoke(Native Method)
21:22:27.174 W/System.err:   at ig5.n1.invoke(Unknown Source:202)
21:22:27.174 W/System.err:   at java.lang.reflect.Proxy.invoke(Proxy.java:1006)
21:22:27.174 W/System.err:   at $Proxy11.getInstallerPackageName(Unknown Source)
21:22:27.174 W/System.err:   at android.app.ApplicationPackageManager.getInstallerPackageName(ApplicationPackageManager.java:2582)
21:22:27.174 W/System.err:   at org.chromium.base.BuildInfo.<init>(SourceFile:45)
21:22:27.174 W/System.err:   at org.chromium.base.BuildInfo.<init>(SourceFile:1)
21:22:27.174 W/System.err:   at org.chromium.base.BuildInfo$Holder.<clinit>(Unknown Source:3)
21:22:27.174 W/System.err: Caused by: android.os.RemoteException: Remote stack trace:
21:22:27.175 W/System.err:   at com.android.server.pm.ComputerEngine.getInstallerPackageName(ComputerEngine.java:5069) ...（system_server 侧）
21:22:27.240 I/NativeCrash: get threads total:139
21:22:27.323 E/NativeCrash: Dumper process exited with status -11
21:22:27.753 I/Zygote: Process 29750 exited due to signal 11 (Segmentation fault)
21:22:27.778 W/ActivityTaskManager: Force removing ActivityRecord{... com.example.multiopen/.StubActivity3 t90}: app died, no saved state
```

系统重启进程（PID 30942）后回到欢迎页，与前两轮相同（`plugin Application.onCreate failed` 仍是被捕获的 Scene Activity process mismatch，`newActivity: stub -> WelcomeActivity`）。

### native 崩溃详情（读自宿主私有目录里微信的 NativeCrash `.dmp`，只摘崩溃信息）

```
Process: (29750) com.example.multiopen
Thread: (3374) L.ThreadPoolSin
Crash Time: 2026-10-01 21:22:27.177   Live Time: 27s
Signal: 5 (SIGTRAP), Code: 1 (TRAP_BRKPT)
Fault Address: 0000007c6c378fe4
[Native Stack]
  #00 pc 00000000000cbfe4 /data/data/com.example.multiopen/files/virtual/1790857305418/lib/libcronet.119.0.6045.214.so (BuildId: df72137ab124b4dfe40adc11c9721ff3205d85e2)
[Java Stack]
（空）
```

## 本轮云端 Claude 要求的重点

1. **回归: 通过。** 出现了 `ServiceManager package binder wrapped`（每个新进程一次，共 2 次）；欢迎页正常；没有新崩溃/黑屏。
2. **`Unknown package: com.tencent.mm` 的 `ExceptionInInitializerError` 还出现吗: 还出现**，调用栈和上一轮逐帧相同（见上）。
3. **还崩不崩: 还崩**（`NativeCrash` / `signal 11` / `Force removing ... app died`；`am_proc_died` 事件这一轮我没抓，但 Zygote 的 `exited due to signal 11` 在）。
   **native 崩溃位置和上一轮完全相同：`SIGTRAP`，`libcronet.119.0.6045.214.so + 0xcbfe4`**（BuildId 相同）。只有线程名不同：上一轮是 `L.#0ThreadPoolF`，这一轮是 `L.ThreadPoolSin`。
   Java 异常（21:22:27.173/.174）到 SIGTRAP（21:22:27.177）之间只差约 3～4 毫秒。
4. **登录页能不能稳定停住: 不能。** 显示（Displayed 21:22:27.121）到进程被系统判死（21:22:27.753）约 0.63 秒；其间 native 崩溃发生在 21:22:27.177。
5. 登录页没稳住，所以没有继续点"扫码登录"，singleTask / singleInstance 桩仍没有走到。
6. 新的 native 崩溃摘要: 没有新的（与上一轮同位置），见上。

### 本地 AI 的分析（未改源码，未验证）

- **ServiceManager 的包装这一轮没有改变这条调用的结果。** 栈里是 `ig5.n1.invoke → Method.invoke → IPackageManager$Stub$Proxy.getInstallerPackageName`，
  异常里的 `Remote stack trace` 来自真实 system_server（`ComputerEngine.getInstallerPackageName`），说明这次调用**原样到达了真实的 PackageManagerService**，没有被宿主的虚拟包逻辑接管。
- 但要说明我从栈里**判断不了**包装的 binder 有没有在这条路径上：如果 `ig5.n1` 持有的是我们包装过的 binder 对应的 `Stub$Proxy`，
  那么包装层只会出现在 `transact` 内部，不会在这个异常栈里留下 `com.example.multiopen` 的帧；也就是说**也许包装确实在路径上，只是对 `getInstallerPackageName` 这个方法它没有拦截（直接透传给了系统）**。
  两种可能（"不在路径上" / "在路径上但没拦这个方法"）从日志看不出区别，需要云端看包装层的实现，或者在包装层的 `transact` 里打日志（带 transaction code）来确认。
- 因为手机上已经没有真实微信，系统对 `com.tencent.mm` 回 "Unknown package" 是**真实的结果**；要让这条调用不抛，必须由宿主拦截并返回一个虚拟包的值。
- 崩溃的两个现象依旧是同一对：Java 层 `BuildInfo$Holder.<clinit>` 抛 `ExceptionInInitializerError` → 约 3～4 毫秒后 `libcronet` 里 `SIGTRAP`（Chromium 检查失败的典型信号）。

## 异常计数
FATAL EXCEPTION（AndroidRuntime）: 0 次
native 崩溃: 1 次（SIGTRAP @ libcronet.119.0.6045.214.so+0xcbfe4，线程 L.ThreadPoolSin；最终退出信号 11；点"登录"后）
`Unknown package: com.tencent.mm`: 1 次（System.err）
`ServiceManager package binder wrapped`: 2 次
plugin Application.onCreate failed: 2 次（被捕获：Scene Activity process mismatch；首次启动 + 崩溃重启后各一次）
rewriteIntent: 3 次（WelcomeActivity ×2，flags=0x20000000；MobileInputUI ×1，flags=0x0）
newActivity: stub: 4 次（LauncherUI、WelcomeActivity、MobileInputUI、重启后 WelcomeActivity）
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
ANR 弹窗: 有（启动期，点系统弹窗"等待"后消失）；点"登录"之后没有再出现
