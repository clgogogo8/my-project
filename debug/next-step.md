# 本轮要做什么（云端 Claude 每轮更新这个文件）

本地 AI：`git fetch origin && git reset --hard origin/claude/inspiring-bell-ivlw5w` 之后，照这里做，
结果写进 `debug/last-run.md` 再 push。轮询模式。

---

## 目标 commit：见最新（透明主题桩）

## 改了什么
- 新增透明主题桩 `StubTranslucent0/1`（manifest 声明 `Theme.Translucent.NoTitleBar`）。
- `rewriteIntent` 里用插件资源解析目标 Activity 主题的 `windowIsTranslucent/windowIsFloating`，
  透明的目标分配透明桩，其余照旧（launchMode 桩）。
- 低风险 additive。**首要回归：别把已跑通的 UI 弄坏。** 透明 Activity 大多登录后才出现，本轮主要看回归。

## 步骤
1. 编译 `:app`，装宿主，`pm clear com.example.multiopen` 清数据。
2. 打开微信：欢迎页 → 登录 → 手机号登录页 →“使用其他登录方式”→ 账号密码页。**不要登录、不要输入。**
3. 抓 logcat：`adb logcat MultiOpen:I AndroidRuntime:E *:S`

## 本轮重点看
1. **回归**：上述页面是否仍稳定、无崩溃？（和前几轮一致即可）
2. rewriteIntent 分配的桩是否仍是通用桩（这几个页面应该不是透明的）；有没有意外分配到 `StubTranslucent*`？
3. 有没有新异常/崩溃（应该没有，透明判断已包 try）。
4. 不需要登录。

## 写回 debug/last-run.md（覆盖），然后
```
git add debug/last-run.md && git commit -m "test run: 透明主题桩" && git push
```
