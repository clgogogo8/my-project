# 测试结果

- commit: 767d19d（Add a stub Activity pool to break the SINGLE_TOP launch loop）
- 编译: 成功（:app，BUILD SUCCESSFUL）
- 设备: 小米 M2011K2C / Android 14
- 被测 APK: 微信 8.0.78（手机上已装的单个 base.apk，280614450 字节，arm64-v8a，非 split）
- 现象: **重大进展：微信界面显示出来了。** 桩池修好了启动循环，WelcomeActivity 被真正创建，屏幕上出现了微信的**欢迎页**（见下面描述）。
  不过点击后约 8 秒内系统弹出了"MultiOpen没有响应"（ANR）弹窗，盖住了页面下半部分；这个弹窗**是启动期主线程忙太久留下的**，后面主线程其实是空闲的（见栈）。
  我点了系统 ANR 弹窗上的"等待"（不是"确定"，也没有点微信界面里的任何东西）后，弹窗消失，欢迎页完整可见。
  没有 FATAL EXCEPTION，进程存活。**我没有点"登录"/"注册"/"语言"，没有输入任何内容，没有登录任何账号。**

## 微信界面（我看到的，只描述）

- 全屏深蓝/黑色背景，中间是一张地球的大图，地球前面有一个小小的人物剪影站在地平线上（这就是微信的欢迎页）。
- 右上角有白色文字按钮"语言"。
- 底部左边是绿色实心按钮"登录"，底部右边是白色按钮"注册"。
- 弹窗没有消除前（点"等待"之前），下半部分被"MultiOpen没有响应 / 等待 / 确定"盖住，只能看到图片和右上角"语言"；点"等待"之后，"登录"和"注册"两个按钮出现。
- 我没有看到隐私政策弹窗或其他界面；焦点窗口是 `com.example.multiopen/.StubActivity2`。

## 关键 logcat（只含 MultiOpen 标签 + 系统 ActivityTaskManager 里与 StubActivity 有关的行）

### MultiOpen 行（30 条 provider installed、20 条 receiver registered 已折叠；没有 FATAL）

```
20:35:35.739 I/MultiOpen: IActivityManager hooked
20:35:35.756 I/MultiOpen: IPackageManager hooked
20:35:45.665 I/MultiOpen: extracted 209 so (arm64-v8a) -> /data/user/0/com.example.multiopen/files/virtual/1790854541722/lib
20:35:59.640 I/MultiOpen: resources built: cookie=15, apk=/data/user/0/com.example.multiopen/files/virtual/1790854541722/base.apk (280614450 bytes)
20:35:59.640 I/MultiOpen: resources self-check OK: 0x7f1202a7 -> com.tencent.mm:style/lc
20:36:00.735 I/MultiOpen: resources built: ...（同上，第 2 次）
20:36:00.735 I/MultiOpen: resources self-check OK: 0x7f1202a7 -> com.tencent.mm:style/lc
20:36:00.735 I/MultiOpen: fake process name -> com.tencent.mm
20:36:01.654 I/MultiOpen: set mInitialApplication -> com.tencent.mm.app.Application
20:36:04.244 I/MultiOpen: virtual Service created: com.tencent.mm.service.ProcessService$MMProcessService
20:36:05.048 E/MultiOpen: plugin Application.onCreate failed
20:36:05.048 E/MultiOpen: java.lang.IllegalStateException: java.lang.IllegalStateException: Scene Activity process mismatch: component=com.tencent.wxpay.internal.presentation.MainProcessPaySceneActivity declared=null current=com.tencent.mm
20:36:05.049 I/MultiOpen: virtual Application created: com.tencent.mm.app.Application
20:36:05.109 I/MultiOpen: newActivity: stub -> com.tencent.mm.ui.LauncherUI
20:36:06.146 I/MultiOpen: rewriteIntent: com.tencent.mm.plugin.account.ui.WelcomeActivity -> stub (flags=0x20000000)
20:36:06.386 I/MultiOpen: virtual Service created: com.tencent.mm.ipcinvoker.wx_extension.service.PushProcessIPCService
20:36:06.495 I/MultiOpen: rewriteIntent: com.tencent.mm.plugin.account.ui.WelcomeActivity -> stub (flags=0x20000000)
20:36:06.821 I/MultiOpen: virtual Service created: androidx.work.impl.background.systemalarm.SystemAlarmService
20:36:07.188 I/MultiOpen: newActivity: stub -> com.tencent.mm.plugin.account.ui.WelcomeActivity
```

### 系统 ActivityTaskManager（StubActivity 相关，本轮重点）

```
20:35:59.654 I/ActivityTaskManager: START u0 {cmp=com.example.multiopen/.StubActivity (has extras)} ... result code=0          ← 启动 LauncherUI
20:36:06.155 I/ActivityTaskManager: START u0 {flg=0x20000000 cmp=com.example.multiopen/.StubActivity1 (has extras)} ... result code=0   ← WelcomeActivity（第 1 次）
20:36:06.504 I/ActivityTaskManager: START u0 {flg=0x20000000 cmp=com.example.multiopen/.StubActivity2 (has extras)} ... result code=0   ← WelcomeActivity（第 2 次）
20:36:07.564 I/ActivityTaskManager: Displayed com.example.multiopen/.StubActivity2 for user 0: +7s917ms
```

另外有 ActivityTaskManager 的 W 级行（没有时间戳，我没有对应到秒）：
```
W/ActivityTaskManager: Activity top resumed state loss timeout for ActivityRecord{... com.example.multiopen/.MainActivity t80}
W/ActivityTaskManager: Activity pause timeout for ActivityRecord{... com.example.multiopen/.MainActivity t80}
W/ActivityTaskManager: Activity top resumed state loss timeout for ActivityRecord{... com.example.multiopen/.StubActivity t80}
W/ActivityTaskManager: Activity pause timeout for ActivityRecord{... com.example.multiopen/.StubActivity t80}
W/ActivityTaskManager: Launch timeout has expired, giving up wake lock!
```

### 看门狗主线程栈（3 条，state 都是 RUNNABLE，但 #2/#3 是空闲）

```
20:36:07.814 main-thread stack #1 (state=RUNNABLE):
    at android.view.DisplayEventReceiver.nativeGetLatestVsyncEventData(Native Method)
    at android.view.DisplayEventReceiver.getLatestVsyncEventData(DisplayEventReceiver.java:348)
    at android.view.Choreographer$FrameData.update(Choreographer.java:1447)
    at android.view.Choreographer.doFrame(Choreographer.java:956)
    at android.view.Choreographer$FrameDisplayEventReceiver.run(Choreographer.java:1617)
    at android.os.Handler.handleCallback ← Looper.loopOnce ← Looper.loop ← ActivityThread.main …   （在画一帧）

20:36:13.815 main-thread stack #2 (state=RUNNABLE):
    at android.os.MessageQueue.nativePollOnce(Native Method)
    at android.os.MessageQueue.next(MessageQueue.java:344)
    at android.os.Looper.loopOnce(Looper.java:176)
    at android.os.Looper.loop(Looper.java:314)
    at android.app.ActivityThread.main(ActivityThread.java:8857) …   （主线程空闲，在等消息）

20:36:19.837 main-thread stack #3 (state=RUNNABLE):   ← 与 #2 相同（nativePollOnce，空闲）
```

bg 线程（20:36:13.8 采样，共 26 个）：全部是 WAITING / TIMED_WAITING 的空闲线程（`[GT]HotPool#0~7`、`wc_srvinit_0~5`、`matrix_x_0~2`、`MMCrashANRThread-0/1`、`IPCThreadPool#Thread-0/1`、
`pool-4-thread-1`、`Recovery.LogWriter`、`Zidl Java DestructorThread`、`Thread-11`、`[GT]HCPerfManager`）。**没有 BLOCKED，没有 RUNNABLE，没有任何线程在等锁。**

## 本轮云端 Claude 要求的重点

- **循环有没有停: 停了。** `rewriteIntent: ... WelcomeActivity -> stub` 只有 **2 次**（上一轮 319 次、再上一轮 735 次）。
- **WelcomeActivity 有没有被真正创建: 是。** `newActivity: stub -> com.tencent.mm.plugin.account.ui.WelcomeActivity`（20:36:07.188）出现了。
- **ActivityTaskManager 的 result code: 全部是 0**。StubActivity 相关的 3 条 START 全是 `result code=0`（新建）；`code=3` 为 0 条。
  这两次 WelcomeActivity 的启动分别分配到了 `StubActivity1`、`StubActivity2`（轮换分配，目标桩 ≠ 栈顶桩），符合预期。
- **微信有没有显示出界面: 有，欢迎页**（描述见上；登录/注册按钮可见，没有进一步操作）。
- 新崩溃/新卡点: 没有新崩溃（FATAL 0）。唯一的新现象是**启动期 ANR 弹窗**：
  - 点击后第一次采样（约 +8 秒）弹窗就已经在了，对应 `Displayed ...StubActivity2: +7s917ms` 和 ActivityTaskManager 的 `resumed state loss timeout` / `pause timeout` / `Launch timeout` 警告：
    启动期间主线程长时间忙，系统等不到它响应生命周期事务，就弹了 ANR。
  - 到 +14 秒、+20 秒的看门狗采样时主线程是空闲的（`nativePollOnce`），说明启动完成后主线程没有卡。
  - 点系统弹窗的"等待"后，弹窗没有再出现（点完 4 秒后的窗口列表里只有 `StubActivity2`，没有 Application Not Responding）。
- 本地 AI 的观察（未验证）: 启动期主线程忙了约 8 秒（Application.onCreate 里的微信初始化，从 `set mInitialApplication` 20:36:01.654 到 `virtual Application created` 20:36:05.049，再到 LauncherUI/WelcomeActivity 的创建和首帧）。
  如果要消除 ANR 弹窗，需要缩短主线程上的启动耗时（或让不必要的初始化异步），这是后面的优化项，不影响"能显示出界面"这个结论。

## 异常计数
FATAL EXCEPTION: 0 次
plugin Application.onCreate failed: 1 次（被捕获：Scene Activity process mismatch，与前两轮相同）
rewriteIntent(WelcomeActivity -> stub): 2 次（flags=0x20000000）
newActivity: stub: 2 次（LauncherUI、WelcomeActivity）
ActivityTaskManager START StubActivity*: 3 次，result code=0 共 3 次，result code=3 共 0 次
mCoreAccount not initialized: 0 次
Resources$NotFoundException: 0 次
ACCESS_NETWORK_STATE: 0 次
baseRevision must not be null: 0 次
Skeleton not initialized: 0 次
UnsatisfiedLinkError: 0 次
ClassNotFoundException: 0 次
SecurityException: 0 次
NoClassDefFoundError: 0 次
ANR 弹窗: 有（约 +8 秒出现；点系统弹窗上的"等待"后消失）
