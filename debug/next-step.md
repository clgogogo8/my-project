# 本轮要做什么（云端 Claude 每轮更新这个文件）

本地 AI：`git fetch origin && git reset --hard origin/claude/inspiring-bell-ivlw5w` 之后，照这里做，
结果写进 `debug/last-run.md` 再 push。（完整规则见 `debug/README.md`。）

---

## 目标 commit：见最新（含“provider 先于 Application.onCreate”的改动）

## 改了什么（供你理解，不用改源码）
- 上一轮资源问题已解决（Resources$NotFound 归零）。
- 本轮修组件初始化顺序：把 ContentProvider 的 onCreate 移到 Application.onCreate **之前**，对齐 Android
  真实顺序（attach → provider → onCreate）。微信核心账号 Kernel（mCoreAccount）在 provider 里初始化，
  之前顺序反了导致 `b96.b: mCoreAccount not initialized!`。

## 步骤
1. 编译 `:app`，装宿主，`pm clear com.example.multiopen` 清数据。
2. 重新添加微信 APK，打开，用 adb 观察约 20 秒。
3. 抓 logcat（只 MultiOpen 标签 + 崩溃栈）。

## 本轮重点看
- `mCoreAccount not initialized` 还在不在、`plugin Application.onCreate failed` 还有没有。
- `FATAL EXCEPTION` 次数、是否仍白屏、是否还反复重启。
- `provider installed` 这批日志现在是否出现在 `virtual Application created` 之前（顺序对了的标志）。
- **若 mCoreAccount 崩消失但换了新崩溃**：把新的第一个异常/`Caused by:` 完整贴出来。
- 如果微信终于显示出任何界面（哪怕启动页/闪屏/隐私弹窗），一定说明——这是重大进展。

## 写回 debug/last-run.md（覆盖），然后
```
git add debug/last-run.md && git commit -m "test run: provider 先于 onCreate" && git push
```
