# 测试结果

- commit: 3807cff（B route step 1: ART-hook getInstallerPackageName via Pine）
- 编译: **成功**。Pine 依赖 `top.canyie.pine:core:0.3.0` 拉到了，BUILD SUCCESSFUL in 14s。输出里有 `Unable to strip the following libraries, packaging them as they are: libpine.so.`（只是提示）和 ArtHook.kt:24 的 `getInstallerPackageName(String)` deprecated 警告。
- 设备: 小米 M2011K2C / Android 14，手机上**没有**真实微信；微信 8.0.78 的 APK 是电脑备份副本。
- 被测 APK: 微信 8.0.78（单个 base.apk，280614450 字节，arm64-v8a，非 split）
- 现象: **重大进展：微信的登录页稳定显示出来了，不再崩溃。** 点"登录"后，登录页（手机号登录）一直停在屏幕上，宿主进程 PID 4950 从头活到尾，没有 native 崩溃、没有 `Unknown package`、没有任何进程死亡。
  我还点了登录页上的"使用其他登录方式"→ 弹出底部菜单 → 点"用微信号/QQ号/邮箱登录"→ 进入账号/密码登录页，同样稳定，没有崩溃。**我没有在任何输入框里输入任何东西，也没有点"同意并继续/同意并登录"，没有登录。**

## 微信界面（我看到的，只描述；截图没有提交）

1. 欢迎页（本轮没出现 ANR 弹窗）：深蓝地球图 + 右上"语言" + 底部绿色"登录"、白色"注册"。
2. **点"登录"后 → 手机号登录页（`MobileInputUI`）**：浅灰背景；左上角"×"；居中大标题"手机号登录"；"国家/地区  中国大陆（+86）"；"手机号"输入框（提示"请填写手机号码"，光标在闪）；
   灰色小字"上述手机号仅用于登录验证"；蓝色链接"使用其他登录方式"；灰色不可点的按钮"同意并继续"；底部"找回密码 | 更多"。
3. 点"使用其他登录方式"→ 屏幕变暗，底部弹出菜单：**"用微信号/QQ号/邮箱登录"、"用 Facebook 账号登录"、"取消"**。
4. 点"用微信号/QQ号/邮箱登录"→ **账号/密码登录页（`LoginUI`）**：左上"×"；标题"微信号/QQ号/邮箱登录"；"账号"输入框（提示"请填写微信号/QQ号/邮箱"）；"密码"输入框（提示"请填写密码"）；
   灰色小字"上述微信号/QQ号/邮箱仅用于登录验证"；蓝色链接"用手机号登录"；灰色按钮"同意并登录"；底部"找回密码 | 更多"。
5. 我没有再往下点（没有点输入框、没有点"同意并登录"、没有点"Facebook"）。

## 关键 logcat（MultiOpen + Pine + 系统；30 条 provider installed、20 条 receiver registered 已折叠）

### Pine / ART hook（本轮重点 2）

```
21:59:06.692 I/MultiOpen(4950): ServiceManager package binder wrapped
21:59:06.694 I/Pine    (4950): Pine native init...
21:59:06.715 D/Pine    (4950): Hooking method public java.lang.String android.app.ApplicationPackageManager.getInstallerPackageName(java.lang.String) with callback com.example.multiopen.ArtHook$install$2@...
21:59:06.715 D/Pine    (4950): InstallReplacementTrampoline: origin 0x71838858 origin_entry 0x7daf2a9510 bridge_jump 0x7e4ae7c000
21:59:06.715 I/MultiOpen(4950): ART hook installed: ApplicationPackageManager.getInstallerPackageName          ← 宿主启动时一次，成功
22:03:06.182 I/Pine    (4950): handleBridge: artMethod=0x71838858 ...
22:03:06.182 D/Pine    (4950): handleCall for method public java.lang.String android.app.ApplicationPackageManager.getInstallerPackageName(java.lang.String)   ← 微信 Application 初始化阶段
22:03:41.220 I/Pine    (4950): handleBridge: artMethod=0x71838858 ...
22:03:41.220 D/Pine    (4950): handleCall for method ... ApplicationPackageManager.getInstallerPackageName(java.lang.String)   ← 登录页刚 Displayed 之后 57 毫秒
```
- `ART hook installed: ApplicationPackageManager.getInstallerPackageName`：**出现了**（1 次，宿主启动时）。`ART hook install failed`：0 次。
- Pine 的 `handleCall` 触发 **2 次**：第 1 次在微信 Application 初始化阶段（22:03:06.182），第 2 次在 **`MobileInputUI` 显示后 57 毫秒（22:03:41.220）**——
  这正是前几轮 Cronet `BuildInfo` 触发 `Unknown package` 崩溃的时刻（前几轮是 Displayed 后约 50～75 毫秒）。**这一次没有崩溃。**

### 登录相关的完整时间线（宿主进程始终是 PID 4950）

```
22:03:02.211 START u0 {cmp=com.example.multiopen/.StubActivity (has extras)} ... result code=0        ← 打开微信实例
22:03:04.213 I/MultiOpen: set mInitialApplication -> com.tencent.mm.app.Application
22:03:07.103 E/MultiOpen: plugin Application.onCreate failed   （同前：Scene Activity process mismatch ... declared=null current=com.tencent.mm；被捕获）
22:03:07.104 I/MultiOpen: virtual Application created: com.tencent.mm.app.Application
22:03:07.160 I/MultiOpen: newActivity: stub -> com.tencent.mm.ui.LauncherUI
22:03:10.177 rewriteIntent: ...WelcomeActivity -> stub (flags=0x20000000)  → START StubActivity1 result code=0
22:03:10.453 rewriteIntent: ...WelcomeActivity -> stub (flags=0x20000000)  → START StubActivity2 result code=0
22:03:10.748 newActivity: stub -> com.tencent.mm.plugin.account.ui.WelcomeActivity
22:03:10.984 Displayed com.example.multiopen/.StubActivity2 for user 0: +8s782ms
22:03:40.611 rewriteIntent: com.tencent.mm.plugin.account.ui.MobileInputUI -> stub (flags=0x0)   ← 点"登录"
22:03:40.622 START u0 {cmp=com.example.multiopen/.StubActivity3 (has extras)} ... result code=0
22:03:40.678 newActivity: stub -> com.tencent.mm.plugin.account.ui.MobileInputUI
22:03:40.997 virtual Service created: com.tencent.mm.service.ProcessService$SupportProcessService
22:03:41.163 Displayed com.example.multiopen/.StubActivity3 for user 0: +541ms
22:03:41.220 Pine handleCall getInstallerPackageName                                              ← 没有 Unknown package，没有崩溃
22:04:47.617 rewriteIntent: com.tencent.mm.plugin.account.ui.LoginUI -> stub (flags=0x0)         ← 点"用微信号/QQ号/邮箱登录"
22:04:47.628 START u0 {cmp=com.example.multiopen/.StubActivity4 (has extras)} ... result code=0
22:04:47.710 newActivity: stub -> com.tencent.mm.plugin.account.ui.LoginUI
22:04:48.039 Displayed com.example.multiopen/.StubActivity4 for user 0: +415ms
```

## 本轮云端 Claude 要求的重点

1. **编译: Pine 依赖拉到了，编译通过。**
2. **`ART hook installed: ApplicationPackageManager.getInstallerPackageName`: 出现了**；`ART hook install failed`: 没有。
3. **`Unknown package: com.tencent.mm`: 0 次。SIGTRAP@libcronet: 没有**（`NativeCrash` 0 次，没有 `exited due to signal` / `exited cleanly`）。
4. **登录页（MobileInputUI）能不能稳定停住: 能。** 一直停到我离开，没有崩溃。描述见上。
5. **继续点页面元素: 做了。** 点了"使用其他登录方式"（弹出底部菜单）、"用微信号/QQ号/邮箱登录"（打开 `LoginUI`）。**没有新崩溃。**
   **桩的使用: 这两个页面都用的是通用池的桩：`MobileInputUI → StubActivity3`，`LoginUI → StubActivity4`，`flags=0x0`，`result code=0`。**
   本轮没有任何 `StubTask*` / `StubInstance*`（singleTask/singleInstance 专用桩）被分配到——`rewriteIntent` 里没有出现它们的名字。
   （我没有去核对 `MobileInputUI`、`LoginUI` 在微信清单里是不是 standard；只是日志里的分配结果是通用桩。）
6. （没有出现 Pine 加载失败或 hook 没挡住的情况，所以没有异常要贴。）

## 本地 AI 的备注

- **对比上一轮的因果证据**：上一轮（103df24）登录页显示后约 70 毫秒就有 `Unknown package` 异常 + 6 毫秒后 `SIGTRAP@libcronet`；这一轮同一时刻 Pine 的 `handleCall` 触发，没有异常，也没有崩溃。
  `ApplicationPackageManager.getInstallerPackageName` 这个 Java 方法被 Pine 钩住后，微信那个绕过所有代理的 `ig5.n1` 路径也被挡住了（因为最终它还是走这个 Java 方法）。
  这个结论的证据是：同一操作、同一时刻，去掉 hook 前有异常和崩溃，加上 hook 后没有。
- 欢迎页启动期的 `Displayed ...StubActivity2: +8s782ms` 依然很慢（约 8.8 秒），但本轮没有出现 ANR 弹窗。
- 启动时的 `plugin Application.onCreate failed`（Scene Activity process mismatch ... declared=null）仍是 1 次，被捕获，没有影响。
- **操作上的两个小插曲（和被测应用无关，写在这里只是为了解释日志）**：
  1. 这一轮我第一次添加微信时，系统文件选择器停在了 `下载内容 > advanced` 子目录，我的点击没有选中 `real.apk`，实例没有添加成功。我只是在选择器里浏览，没有选择或改动任何文件；之后改为先回到"下载内容"根目录再点 `real.apk`，添加成功。
  2. 日志里的 **3 条 `FATAL EXCEPTION` 都是我自己的 `uiautomator dump` 在文件选择器界面里抛的 NPE**（`AccessibilityNodeInfoDumper.childNafCheck`），与被测应用无关（22:02:19、22:02:21、22:02:36）。

## 异常计数
FATAL EXCEPTION: 3 次（**全部是 uiautomator 工具自身的 NPE，与应用无关**）；应用自身: 0 次
native 崩溃 / NativeCrash: 0 次
进程死亡（`exited due to signal` / `exited cleanly`）: 0 次（宿主进程 PID 4950 全程存活）
`Unknown package: com.tencent.mm`: 0 次
`ART hook installed`: 1 次；`ART hook install failed`: 0 次；Pine `handleCall`: 2 次
plugin Application.onCreate failed: 1 次（被捕获：Scene Activity process mismatch）
START StubActivity*: 5 次（StubActivity、StubActivity1、StubActivity2、StubActivity3、StubActivity4），全部 result code=0
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
