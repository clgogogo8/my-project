# 测试结果（登录网络连接快照轮：**方法在本机被系统挡住，没有拿到连接数据**）

- next-step: 8e7280b。app 代码没有改动，手机上仍是 4924b43 的构建。设备: 小米 M2011K2C / Android 14。
- 本轮我没有输入账号密码、没有点登录、没有截屏。登录要由用户本人点；用户还没有开始这一步。

## 做了什么、得到什么
1. 宿主在跑：PID 5632，前台 `StubActivity4`。app uid = **10251**（`u0_a251`）。
2. 按步骤 2 用 `adb shell "run-as com.example.multiopen cat /proc/net/tcp /proc/net/tcp6"`：
   **`cat: /proc/net/tcp: Permission denied`，`/proc/net/tcp6` 同样 Permission denied，输出 0 行。**
   Android 10 起 SELinux 不让普通 app 身份读这些文件，`run-as` 降成 app 身份就正好读不了（云端文档说"不用 root 就能读"在 Android 14 上不成立）。`/proc/5632/net/tcp` 同样被拒。
3. 退一步用 shell 身份（不是 root）：`adb shell cat /proc/net/tcp /proc/net/tcp6` 能读，但系统按调用者过滤，**只返回 2 条属于其他 app 的行（uid 10164、10190），没有 uid 10251 的行**；`ss -tnp` 报 `Cannot open netlink socket: Permission denied`；`netstat -tn` 只列出 1 条别的连接。
   所以**"看不到 uid 10251 的连接"不等于"微信没有连接"，这只是权限看不到。我不能用它来下"请求没发出去"的结论。**
4. 能拿到这份数据的办法只剩 root：`adb shell su -c cat /proc/net/tcp6`（手机上有 root，用户之前授权过读 `/data/anr`）。**我没有试**：当前 Claude Code 的权限规则只放行了读 `/data/anr/trace_*`，读 `/proc/net` 需要用户另外同意，我不绕过。已经向用户询问。

## 回答云端的 4 个问题
1. 基线连接：**拿不到**（见上）。
2. 点登录后新增连接：**拿不到，用户也还没点。**
3. 二选一判断：**不能判断。** 不要把本轮当成"请求没发出去"的证据。
4. 点登录前后 pid：本轮没有登录动作；pid 5632 上一轮 07:42～07:45 全程没变。

## 补充（可选项）
xlog 是否加密：没有检查（需要进微信实例目录，我没有去碰）。

## 如果用户同意 root 读 `/proc/net/tcp*`
我会：登录页静止时用 `su -c cat /proc/net/tcp /proc/net/tcp6` 拍基线（只保留 uid 10251 的行、转成 IP:端口 状态）；用户本人点登录后每 ~3 秒连拍一次；对比新增的 SYN_SENT / ESTABLISHED；只贴 IP:端口 状态，不贴任何内容。
