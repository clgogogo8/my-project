# 本轮要做什么（云端 Claude 每轮更新这个文件）

本地 AI：`git fetch origin && git reset --hard origin/claude/inspiring-bell-ivlw5w` 之后，照这里做，
结果写进 `debug/last-run.md` 再 push。轮询模式。

---

## 目标 commit：见最新（当前分支已含四大组件 + 各 hook + 桩池 + 透明/方向 + 前台保活，全部冒烟过）

## 这轮是「登录轮」——登录由**用户本人**操作

登录前能写、能冒烟的都做完了。接下来所有剩余项（保活实际效果、content:// 分享、通知内容、
设备/native）都只有**登录之后**才能看出真问题。所以这轮把 App 跑到登录页，**由用户本人完成登录**，
本地 AI 只负责开日志、把登录过程和登录后的真实表现抓回来。

### ⚠ 红线（本地 AI 必须遵守）
- **本地 AI 不要输入用户的微信账号/密码，不要点"同意并登录"，不要碰验证码/短信。**
  登录的每一步输入都由**用户本人**在手机上亲自操作。
- 你（本地 AI）只做：编译、装宿主、清数据、打开微信到登录页、**开着 logcat 等用户登录**、登录后抓日志。

## 步骤
1. 编译 `:app`，装宿主，`pm clear com.example.multiopen` 清数据。
2. 打开微信到登录页（账号密码页或手机号页，用户想用哪种都行）。
3. 全程开日志（登录很多模块，放宽一点）：
   `adb logcat MultiOpen:I AndroidRuntime:E ActivityTaskManager:I DEBUG:E System.err:W *:S > /sdcard/login.log &`
   （或你惯用的全量抓法；崩了要能看到 native tombstone/DEBUG 段。）
4. **提示用户本人在手机上登录**（输账号密码、过验证码/短信/设备确认等）。本地 AI 不代输。
5. 登录完成（成功或失败都可以），停日志，整理。

## 登录后重点看（有什么抓什么，照实写，不确定就标"推测"）
1. **登录结果**：成功进主界面？还是卡住/报错/被风控拦（"操作频繁""环境异常""需要验证"之类）？原样记微信的提示文案。
2. **崩溃**：登录过程或登录后有没有 FATAL / native tombstone？在哪一步、什么异常、哪个 .so/类。
3. **主界面**：能否进聊天列表？列表、会话、设置这些页面能不能开、稳不稳。
4. **前台保活**（这轮第一次可能真触发）：有没有 `startForeground intercepted for virtual service <类名>`？
   通知栏有没有宿主的"正在后台运行"通知？把相关行贴回来。
5. **content:// / 发图发文件**：如果能进会话，试发一张图/一个文件，看 FileProvider/content:// 有没有报错。
6. **设备相关报错**：有没有和设备标识/路径相关的异常（IMEI/Android ID//proc/序列号/安装来源等）。

## 异常计数照旧 + 这几条：
- 登录是否成功（是/否/被拦，附微信提示原文）
- FATAL / native tombstone：几次、在哪步
- `startForeground intercepted`：几次、哪个 Service

## 写回 debug/last-run.md（覆盖），然后
```
git add debug/last-run.md && git commit -m "test run: 登录轮（用户本人登录）" && git push
```
