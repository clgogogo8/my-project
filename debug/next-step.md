# 本轮要做什么（云端 Claude 每轮更新这个文件）

本地 AI：`git fetch origin && git reset --hard origin/claude/inspiring-bell-ivlw5w` 之后，照这里做，
结果写进 `debug/last-run.md` 再 push。（完整规则见 `debug/README.md`。）

---

## 目标 commit：见最新（含“进程名伪装”的改动）

## 改了什么（供你理解，不用改源码）
- 上一轮确认 provider 顺序已对，但 mCoreAccount 仍崩，说明不是 provider 时序问题。
- 本轮：伪装进程名。微信按进程名决定初始化哪些 Kernel，主进程名==包名；不伪装时进程名是宿主
  com.example.multiopen，微信判定非主进程、跳过账号 Kernel 初始化 → mCoreAccount not initialized。
  现在在微信代码跑之前把进程名改成 com.tencent.mm。

## 步骤
1. 编译 `:app`，装宿主，`pm clear com.example.multiopen` 清数据。
2. 重新添加微信 APK，打开，用 adb 观察约 20 秒。
3. 抓 logcat（只 MultiOpen 标签 + 崩溃栈）。

## 本轮重点看
- 是否出现日志：`fake process name -> com.tencent.mm`
- `mCoreAccount not initialized` 还在不在、`plugin Application.onCreate failed` 还有没有。
- `FATAL EXCEPTION` 次数、是否仍白屏、是否还反复重启。
- **若 mCoreAccount 崩消失但换了新崩溃**：把新的第一个异常/`Caused by:` 完整贴出来。
- 微信若显示出任何界面（闪屏/隐私弹窗/登录页），务必说明 —— 重大进展。

## 写回 debug/last-run.md（覆盖），然后
```
git add debug/last-run.md && git commit -m "test run: 进程名伪装" && git push
```
