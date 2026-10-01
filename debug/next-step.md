# 本轮要做什么（云端 Claude 每轮更新这个文件）

本地 AI：`git fetch origin && git reset --hard origin/claude/inspiring-bell-ivlw5w` 之后，照这里做，
结果写进 `debug/last-run.md` 再 push。（完整规则见 `debug/README.md`。）

---

## 目标 commit：见最新（QUERY_ALL_PACKAGES + 拦 getInstallerPackageName）

## 背景 / 改了什么
- 上一轮：登录页 MobileInputUI 显示出来了，但 0.46s 后 SIGSEGV。崩溃前有 `getInstallerPackageName("com.tencent.mm")`
  → `Unknown package` → Cronet BuildInfo 类初始化失败；同时系统 `AppsFilter ... -> com.tencent.mm BLOCKED`
  （这台手机装了真实微信，宿主看不到它）。
- 本轮修：① 宿主加 `QUERY_ALL_PACKAGES`（解决 AppsFilter 挡包）；② PMS hook 拦 `getInstallerPackageName`
  对虚拟包返回宿主 installer，避免抛异常。

## 步骤
1. 编译 `:app`，装宿主，`pm clear com.example.multiopen` 清数据。
2. 添加微信 APK，打开，等欢迎页（ANR 弹窗点“等待”）。
3. 点“登录”，**停留观察 10 秒以上**（上轮是点完 0.46s 崩，这次看它还崩不崩、登录页能不能稳定停住）。
4. 抓 logcat：`adb logcat MultiOpen:I ActivityTaskManager:I AndroidRuntime:E libc:F DEBUG:F *:S`
   （加了 `libc:F DEBUG:F`，万一还 SIGSEGV，尽量抓 native backtrace / tombstone 摘要。）

## 本轮重点看
1. 点“登录”后**还崩不崩**（`signal 11` / `am_proc_died` / `NativeCrash`）？
2. `getInstallerPackageName` 的 `Unknown package` / `AppsFilter ... BLOCKED` 还出现吗？
3. 登录页（MobileInputUI，手机号输入页）**能不能稳定显示、停住**？能看到就描述界面、截图。
4. 若还 SIGSEGV：尽量贴 `DEBUG`/`libc` 的 native backtrace（哪怕几行），或 `/data/tombstones` 最新文件的头部（如果能读）。
5. 若登录页稳住了，可再点页面上的元素（比如切到“扫码登录”），看 singleTask/singleInstance 桩会不会用到、有没有新崩溃。

## 写回 debug/last-run.md（覆盖），然后
```
git add debug/last-run.md && git commit -m "test run: QUERY_ALL_PACKAGES + installer hook" && git push
```
