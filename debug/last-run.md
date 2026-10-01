# 测试结果

- commit: fc28487（Fix false translucent-stub assignment; gate on windowIsFloating only；改动 HookInstrumentation.kt）
- 编译: 成功（:app，BUILD SUCCESSFUL）
- 设备: 小米 M2011K2C / Android 14，手机上没有真实微信；微信 8.0.78 的 APK 是电脑备份副本（单个 base.apk，280614450 字节，arm64-v8a，非 split）。
- 现象: **桩分配已修正且回归通过。** 欢迎页 → 手机号登录页（MobileInputUI）→ 账号/密码登录页（LoginUI）稳定显示、画面正常，宿主进程 PID 20073 全程存活，无崩溃，本轮冷启动没有出现 ANR 弹窗。
  **MobileInputUI → `StubActivity3`，LoginUI → `StubActivity4`（通用桩）；`StubTranslucent*` 在日志里 0 次。**
  **没有输入任何账号/密码，没有点"同意并继续/同意并登录"，没有登录。**

## ⚠ 更正（我上一轮 last-run.md 里的一处核查是错的）

上一轮（commit 5b21dec 那份）我写道：用 aapt2 查微信 APK 的主题，"application 主题链上没有任何一层设置 `windowIsTranslucent`（0x01010054）或 `windowIsFloating`（0x01010056）"，并据此判断"MobileInputUI/LoginUI 不应该被判成透明，更像云端的透明判断逻辑有 bug"。
**这个结论是错的，原因是我用错了框架属性 ID。** 我这一轮从手机的 `/system/framework/framework-res.apk` 重新核对（`aapt2 dump resources`）：

```
resource 0x01010054 attr/windowBackground
resource 0x01010056 attr/windowNoTitle
resource 0x01010057 attr/windowIsFloating
resource 0x01010058 attr/windowIsTranslucent
```
- 也就是说我上一轮查的 `0x01010054`、`0x01010056` 其实是 `windowBackground`、`windowNoTitle`；正确的是 **windowIsFloating = 0x01010057，windowIsTranslucent = 0x01010058**。
- 用正确的 ID 重查微信 APK 的主题链：
  - **application 主题 `0x7f1202a7`（混淆名 `style/lc`，MobileInputUI/LoginUI 实际使用）自己就显式设了 `windowIsTranslucent: 0x01010058=true`。**
  - Welcome/LauncherUI 的主题 `0x7f1202b2`（`style/lm`，链 17 层）上，`windowIsTranslucent` 和 `windowIsFloating` 都没有设置。
- 这与运行时日志一致（见下面 `theme check`：lc 是 `translucent=true`，lm 是 `translucent=false`）。
- 因此：**5b21dec 把 MobileInputUI/LoginUI 分到透明桩，是按"主题确实是 windowIsTranslucent=true"判的，不是判断逻辑的 bug**；我上一轮的"更像云端有 bug"的说法不对，请忽略。
  云端 fc28487 改成只认 `windowIsFloating` 的理由（沉浸式全屏主题会为状态栏设 `windowIsTranslucent=true`，但页面是不透明的）与我这一轮的事实不矛盾。
- 上一轮文件里还写过"Welcome 主题链上有 `windowIsTranslucent=@null`"，同样是 ID 用错（那其实是 `windowBackground=@null`），也请忽略。

## 关键 logcat（MultiOpen + ActivityTaskManager；30 条 provider installed、20 条 receiver registered 已折叠）

### `theme check` 日志（本轮重点 2，原样）

```
22:54:42.344 I/MultiOpen: theme check com.tencent.mm.plugin.account.ui.WelcomeActivity theme=0x7f1202b2(com.tencent.mm:style/lm) translucent=false floating=false -> translucentStub=false
22:54:42.518 I/MultiOpen: theme check com.tencent.mm.plugin.account.ui.WelcomeActivity theme=0x7f1202b2(com.tencent.mm:style/lm) translucent=false floating=false -> translucentStub=false
22:55:00.973 I/MultiOpen: theme check com.tencent.mm.plugin.account.ui.MobileInputUI theme=0x7f1202a7(com.tencent.mm:style/lc) translucent=true floating=false -> translucentStub=false
22:55:13.066 I/MultiOpen: theme check com.tencent.mm.plugin.account.ui.LoginUI theme=0x7f1202a7(com.tencent.mm:style/lc) translucent=true floating=false -> translucentStub=false
```
- 共 4 条（WelcomeActivity ×2、MobileInputUI ×1、LoginUI ×1）。
- **LauncherUI 没有 `theme check` 日志**：它不是通过 `rewriteIntent`（插件内部 startActivity）启动的，而是宿主的 MainActivity 打开实例时直接用 `StubActivity` 承载的，所以没经过这个判断点。next-step 要的四个里缺这一个。
- 结果：MobileInputUI、LoginUI 的 `translucent=true floating=false`，但 `translucentStub=false`（因为现在只认 floating）；WelcomeActivity `translucent=false floating=false`。**floating 对这些页面都是 false。**

### 桩分配与页面时间线

```
22:54:42.343 rewriteIntent: ...WelcomeActivity -> stub (flags=0x20000000) → StubActivity1 code=0
22:54:42.518 rewriteIntent: ...WelcomeActivity -> stub (flags=0x20000000) → StubActivity2 code=0
Displayed com.example.multiopen/.StubActivity2 for user 0: +8s703ms          ← 欢迎页（冷启动）
22:55:00.973 rewriteIntent: ...MobileInputUI -> stub (flags=0x0)  → START StubActivity3 result code=0
Displayed com.example.multiopen/.StubActivity3 for user 0: +615ms            ← 手机号登录页
22:55:13.066 rewriteIntent: ...LoginUI -> stub (flags=0x0)        → START StubActivity4 result code=0
Displayed com.example.multiopen/.StubActivity4 for user 0: +373ms            ← 账号/密码页
```
- START StubActivity*: StubActivity、StubActivity1、StubActivity2、StubActivity3、StubActivity4，共 5 次，全部 result code=0。
- **`START ... StubTranslucent*`：0 行（已消失）。**

## 本轮云端 Claude 要求的重点

1. **桩分配修正: 是。** MobileInputUI、LoginUI 回到通用桩（StubActivity3/4），`StubTranslucent*` 0 次。
2. **`theme check` 行: 已原样贴出**（缺 LauncherUI，原因见上）。
3. **回归: 通过。** 三个页面稳定、画面正常（我对账号/密码页截图确认：浅灰色背景的竖屏页面，标题"微信号/QQ号/邮箱登录"，账号、密码输入框，灰色"同意并登录"，"用手机号登录"链接，"找回密码 | 更多"；没有透视、叠层或黑底），无崩溃。
4. 没有登录。

## 异常计数
FATAL EXCEPTION: 0 次
native 崩溃 / NativeCrash: 0 次
进程死亡: 0 次（宿主 PID 20073 全程存活）
`Unknown package: com.tencent.mm`: 0 次
plugin Application.onCreate failed: 1 次（被捕获：Scene Activity process mismatch）
`ART hook installed`: 1 次
`theme check`: 4 次
rewriteIntent: 4 次（WelcomeActivity ×2，flags=0x20000000；MobileInputUI ×1、LoginUI ×1，flags=0x0）
START StubActivity*: 5 次，全部 result code=0；StubTranslucent*: 0 次
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
