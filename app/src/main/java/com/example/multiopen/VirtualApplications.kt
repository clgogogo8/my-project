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
            // 微信是多进程 App，按“当前进程名”决定初始化哪些 Kernel；它的主进程名 == 包名。
            // 不伪装的话进程名是宿主的 com.example.multiopen，微信判定“非主进程”，跳过账号 Kernel 初始化，
            // 之后 LauncherUI 访问 mCoreAccount 就 "not initialized"。必须赶在微信任何代码跑之前改。
            fakeProcessName(rt.app.packageName)
            Watchdog.start() // 诊断：主线程卡住时定时打印它的调用栈
            val vctx = VirtualContext(host.applicationContext, rt.app.instanceId, VirtualCore.dataDir(rt.app), rt) { get(rt.app.instanceId) }
            val app = rt.classLoader.loadClass(rt.app.applicationClass ?: "android.app.Application")
                .getDeclaredConstructor().newInstance() as Application
            // 先登记占位：Tinker 等在 attachBaseContext 期间就会重入（startService → ensure），
            // 若此时 apps 里没有，会重复创建第二个 Application。@Synchronized 是可重入锁，同线程重入安全。
            apps[rt.app.instanceId] = app
            try {
                ContextWrapper::class.java.getDeclaredMethod("attachBaseContext", Context::class.java)
                    .apply { isAccessible = true }.invoke(app, vctx)
            } catch (t: Throwable) {
                apps.remove(rt.app.instanceId) // attachBaseContext 失败则回滚占位
                throw t
            }
            // 让 ActivityThread.currentApplication() 返回这个虚拟 Application：微信的 report.service /
            // platformtools 等进程级基础设施（常在后台线程）通过它拿全局 Context/Resources，否则拿到宿主
            // Application，其 Resources 不含微信 apk → le5.j 查资源 NotFound。必须在 onCreate 前设好。
            setInitialApplication(app)
            // 系统真实启动顺序：attachBaseContext → 所有 ContentProvider.onCreate → Application.onCreate。
            // 微信把核心 Kernel（mCoreAccount）初始化放在某个 ContentProvider 里，所以 provider 必须先于
            // Application.onCreate，否则 onCreate 里访问 mCoreAccount 会 "not initialized"。
            try { VirtualProviders.ensure(host, rt) } catch (t: Throwable) { Log.e(MultiOpenApp.TAG, "providers ensure failed", t) }
            try { app.onCreate() } catch (t: Throwable) { Log.e(MultiOpenApp.TAG, "plugin Application.onCreate failed", t) }
            Log.i(MultiOpenApp.TAG, "virtual Application created: ${app.javaClass.name}")
            // 静态广播注册时机不敏感，放在 onCreate 之后
            try { VirtualReceivers.ensure(host, rt) } catch (t: Throwable) { Log.e(MultiOpenApp.TAG, "receivers ensure failed", t) }
            app
        } catch (t: Throwable) {
            Log.e(MultiOpenApp.TAG, "create virtual Application failed", t)
            null
        }
    }

    /**
     * 伪装进程名：改 ActivityThread.mBoundApplication.processName（Application.getProcessName() /
     * ActivityThread.currentProcessName() 都读它）和其 appInfo.processName。
     * 读 /proc/self/cmdline 的 native 路径改不了，但微信主要走 ActivityThread。进程级生效，多开单进程近似。
     */
    private fun fakeProcessName(name: String) {
        try {
            val atClass = Class.forName("android.app.ActivityThread")
            val at = atClass.getMethod("currentActivityThread").invoke(null)
            val bound = atClass.getDeclaredField("mBoundApplication").apply { isAccessible = true }.get(at) ?: return
            runCatching {
                bound.javaClass.getDeclaredField("processName").apply { isAccessible = true }.set(bound, name)
            }
            runCatching {
                val appInfo = bound.javaClass.getDeclaredField("appInfo").apply { isAccessible = true }
                    .get(bound) as? android.content.pm.ApplicationInfo
                appInfo?.processName = name
            }
            Log.i(MultiOpenApp.TAG, "fake process name -> $name")
        } catch (t: Throwable) {
            Log.w(MultiOpenApp.TAG, "fake process name failed", t)
        }
    }

    /** 把虚拟 Application 设为 ActivityThread.mInitialApplication 并加入 mAllApplications */
    private fun setInitialApplication(app: Application) {
        try {
            val atClass = Class.forName("android.app.ActivityThread")
            val at = atClass.getDeclaredMethod("currentActivityThread").apply { isAccessible = true }.invoke(null)
            atClass.getDeclaredField("mInitialApplication").apply { isAccessible = true }.set(at, app)
            @Suppress("UNCHECKED_CAST")
            val all = atClass.getDeclaredField("mAllApplications").apply { isAccessible = true }.get(at) as? ArrayList<Application>
            if (all != null && !all.contains(app)) all.add(app)
            Log.i(MultiOpenApp.TAG, "set mInitialApplication -> ${app.javaClass.name}")
        } catch (t: Throwable) {
            Log.w(MultiOpenApp.TAG, "set mInitialApplication failed", t)
        }
    }
}
