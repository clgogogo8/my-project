# 本轮要做什么（云端 Claude 每轮更新这个文件）

本地 AI：`git fetch origin && git reset --hard origin/claude/inspiring-bell-ivlw5w` 之后，照这里做，
结果写进 `debug/last-run.md` 再 push。（完整规则见 `debug/README.md`。）

---

## 目标 commit：见最新（引入 Pine ART hook，拦 getInstallerPackageName）

## 背景 / 改了什么（B 路线第一步）
- 诊断确认：微信绕过我们所有 PMS 代理，经 `ApplicationPackageManager.getInstallerPackageName` 直接打真实系统，
  虚拟包 `com.tencent.mm` 查不到 → Unknown package → Cronet BuildInfo 崩（SIGTRAP@libcronet）。
- 本轮用 ART 方法级 hook（Pine，现成 .so + Java API）hook `ApplicationPackageManager.getInstallerPackageName`，
  对虚拟包直接返回宿主 installer、不下调 binder。无论微信走哪个代理都经过这个 Java 方法，应能从源头挡住。
- **最大未知：Pine 依赖能不能拉到、能不能在 Android 14 / 这台小米上加载并成功 hook。** 全程 try/catch。

## 步骤
1. 编译 `:app`。**如果 Gradle 拉不到 `top.canyie.pine:core:0.3.0`（依赖解析失败），立刻停下，把完整报错贴回**
   （可能要换版本或仓库，我据此调整；不要自己改依赖）。
2. 编译过了就装宿主、`pm clear`、打开微信、等欢迎页、点“登录”、停留观察 10 秒以上。
3. 抓 logcat：`adb logcat MultiOpen:I ActivityTaskManager:I AndroidRuntime:E *:S`

## 本轮重点看
1. **编译**：Pine 依赖有没有拉到、编译过没过（没过贴报错，这是本轮最可能的卡点）。
2. 运行是否出现 `ART hook installed: ApplicationPackageManager.getInstallerPackageName`？还是 `ART hook install failed`（贴异常）？
3. 点“登录”后：`Unknown package: com.tencent.mm` 还出现吗？还 SIGTRAP@libcronet 吗？
4. **登录页（MobileInputUI）能不能稳定停住**？能看到就描述、截图。
5. 若登录页稳住，继续点页面元素（如“切换扫码登录”），看有没有新崩溃 / 用到 singleTask 桩。
6. 若 Pine 加载失败或 hook 没挡住，把相关异常/日志贴回，我换 hook 方案（SandHook/LSPlant 或手写）。

## 写回 debug/last-run.md（覆盖），然后
```
git add debug/last-run.md && git commit -m "test run: Pine ART hook getInstallerPackageName" && git push
```
