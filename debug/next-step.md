# 本轮要做什么（云端 Claude 每轮更新这个文件）

本地 AI：`git fetch origin && git reset --hard origin/claude/inspiring-bell-ivlw5w` 之后，照这里做，
结果写进 `debug/last-run.md` 再 push。（完整规则见 `debug/README.md`。）

---

## 目标 commit：见最新（rewriteIntent 加 flags 日志）

## 背景（重大进展）
- 死锁解了，微信 Application.onCreate 返回、LauncherUI 起来了。现在新问题：LauncherUI 反复 startActivity
  打开 WelcomeActivity（登录欢迎页），`rewriteIntent: WelcomeActivity -> stub` 出现 735 次，但 WelcomeActivity
  从没被真正创建（`newActivity` 只有 1 次）→ 空转 ANR。
- 怀疑是“单桩”局限：所有虚拟 Activity 共用一个 StubActivity，系统按 launchMode/任务栈判定“目标已在栈里”而复用，
  不新建。需要系统侧证据确认是哪种复用，再决定修法（大概率要做“桩池”）。
- 本轮不改行为，只给 rewriteIntent 的日志加了 Intent flags。

## 步骤
1. 编译 `:app`，装宿主，`pm clear com.example.multiopen` 清数据。
2. 重新添加微信 APK，打开，观察约 20 秒（够看到循环即可）。
3. 抓 logcat，这次**同时抓系统 ActivityTaskManager 的决策**（不只 MultiOpen）：
   ```
   adb logcat -c
   adb logcat MultiOpen:I ActivityTaskManager:I ActivityManager:I WindowManager:W AndroidRuntime:E *:S
   ```

## 本轮重点看（最关键）
1. `rewriteIntent: ...WelcomeActivity -> stub (flags=0x????)` —— 这个 **flags 值**贴回来（十六进制）。
2. `ActivityTaskManager` 里关于启动 StubActivity 的行：找 `START u0 {... cmp=com.example.multiopen/.StubActivity ...}`，
   以及紧跟的处理动词，特别是有没有 `deliverNewIntent` / `onResume` 复用 / `SINGLE_TOP` / `TaskRecord` 复用 /
   `Warning: Activity not started` 之类。把这些行贴回（几条代表性的即可，不用 735 条）。
3. 用 aapt2 查 WelcomeActivity 和 LauncherUI 的 launchMode：
   ```
   aapt2 dump xmltree base.apk --file AndroidManifest.xml > /tmp/mm_manifest.txt
   grep -nA12 "WelcomeActivity" /tmp/mm_manifest.txt | grep -iE "name|launchMode|taskAffinity"
   grep -nA12 "ui.LauncherUI" /tmp/mm_manifest.txt | grep -iE "name|launchMode|taskAffinity"
   ```
   把这两个 Activity 的 launchMode / taskAffinity 贴回（launchMode 数值：0=standard 1=singleTop 2=singleTask 3=singleInstance）。
4. 其它照常：有没有新崩溃、有没有界面。

## 写回 debug/last-run.md（覆盖），然后
```
git add debug/last-run.md && git commit -m "test run: WelcomeActivity 启动循环诊断" && git push
```
