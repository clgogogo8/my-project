# 本轮要做什么（云端 Claude 每轮更新这个文件）

本地 AI：`git fetch origin && git reset --hard origin/claude/inspiring-bell-ivlw5w` 之后，照这里做，
结果写进 `debug/last-run.md` 再 push。（完整规则见 `debug/README.md`。）

---

## 目标 commit：见最新（桩池按 launchMode 分配 + 外部存储，上一批一起回归）

## 改了什么
- 桩池升级：standard/singleTop 仍走已验证的通用池；新增 singleTask(StubTask0..3) / singleInstance(StubInstance0..1)
  专用桩，按目标 Activity 的 launchMode 分配。
- 外部存储：app 私有外部目录重定向到实例目录。
- **首要目的仍是回归验证：确认这些没有把已经跑通的微信欢迎页弄坏。**

## 步骤
1. 编译 `:app`，装宿主，`pm clear com.example.multiopen` 清数据。
2. 添加微信 APK，打开，等欢迎页（ANR 弹窗点“等待”）。
3. 若欢迎页出来了，**点一下“登录”或“注册”**（只点进去看会不会跳转/崩，不要输入账号、不要真登录），
   看能不能打开下一个页面（这会用到 singleTask/singleInstance 桩）。
4. 抓 logcat：`adb logcat MultiOpen:I ActivityTaskManager:I AndroidRuntime:E *:S`

## 本轮重点看
1. **回归**：微信欢迎页是否仍正常显示？有没有新的 FATAL？
2. **桩分配**：点“登录/注册”后，`rewriteIntent: ... -> stub` 用的是哪个桩（StubActivity* / StubTask* / StubInstance*）？
   有没有 `newActivity: stub -> <登录/注册页类名>`？下一个页面有没有显示出来？
3. ActivityTaskManager 里新页面的 `START ... result code=?`（0=新建成功）。
4. 若点进去崩了或又循环，把第一个 FATAL / 新的 rewriteIntent 刷屏情况贴回。

## 写回 debug/last-run.md（覆盖），然后
```
git add debug/last-run.md && git commit -m "test run: launchMode 桩池 + 外部存储回归" && git push
```
