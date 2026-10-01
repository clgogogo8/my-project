package com.example.multiopen

import android.os.Looper
import android.util.Log

/**
 * 诊断用：插件启动后定时打印主线程栈；并在第 2 次采样时把参与微信启动/ForkJoin 的后台线程栈也打出来，
 * 用于定位“主线程 ForkJoinTask.get() 等的那个任务在哪个线程、在等什么、还是根本没人执行”。只启一次。
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
                    Log.w(MultiOpenApp.TAG, "main-thread stack #$i (state=${main.state}):\n" + fmt(main.stackTrace, 45))
                    if (i == 2) dumpBackgroundThreads(main)
                }
            } catch (t: Throwable) {
                Log.w(MultiOpenApp.TAG, "watchdog stopped: $t")
            }
        }, "MultiOpenWatchdog").start()
    }

    /** 打印栈里涉及微信启动框架或 ForkJoin 的后台线程（跳过纯 idle 的框架线程，控制日志量） */
    private fun dumpBackgroundThreads(main: Thread) {
        for ((t, st) in Thread.getAllStackTraces()) {
            if (t === main) continue
            val relevant = st.any {
                val c = it.className
                c.startsWith("com.tencent") || c.contains("ForkJoin") ||
                    c.startsWith("ph5") || c.startsWith("gp0") || c.startsWith("yu5") || c.startsWith("xu5")
            }
            if (relevant) Log.w(MultiOpenApp.TAG, "bg thread '${t.name}' (${t.state}):\n" + fmt(st, 20))
        }
    }

    private fun fmt(st: Array<StackTraceElement>, max: Int): String =
        st.take(max).joinToString("\n") { "    at $it" }
}
