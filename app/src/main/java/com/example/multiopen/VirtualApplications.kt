package com.example.multiopen

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.util.Log

/** 为每个虚拟应用实例创建并持有它自己的 Application（插件声明的子类，没有则用 android.app.Application） */
object VirtualApplications {
    private val apps = HashMap<String, Application>()

    @Synchronized
    fun get(instanceId: String): Application? = apps[instanceId]

    @Synchronized
    fun ensure(host: Context, rt: PluginRuntime): Application? {
        apps[rt.app.instanceId]?.let { return it }
        return try {
            val vctx = VirtualContext(host.applicationContext, rt.app.instanceId, VirtualCore.dataDir(rt.app), rt) { get(rt.app.instanceId) }
            val app = rt.classLoader.loadClass(rt.app.applicationClass ?: "android.app.Application")
                .getDeclaredConstructor().newInstance() as Application
            ContextWrapper::class.java.getDeclaredMethod("attachBaseContext", Context::class.java)
                .apply { isAccessible = true }.invoke(app, vctx)
            apps[rt.app.instanceId] = app
            try { app.onCreate() } catch (t: Throwable) { Log.e(MultiOpenApp.TAG, "plugin Application.onCreate failed", t) }
            Log.i(MultiOpenApp.TAG, "virtual Application created: ${app.javaClass.name}")
            // Application 就绪后，把该实例的静态广播与 ContentProvider 装上（各自只装一次）
            try { VirtualReceivers.ensure(host, rt) } catch (t: Throwable) { Log.e(MultiOpenApp.TAG, "receivers ensure failed", t) }
            try { VirtualProviders.ensure(host, rt) } catch (t: Throwable) { Log.e(MultiOpenApp.TAG, "providers ensure failed", t) }
            app
        } catch (t: Throwable) {
            Log.e(MultiOpenApp.TAG, "create virtual Application failed", t)
            null
        }
    }
}
