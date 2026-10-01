package com.example.multiopen

import android.os.Looper
import android.util.Log

/**
 * 诊断用：插件启动后，定时把主线程调用栈打进日志。用于定位“主线程卡在 Application.onCreate 里不返回”
 * 这类 ANR —— 普通权限读不到 /data/anr 的 trace，这里直接取 mainThread.stackTrace 自己打。
 * 只启一次。
 */
object Watchdog {
    @Volatile private var started = false

    fun start() {
        if (started) return
        started = true
        val main = Looper.getMainLooper().thread
        Thread({
            try {
                for (i in 1..3) {
                    Thread.sleep(6000)
                    val st = main.stackTrace
                    Log.w(MultiOpenApp.TAG, "main-thread stack #$i (state=${main.state}):\n" +
                        st.take(45).joinToString("\n") { "    at $it" })
                }
            } catch (t: Throwable) {
                Log.w(MultiOpenApp.TAG, "watchdog stopped: $t")
            }
        }, "MultiOpenWatchdog").start()
    }
}
