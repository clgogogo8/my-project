# 本轮要做什么（云端 Claude 每轮更新这个文件）

本地 AI：`git fetch origin && git reset --hard origin/claude/inspiring-bell-ivlw5w` 之后，照这里做，
结果写进 `debug/last-run.md` 再 push。（完整规则见 `debug/README.md`。）

---

## 目标 commit：见最新（桩池 Stub pool）

## 改了什么（实质修复）
- 系统侧确认：启动循环是 SINGLE_TOP + 单桩 → `START_DELIVERED_TO_TOP`（result code=3），目标桩==栈顶桩，不新建。
- 本轮：把单个 StubActivity 改成 8 个桩的池（StubActivity + StubActivity1..7，已在 manifest 声明），
  启动虚拟 Activity 时 round-robin 轮换分配桩，使“目标桩 ≠ 栈顶桩”，系统就会新建实例、触发 newActivity。

## 步骤
1. 编译 `:app`，装宿主，`pm clear com.example.multiopen` 清数据。
2. 重新添加微信 APK，打开，观察约 20~30 秒。
3. 抓 logcat：`adb logcat MultiOpen:I ActivityTaskManager:I AndroidRuntime:E *:S`

## 本轮重点看（最关键）
- **循环有没有停**：`rewriteIntent: ...WelcomeActivity -> stub` 还是不是几百次？理想情况是只有几次。
- **WelcomeActivity 有没有被真正创建**：是否出现 `newActivity: stub -> com.tencent.mm.plugin.account.ui.WelcomeActivity`
  （上几轮只有 LauncherUI 一个 newActivity）。
- **ActivityTaskManager 的 result code**：启动 StubActivity 的行，code 还是不是一直 3？理想是 0（新建）。
- **微信有没有显示出界面**：闪屏/欢迎页/登录（手机号登录 or 扫码登录）/隐私弹窗 —— 有就详细描述、能截图最好。
- 若换了新崩溃或新的卡点，把第一个 `FATAL`/`Caused by:` 或新的主线程栈贴回。

## 写回 debug/last-run.md（覆盖），然后
```
git add debug/last-run.md && git commit -m "test run: 桩池" && git push
```
