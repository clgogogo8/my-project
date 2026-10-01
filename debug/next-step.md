# 本轮要做什么（云端 Claude 每轮更新这个文件）

本地 AI：`git fetch origin && git reset --hard origin/claude/inspiring-bell-ivlw5w` 之后，照这里做，
结果写进 `debug/last-run.md` 再 push。（完整规则见 `debug/README.md`。）

---

## 目标 commit：见最新（Watchdog 增强：加后台线程栈）

## 背景（供你理解）
- 主线程卡在 `ForkJoinTask.get()`（微信 `ph5.n0` 启动框架），等一个任务完成；进程里似乎没有 ForkJoin worker，
  所有线程睡眠。需要看那个任务在哪个线程、在等什么。
- 本轮不改行为，只增强诊断：watchdog 第 2 次采样时，额外打印所有“涉及 com.tencent / ForkJoin / ph5 / gp0 /
  yu5 / xu5”的后台线程栈（日志行形如 `bg thread '名字' (状态): ...`）。

## 步骤
1. 编译 `:app`，装宿主，`pm clear com.example.multiopen` 清数据。
2. 重新添加微信 APK，打开，**观察满 20 秒以上**（让 watchdog 跑到第 2、3 次）。别点 ANR 的“确定”。
3. 抓 logcat（只 MultiOpen 标签）。

## 本轮重点看（最关键）
- 所有 `bg thread '...'` 的完整栈 —— **这是本轮核心产物，请完整贴回**（尤其栈里含 ForkJoin、ph5.n0、
  gp0、com.tencent.* 的线程）。
- 有没有名字像 `ForkJoinPool-*-worker-*` 或 `ForkJoinPool.commonPool-worker-*` 的线程？它们的状态和栈是什么？
- 那些 `[GT]ColdPool#N` / `[GT]HotPool#N` / `wc_srvinit_N` 线程里，有没有哪个栈顶也是在 `wait/park/await`
  等另一个东西（等待链），或者卡在某个 `com.tencent.*` 调用上？把这类线程重点标出来。
- 主线程 #1/#2/#3 栈是否仍与上一轮相同（确认仍卡同一处）。

## 写回 debug/last-run.md（覆盖），然后
```
git add debug/last-run.md && git commit -m "test run: 后台线程栈" && git push
```
