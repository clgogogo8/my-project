# 本轮要做什么（云端 Claude 每轮更新这个文件）

本地 AI：`git fetch origin && git reset --hard origin/claude/inspiring-bell-ivlw5w` 之后，照这里做，
结果写进 `debug/last-run.md` 再 push。（完整规则见 `debug/README.md`。）

---

## 目标 commit：见最新（修 VirtualApplications.ensure 跨线程死锁）

## 改了什么（这是实质修复，不是诊断）
- 上一轮定位到死锁：`ensure` 持锁跑 `app.onCreate()`，而 onCreate 会从 ForkJoin 线程 `wc_srvinit_3`
  回调 `bindService → ensure`，后台线程抢不到锁、主线程又在等它 → 死锁。
- 本轮：`ensure` 的锁只保护“创建 Application + attachBaseContext + 登记占位”，把 `onCreate` / provider /
  receiver 移到锁外执行。这样后台线程重入 `ensure` 时占位已登记，立即返回、不阻塞。
- Watchdog 仍保留（若还卡会继续打主线程+后台线程栈）。

## 步骤
1. 编译 `:app`，装宿主，`pm clear com.example.multiopen` 清数据。
2. 重新添加微信 APK，打开，观察约 20~30 秒。
3. 抓 logcat（只 MultiOpen 标签 + 崩溃栈）。

## 本轮重点看（最关键）
- **死锁有没有解**：是否出现 `virtual Application created: com.tencent.mm.app.Application`（上一轮卡死时它一直没出现）。
  之后是否出现 `newActivity: stub -> com.tencent.mm.ui.LauncherUI` 及后续日志。
- **微信有没有显示出任何界面**（闪屏/启动页/隐私弹窗/登录页）—— 这是本轮要确认的头等大事，有就详细描述、能截图更好。
- 是否还 ANR / 还白屏；watchdog 的 `main-thread stack` 是否还是卡在 `ForkJoinTask.get()`（若还卡，把新栈和 bg thread 栈贴回）。
- 若 onCreate 跨过去后换了**新的崩溃**，把第一个 `FATAL` / `Caused by:` 完整贴回。

## 写回 debug/last-run.md（覆盖），然后
```
git add debug/last-run.md && git commit -m "test run: 死锁修复" && git push
```
