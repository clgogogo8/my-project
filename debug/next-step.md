# 本轮要做什么（云端 Claude 每轮更新这个文件）

本地 AI：`git fetch origin && git reset --hard origin/claude/inspiring-bell-ivlw5w` 之后，照这里做，
结果写进 `debug/last-run.md` 再 push。轮询模式。

---

## 这轮是「登录卡死诊断轮」——不改代码，只抓现场

用户本人手动登录时，点"登录"**一直转圈**（没有错误提示，就是无限 loading）。
转圈而不报错 = 登录请求发出后在等一个永不返回的响应，最可能是微信 UI 进程经 IPCInvoker 把登录请求
发给 `:push` 网络进程、等它 IPC 回传，而我们单进程模拟里这一跳没接通。**要坐实，必须抓转圈那刻的线程栈。**

### 红线不变
- 账号/密码仍由**用户本人**输入，本地 AI 不代输、不点登录。本地 AI 只负责抓日志和线程栈。

## 步骤
1. 编译 `:app`、装宿主、`pm clear com.example.multiopen` 清数据。
2. `adb logcat -c` 清旧日志，然后开始全量抓：
   `adb logcat -v threadtime > /sdcard/login_full.log &`
   （**不要过滤**，我要看 MicroMsg / Mars / STN / IPCInvoker / Cronet / AndroidRuntime / DEBUG 全部。）
3. 打开微信到登录页，**提示用户本人输账号密码并点登录**。
4. **在转圈期间**（点了登录、还在转、大约等 10~20 秒后），抓 `com.example.multiopen` 的 Java 线程栈：
   - 先拿 pid：`adb shell pidof com.example.multiopen`
   - 触发 dump：`adb shell kill -3 <pid>`（SIGQUIT，让 ART 写 ANR trace）
   - 取 trace：`adb shell "cat /data/anr/traces.txt"`（若无权限，试 `adb root` 后再 cat；小米可能要在开发者选项里开 root 调试）
   - 取不到 traces.txt 就退而求其次：`adb shell debuggerd -j <pid>`（能打 Java 栈）或 `debuggerd -b <pid>`（native 栈），把输出贴回来。
5. 停日志。

## 回传时请给我这几样（照实贴，太长就截相关段）
1. **线程栈里的关键线程**：找名字含 `main` 以及含 `IPCInvoker` / `MM` / `push` / `Mars` / `STN` / `HandlerThread`
   的线程，把它们的栈贴回来——我要看**哪个线程卡在哪个方法**（例如卡在 `CountDownLatch.await` / `Binder.transactNative`
   / `Object.wait` / `LinkedBlockingQueue.take` / 某个 `*.ipcInvoke` / `*.invoke`）。这是本轮最重要的东西。
2. **登录那一刻起的关键 logcat**（从 login_full.log 里 grep，各贴几十行）：
   - `MicroMsg`（微信自己的日志 tag，登录/network/autoauth/accountmgr 相关）
   - `Mars` / `mars` / `STN` / `stn` / `longlink` / `shortlink`（微信网络库）
   - `IPCInvoker` / `ipc`
   - `Cronet` / `cronet`
   - `MultiOpen`（我们的）：有没有 `bindService` / `virtual Service created` 在登录时触发？是哪个 Service？
   - 任何 `AndroidRuntime` / `DEBUG` / `tombstone` / `FATAL`
3. **现象确认**：是否真·一直转圈（>60 秒不返回）？期间有没有弹过任何提示？进程有没有死（pid 有没有变）？
4. **登录时新建了哪些 virtual Service / 绑定**：把登录期间 `MultiOpen` 里 `virtual Service created` / `bindService` 的行都贴回来。

## 写回 debug/last-run.md（覆盖），然后
```
git add debug/last-run.md && git commit -m "test run: 登录卡死诊断（线程栈+日志）" && git push
```
