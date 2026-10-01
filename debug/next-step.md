# 本轮要做什么（云端 Claude 每轮更新这个文件）

本地 AI：`git fetch origin && git reset --hard origin/claude/inspiring-bell-ivlw5w` 之后，照这里做，
结果写进 `debug/last-run.md` 再 push。恢复轮询模式。

---

## 目标 commit：见最新（Activity 方向锁定 screenOrientation）

## 改了什么
- ManifestParser 收集每个 Activity 的 `screenOrientation`；HookInstrumentation.patch 里按它调
  `setRequestedOrientation`，给桩承载的真实 Activity 补上方向属性（桩自己没声明方向）。
- 低风险 additive，**首要是回归：别把已跑通的 UI（欢迎页→登录页→账号密码页）弄坏。**

## 步骤
1. 编译 `:app`，装宿主，`pm clear com.example.multiopen` 清数据。
2. 打开微信，走：欢迎页 → 点“登录”→ 手机号登录页 → “使用其他登录方式”→ “用微信号/QQ号/邮箱登录”→ 账号密码页。
   **不要输入账号/密码、不要点“同意并登录”。**（登录留到最后、用户本人做。）
3. 抓 logcat：`adb logcat MultiOpen:I AndroidRuntime:E *:S`

## 本轮重点看
1. **回归**：上述页面是否仍稳定显示、无崩溃？（和前几轮一致即可）
2. 这些页面的方向是否正常（竖屏，没有被转成横屏或显示异常）？
3. 有没有因为 `setRequestedOrientation` 出现任何异常/崩溃（应该没有，已包 try）。
4. 不需要登录、不需要输入。

## 写回 debug/last-run.md（覆盖），然后
```
git add debug/last-run.md && git commit -m "test run: screenOrientation 方向锁定" && git push
```
