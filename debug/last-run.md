# 测试结果

- commit: 5b21dec（Add translucent-theme stubs and route translucent activities to them；改动 AndroidManifest.xml、HookInstrumentation.kt、StubActivity.kt）
- 编译: 成功（:app，BUILD SUCCESSFUL）
- 设备: 小米 M2011K2C / Android 14，手机上没有真实微信；微信 8.0.78 的 APK 是电脑备份副本（单个 base.apk，280614450 字节，arm64-v8a，非 split）。
- 现象: **回归通过**——欢迎页 → 手机号登录页（MobileInputUI）→ 账号/密码登录页（LoginUI）全部稳定显示、画面正常，宿主进程 PID 19897 全程存活，无崩溃，本轮冷启动期没有出现 ANR 弹窗。
  **但有一处和预期不同：手机号登录页和账号/密码页被分配到了透明桩 `StubTranslucent0` / `StubTranslucent1`**（next-step 预期这几个页面应该是通用桩）。页面渲染看起来没有问题。
  **没有输入任何账号/密码，没有点"同意并继续/同意并登录"，没有登录。**

## 关键 logcat（MultiOpen + ActivityTaskManager）

页面时间线与桩分配：

```
START u0 {cmp=.../.StubActivity ...} result code=0                                              ← 打开实例（LauncherUI）
22:46:29.274 rewriteIntent: ...WelcomeActivity -> stub (flags=0x20000000) → START StubActivity1 code=0
22:46:29.439 rewriteIntent: ...WelcomeActivity -> stub (flags=0x20000000) → START StubActivity2 code=0
Displayed com.example.multiopen/.StubActivity2 for user 0: +8s75ms                              ← 欢迎页（冷启动）
22:46:48.423 rewriteIntent: ...MobileInputUI -> stub (flags=0x0)
22:46:48.436 I/ActivityTaskManager: START u0 {cmp=com.example.multiopen/.StubTranslucent0 (has extras)} ... result code=0   ← 手机号登录页，分到透明桩
22:46:48.963 Displayed com.example.multiopen/.StubTranslucent0 for user 0: +531ms
22:47:00.267 rewriteIntent: ...LoginUI -> stub (flags=0x0)
22:47:00.279 I/ActivityTaskManager: START u0 {cmp=com.example.multiopen/.StubTranslucent1 (has extras)} ... result code=0   ← 账号/密码页，分到透明桩
22:47:00.663 Displayed com.example.multiopen/.StubTranslucent1 for user 0: +390ms
```
（30 条 provider installed、20 条 receiver registered 已折叠；`ART hook installed` 1 次；日志里**没有**任何与"透明判断"相关的 MultiOpen 日志行，所以我看不到判断依据。）

`rewriteIntent` 本轮只有 4 条：WelcomeActivity ×2（flags=0x20000000）、MobileInputUI ×1、LoginUI ×1（flags=0x0）。`StubTranslucent*` 在日志里一共出现 4 次，就是上面这 4 行 START / Displayed。

## 本轮云端 Claude 要求的重点

1. **回归: 通过。** 欢迎页 / 手机号登录页 / 账号密码页稳定、无崩溃（FATAL 0、NativeCrash 0、`Unknown package` 0、进程死亡 0）。我对账号/密码页截图确认：浅灰色背景的竖屏页面，标题"微信号/QQ号/邮箱登录"，账号、密码输入框，灰色"同意并登录"按钮，"用手机号登录"链接，"找回密码 | 更多"，全部正常显示，没有透视/叠层/黑底之类的异常。
2. **桩分配: 不是预期的全部通用桩。** 欢迎页仍是通用桩（StubActivity1/2）；**MobileInputUI → `StubTranslucent0`，LoginUI → `StubTranslucent1`，是"意外分配到透明桩"。**
3. **新异常/崩溃: 没有。** E 级 MultiOpen 日志只有 1 条被捕获的 `plugin Application.onCreate failed`（Scene Activity process mismatch，与前几轮相同）。
4. 没有登录。

### 本地 AI 的核查：这两个页面的主题到底是不是透明的（只读 aapt2 对微信 APK 的输出，没有改任何东西）

- **MobileInputUI 和 LoginUI 在清单里没有声明 `android:theme`**（`aapt2 dump xmltree` 里这两个 `<activity>` 元素没有 theme 属性；LoginUI 只有 `screenOrientation=1`），所以它们实际用的是 **application 级主题 `@0x7f1202a7`（混淆名 `style/lc`）**。
  作为对比：WelcomeActivity 和 LauncherUI 各自声明了 `theme=@0x7f1202b2`。
- 我沿着这两个主题的 parent 链往上追（application 主题 15 层，Welcome/LauncherUI 主题 17 层，最后都汇到 `0x7f120317`），按框架属性的数字 ID 查找：
  - application 主题 `0x7f1202a7` 这一条链上：**没有任何一层设置了 `windowIsTranslucent`（0x01010054）或 `windowIsFloating`（0x01010056）。**
  - Welcome/LauncherUI 主题 `0x7f1202b2` 这一条链上：有一层（混淆名 `hx`）写了 `windowIsTranslucent: 0x01010054=@null`（值是 `@null` 引用，不是 `true`）。
  - 整个资源表里，`windowIsTranslucent=true` 和 `windowIsFloating=true` 的 style 条目：**0 条**（我 grep 了 `0x01010054)=true` / `0x01010056)=true`，aapt2 对布尔值的打印格式我没有核实，所以这个"0 条"只能当作参考，不是定论）。
- **据此我的判断：从清单主题看，MobileInputUI 和 LoginUI 不应该被判成透明。** 它们被分到透明桩，更像是云端新加的"透明判断"逻辑有 bug。
  **我无法确定具体原因（推测，未验证）**：可能是对"没有声明 theme 的 Activity"用的主题解析路径取到了异常值；也可能是对 `windowIsTranslucent=@null` 这种引用的处理把"无值/@null"当成了 true；也可能是读 `windowIsFloating`/`windowIsTranslucent` 时用了 `obtainStyledAttributes` 的默认值。
  Welcome（主题链上有 `windowIsTranslucent=@null`）反而没被判成透明，所以"@null 被当 true"这个猜测并不完全对得上；真正原因需要云端在判断处打日志（输入的主题 id、解析出的两个布尔值）才能确定。
- 这次分错没有造成可见的问题：两个页面都渲染正常、进程稳定。风险在于以后真正需要不透明窗口的页面被放进透明桩（或者反过来），可能出现背景透出、状态栏/导航栏样式不同之类的差异；本轮没有观察到。

## 异常计数
FATAL EXCEPTION: 0 次
native 崩溃 / NativeCrash: 0 次
进程死亡（`exited due to signal` / `exited cleanly`）: 0 次（宿主 PID 19897 全程存活）
`Unknown package: com.tencent.mm`: 0 次
plugin Application.onCreate failed: 1 次（被捕获：Scene Activity process mismatch）
`ART hook installed`: 1 次
rewriteIntent: 4 次（WelcomeActivity ×2，flags=0x20000000；MobileInputUI ×1、LoginUI ×1，flags=0x0）
START StubActivity*: 5 次，全部 result code=0（StubActivity、StubActivity1、StubActivity2、StubTranslucent0、StubTranslucent1）
意外分配到 StubTranslucent*: 2 次（MobileInputUI、LoginUI）
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
