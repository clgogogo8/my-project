package com.example.multiopen

import android.app.Application
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log

/**
 * 进程内 Service 管理器。宿主与插件同进程，这里不走“桩 Service + AMS Intent 改写”，而是直接在进程里
 * 反射创建真实 Service、attach 插件 Context、驱动 onCreate/onStartCommand/onBind/onDestroy，并用一个
 * 主线程 Handler 把 bindService 的 IBinder 回调给 ServiceConnection。
 *
 * 覆盖：应用逻辑层面的 Service（组件间 bind 通信、onStartCommand 处理）。
 * 不覆盖（需后续桩 Service + AMS 方案）：系统级后台保活、startForeground 前台通知、跨进程绑定、
 * stopSelf(token) 经 AMS 的精确停止、隐式 Intent 按 action 解析 Service。
 */
object VirtualServices {
    private val handler = Handler(Looper.getMainLooper())
    private val services = HashMap<String, Service>()        // "instance/类名" -> Service
    private val bindings = HashMap<ServiceConnection, String>() // conn -> key（用于 unbind）

    private fun key(instanceId: String, cls: String) = "$instanceId/$cls"

    /** @return 目标 Service 的 ComponentName；若不是插件的 Service（应交给 super 处理）则为 null */
    @Synchronized
    fun start(host: Context, instanceId: String, rt: PluginRuntime?, intent: Intent): ComponentName? {
        val cls = pluginServiceClass(rt, intent) ?: return null
        return try {
            val service = services.getOrPut(key(instanceId, cls)) { create(host, instanceId, rt!!, cls) }
            service.onStartCommand(intent, 0, START_ID.incrementAndGet())
            ComponentName(rt!!.app.packageName, cls)
        } catch (t: Throwable) {
            Log.e(MultiOpenApp.TAG, "startService $cls 失败", t); null
        }
    }

    @Synchronized
    fun stop(instanceId: String, intent: Intent, rt: PluginRuntime?): Boolean? {
        val cls = pluginServiceClass(rt, intent) ?: return null
        val service = services.remove(key(instanceId, cls)) ?: return true
        return try { service.onDestroy(); true } catch (t: Throwable) { Log.e(MultiOpenApp.TAG, "stopService $cls 失败", t); false }
    }

    @Synchronized
    fun bind(host: Context, instanceId: String, rt: PluginRuntime?, intent: Intent, conn: ServiceConnection): Boolean? {
        val cls = pluginServiceClass(rt, intent) ?: return null
        return try {
            val k = key(instanceId, cls)
            val service = services.getOrPut(k) { create(host, instanceId, rt!!, cls) }
            val binder = service.onBind(intent)
            bindings[conn] = k
            val name = ComponentName(rt!!.app.packageName, cls)
            handler.post { conn.onServiceConnected(name, binder) } // 同步回调会在某些调用处死锁，统一异步
            true
        } catch (t: Throwable) {
            Log.e(MultiOpenApp.TAG, "bindService $cls 失败", t); false
        }
    }

    /** @return true 表示这是我们管理的绑定（已处理）；false 表示不是，交给 super */
    @Synchronized
    fun unbind(conn: ServiceConnection): Boolean {
        val k = bindings.remove(conn) ?: return false
        services[k]?.let { s -> try { s.onUnbind(Intent()) } catch (t: Throwable) { Log.w(MultiOpenApp.TAG, "onUnbind 失败", t) } }
        return true
    }

    /** 判断 Intent 的显式组件是否指向这个插件的 Service；是则返回规范化类名 */
    private fun pluginServiceClass(rt: PluginRuntime?, intent: Intent): String? {
        rt ?: return null
        val comp = intent.component ?: return null // 隐式 Service 暂不支持
        val pkg = rt.app.packageName
        if (comp.packageName != pkg || comp.className.isEmpty()) return null
        val cls = if (comp.className.startsWith(".")) pkg + comp.className else comp.className
        return try {
            if (Service::class.java.isAssignableFrom(rt.classLoader.loadClass(cls))) cls else null
        } catch (_: Throwable) { null }
    }

    private fun create(host: Context, instanceId: String, rt: PluginRuntime, cls: String): Service {
        val app = VirtualApplications.ensure(host, rt)
        val vctx = VirtualContext(host.applicationContext, instanceId, VirtualCore.dataDir(rt.app), rt) { VirtualApplications.get(instanceId) }
        val service = rt.classLoader.loadClass(cls).getDeclaredConstructor().newInstance() as Service
        if (!attach(service, vctx, cls, app)) {
            // 降级：拿不全 attach 参数时，至少把 baseContext 接上，简单 Service 仍可工作
            ContextWrapper::class.java.getDeclaredMethod("attachBaseContext", Context::class.java)
                .apply { isAccessible = true }.invoke(service, vctx)
        }
        service.onCreate()
        Log.i(MultiOpenApp.TAG, "virtual Service created: $cls")
        return service
    }

    /** 反射调 Service.attach(Context, ActivityThread, String, IBinder, Application, Object) */
    private fun attach(service: Service, context: Context, className: String, application: Application?): Boolean = try {
        val atClass = Class.forName("android.app.ActivityThread")
        val thread = atClass.getMethod("currentActivityThread").invoke(null)
        val am = iActivityManager()
        val m = Service::class.java.getDeclaredMethod("attach",
            Context::class.java, atClass, String::class.java,
            IBinder::class.java, Application::class.java, Any::class.java).apply { isAccessible = true }
        m.invoke(service, context, thread, className, Binder(), application, am)
        true
    } catch (t: Throwable) {
        Log.w(MultiOpenApp.TAG, "Service.attach 失败，降级", t); false
    }

    private fun iActivityManager(): Any? = runCatching {
        val am = Class.forName("android.app.ActivityManager")
        am.getMethod("getService").invoke(null) // API 26+
    }.recoverCatching {
        val amn = Class.forName("android.app.ActivityManagerNative")
        amn.getMethod("getDefault").invoke(null) // API 24/25
    }.getOrNull()

    private val START_ID = java.util.concurrent.atomic.AtomicInteger(0)
}
