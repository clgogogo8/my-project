# 测试结果

- commit: a11ea77（Serve virtual-package content:// providers in-process via AMS hook；本地 HEAD 即该提交）
- 编译: 成功（:app，BUILD SUCCESSFUL）
- 设备: 小米 M2011K2C / Android 14，手机上没有真实微信；微信 8.0.78 的 APK 是电脑备份副本（单个 base.apk，280614450 字节，arm64-v8a，非 split）
- 现象: **回归通过**——欢迎页 → 手机号登录页（MobileInputUI）→ 账号/密码登录页（LoginUI）全部稳定显示，宿主进程 PID 6757 全程存活，没有崩溃、没有 native 崩溃、没有新的 ANR/黑屏。
  欢迎页启动期有一次系统 ANR 弹窗（点系统弹窗上的"等待"后消失）；之后点"登录"、"使用其他登录方式"、"用微信号/QQ号/邮箱登录"都正常。
  **没有输入任何账号/密码，没有点"同意并继续/同意并登录"，没有登录。**

## 关键 logcat（MultiOpen + Pine + ActivityTaskManager；30 条 provider installed、20 条 receiver registered 已折叠）

```
22:20:07.229 I/Pine: Pine native init...
22:20:07.252 I/MultiOpen: ART hook installed: ApplicationPackageManager.getInstallerPackageName
22:23:12.653 I/MultiOpen: resources built ... (280614450 bytes) / resources self-check OK: 0x7f1202a7 -> com.tencent.mm:style/lc
22:23:12.654 I/MultiOpen: fake process name -> com.tencent.mm
22:23:13.594 I/MultiOpen: set mInitialApplication -> com.tencent.mm.app.Application
22:23:15.369 I/Pine: handleBridge: artMethod=0x71838858 ...                    ← getInstallerPackageName 被 Pine 接管（Application 初始化阶段）
22:23:16.525 E/MultiOpen: plugin Application.onCreate failed                    （同前：Scene Activity process mismatch ... declared=null；被捕获）
22:23:16.525 I/MultiOpen: virtual Application created: com.tencent.mm.app.Application
22:23:16.603 I/MultiOpen: newActivity: stub -> com.tencent.mm.ui.LauncherUI
22:23:17.255 rewriteIntent: ...WelcomeActivity -> stub (flags=0x20000000) → START StubActivity1 code=0
22:23:17.515 rewriteIntent: ...WelcomeActivity -> stub (flags=0x20000000) → START StubActivity2 code=0
22:23:18.955 newActivity: stub -> com.tencent.mm.plugin.account.ui.WelcomeActivity
22:23:19.580 Displayed com.example.multiopen/.StubActivity2 for user 0: +8s0ms
22:23:33.367 rewriteIntent: ...MobileInputUI -> stub (flags=0x0) → START StubActivity3 code=0              ← 点"登录"
22:23:33.417 newActivity: stub -> com.tencent.mm.plugin.account.ui.MobileInputUI
22:23:33.949 Displayed com.example.multiopen/.StubActivity3 for user 0: +572ms
22:23:34.018 I/Pine: handleBridge: artMethod=0x71838858 ...                    ← 登录页显示后 69 毫秒，Pine 又接管一次（前几轮崩溃的时刻），无异常
22:23:44.375 rewriteIntent: ...LoginUI -> stub (flags=0x0) → START StubActivity4 code=0                  ← 点"用微信号/QQ号/邮箱登录"
22:23:44.463 newActivity: stub -> com.tencent.mm.plugin.account.ui.LoginUI
22:23:44.763 Displayed com.example.multiopen/.StubActivity4 for user 0: +382ms
```

## 本轮云端 Claude 要求的重点

1. **回归: 通过。** 欢迎页、手机号登录页、账号/密码页都稳定显示（我截图确认了账号/密码页：标题"微信号/QQ号/邮箱登录"，账号、密码输入框，灰色"同意并登录"，"用手机号登录"链接，"找回密码 | 更多"），无崩溃，与上一轮一致。
2. **`served local content provider: <authority>`: 0 次，没有出现。** 日志里也没有任何含 `getContentProvider` / `authority` / `content://` / `local content` 的行（排除 `provider installed` 之外）。
   我的理解（推测）：这条日志只在微信向系统请求"已登记的插件 provider 的 authority"时才会打；本轮的操作（欢迎页 → 登录页 → 账号/密码页）没有触发这类 content:// 查询，所以没有命中。我没有核对微信内部什么时候会发这类查询。
3. **新的崩溃 / ANR / 黑屏: 没有。** FATAL EXCEPTION 0 次；NativeCrash 0 次；进程死亡 0 次。（启动期那一次 ANR 弹窗上一轮之前就有，点"等待"后消失。）
4. **头像选择 / 从相册选图 / 拍照等触发 FileProvider 的入口: 我没有去点（跳过）。**
   原因：在登录相关页面里我没有看到能不登录就进入这类入口的元素；我所知的入口在"注册"流程里（需要进入注册页、会涉及相册），而相册会显示手机里的照片，涉及隐私，我也没有被要求去走注册流程，所以不点。需要的话请云端告诉我可以走哪条具体路径。

### 本地 AI 的备注

- Pine hook 的工作状态与上一轮一致：`ART hook installed` 1 次；`handleBridge` 2 次（22:23:15.369 在 Application 初始化阶段；22:23:34.018 在登录页 Displayed 后约 69 毫秒，正是之前 Cronet 触发 `Unknown package` 崩溃的时刻）；`Unknown package` 0 次，没有崩溃。
- 桩的使用与上一轮相同：`MobileInputUI → StubActivity3`，`LoginUI → StubActivity4`，`result code=0`；没有 `StubTask*` / `StubInstance*`。
- 欢迎页启动期 `Displayed ...StubActivity2: +8s0ms`（约 8 秒）还是慢，伴随一次 ANR 弹窗；与本次改动无关，之前就有。
- 操作插曲（与被测应用无关）：这一轮添加实例时系统文件选择器又停在了 `下载内容 > advanced` 子目录，我先点面包屑"下载内容"回到根目录再选 `real.apk`，添加成功；只是浏览，没有选择或改动其他文件。

## 异常计数
FATAL EXCEPTION: 0 次（本轮没有 uiautomator 的 NPE）
native 崩溃 / NativeCrash: 0 次
进程死亡: 0 次（宿主进程 PID 6757 全程存活）
`served local content provider`: 0 次
`Unknown package: com.tencent.mm`: 0 次
`ART hook installed`: 1 次；Pine `handleBridge`: 2 次
plugin Application.onCreate failed: 1 次（被捕获：Scene Activity process mismatch）
rewriteIntent: 4 次（WelcomeActivity ×2，flags=0x20000000；MobileInputUI ×1、LoginUI ×1，flags=0x0）
START StubActivity*: 5 次（StubActivity、StubActivity1～4），全部 result code=0
mCoreAccount not initialized: 0 次
Resources$NotFoundException: 0 次
ACCESS_NETWORK_STATE: 0 次
baseRevision must not be null: 0 次
Skeleton not initialized: 0 次
UnsatisfiedLinkError: 0 次
ClassNotFoundException: 0 次
SecurityException: 0 次
NoClassDefFoundError: 0 次
ANR 弹窗: 有 1 次（欢迎页启动期；点"等待"后消失）
