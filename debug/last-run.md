# 测试结果（登录卡死诊断轮：**部分完成**，缺"转圈那一刻"的现场）

- commit: 63b94aa（Set up login-hang diagnosis round: capture thread dump + full logcat）。app 代码与上一轮 4924b43/f99616c 相比没有改动，手机上装的是 4924b43 的构建（lastUpdateTime 22:58:40）。
- 设备: 小米 M2011K2C / Android 14。手机上有 `/system/bin/su`（root 管理器是 APatch）。
- **登录是用户本人做的，我没有输入任何账号/密码，也没有点"登录"。**
- 这一轮我没有按步骤 1 执行 `pm clear`：用户当时手机上正开着微信实例，我怕清掉用户正在用的状态。

## 本轮实际做到了什么 / 没做到什么

| 项 | 结果 |
|---|---|
| 全量 logcat 抓取（不过滤，存在本机，不提交） | 已做。开始前先把设备上已有的日志缓冲完整存了下来（22:58～23:25，约 7.7 万行）；之后一直在实时抓。 |
| 用户在转圈时抓线程栈 | **没有拿到"转圈那一刻"的栈。** 原因见下面。 |
| 拿到一份宿主的线程栈 | 拿到了一份：PID 1683，**23:25:09**，由我用 `run-as com.example.multiopen kill -3` 触发（宿主进程没死），`/data/anr/trace_05`，281625 字节。**我不知道这个时刻用户界面是白屏、转圈还是别的状态。** |
| 复现/现象确认（转圈 >60 秒？） | **没法确认。** 实时抓取开始之后，日志里没有再出现用户的新操作（见下面"日志线索"），宿主进程现在不在运行。 |

取线程栈的方法（供云端参考，已验证）：
1. `adb shell "run-as com.example.multiopen kill -3 <pid>"`：宿主是 debuggable，`run-as` 可以对自己发 SIGQUIT，ART 写出线程栈，进程不会死。
2. 文件落在 `/data/anr/trace_NN`，属主 `tombstoned:system`、权限 0660。**shell 和宿主自己的身份都读不了**，`debuggerd -j` 报 `debuggerd: root is required`。
3. 需要 root 才能读：`adb shell su -c cat /data/anr/trace_NN`。用户已经授权了 root（并在 Claude Code 里加了只允许读 `trace_*`/`anr_*` 的规则）。

## 线程栈（PID 1683，2026-10-01 23:25:09，`/data/anr/trace_05`，只含类名/方法名）

- DALVIK THREADS (83)；解析出 128 个线程块；`state`：127 个 S（睡眠）、1 个 R（只有 `Signal Catcher`，就是在写这份 dump 的线程）。
- **`Blocked`（等监视器锁）的线程: 0 个。**
- **栈里含 `com.example.multiopen`（宿主）的线程: 0 个。**
- 栈里含 `com.tencent` 的 Java 线程: 1 个，是 `Zidl Java DestructorThread`（`Object.wait ← ReferenceQueue.remove`，空闲）。
- 栈顶不像"空闲等待"的 Java 线程: 只有 `HeapTaskDaemon`（`VMRuntime.runHeapTasks`，ART 的堆整理守护线程）。

**主线程 `main`（tid=1，state=S）**：Java 栈就是标准的空闲循环，**没有被卡**：
```
at android.os.MessageQueue.nativePollOnce(Native method)
at android.os.MessageQueue.next(MessageQueue.java:344)
at android.os.Looper.loopOnce(Looper.java:176)
at android.os.Looper.loop(Looper.java:314)
at android.app.ActivityThread.main(ActivityThread.java:8857)
at java.lang.reflect.Method.invoke(Native method)
at com.android.internal.os.RuntimeInit$MethodAndArgsCaller.run(RuntimeInit.java:565)
at com.android.internal.os.ZygoteInit.main(ZygoteInit.java:1013)
```
Native 栈（栈顶的 `WaitHoldingLocks` 是线程被挂起去写这份 dump；中间能看到它正在分发一次 vsync）：
```
#00 libc.so syscall+32
#01 libart.so art::ConditionVariable::WaitHoldingLocks+144
#02 libart.so art::CheckJNI::CallMethodV+468 ← CallObjectMethodV+76
#04 libandroid_runtime.so _JNIEnv::CallObjectMethod
#05 libandroid_runtime.so NativeDisplayEventReceiver::dispatchVsync+68
#06 libgui.so DisplayEventDispatcher::handleEvent+280
#07 libutils.so Looper::pollInner+1252 ← pollOnce
#09 libandroid_runtime.so android_os_MessageQueue_nativePollOnce+48
#11 android.os.MessageQueue.next+256 ← Looper.loopOnce+176 ← Looper.loop+520
```

**网络/IPC/微信相关线程**（next-step 重点 1 要的那些）：
- `mars::N`（`mars::5785`、`mars::6344`，3 个）、`mars::comm`（2 个）：都在 `pthread_cond_timedwait`（`futex_wait`），在 `libmarscomm.so` 里空闲等待任务；`mars::comm` 状态为 `(not attached)`（没有 Java 栈）。
- `mmcronet::quic:`（3 个，`libcronet.119.0.6045.214.so`）：`pthread_cond_timedwait` / `epoll_pwait`，空闲。
- `wechatlv:center`（`libwechatlv.so`）：`epoll_pwait`，空闲；`wc_lp_srvinit_0`、`IPCThreadPool#WorkerThread`：都是 `Looper.pollOnce / MessageQueue.nativePollOnce` 空闲循环。
- `lu_worker.N`（12 个，`libwemagic_common.so` / `libaff_biz.so`）：`futex` 等待，空闲。
- `MMCrashANRThread-0/1`：`ScheduledThreadPoolExecutor.DelayedWorkQueue.take` 空闲。`Check-ANR-State-Thread`：`Thread.sleep`。
- 没有任何线程停在 `CountDownLatch.await` / `Binder.transactNative` / `Object.wait`（微信的业务调用）/ `LinkedBlockingQueue.take`（业务线程）上等一个结果；线程名里没有 `push` 或 `STN`，线程名含 `IPC`、`MM`、`mars`、`mmcronet`、`wc_` 的都在上面。

**结论（只对这一份 dump 成立）**：这个时刻整个宿主进程**完全空闲**：主线程在 Looper 里，微信的网络线程（mars/cronet）和 IPC 线程都在空闲等待，**没有任何线程在 Java 层阻塞等待某个结果**。
所以如果用户当时看到的是"一直转圈"，那它**不是一个线程被锁/被 Binder/被 latch 卡住**，更像是：登录请求没有真正发出去或者回包没有被送回去，UI 在等一个"永远不会再来的回调"。这是我从栈做的推断，**没有证据证明那一刻用户界面是不是在转圈**，所以也可能只是在说"进程空闲"。云端需要在用户真正转圈时再抓一份才能坐实。

## 日志线索（来自设备日志缓冲，用户那次会话 23:17 起；设备时钟）

MultiOpen 相关行（只含 MultiOpen 标签）：
```
23:17:40.117 I MultiOpen: ART hook installed: Service.startForeground/stopForeground routing        ← 宿主进程启动（PID 1683）
23:17:47.189 I MultiOpen: virtual Service created: com.tencent.mm.service.ProcessService$MMProcessService
23:17:47.557 I MultiOpen: virtual Application created: com.tencent.mm.app.Application
23:17:47.627 I MultiOpen: newActivity: stub -> com.tencent.mm.plugin.account.ui.WelcomeActivity
23:17:48.319 I MultiOpen: virtual Service created: androidx.work.impl.background.systemalarm.SystemAlarmService
23:17:58.202 I MultiOpen: rewriteIntent: ...MobileInputUI -> stub (flags=0x0)
23:17:58.277 I MultiOpen: newActivity: stub -> com.tencent.mm.plugin.account.ui.MobileInputUI
23:17:58.695 I MultiOpen: virtual Service created: com.tencent.mm.service.ProcessService$SupportProcessService
23:18:02.713 I MultiOpen: rewriteIntent: ...LoginUI -> stub (flags=0x0)
23:18:02.799 I MultiOpen: newActivity: stub -> com.tencent.mm.plugin.account.ui.LoginUI
```
- **登录期间新建的 virtual Service / 绑定（next-step 重点 4）：** 整个会话只创建过 3 个虚拟 Service：`MMProcessService`（23:17:47）、`SystemAlarmService`（23:17:48）、`SupportProcessService`（23:17:58）。
  **23:18:02 进入账号/密码页（LoginUI）之后，到 23:25 之前，没有任何新的 `virtual Service created`，也没有 `bindService` / `startService` / `startForeground` 日志。**
  也就是说，从用户到达账号/密码页起，宿主这边没有再看到微信去创建或绑定任何 Service（登录发请求时通常会去绑定网络相关的 Service）。我没办法确认这期间用户是否真的点了"登录"。
- 23:18:03 之后宿主进程在 logcat 里主要是渲染/输入法/`PayMarsLiteApp`（微信支付的 mars 初始化，23:20:49）等输出。这段时间宿主进程的 E/F 级日志只有 `WindowManager`、`OpenGLRenderer`、`FA`、`ContentCatcherManager` 这几个无关的 tag（没有 `AndroidRuntime`）。**我没有专门去查这段时间的 NativeCrash / `Unknown package` / ANR 弹窗，所以不能说它们为 0。**
- 日志里没有 `MicroMsg` / `Mars` / `STN` / `IPCInvoker` 这类 tag 的 logcat 输出（我只统计了条数，没看内容）：这些模块的日志大概走微信自己的文件日志（xlog），不走 logcat，所以 logcat 上看不到登录请求的日志；要看它们得读实例目录里的微信日志文件，我这轮没有去读（那里可能有账号信息）。

## 现在的状态（本轮没能继续的原因）

- 实时抓取开始之后，日志里**没有再出现用户的任何新操作**（最后一条 MultiOpen 事件仍是 23:18:02 的 LoginUI）。
- **宿主进程现在没有在运行**（`pidof com.example.multiopen` 为空），手机前台窗口是 `me.bmax.apatch/.ui.MainActivity`（APatch root 管理器）。我不知道进程是什么时候、因为什么没的（我没有去查这段时间的进程死亡记录）。
- 所以我这一轮**没有机会在"用户点了登录并在转圈"时抓栈**。需要用户重新打开微信实例、本人登录、转圈后告诉我，我再触发 `kill -3` 并用 su 读取。

## 异常计数（只列我实际核对过的）
宿主进程 23:18:03 之后的 E/F 级日志里 `AndroidRuntime` / FATAL: 没有出现（该 tag 不在 E/F 的 tag 列表里）
NativeCrash / `Unknown package` / ANR 弹窗: **本轮没有专门查，未知**（我没有截屏，避免拍到用户输入的内容）
线程栈 `Blocked` 线程: 0 个；栈含宿主包名的线程: 0 个
