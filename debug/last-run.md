# 测试结果（滚动连抓转圈线程栈：**已完成**；转圈窗口内全程没有线程被卡）

- next-step: 9a95253（滚动连抓转圈线程栈）。app 代码没有改动，手机上仍是 4924b43 的构建。
- 设备: 小米 M2011K2C / Android 14，root 由 APatch 提供。
- **本轮登录是用户明确要求我代为操作的**：我把账号/密码输入到当前 LoginUI 页面、收起键盘、点"同意并登录"。账号、密码、手机号都**没有**写进本文件、提交信息或任何仓库文件；输入之后我没有截屏。
- 点击登录时刻：宿主时钟 07:43:13（= 设备时钟约 23:43:42；设备日志/ dump 时间戳用设备时钟，两者相差 8 小时多约 30 秒）。

## 抓取方式
- 脚本每 ~4 s 对宿主发一次 `run-as com.example.multiopen kill -3 <pid>`，共 50 次，07:42:21 起到 07:45:46 止。50 次全部成功（`ok`），每次都有对应的 `/data/anr/trace_NN`（trace_16…trace_55；roll.log 里 trace_19 后直接是 trace_21，trace_20 为什么跳过我没查）。
- 读取用 `adb shell su -c cat /data/anr/trace_NN`（需要 root，用户已授权）。我读了转圈窗口里的 4 份：trace_19（点击后约 10 s）、trace_30（约 50 s）、trace_42（约 100 s）、trace_55（约 150 s）。其余 dump 没有逐份读，**所以下面的结论只对这 4 份成立，不代表其余 46 份**。

## 线程栈结果（4 份，宿主 PID 5632，全程同一个 PID）

| dump | 设备时间 | 线程数 | 状态 | Blocked（等锁） | 主线程 |
|---|---|---|---|---|---|
| trace_19 | 23:43:49 | 139 | S 138 / R 1 | 0 | `MessageQueue.nativePollOnce`（空闲 Looper） |
| trace_30 | 23:44:30 | 145 | S 144 / R 1 | 0 | `nativePollOnce`（空闲） |
| trace_42 | 23:45:21 | 144 | S 142 / R 2 | 0 | `Choreographer.scheduleVsync`（在排下一帧，正在动画/重绘） |
| trace_55 | 23:46:15 | 143 | S 142 / R 1 | 0 | `nativePollOnce`（空闲） |

（R 的那个线程是 `Signal Catcher`，就是写 dump 的线程自己；trace_42 多出的一个 R 是主线程，栈顶在 `scheduleVsync`。）

- **没有任何一份 dump 里有线程处于 Blocked / 在等监视器锁。**
- **微信网络/IPC 线程在全部 4 份里都是空闲等待**：`mars::*`、`mars::comm`、`aff::IlinkCore`、`ilink2_cloud_lp`、`wc_srvinit_4` 停在 `futex`（`syscall`）；`mmcronet::quic:` 停在 `futex`/`epoll_pwait`；`wechatlv:center`、`wechatlv:fs`、`wc_lp_srvinit_*`、`IPCThreadPool#Inner`/`#Work*` 停在 `epoll_pwait` / `MessageQueue.nativePollOnce`；`wc_srvinit_0/2/3/5`、`IPCThreadPool#Thre*` 停在 `Unsafe.park`（线程池空闲）。
- 我用关键字（`CountDownLatch.await`、`Binder.transact`、`LinkedBlockingQueue.take`、`Future.get`、`Object.wait`）统计过，每份约 31–33 个线程命中，但是我只看了命中列表的前 12 个线程名：`Timer-*`、`OkHttp ConnectionPool`、`Okio Watchdog`、`Recovery.LogWriter`、`[GT]HotPool/ColdPool`、`audio_mix_controller`，都是平时会空闲挂着的池/定时线程。**剩下约 20 个我没有看，也没有逐个读栈**，所以"没有业务线程在等结果"这一条我只能说"网络/IPC 相关线程核对过都空闲，其余没完全核对"。
- 线程数在点击后从 139 增加到 145（出现 `wechatlv:center`、`wechatlv:fs`、`io.liteapp.work`、多出一组 `lu_worker`），说明点击之后微信确实创建了一些新的 native 线程，但它们全部停在空闲。
- 栈里含 `com.example.multiopen` 字样的线程很多（mars、lu_worker 等），但那只是因为 so 的路径在宿主 app 目录下，**我没有单独检查有没有宿主的 Java 方法帧**，这条不下结论。

**结论（只对这 4 份 dump 成立）**：转圈期间进程完全不卡：没有锁、没有线程阻塞在 Binder/latch/队列上等回包，网络线程（mars/cronet/ilink）全部空闲。和上一轮 23:25:09 那份 dump（那时不知道界面状态）结论一致。这支持"请求没有真正发出去，或回包不会再回来，UI 在等一个不会来的回调"，**但不能证明**，因为 dump 只能说明"没有线程在等"，不能说明"为什么没发"。

## 点击登录之后的 MultiOpen 日志（设备日志缓冲，设备时间 23:42 起）
- **MultiOpen 标签的行：0 行。** 没有新的 `virtual Service created`、没有 `bindService`/`startService`/`startForeground`、没有 `rewriteIntent`/`newActivity`。
- 我只查了 23:42 之后；5632 是新进程，它启动时的 MultiOpen 日志我这轮没有去看。
- 同一窗口里宿主进程 5632 的日志只有渲染/输入法相关（`Looper`、`InputTransport`、`Choreographer`、`ImeTracker`、`cso-p`、`PayMarsLiteApp`、`flutter`）。`cso-p` 的内容我没有展开看（行数 143，可能是微信自己的日志，**不确定里面有没有账号信息，所以没有写进这里**）。

## 进程与异常
- 宿主 PID 5632 在 07:42:21～07:45:46 全程存活（50 次抓取都能 `pidof`），**没有死亡/重启**。
- 设备日志里 23:42 起 `FATAL` / `AndroidRuntime` / `lowmemorykiller` / `am_kill` / `NativeCrash` / `ANR in`：只有一条 `AndroidRuntime: VM exiting`，是 **`com.baidu.input_mi`（百度输入法，pid 8022）在 23:43:20 自己退出**，与宿主无关。宿主没有任何崩溃、没有被杀。

## 我不知道的
- **点击后手机屏幕具体是什么样（转圈/白屏/有无提示）、持续多久**：我为了不拍到输入内容没有截屏，用户也没有告诉我。dump 里只有 trace_42 主线程在排帧，**不足以证明有转圈动画**。
- **登录请求有没有真的发出去**：logcat 看不到微信自己的网络日志（走 xlog 文件）。我没有读 xlog（可能含账号信息，用户没同意）。如果云端需要，需用户明确说"同意读 xlog"。
- 登录最终结果（成功/失败/错误码）：我不知道，没有看。
- 没有逐份读剩下的 46 份 dump。

## 抓栈方法备忘
`run-as … kill -3 <pid>` 触发 → `/data/anr/trace_NN`（tombstoned:system 0660）→ `adb shell su -c cat /data/anr/trace_NN > 本机文件`。全量 logcat 与 dump 原文都只存在本机，**没有提交**。
