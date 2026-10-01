# 测试结果

- commit: 4ca0148（Fix login-page SIGSEGV: make com.tencent.mm visible, stub getInstallerPackageName）
- 编译: 成功（:app，BUILD SUCCESSFUL；唯一警告 PackageManagerHook.kt:88 `getInstallerPackageName(String)` is deprecated）
- 设备: 小米 M2011K2C / Android 14。**这一轮手机上已经卸载了真实微信**（`pm path com.tencent.mm` 为空）；微信 8.0.78 的 APK 是电脑上备份的副本，推到 /sdcard/Download 再放进多开容器。
- 被测 APK: 微信 8.0.78（单个 base.apk，280614450 字节，arm64-v8a，非 split）
- 现象: 欢迎页正常（这一轮没出现 ANR 弹窗）。**点"登录"后仍然崩溃**：`MobileInputUI` 被创建并显示（`Displayed ...StubActivity3 +538ms`），约 0.37 秒后进程死亡，系统重启进程回到欢迎页。
  死因这次**拿到了 native 信息**（见下）：真正的崩溃是 **SIGTRAP（断点陷阱），发生在 `libcronet.119.0.6045.214.so`**，由微信自己的 NativeCrash 处理器在转储时又崩了一次，系统记录的最终退出信号是 11。
  登录页还是没能稳定停住，我没有机会看到它的画面（截图是点击后约 2 秒才截的，已是重启后的欢迎页）。我只点了"登录"一下，没有输入任何内容。

## 关键 logcat（MultiOpen + 系统）

### 点击"登录"的时间线（PID 27755）

```
21:13:13.066 I/MultiOpen: rewriteIntent: com.tencent.mm.plugin.account.ui.MobileInputUI -> stub (flags=0x0)
21:13:13.077 I/ActivityTaskManager: START u0 {cmp=com.example.multiopen/.StubActivity3 (has extras)} ... result code=0
21:13:13.112 I/MultiOpen: newActivity: stub -> com.tencent.mm.plugin.account.ui.MobileInputUI
21:13:13.469 I/MultiOpen: virtual Service created: com.tencent.mm.service.ProcessService$SupportProcessService
21:13:13.612 I/ActivityTaskManager: Displayed com.example.multiopen/.StubActivity3 for user 0: +538ms
21:13:13.681 W/System.err: java.lang.ExceptionInInitializerError            ← 与上一轮同一个异常
21:13:13.681 W/System.err:   at org.chromium.base.BuildInfo.getInstance(Unknown Source:10)
21:13:13.681 W/System.err:   at org.chromium.base.BuildInfo.getAll(Unknown Source:0)
21:13:13.681 W/System.err: Caused by: java.lang.IllegalArgumentException: Unknown package: com.tencent.mm
21:13:13.681 W/System.err:   at android.content.pm.IPackageManager$Stub$Proxy.getInstallerPackageName(IPackageManager.java:5422)
21:13:13.681 W/System.err:   at java.lang.reflect.Method.invoke(Native Method)
21:13:13.681 W/System.err:   at ig5.n1.invoke(Unknown Source:202)
21:13:13.681 W/System.err:   at java.lang.reflect.Proxy.invoke(Proxy.java:1006)
21:13:13.681 W/System.err:   at $Proxy11.getInstallerPackageName(Unknown Source)
21:13:13.682 W/System.err:   at android.app.ApplicationPackageManager.getInstallerPackageName(ApplicationPackageManager.java:2582)
21:13:13.682 W/System.err:   at org.chromium.base.BuildInfo.<init>(SourceFile:45)
21:13:13.682 W/System.err:   at org.chromium.base.BuildInfo.<init>(SourceFile:1)
21:13:13.682 W/System.err:   at org.chromium.base.BuildInfo$Holder.<clinit>(Unknown Source:3)
21:13:13.682 W/System.err: Caused by: android.os.RemoteException: Remote stack trace:
21:13:13.682 W/System.err:   at com.android.server.pm.ComputerEngine.getInstallerPackageName(ComputerEngine.java:5069) ...
21:13:13.685 V/NativeCrash(27755): Entered signal handler.
21:13:13.727 V/NativeCrash(31969): Opening dump file: /data/user/0/com.example.multiopen/files/virtual/1790856739251/data/files/crash/NativeCrash_com.tencent.mm_1790856761382.dmp (及 .fulldmp)
21:13:13.749 I/NativeCrash(31969): get threads total:135
21:13:13.798 V/NativeCrash(31969): Dump Java in cloned process
21:13:13.822 E/NativeCrash(27755): Dumper process exited with status -11
21:13:13.982 I/ActivityManager: Process com.example.multiopen (pid 27755) has died: fg  TOP
21:13:13.983 I/Zygote: Process 27755 exited due to signal 11 (Segmentation fault)
21:13:14.002 W/ActivityTaskManager: Force removing ActivityRecord{... com.example.multiopen/.StubActivity3 t88}: app died, no saved state
```

系统重启进程后（PID 27756，回到欢迎页，与上一轮相同）：

```
21:13:14.332 I/MultiOpen: IActivityManager hooked / IPackageManager hooked
21:13:14.477 I/MultiOpen: resources built: ... (280614450 bytes)   resources self-check OK: 0x7f1202a7 -> com.tencent.mm:style/lc
21:13:14.478 I/MultiOpen: fake process name -> com.tencent.mm
21:13:15.151 I/MultiOpen: set mInitialApplication -> com.tencent.mm.app.Application
21:13:16.928 E/MultiOpen: plugin Application.onCreate failed    （同前：Scene Activity process mismatch ... declared=null current=com.tencent.mm）
21:13:16.928 I/MultiOpen: virtual Application created: com.tencent.mm.app.Application
21:13:16.993 I/MultiOpen: newActivity: stub -> com.tencent.mm.plugin.account.ui.WelcomeActivity
```

### native 崩溃详情（本轮重点 4：读到了）

微信自带的 NativeCrash 把转储写在宿主私有目录（`files/virtual/<实例>/data/files/crash/NativeCrash_com.tencent.mm_1790856761382.dmp`，1491 字节，文本；另有 `.fulldmp` 179584 字节，我没有贴）。
我用 `run-as` 读了 `.dmp`，**摘录（只含崩溃信息，没有贴寄存器）**：

```
Device: M2011K2C   API Level: 34   Arch: arm64
Process: (27755) com.example.multiopen
Thread: (31263) L.#0ThreadPoolF
Crash Time: 2026-10-01 21:13:13.685   Live Time: 32s
Signal: 5 (SIGTRAP), Code: 1 (TRAP_BRKPT)
Fault Address: 0000007c76ed1fe4

[Native Stack]
  #00 pc 00000000000cbfe4 /data/data/com.example.multiopen/files/virtual/1790856739251/lib/libcronet.119.0.6045.214.so (BuildId: df72137ab124b4dfe40adc11c9721ff3205d85e2)

[Java Stack]
（空）
```

- 栈只有 1 帧，在 `libcronet.119.0.6045.214.so` 偏移 `0xcbfe4`；崩溃线程 `L.#0ThreadPoolF`（Chromium/Cronet 的线程池）。
- `libcronet` 是被我们宿主解压出来的那份（路径在虚拟实例的 `lib/` 目录下，`extracted 209 so` 里的一个）。
- 本机的 `/data/tombstones` 仍然读不了；`libc:F` / `DEBUG:F` 的过滤器里**没有抓到任何行**（微信自己的 NativeCrash 处理器先接管了信号）。

## 本轮云端 Claude 要求的重点

1. **点"登录"后还崩不崩: 还崩。** `signal 11` / `am_proc_died` / `NativeCrash` 都在。区别：这次我拿到了 native 信息。
2. **`getInstallerPackageName` 的 `Unknown package` 还出现吗: 还出现**（`BuildInfo$Holder.<clinit>` 抛 `ExceptionInInitializerError`，Caused by `IllegalArgumentException: Unknown package: com.tencent.mm`，与上一轮完全相同）。
   **`AppsFilter ... -> com.tencent.mm BLOCKED`: 不再出现**（0 行）。但要注意：**这一轮手机上已经没有真实微信**，系统对 `com.tencent.mm` 本来就返回 "Unknown package"，所以 AppsFilter 那一行消失不能说明 `QUERY_ALL_PACKAGES` 起作用了。
3. **登录页能不能稳定显示: 不能。** `MobileInputUI` 创建并显示了约 0.37 秒（Displayed 21:13:13.612 → 进程死亡 21:13:13.982），然后进程死亡、系统重启。
4. **native backtrace: 有（只有 1 帧）**，见上：`SIGTRAP` @ `libcronet.119.0.6045.214.so + 0xcbfe4`，线程 `L.#0ThreadPoolF`。
5. 登录页没稳住，所以没有继续点页面上的其它元素，singleTask / singleInstance 桩仍然没走到。

### 本地 AI 的分析（未验证，没有改源码）

- **宿主的 `getInstallerPackageName` 拦截没有生效，原因看栈能说清楚**：调用栈是
  `ApplicationPackageManager.getInstallerPackageName → $Proxy11.getInstallerPackageName → java.lang.reflect.Proxy.invoke → ig5.n1.invoke(:202) → Method.invoke → IPackageManager$Stub$Proxy.getInstallerPackageName`。
  栈里**没有任何 `com.example.multiopen` 的帧**；`ig5.n1`（混淆名，看起来是微信自己的类）这个 IPackageManager 动态代理直接把调用 `Method.invoke` 到真实的 `IPackageManager$Stub$Proxy` 上。
  也就是说微信在宿主之后又把 `ActivityThread.sPackageManager`（或 `ApplicationPackageManager.mPM`）包了一层，**它包的是真实的 binder 代理，绕过了宿主的 hook**。（这是推断，我没看微信那段代码，只看了栈。）
- **异常和 native 崩溃的关联（有证据，但不是完全证明）**：`BuildInfo.getAll` 是 Chromium 里被 native 通过 JNI 调用的 Java 方法；它在 `BuildInfo$Holder.<clinit>` 抛 `ExceptionInInitializerError` 之后，约 4 毫秒内 `NativeCrash` 进入信号处理器（21:13:13.681 → 21:13:13.685），
  崩溃线程是 Cronet 的线程池，信号是 `SIGTRAP`（Chromium 的 `CHECK` / `IMMEDIATE_CRASH` 失败时就是用断点陷阱）。我据此推测：native 侧在 JNI 调用 `BuildInfo.getAll()` 时遇到 Java 异常，Chromium 的检查失败触发 `SIGTRAP`。
  我没有符号，无法确认 `0xcbfe4` 具体是哪个函数。
- **最终退出信号是 11 而不是 5**：因为微信的转储进程自己也崩了（`Dumper process exited with status -11`），系统看到的是后面的这次崩溃。所以"signal 11"只是现象，真正的起点是 SIGTRAP。
- 可能的修法思路（供云端决定，我没有试）:
  - 让这个 `getInstallerPackageName("com.tencent.mm")` 返回正常值（不抛异常）：要么拦在微信包装之前/之后（比如在微信安装它的代理之后再 hook 一次，或者包住它的 target），要么在宿主侧把真实 `IPackageManager` 的 binder 层拦住。
  - 或者想办法让 Cronet 的 `BuildInfo` 初始化时拿到的 installer 为 null 而不是抛异常。
- 环境提醒：这一轮真实微信已经不在手机上。因此"真实微信存在 → 包可见性"这条线索被排除了：**去掉真实微信后，崩溃一模一样**（同一个 `Unknown package`、同一个 `libcronet` SIGTRAP）。所以根因不是 AppsFilter / 包可见性，而是 `getInstallerPackageName("com.tencent.mm")` 本身对虚拟包没有给出有效结果。

## 异常计数
FATAL EXCEPTION（AndroidRuntime）: 0 次
native 崩溃: 1 次（SIGTRAP @ libcronet.119.0.6045.214.so+0xcbfe4，线程 L.#0ThreadPoolF；最终退出信号 11；点"登录"后约 0.37 秒）
`Unknown package: com.tencent.mm`: 1 次（System.err）
AppsFilter ... com.tencent.mm BLOCKED: 0 次
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
ANR 弹窗: 本轮没有出现
