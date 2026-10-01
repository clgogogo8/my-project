package com.example.multiopen

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.util.Log

/** 为每个虚拟应用实例创建并持有它自己的 Application（插件声明的子类，没有则用 android.app.Application） */
object VirtualApplications {
    private val lock = Any()
    private val apps = HashMap<String, Application>()

    fun get(instanceId: String): Application? = synchronized(lock) { apps[instanceId] }

    /**
     * 锁内只做“创建 Application + attachBaseContext + 登记占位”；耗时且会跨线程回调进来的 onCreate / provider
     * 放到锁外执行。原因：微信的 Application.onCreate 会在主线程同步等后台 ForkJoin 任务，而那些任务又会从别的
     * 线程调宿主的 bindService → 回到 ensure；若整段都持锁，后台线程抢不到锁、主线程又在等它 → 跨线程死锁。
     * 占位先登记，所以任何重入（同线程 attachBaseContext、或跨线程 onCreate 期间）都能立刻拿到同一个实例、不阻塞。
     */
    fun ensure(host: Context, rt: PluginRuntime): Application? {
        val id = rt.app.instanceId
        val created: Application
        synchronized(lock) {
            apps[id]?.let { return it } // 已创建（占位或完成）→ 立即返回，绝不在锁内等待
            created = try {
                // 进程名必须赶在微信任何代码跑之前伪装（多进程 Kernel 分发按进程名，主进程名==包名）
                fakeProcessName(rt.app.packageName)
                val vctx = VirtualContext(host.applicationContext, id, VirtualCore.dataDir(rt.app), rt) { get(id) }
                val app = rt.classLoader.loadClass(rt.app.applicationClass ?: "android.app.Application")
                    .getDeclaredConstructor().newInstance() as Application
                apps[id] = app // 先登记占位，再 attach：attachBaseContext 期间的重入能拿到它，避免重复创建
                ContextWrapper::class.java.getDeclaredMethod("attachBaseContext", Context::class.java)
                    .apply { isAccessible = true }.invoke(app, vctx)
                app
            } catch (t: Throwable) {
                apps.remove(id)
                Log.e(MultiOpenApp.TAG, "create virtual Application failed", t)
                return null
            }
        }
        // —— 锁外 —— 走到这里的线程一定是刚新建该实例的那个（已存在的在锁内就 return 了），由它负责初始化。
        Watchdog.start() // 诊断
        // currentApplication() 指向虚拟 Application（微信后台基础设施靠它拿全局 Context/Resources）
        setInitialApplication(created)
        // 系统真实顺序：provider.onCreate 先于 Application.onCreate（微信核心 Kernel 在 provider 里初始化）
        try { VirtualProviders.ensure(host, rt) } catch (t: Throwable) { Log.e(MultiOpenApp.TAG, "providers ensure failed", t) }
        try { created.onCreate() } catch (t: Throwable) { Log.e(MultiOpenApp.TAG, "plugin Application.onCreate failed", t) }
        Log.i(MultiOpenApp.TAG, "virtual Application created: ${created.javaClass.name}")
        try { VirtualReceivers.ensure(host, rt) } catch (t: Throwable) { Log.e(MultiOpenApp.TAG, "receivers ensure failed", t) }
        return created
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
