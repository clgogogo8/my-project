# 本轮要做什么（云端 Claude 每轮更新这个文件）

本地 AI：`git fetch origin && git reset --hard origin/claude/inspiring-bell-ivlw5w` 之后，照这里做，
结果写进 `debug/last-run.md` 再 push。（完整规则见 `debug/README.md`。）

---

## 目标 commit：见最新（两条诊断日志）

## 背景 / 本轮目的（纯诊断，不改行为）
- ServiceManager 包装生效了，但登录页仍 SIGTRAP@libcronet，`getInstallerPackageName("com.tencent.mm")` 仍打到真实系统。
- 要确认二选一：① 微信根本没走我们包装的 binder（直连真实 binder）；② 走了但 asInterface 没采用我们的接口。
- 加了两条日志：
  - `wrapped package binder: queryLocalInterface -> ourIpm`（微信经过我们包装 binder 时打）
  - `intercept getInstallerPackageName(...)`（我们的拦截被调到时打）

## 步骤
1. 编译 `:app`，装宿主，`pm clear com.example.multiopen` 清数据。
2. 添加微信 APK，打开，等欢迎页；点“登录”，等它崩/重启。
3. 抓 logcat：`adb logcat MultiOpen:I AndroidRuntime:E *:S`

## 本轮重点看（就看这两条日志在不在，这是关键）
1. 整个过程里有没有出现 `wrapped package binder: queryLocalInterface -> ourIpm`？出现几次？
2. 点“登录”崩溃前后，有没有出现 `intercept getInstallerPackageName(com.tencent.mm)`？
3. `Unknown package: com.tencent.mm` 还在不在（应该还在，除非 2 出现了）。
4. 这两条的**有/无组合**最重要，请明确写出来：
   - 都没有 → 微信直连真实 binder，绕过了 ServiceManager 缓存。
   - 有 queryLocalInterface、没有 intercept → asInterface 没采用我们的接口。
   - 两条都有但还崩 → 拦截被调到了但没挡住。
5. 其余照常（崩不崩、native 摘要可略，和上轮同位置就只说“同上”）。

## 写回 debug/last-run.md（覆盖），然后
```
git add debug/last-run.md && git commit -m "test run: PMS 拦截诊断日志" && git push
```
