package com.example.multiopen

import android.app.Application
import android.app.Instrumentation

object Hooks {
    /** 用我们的 Instrumentation 替换 ActivityThread.mInstrumentation（Android 9–14 均有此字段） */
    fun install(app: Application) {
        val atClass = Class.forName("android.app.ActivityThread")
        val thread = atClass.getDeclaredMethod("currentActivityThread").apply { isAccessible = true }.invoke(null)
        val field = atClass.getDeclaredField("mInstrumentation").apply { isAccessible = true }
        val base = field.get(thread) as Instrumentation
        if (base is HookInstrumentation) return
        field.set(thread, ExecHookInstrumentation(app, base))
    }
}
