# 本轮要做什么（云端 Claude 每轮更新这个文件）

本地 AI：`git fetch origin && git reset --hard origin/claude/inspiring-bell-ivlw5w` 之后，照这里做，
结果写进 `debug/last-run.md` 再 push。轮询模式。

---

## 这轮：坐死最后一环——"点登录后，请求有没有真的发出去"

线程栈已证明没有线程被卡（转圈期全程空闲）。剩最后一个问题：登录请求到底发没发出去。
**不读 xlog**（微信正式版 xlog 是加密的，读出来是乱码）。改为**直接看进程的对外网络连接**——
更干脆、不用解密、也不用 root（`run-as` 降到 app 自己身份就能读 `/proc/net/tcp`）。

### 红线
- 账号/密码由**用户本人**输入。本地 AI 不代输、不点登录、不截屏。
- 只看网络连接的"有没有/到哪个 IP:端口/什么状态"，**不抓包内容**。

## 步骤
1. 确认宿主在跑，用户把微信打到登录页。拿 app uid 和 pid：
   `adb shell run-as com.example.multiopen id`（记下 uid=u0_aXXX 对应的数字 uid）
   `adb shell pidof com.example.multiopen`
2. **登录页静止时**先拍一张连接快照（基线）：
   `adb shell "run-as com.example.multiopen cat /proc/net/tcp /proc/net/tcp6" > net_before.txt`
3. **提示用户本人点登录**，记下点击时刻；在转圈期间再连拍几张（隔几秒一张）：
   `adb shell "run-as com.example.multiopen cat /proc/net/tcp /proc/net/tcp6" > net_after_1.txt`（多拍几张 _2 _3）
4. 解析：`/proc/net/tcp` 每行 `local_address rem_address st ... uid`，地址是 `十六进制IP:十六进制端口`，
   `st` 状态：`01`=ESTABLISHED、`02`=SYN_SENT、`06`=TIME_WAIT、`08`=CLOSE_WAIT、`0A`=LISTEN。
   **只看属于微信这个 app uid 的行**（uid 列 == 上面那个数字 uid）。把十六进制 IP:端口转成点分十进制:端口。

## 回传请给我（照实写）
1. **基线（登录页静止）**：该 uid 有几条对外连接？分别是哪些 `IP:端口 状态`？有没有连到腾讯段的长连接
   （端口常见 80/8080/443；IP 反查到 tencent/weixin 更好，但不强求，给 IP 就行）？
2. **点登录后**：有没有**新增**对外连接？特别是有没有新的 `SYN_SENT`（正在发起连接）或新的 `ESTABLISHED`？
   贴出点击后新增/变化的那几行（IP:端口 状态）。
3. **最关键的二选一判断**：
   - 点登录后**没有任何新的对外连接、原有连接也没动** → 写"请求没有发出去"。
   - 点登录后**有新连接发起（SYN_SENT）但连不上/一直不 ESTABLISHED**，或 **ESTABLISHED 了但之后没动静** → 写清是哪种。
4. 顺带：点登录前后 `pidof` 有没有变（进程有没有死）。

## （可选）确认 xlog 是不是加密
如果方便，`ls` 一下实例数据目录里的 `*.xlog`，`head -c 16 | xxd` 看头部：
mars 明文 xlog 头是可读的、加密的是随机字节。只回报"加密/明文"，**不要**贴日志内容。

## 写回 debug/last-run.md（覆盖），然后
```
git add debug/last-run.md && git commit -m "test run: 登录网络连接快照" && git push
```
