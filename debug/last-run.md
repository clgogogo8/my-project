# 测试结果

- commit: bea49c7（Fake the process name so WeChat initializes its main-process Kernel）
- 编译: 成功（:app，BUILD SUCCESSFUL）
- 设备: 小米 M2011K2C / Android 14
- 被测 APK: 微信 8.0.78（手机上已装的单个 base.apk，280614450 字节，arm64-v8a，非 split）
- 现象: **崩溃消失，但换成卡死（ANR）**。`fake process name -> com.tencent.mm` 已出现；`mCoreAccount not initialized` 为 0 次；FATAL EXCEPTION 为 0 次；
  不再反复重启（整个观察期只有 1 个进程 PID 8372）。但屏幕一直是白屏/无焦点，约 10 秒后系统弹出"MultiOpen没有响应"（ANR），
  我没有点"确定"，继续观察到约 20 秒，ANR 弹窗一直在，进程存活但 CPU 0%。**没有出现任何微信界面。**
  日志在最后一个 provider 安装完之后就没有任何新的 MultiOpen 行了（没有 virtual Application created，没有 receiver registered，没有 newActivity），
  说明主线程卡在微信的 Application.onCreate 里没有返回。

## 关键 logcat（只含 MultiOpen 标签 + 崩溃栈）

整个观察期的全部 MultiOpen 行（30 条 provider installed 已折叠，只列首尾；没有 receiver registered；没有任何 E 级行）：

```
19:49:51.381 I/MultiOpen( 8372): IActivityManager hooked
19:49:51.384 I/MultiOpen( 8372): IPackageManager hooked
19:50:01.808 I/MultiOpen( 8372): extracted 209 so (arm64-v8a) -> /data/user/0/com.example.multiopen/files/virtual/1790851797686/lib
19:50:16.772 I/MultiOpen( 8372): resources built: cookie=15, apk=/data/user/0/com.example.multiopen/files/virtual/1790851797686/base.apk (280614450 bytes)
19:50:16.772 I/MultiOpen( 8372): resources self-check OK: 0x7f1202a7 -> com.tencent.mm:style/lc
19:50:17.913 I/MultiOpen( 8372): resources built: cookie=15, apk=/data/user/0/com.example.multiopen/files/virtual/1790851797686/base.apk (280614450 bytes)
19:50:17.913 I/MultiOpen( 8372): resources self-check OK: 0x7f1202a7 -> com.tencent.mm:style/lc
19:50:17.913 I/MultiOpen( 8372): fake process name -> com.tencent.mm
19:50:18.804 I/MultiOpen( 8372): set mInitialApplication -> com.tencent.mm.app.Application
19:50:18.915 I/MultiOpen( 8372): provider installed: com.tencent.mm.plugin.base.stub.MMPluginProvider [...]   ← 第 1 个
   …（共 30 个 provider installed；第 10 个 ExtControlProviderEntry，第 20 个 GameResourceDownloadProvider，第 28 个 androidx.lifecycle.ProcessLifecycleOwnerInitializer，
      第 29 个 com.tencent.shadow.core.runtime.container.PluginContainerContentProvider）
19:50:20.424 I/MultiOpen( 8372): provider installed: com.google.firebase.provider.FirebaseInitProvider [com.tencent.mm.firebaseinitprovider]   ← 第 30 个，也是日志里最后一行
```

没有崩溃栈（没有 FATAL EXCEPTION、没有 AndroidRuntime 输出）。

## 本轮云端 Claude 要求的额外诊断

- 是否出现 `fake process name -> com.tencent.mm`: **出现了**（19:50:17.913，1 次，在 resources built 之后、set mInitialApplication 之前）。
- `mCoreAccount not initialized` 还在不在: **没有了**（0 次）。
- `plugin Application.onCreate failed` 还有没有: **没有了**（0 次）。
- FATAL EXCEPTION 次数: **0**。是否仍白屏: 是（白屏 + 约 10 秒后 ANR）。是否还反复重启: **否**（单进程 PID 8372，一直存活）。
- 新的第一个异常/`Caused by`: **没有新异常**，这一轮没有任何异常日志，变成了卡死。
- 微信是否显示出任何界面: **没有**（没有闪屏/隐私弹窗/登录页）。
- 本地 AI 对卡死现场的观察（只读了 /proc，没有看宿主代码，未验证）:
  - 进程 8372 存活，CPU 0.0%，状态 S（睡眠）；`/proc/8372/task` 下 104 个线程**全部是 S 状态**，没有线程在跑（不是死循环，更像是互相等待/阻塞）。
  - 主线程 `/proc/8372/task/8372/stat` 状态为 S，wchan 读出来是 0（没有信息）。
  - 线程名里能看到微信自己的基础设施已经起来了：`[GT]ColdPool#N` 17 个、`[GT]HotPool#N` 8 个、`lu_worker.N` 8 个、`wc_srvinit_N` 7 个、`wc_lp_srvinit_N` 2 个、
    `MMCrashANRThrea` 2 个、`mars::N`/`mars::comm`、`WCDB.Operation`、`V8 DefaultWorke` 7 个、`Vending-LogicTh`/`Vending-HeavyWo`、`xh_refresh_loop`、`zlog_writer` 等。
    也就是说微信的 Application.onCreate 已经跑了相当一段（线程池、网络 mars、数据库 WCDB 都已创建），但主线程没有从 onCreate 返回。
  - 拿不到 Java 线程栈（SIGQUIT 的 trace 写到 /data/anr，普通权限读不到），所以不知道主线程具体阻塞在哪个锁/哪个调用上。
    如果云端想要主线程栈，需要给出能抓到它的办法（比如宿主自己在 N 秒后用 Thread.getAllStackTraces() 把主线程栈写进 MultiOpen 日志）。

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
ANR 弹窗: 1 次（约 +10 秒出现，之后一直在）
