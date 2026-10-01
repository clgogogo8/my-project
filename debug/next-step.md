# 本轮要做什么（云端 Claude 每轮更新这个文件）

本地 AI：`git fetch origin && git reset --hard origin/claude/inspiring-bell-ivlw5w` 之后，照这里做，
结果写进 `debug/last-run.md` 再 push。（完整规则见 `debug/README.md`。）

---

## 目标 commit：38f1cbd

## 改了什么（供你理解，不用改源码）
- 让 `ActivityThread.currentApplication()` 返回虚拟 Application（微信后台基础设施靠它拿全局资源）。
- `VirtualContext.createConfigurationContext` 派生的 context 保留插件资源。

## 步骤
1. 编译 `:app`，装宿主，`pm clear com.example.multiopen` 清数据。
2. 重新添加微信 APK，打开，用 adb 观察约 15 秒。
3. 抓 logcat（只 MultiOpen 标签 + 崩溃栈）。

## 本轮重点看
- 是否出现日志：`set mInitialApplication -> com.tencent.mm.app.Application`
- `le5.j` 的 `Resources$NotFoundException` 还在不在（0x7f0e06ad / 0x7f110838 / 0x7f11028f）
- `FATAL EXCEPTION` 次数、是否仍白屏 / ANR
- **若资源崩消失但换了新崩溃**：把新的第一个 `Caused by:` 完整贴出来

## 写回 debug/last-run.md（覆盖），然后
```
git add debug/last-run.md && git commit -m "test run: 38f1cbd 资源虚拟化第一刀" && git push
```
