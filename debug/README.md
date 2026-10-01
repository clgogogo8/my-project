# 调试协作协议（云端 Claude ⇄ 本地 AI）

云端的 Claude 改代码、本地 AI 在带 Android SDK 的机器 + 真机上验证。两边通过本仓库异步协作，
不用人工搬运大段日志。本地 AI 请按下面做。

## 每一轮的循环

1. **拉最新代码**（分支 `claude/inspiring-bell-ivlw5w`）：
   ```
   git fetch origin && git reset --hard origin/claude/inspiring-bell-ivlw5w
   ```
   记下当前 commit 号（`git rev-parse --short HEAD`）。
2. **读 `debug/next-step.md`**：云端 Claude 每轮在那里写明本轮要测什么、重点看什么。按它做（编译 + 装宿主 + 清数据 + 真机复现，细节见 `docs/REAL_DEVICE_TEST.md`）。
3. **把结果写进 `debug/last-run.md`**（覆盖整个文件，用下面的模板），然后：
   ```
   git add debug/last-run.md && git commit -m "test run: <commit号> <一句话结果>" && git push
   ```
4. 云端 Claude 会 `git fetch` 读 `debug/last-run.md`，分析后把修复 push 回同分支。回到第 1 步。

## 写入规则（重要）

- **只提交 `debug/last-run.md` 这一个文件。** 不要提交 apk、完整 logcat、临时 `.log`、`build/` 产物。
- **logcat 只放 `MultiOpen` 标签的行和崩溃栈（FATAL / Caused by）。** 去掉 Android 框架噪音帧。
- **绝不写入任何隐私**：手机号、账号、token、二维码、聊天内容一律不要进文件。有疑问就删。
- 崩溃栈保留类名和关键调用链即可，长行可截断并标注。

## `debug/last-run.md` 模板

```markdown
# 测试结果

- commit: <短 hash>
- 编译: 成功 / 失败（失败贴完整报错）
- 设备: 小米 M2011K2C / Android 14
- 被测 APK: test-multi / 微信 8.0.78 等
- 现象: 一句话（白屏 / 进登录页 / ANR / 崩溃重启 N 次…）

## 关键 logcat（只含 MultiOpen 标签 + 崩溃栈）
<粘贴这里>

## 本轮云端 Claude 要求的额外诊断
<例如某条自检日志、aapt 输出，粘贴这里>

## 异常计数
Resources$NotFoundException: N 次
其它关键词: ...
```

## 本地 AI 的纪律

- 云端没让改源码时，**不要自己改 app 源码**去绕过问题；发现根因就写进 `last-run.md` 的分析里，由云端统一改。
- 编译报错先停，原样贴回，不要降 Android 版本或删代码硬过。
