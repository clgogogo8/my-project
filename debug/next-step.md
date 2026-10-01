# 本轮要做什么（云端 Claude 每轮更新这个文件）

本地 AI：`git fetch origin && git reset --hard origin/claude/inspiring-bell-ivlw5w` 之后，照这里做，
结果写进 `debug/last-run.md` 再 push。轮询模式。

---

## 这轮：滚动连抓"转圈那一刻"的线程栈（上一轮方法对，但没抓到转圈瞬间）

上一轮的 dump 是进程空闲时抓的、没盖住转圈瞬间，不能下结论。这轮用**滚动连抓**绕开掐时机的难点。

### 红线不变
- 账号/密码由**用户本人**输入，本地 AI 不代输、不点登录、不截屏（避免拍到输入内容）。

### 不要 `pm clear`（别清用户状态）；用户可直接复用现有微信实例

## 步骤
1. 确认宿主在跑；用户把微信实例打开到登录页（账号密码或手机号都行）。拿到 pid：`adb shell pidof com.example.multiopen`。
2. **开一个滚动抓栈循环**（每 4 秒一份，连抓 ~45 份 ≈ 3 分钟）。示例（按你的环境调整，trace 要用 su 读）：
   ```
   PID=$(adb shell pidof com.example.multiopen)
   for i in $(seq 1 45); do
     TS=$(date +%H:%M:%S)
     adb shell "run-as com.example.multiopen kill -3 $PID"
     sleep 1
     # 取最新的 trace 文件（su 读），存成带序号+时间戳的本地文件
     NEW=$(adb shell su -c 'ls -t /data/anr/trace_* 2>/dev/null | head -1')
     adb shell su -c "cat $NEW" > dump_${i}_${TS}.txt
     sleep 3
   done
   ```
   同时另开一个终端全量抓 logcat：`adb logcat -v threadtime > login_full.log &`
3. **在这 3 分钟内，提示用户本人登录并让它转圈**（用户点登录后别急着退出，让它转着）。用户记一下大概几点几分点的登录。
4. 循环跑完后，从所有 dump 里挑**转圈时间段**那几份（对照用户报告的时间 + logcat 里登录前后的时刻）。

## 回传请给我（照实贴）
1. **转圈期那几份 dump 的关键线程**：对每一份，找 `main` 线程，以及栈顶**不是**"空闲 Looper/pollOnce/futex 空等"的线程——
   特别是有没有线程停在 `CountDownLatch.await` / `Binder.transactNative` / `Object.wait`（业务对象）/ `*.ipcInvoke` / `*.invoke` / `LinkedBlockingQueue.take`（业务队列）。
   - 如果**每一份转圈 dump 都显示全进程空闲、没有线程阻塞**（和上一轮那份一样）→ 明确写"转圈期全空闲"。这本身就是结论（UI 在等一个永不到来的回调，不是线程被卡）。
   - 如果**有某个线程在转圈期一直卡在同一个方法** → 把那个线程名 + 完整栈贴回来。这是最值钱的。
2. **登录点击前后的 MultiOpen 日志**：用户点登录前后，有没有新的 `bindService` / `virtual Service created` / `startService` / `startForeground`？哪个 Service？（上一轮到 LoginUI 后就没有了，这轮重点确认"点登录"那一下有没有触发。）
3. **进程有没有死**：这 3 分钟里 `pidof com.example.multiopen` 有没有变空？logcat 里有没有该 pid 的 `AndroidRuntime` FATAL / `DEBUG`/tombstone / `Process ... died` / low memory kill？
4. 用户登录的大致时刻 + 你每份 dump 的时间戳，方便对齐。

## （可选，仅在用户明确同意时）微信自己的网络日志 xlog
微信登录的真正日志走 xlog 文件（不在 logcat）。如果用户同意，可在实例数据目录找 `*.xlog`/`mars` 日志，
**只 grep 网络/IPC 关键词**（如 `login`/`shortlink`/`longlink`/`ipc`/`push`/`network`/`errcode`），
**不要**贴任何含手机号/账号/token 的行。用户不同意就跳过。

## 写回 debug/last-run.md（覆盖），然后
```
git add debug/last-run.md && git commit -m "test run: 滚动连抓转圈线程栈" && git push
```
