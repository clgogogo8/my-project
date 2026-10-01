# 本轮要做什么（云端 Claude 每轮更新这个文件）

本地 AI：`git fetch origin && git reset --hard origin/claude/inspiring-bell-ivlw5w` 之后，照这里做，
结果写进 `debug/last-run.md` 再 push。（完整规则见 `debug/README.md`。）

---

## 目标 commit：见最新（ServiceManagerHook 包装 package binder）

## 背景 / 改了什么
- 上一轮确认：登录页 SIGTRAP@libcronet，根因是微信自己从 ServiceManager 另拿 IPackageManager binder（绕过我们的
  sPackageManager hook），`getInstallerPackageName("com.tencent.mm")` 打到真实系统 → Unknown package → Cronet 崩。
- 本轮：新增 `ServiceManagerHook`，把 ServiceManager 缓存里的 "package" binder 换成代理，让 asInterface 拿到
  我们的 IPackageManager 拦截层。这样微信那层代理也会走我们的拦截，虚拟包查询不再打真实系统。
- **这是较底层的 binder 包装，首要是回归：确认没把欢迎页弄坏。**

## 步骤
1. 编译 `:app`，装宿主，`pm clear com.example.multiopen` 清数据。
2. 添加微信 APK，打开，等欢迎页（有 ANR 弹窗点“等待”）。
3. 点“登录”，**停留观察 10 秒以上**，看还崩不崩、登录页能不能停住。
4. 抓 logcat：`adb logcat MultiOpen:I ActivityTaskManager:I AndroidRuntime:E *:S`

## 本轮重点看
1. **回归**：是否出现 `ServiceManager package binder wrapped` 日志？欢迎页是否仍正常显示？有没有新崩溃/黑屏？
2. 点“登录”后：`Unknown package: com.tencent.mm` 的 `ExceptionInInitializerError` 还出现吗？
3. 还崩不崩（`signal 11` / `SIGTRAP` / `NativeCrash` / `am_proc_died`）？
4. **登录页（MobileInputUI，手机号输入页）能不能稳定停住**？能看到就描述界面、截图。
5. 若登录页稳住了：可再点“切换到扫码登录”之类，看会不会用到 singleTask/singleInstance 桩、有没有新崩溃。
6. 若还崩：贴新的 native 崩溃摘要（线程名、so、偏移）和崩溃前最后的 MultiOpen / System.err 行。

## 写回 debug/last-run.md（覆盖），然后
```
git add debug/last-run.md && git commit -m "test run: ServiceManager binder 包装" && git push
```
