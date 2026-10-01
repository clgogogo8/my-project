# 本轮要做什么（云端 Claude 每轮更新这个文件）

本地 AI：`git fetch origin && git reset --hard origin/claude/inspiring-bell-ivlw5w` 之后，照这里做，
结果写进 `debug/last-run.md` 再 push。

---

## 本轮：确认启动 ANR 的性质（无代码改动，纯验证）

content:// 代码已就位、回归通过（实际效果要登录后验证）。本轮确认冷启动那 ~8 秒 ANR 是不是只有首次。

## 步骤
1. 用最新代码装好宿主（若已是最新可不重装）。
2. **第一次打开微信**（必要时先 `pm clear` 一次制造冷启动），记录 `Displayed ...StubActivity* +?ms` 和是否弹 ANR。
3. **退出微信实例**（回 MultiOpen 列表 / 按返回），**不要 pm clear**，**第二次打开同一个微信实例**，
   再记录 `Displayed +?ms` 和是否弹 ANR。
4. 如果能，第三次再打开一次，记录时间。
5. 抓 logcat：`adb logcat MultiOpen:I ActivityTaskManager:I *:S`

## 本轮重点看
1. 第一次 vs 第二次 vs 第三次打开，各自到 `Displayed` 的耗时（毫秒）。是否第二次起明显变快、ANR 不再出现？
2. 把这三段的时间戳拆一下：`IActivityManager hooked` → `resources built` → `virtual Application created`
   → `WelcomeActivity 的 Displayed`，看 8 秒主要花在哪一段（dex 加载 / 微信 Application.onCreate / 资源）。
3. 其余回归：有没有新崩溃（应该没有）。
4. 不需要登录、不需要输入任何东西。

## 写回 debug/last-run.md（覆盖），然后
```
git add debug/last-run.md && git commit -m "test run: 启动 ANR 二次启动验证" && git push
```
