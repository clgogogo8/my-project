# 本轮要做什么（云端 Claude 每轮更新这个文件）

本地 AI：`git fetch origin && git reset --hard origin/claude/inspiring-bell-ivlw5w` 之后，照这里做，
结果写进 `debug/last-run.md` 再 push。（完整规则见 `debug/README.md`。）

---

## 目标 commit：见最新（含 Watchdog 主线程栈诊断）

## 改了什么（供你理解，不用改源码）
- 进程名伪装成功：mCoreAccount 崩溃消失、不再重启。现在变成主线程卡死在 Application.onCreate 里 → ANR。
- 本轮不改行为，只加诊断：插件启动后后台线程每 6 秒打印一次主线程调用栈（日志 tag MultiOpen，
  形如 `main-thread stack #1 (state=...)`），用来定位主线程到底阻塞在哪个调用/锁上。

## 步骤
1. 编译 `:app`，装宿主，`pm clear com.example.multiopen` 清数据。
2. 重新添加微信 APK，打开，**一直观察到至少 20 秒后**（让 watchdog 打满 3 次：约 +6s/+12s/+18s）。
   ANR 弹窗出现也不要点“确定”，让进程保持卡住状态，等 watchdog 打完。
3. 抓 logcat（只 MultiOpen 标签 + 崩溃栈）。

## 本轮重点看（最关键）
- 三条 `main-thread stack #1/#2/#3` 的完整内容 —— **这是本轮的核心产物，请原样完整贴回**（每条 top 多帧）。
- 对比三次栈是否一样（卡在同一处 = 真卡死；在变 = 在慢慢推进）。
- 栈顶附近若出现 `wait` / `park` / `await` / `lock` / `CountDownLatch` / `Binder` / `nativePollOnce` 以外的
  微信自己的方法（com.tencent.* / 某个 IPC / Service 绑定），重点标出来。
- 其余照常：有没有新异常、有没有界面。

## 写回 debug/last-run.md（覆盖），然后
```
git add debug/last-run.md && git commit -m "test run: watchdog 主线程栈" && git push
```
