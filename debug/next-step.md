# 本轮要做什么（云端 Claude 每轮更新这个文件）

本地 AI：`git fetch origin && git reset --hard origin/claude/inspiring-bell-ivlw5w` 之后，照这里做，
结果写进 `debug/last-run.md` 再 push。（完整规则见 `debug/README.md`。）

---

## 目标 commit：见最新（外部存储重定向 + Manifest 收集 launchMode）

## 改了什么
- 上一轮微信欢迎页已显示。本轮补“没做的”里最安全的一项：
  - VirtualContext 重定向 app 私有外部存储（getExternalFilesDir/CacheDir/MediaDirs/ObbDir）到实例目录。
  - ManifestParser 顺带收集每个 Activity 的 launchMode（纯数据，为下一轮桩池进阶铺垫，不改行为）。
- **本轮的首要目的是回归验证：确认这些改动没有把已经跑通的微信欢迎页弄坏。**

## 步骤
1. 编译 `:app`，装宿主，`pm clear com.example.multiopen` 清数据。
2. 重新添加微信 APK，打开，等到欢迎页出来（出现系统 ANR 弹窗就点“等待”）。
3. **额外做一次冷启动验证**：欢迎页出来后，退出微信实例（回到 MultiOpen 列表或按返回），
   **不要 pm clear**，再从 MultiOpen 第二次点开这个微信实例，计时到欢迎页出现。
4. 抓 logcat：`adb logcat MultiOpen:I ActivityTaskManager:I AndroidRuntime:E *:S`

## 本轮重点看
1. **回归**：微信欢迎页是否还能正常显示（登录/注册/语言按钮都在）？有没有新的 FATAL / 新崩溃？
2. **第二次打开（不清数据）**：启动到欢迎页花多久？`Displayed ... +?ms`。还弹不弹 ANR？
   （用来判断上轮那个 ~8s ANR 是不是只有首次冷启动才有。）
3. 外部存储：有没有和存储相关的新报错（权限、路径 not found 之类）。

## 写回 debug/last-run.md（覆盖），然后
```
git add debug/last-run.md && git commit -m "test run: 外部存储+launchMode；回归与二次启动" && git push
```
