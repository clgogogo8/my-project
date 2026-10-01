# 测试结果

- commit: 4924b43（Update test plan: keep-alive smoke test；含 eb62eed "Route virtual-service startForeground to a host keep-alive service"）
- 编译: **成功**（:app，BUILD SUCCESSFUL；新增的 KeepAliveService.kt、ArtHook 两个 hook、manifest service 都编过；唯一警告 ArtHook.kt:25 `getInstallerPackageName(String)` is deprecated）
- 设备: 小米 M2011K2C / Android 14，手机上没有真实微信；微信 8.0.78 的 APK 是电脑备份副本（单个 base.apk，280614450 字节，arm64-v8a，非 split）。
- 现象: **冒烟通过**——KeepAliveService 装上、启动不崩、回归还在。欢迎页 → 手机号登录页（MobileInputUI）→ 账号/密码登录页（LoginUI）稳定显示、画面正常，宿主进程 PID 20765 全程存活，无崩溃。
  冷启动期有 1 次系统 ANR 弹窗（点系统弹窗"等待"后消失，与前几轮一致）。
  **这个流程里没有任何虚拟 Service 调用 `startForeground`，所以前台保活路径本轮没有被触发**（`startForeground intercepted` 0 次，通知栏没有宿主通知）。
  **没有输入任何账号/密码，没有点"同意并登录"，没有登录。**

## 关键 logcat（MultiOpen + Pine + ActivityTaskManager；30 条 provider installed、20 条 receiver registered 已折叠）

### 启动日志（本轮重点 2）

```
22:58:49.378 I/Pine: Pine native init...
22:58:49.397 I/MultiOpen: ART hook installed: ApplicationPackageManager.getInstallerPackageName
22:58:49.397 I/MultiOpen: ART hook installed: Service.startForeground/stopForeground routing        ← 本轮新增，宿主进程启动时 1 次
```
- `ART hook installed: Service.startForeground/stopForeground routing`: **出现 1 次**（宿主启动时）。
- `ART foreground-routing hook install failed`、`hook startForeground(...) 跳过`、`hook stopForeground(...) 跳过`: **0 次**（我 grep 了 `install failed|跳过`，没有匹配）。
  也就是说 Pine 在这台设备/Android 14 上成功 hook 到了这几个方法（至少没有报失败/跳过）。我没有验证 hook 真正生效，因为本轮没有触发。
- Pine `handleBridge`: 共 2 次（与前几轮的次数相同；我这轮没有把这 2 次对应到具体时刻，也没有区分是哪个 hook 触发的）。

### 本轮重点 4（顺带）

- `startForeground intercepted for virtual service <类名>`: **0 次**；`stopForeground intercepted`: **0 次**。
- 通知栏: **没有出现宿主的"正在后台运行"通知**（`dumpsys notification` 里 `pkg=com.example.multiopen` 0 条）。
- `KeepAliveService` 相关日志（promote/demote/startForeground 失败）: **没有任何行**（我 grep 了 `startForeground|stopForeground|KeepAlive|foreground`，只匹配到启动时那一条 hook 日志，以及微信 provider 名 `AppForegroundDelegate$Provider`，与保活无关）。
- 前台服务: `dumpsys activity services com.example.multiopen` 里没有 ServiceRecord / isForeground 输出（说明本轮没有宿主 Service 在运行）。
- 本轮创建的虚拟 Service（`virtual Service created`），都没有走到 startForeground：
  ```
  22:59:13.240 com.tencent.mm.service.ProcessService$MMProcessService
  22:59:14.860 com.tencent.mm.ipcinvoker.wx_extension.service.PushProcessIPCService
  22:59:15.140 androidx.work.impl.background.systemalarm.SystemAlarmService
  22:59:30.742 com.tencent.mm.service.ProcessService$SupportProcessService
  ```

### 页面时间线（桩分配与前几轮相同）

```
rewriteIntent: ...WelcomeActivity -> stub (flags=0x20000000) ×2 → StubActivity1 / StubActivity2，result code=0
Displayed com.example.multiopen/.StubActivity2 for user 0: +6s789ms          ← 欢迎页（冷启动；这次比前几轮的 ~8s 快）
rewriteIntent: ...MobileInputUI -> stub (flags=0x0) → StubActivity3 result code=0    Displayed +541ms
rewriteIntent: ...LoginUI -> stub (flags=0x0)       → StubActivity4 result code=0    Displayed +401ms
```
`theme check` 4 条（与上一轮一致，Welcome `translucent=false`，MobileInputUI/LoginUI `translucent=true floating=false -> translucentStub=false`）；`StubTranslucent*` 0 次。

## 本轮云端 Claude 要求的重点

1. **编译: 成功。** 新加的 KeepAliveService.kt、两个 hook、manifest service 都编译通过。
2. **启动日志: 有 `ART hook installed: Service.startForeground/stopForeground routing`（1 次）；没有失败/跳过日志。**
3. **回归: 通过。** 欢迎页 / 手机号登录页 / 账号密码页稳定、无崩溃、画面正常（我对账号/密码页截图确认：浅灰色背景的竖屏页面，标题"微信号/QQ号/邮箱登录"，账号、密码输入框，灰色"同意并登录"按钮，"用手机号登录"链接，"找回密码 | 更多"）。
4. 顺带: 没有触发，见上。
5. 没有登录。

## 异常计数
FATAL EXCEPTION: 0 次
native 崩溃 / NativeCrash: 0 次
进程死亡: 0 次（宿主 PID 20765 全程存活）
`Unknown package: com.tencent.mm`: 0 次
`ART hook installed: ...routing`: 1 次
`startForeground intercepted`: 0 次
`stopForeground intercepted`: 0 次
ART foreground-routing hook install failed / 跳过: 0 次
plugin Application.onCreate failed: 1 次（被捕获：Scene Activity process mismatch）
rewriteIntent: 4 次；`theme check`: 4 次；StubTranslucent*: 0 次
START StubActivity*: 5 次，全部 result code=0
mCoreAccount not initialized: 0 次
Resources$NotFoundException: 0 次
ACCESS_NETWORK_STATE: 0 次
baseRevision must not be null: 0 次
Skeleton not initialized: 0 次
UnsatisfiedLinkError: 0 次
ClassNotFoundException: 0 次
SecurityException: 0 次
NoClassDefFoundError: 0 次
ANR 弹窗: 有 1 次（欢迎页冷启动期；点"等待"后消失）
