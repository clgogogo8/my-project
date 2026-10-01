# 测试结果

- commit: f0112f8
- 编译: 成功（BUILD SUCCESSFUL，:app / :test-hello / :test-multi）
- 设备: 小米 M2011K2C / Android 14
- 被测 APK: 微信 8.0.78（手机上已装的单个 base.apk，280614450 字节，arm64-v8a，非 split）
- 现象: 白屏，焦点一直为空；约 10 秒后系统弹出"MultiOpen没有响应"（ANR），进程卡死；4 次 FATAL EXCEPTION（均在同一进程）。
  资源自检通过，但 Resources$NotFoundException 仍然存在，崩溃点和上一轮（73fbb19）完全相同。

## 关键 logcat（只含 MultiOpen 标签 + 崩溃栈）

receiver registered 共 20 条、provider installed 共 30 条，均已折叠（都是微信的类名，无异常）。

```
19:18:55.311 I/MultiOpen(22558): IActivityManager hooked
19:18:55.315 I/MultiOpen(22558): IPackageManager hooked
19:19:05.552 I/MultiOpen(22558): extracted 209 so (arm64-v8a) -> /data/user/0/com.example.multiopen/files/virtual/1790849941595/lib
19:19:21.201 I/MultiOpen(22558): resources built: cookie=15, apk=/data/user/0/com.example.multiopen/files/virtual/1790849941595/base.apk (280614450 bytes)
19:19:21.201 I/MultiOpen(22558): resources self-check OK: 0x7f1202a7 -> com.tencent.mm:style/lc
19:19:22.347 I/MultiOpen(22558): resources built: cookie=15, apk=/data/user/0/com.example.multiopen/files/virtual/1790849941595/base.apk (280614450 bytes)
19:19:22.347 I/MultiOpen(22558): resources self-check OK: 0x7f1202a7 -> com.tencent.mm:style/lc
19:19:23.669 E/MultiOpen(22558): plugin Application.onCreate failed
19:19:23.669 E/MultiOpen(22558): android.content.res.Resources$NotFoundException
19:19:23.669 E/MultiOpen(22558): Caused by: android.content.res.Resources$NotFoundException: Resource ID #0x7f11028f
19:19:23.670 I/MultiOpen(22558): virtual Application created: com.tencent.mm.app.Application
19:19:25.470 I/MultiOpen(22558): newActivity: stub -> com.tencent.mm.ui.LauncherUI
19:19:25.518 I/MultiOpen(22558): virtual Service created: com.tencent.mm.ipcinvoker.wx_extension.service.MainProcessIPCService
```

崩溃（FATAL EXCEPTION 共 4 次，PID 22558：[GT]ColdPool#2、[GT]ColdPool#6、main、ANR-Dump-Thread；最后一个是 ANR 触发，不是新问题）：

```
FATAL EXCEPTION: [GT]ColdPool#2
android.content.res.Resources$NotFoundException: Resource ID #0x7f0e06ad
    at le5.j.getLayout(Unknown Source:2)
    at kw5.l0.invoke(Unknown Source:80) ← pp0.r.run ← q36.l.run ← r36.v.run ← j36.c.run

FATAL EXCEPTION: [GT]ColdPool#6
android.content.res.Resources$NotFoundException: Resource ID #0x7f110838
    at le5.j.openRawResource(Unknown Source:0)
    at com.tencent.mm.plugin.report.service.j.a / j.b ← g0.Q ← g0.idkeyStat ← u44.f.idkeyStat

FATAL EXCEPTION: main
java.lang.RuntimeException: Unable to start activity ComponentInfo{com.example.multiopen/com.example.multiopen.StubActivity}: android.content.res.Resources$NotFoundException: Resource ID #0x7f110838
Caused by: android.content.res.Resources$NotFoundException: Resource ID #0x7f110838
    at le5.j.openRawResource(Unknown Source:0)
    at com.tencent.mm.plugin.report.service.j.a(Unknown Source:16)
    at com.tencent.mm.plugin.report.service.j.b(Unknown Source:11)
    at com.tencent.mm.plugin.report.service.g0.Q(Unknown Source:17)
    at com.tencent.mm.plugin.report.service.g0.idkeyStat(Unknown Source:128)
    at u44.f.idkeyStat(Unknown Source:6)
    at com.tencent.mm.sdk.platformtools.r4.Z(Unknown Source:67)
    at com.tencent.mm.network.a3.j(Unknown Source:21)
    at com.tencent.mm.booter.NotifyReceiver.c(Unknown Source:11)
    at qt.m6.callback(Unknown Source:11)
    at com.tencent.mm.sdk.event.d.d(Unknown Source:268)
    at com.tencent.mm.sdk.event.IEvent.e(Unknown Source:3)
    at com.tencent.mm.ui.ek.a(Unknown Source:16)
    at com.tencent.mm.ui.MMFragmentActivity.onCreate(Unknown Source:43)
    at com.tencent.mm.ui.LauncherUI.onCreate(Unknown Source:130)
    at com.example.multiopen.HookInstrumentation.callActivityOnCreate(HookInstrumentation.kt:44)
```

## 本轮云端 Claude 要求的额外诊断

- `resources built`: cookie=15, apk=.../virtual/1790849941595/base.apk (280614450 bytes)，出现 2 次（19:19:21 和 19:19:22），内容相同。
  APK 字节数与手机上已装微信 base.apk 大小一致。
- 资源自检: **self-check OK**（出现 2 次）: 0x7f1202a7 -> com.tencent.mm:style/lc。没有出现 self-check FAILED。
  注意：自检用的是 0x7f1202a7，不是崩溃的那三个 ID。
- 上一轮的 aapt2 结果（供参考，本轮未重跑）: 0x7f11028f（raw/domain_mainland，r/s/domain_mainland.json）、
  0x7f0e06ad（layout/a8y，r/p/a8y.xml）、0x7f110838（raw/invalid_idkey，r/s/invalid_idkey.txt）
  都在资源表里，对应文件也都在 APK 压缩包里；APK 里没有 res/ 目录，资源路径被混淆到 r/ 下。
- 本地 AI 的推断（未看宿主代码，未验证）: 宿主构造的 Resources 能解析微信资源（自检 OK），
  但报错的是微信自己的 Resources 类 le5.j（getLayout / openRawResource），
  所以微信运行时实际用的 Resources 对象，可能不是宿主构造的那个。

## 异常计数
Resources$NotFoundException: 7 次（含该字样的日志行数，包括 FATAL 标题、Caused by 以及 plugin Application.onCreate failed 里的行；
  涉及 3 个不同资源 ID：0x7f11028f、0x7f0e06ad、0x7f110838）
FATAL EXCEPTION: 4 次
ACCESS_NETWORK_STATE: 0 次
baseRevision must not be null: 0 次
Skeleton not initialized: 0 次
UnsatisfiedLinkError: 0 次
ClassNotFoundException: 0 次
SecurityException: 0 次
NoClassDefFoundError: 0 次
