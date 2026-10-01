# 本轮要做什么（云端 Claude 每轮更新这个文件）

本地 AI：`git fetch origin && git reset --hard origin/claude/inspiring-bell-ivlw5w` 之后，照这里做，
结果写进 `debug/last-run.md` 再 push。恢复轮询模式（登录留到最后，先做其余项）。

---

## 目标 commit：见最新（content:// 进程内解析）

## 改了什么
- 在 `ActivityManagerHook` 里拦 `getContentProvider` / `getContentProviderExternal`：当请求的 authority 是我们
  已登记的插件 provider 时，返回本地 provider 的 IContentProvider（ContentProviderHolder，进程内，不走系统）。
- 目的：让微信内部 content:// 查询（FileProvider 等）能命中本地 provider，为发图/文件分享铺路。
- **首要仍是回归：别把已经跑通的 UI（欢迎页→登录页）弄坏。**

## 步骤
1. 编译 `:app`，装宿主，`pm clear com.example.multiopen` 清数据。
2. 打开微信，等欢迎页，点“登录”到手机号登录页，再点“使用其他登录方式→用微信号/QQ号/邮箱登录”到账号密码页。
   **不要输入任何账号/密码，不要点“同意并登录”。**（登录这步留到最后、由用户本人做。）
3. 抓 logcat：`adb logcat MultiOpen:I Pine:I ActivityTaskManager:I AndroidRuntime:E *:S`

## 本轮重点看
1. **回归**：欢迎页 / 登录页 / 账号密码页是否仍稳定显示、无崩溃？（和上一轮一致就行）
2. 有没有 `served local content provider: <authority>` 日志？出现了哪些 authority？
3. 有没有因为这个改动出现**新的崩溃 / ANR / 黑屏**？有就贴第一个崩溃栈。
4. 如果能在登录页点到“头像选择 / 从相册选图 / 拍照”之类触发 FileProvider 的入口（不需要登录就能点的），
   试着点一下看 content:// 相关有没有报错——点不到就跳过，不用登录。

## 写回 debug/last-run.md（覆盖），然后
```
git add debug/last-run.md && git commit -m "test run: content:// 进程内解析" && git push
```
