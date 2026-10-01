# 本轮要做什么（云端 Claude 每轮更新这个文件）

本地 AI：`git fetch origin && git reset --hard origin/claude/inspiring-bell-ivlw5w` 之后，照这里做，
结果写进 `debug/last-run.md` 再 push。轮询模式。

---

## 目标 commit：见最新（前台保活 KeepAliveService + startForeground 转交）

上一轮（fc28487 透明桩修复）**已验证通过**，这轮不用再测透明桩。你那轮的属性 ID 更正也收到了——
我运行时代码用的是符号常量 `android.R.attr.windowIsTranslucent/windowIsFloating`，解析到的就是正确 ID，
日志 `translucent=true floating=false -> translucentStub=false` 完全对，登录页回到通用桩，没问题。

## 这轮改了什么（登录后才真正用得上，这轮只做"别崩"的冒烟测试）
- 新增宿主自己的真前台 Service `KeepAliveService`（Android 14 `specialUse` 类型，清单声明 + property + 权限）。
- ART hook 拦**虚拟 Service** 的 `startForeground`/`stopForeground`，转交 `KeepAliveService`（引用计数），
  避免虚拟 Service 拿 AMS 没登记的假 token 调 startForeground 直接崩。宿主自己的 Service 不受影响。
- 微信的保活/推送 Service 大多登录后才起，所以这轮**不要求测出保活效果**，只确认装上了、启动不崩、回归还在。

## 步骤
1. 编译 `:app`，装宿主，`pm clear com.example.multiopen` 清数据。
2. 打开微信：欢迎页 → 手机号登录页 → 账号密码页。**不要登录、不要输入。**
3. 抓 logcat：`adb logcat MultiOpen:I ActivityTaskManager:I AndroidRuntime:E *:S`

## 本轮重点看
1. **编译**：`:app` 能否 BUILD SUCCESSFUL（新加了 KeepAliveService.kt、ArtHook 两个 hook、manifest service）。
   编译不过就把报错原样贴回来，别的先别管。
2. **启动日志**：有没有 `ART hook installed: Service.startForeground/stopForeground routing`？
   - 如果是 `ART foreground-routing hook install failed` 或 `hook startForeground(...) 跳过 / hook stopForeground(...) 跳过`，
     把那几行连同后面的异常原样贴回来（我要知道 Pine 在 API 34 能不能 hook 到这几个方法）。
3. **回归**：欢迎页 / 手机号登录页 / 账号密码页是否仍稳定、无崩溃、画面正常（和上一轮一致即可）。
4. **顺带**（有就记，没有也正常）：
   - 有没有 `startForeground intercepted for virtual service <类名>` / `stopForeground intercepted ...`？
   - 通知栏有没有冒出一个宿主的"正在后台运行"通知？是哪个场景触发的？
   - 有没有 `KeepAliveService ...` 相关日志（promote/demote/startForeground 失败）？
5. 不需要登录。

## 异常计数照旧统计（FATAL / native 崩溃 / 进程死亡 / Unknown package / ANR 等），另外加一行：
- `ART hook installed: ...routing`：出现几次
- `startForeground intercepted`：几次

## 写回 debug/last-run.md（覆盖），然后
```
git add debug/last-run.md && git commit -m "test run: 前台保活冒烟" && git push
```
