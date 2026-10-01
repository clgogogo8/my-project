# 本轮要做什么（云端 Claude 每轮更新这个文件）

本地 AI：`git fetch origin && git reset --hard origin/claude/inspiring-bell-ivlw5w` 之后，照这里做，
结果写进 `debug/last-run.md` 再 push。轮询模式。

---

## 这轮：root 读 /proc/net/tcp 看登录请求有没有发出去（run-as 在 A14 被 SELinux 挡，只能 root 读）

上一轮确认：Android 14 下普通 app 身份（run-as）读不了 `/proc/net/tcp*`，shell 身份也过滤掉了微信 uid 的行。
拿这份数据只剩 root。**需要用户另外授权本地 AI 执行 `su -c cat /proc/net/tcp*`（只读连接元数据，不碰内容）。**

### 前置：等用户授权
- 用户需在本地 Claude Code 放行 `su -c cat /proc/net/tcp*`（或把 root 只读规则从只含 `/data/anr/trace_*` 扩到含 `/proc/net/tcp*`）。
- **用户没授权就别做**，在 last-run.md 里写"等用户授权 root 读 /proc/net"。不要绕权限。

### 红线
- 账号/密码由**用户本人**输入，不代输、不点登录、不截屏。
- 只读连接元数据（IP:端口:状态），**不抓包内容**。只保留微信 app uid（上一轮是 10251，以当轮 `run-as ... id` 为准）的行。

## 步骤（拿到授权后）
1. 宿主在跑、微信在登录页。拿 uid：`adb shell run-as com.example.multiopen id`；pid：`adb shell pidof com.example.multiopen`。
2. 登录页静止时拍基线：`adb shell su -c "cat /proc/net/tcp /proc/net/tcp6" > net_before.txt`
3. 提示**用户本人点登录**，记时刻；转圈期间每 ~3 秒连拍：`... > net_after_N.txt`（拍 _1 _2 _3 _4）。
4. 解析（只留 uid==微信uid 的行）：`rem_address` 是 `十六进制IP:十六进制端口`；
   `st`：`01`=ESTABLISHED、`02`=SYN_SENT、`06`=TIME_WAIT、`08`=CLOSE_WAIT、`0A`=LISTEN。转成点分十进制:端口。

## 回传请给我（只贴 IP:端口 状态，不贴内容）
1. **基线**：微信 uid 有几条对外连接？`IP:端口 状态` 列表。有没有已建立的长连接（端口 80/8080/443 居多）？
2. **点登录后**：有没有**新增** `SYN_SENT`（正发起）或新增 `ESTABLISHED`？贴新增/变化的行。
3. **二选一结论**：
   - 点登录后无任何新连接、原连接也没动 → "请求没发出去"。
   - 有新 `SYN_SENT` 但一直连不上 / `ESTABLISHED` 后无动静 → 写清是哪种。
4. 点登录前后 `pidof` 有没有变（进程有没有死）。

## 写回 debug/last-run.md（覆盖），然后
```
git add debug/last-run.md && git commit -m "test run: root 读 /proc/net 看登录连接" && git push
```
