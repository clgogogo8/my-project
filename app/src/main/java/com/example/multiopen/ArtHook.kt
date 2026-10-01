package com.example.multiopen

import android.app.Service
import android.content.Context
import android.util.Log
import top.canyie.pine.Pine
import top.canyie.pine.callback.MethodHook

/**
 * ART 方法级 hook（底层改 ArtMethod，全局对所有实例生效）。用于拦截微信绕过我们 PMS 代理、直接经某个
 * Java 方法打到真实系统的调用——典型是 `ApplicationPackageManager.getInstallerPackageName(String)`：
 * 无论微信的 ig5.n1 代理从哪拿的 binder，最终都经过这个 Java 方法，在这里对虚拟包直接返回安全值、不下调
 * binder，就不会抛 "Unknown package"，Cronet BuildInfo 初始化也就不崩。
 *
 * 用 Pine（现成 .so + Java API）。Android 14 兼容性与加载是否成功由真机验证；全程 try/catch，失败不影响宿主。
 */
object ArtHook {
    @Volatile private var installed = false

    @Synchronized
    fun install(host: Context, isVirtual: (String) -> Boolean) {
        if (installed) return
        try {
            runCatching { Pine.disableHiddenApiPolicy(true, true) }
            val fallback = runCatching { host.packageManager.getInstallerPackageName(host.packageName) }.getOrNull()

            val apm = Class.forName("android.app.ApplicationPackageManager")
            val m = apm.getDeclaredMethod("getInstallerPackageName", String::class.java)
            Pine.hook(m, object : MethodHook() {
                override fun beforeCall(frame: Pine.CallFrame) {
                    val pkg = frame.args?.getOrNull(0) as? String ?: return
                    if (isVirtual(pkg)) {
                        // 跳过原方法，返回宿主 installer，避免系统对虚拟包抛 Unknown package
                        frame.result = fallback
                    }
                }
            })
            installed = true
            Log.i(MultiOpenApp.TAG, "ART hook installed: ApplicationPackageManager.getInstallerPackageName")
        } catch (t: Throwable) {
            Log.e(MultiOpenApp.TAG, "ART hook install failed", t)
        }
    }

    @Volatile private var fgInstalled = false

    /**
     * 拦截虚拟 Service 的 startForeground / stopForeground，转交给宿主自己的 [KeepAliveService]。
     *
     * 原因：虚拟 Service 是我们反射创建、attach 了一个 AMS 没登记的假 Binder token 的实例，插件（如微信
     * 推送/保活 Service）调 startForeground 时真实实现会拿这个 token 去 AMS setServiceForeground，直接抛
     * 异常把插件搞崩。所以在 ArtMethod 层把这几个方法拦下来：**只对虚拟 Service 生效**（宿主自己的
     * KeepAliveService 不在 VirtualServices 注册表里，照常走真实前台），把“进程前台化”交给宿主那个真 Service。
     *
     * 跳过原方法的方式与上面的 getInstallerPackageName 一致：在 beforeCall 里 setResult 即短路原方法
     * （已在真机验证）。startForeground 返回 void，这里 setResult(null) 表示“我已处理、别再下调 AMS”。
     * 全程 try/catch：即便某个重载没 hook 上，最坏也只是回到现状（插件 startForeground 仍会崩），不引入新崩溃。
     */
    @Synchronized
    fun installForegroundRouting(host: Context) {
        if (fgInstalled) return
        try {
            val startHook = object : MethodHook() {
                override fun beforeCall(frame: Pine.CallFrame) {
                    val svc = frame.thisObject as? Service ?: return
                    if (!VirtualServices.isManaged(svc)) return // 宿主自己的前台 Service 照常放行
                    KeepAliveService.promote(host)
                    frame.result = null // 短路原方法，不让它拿假 token 打 AMS
                    Log.i(MultiOpenApp.TAG, "startForeground intercepted for virtual service ${svc.javaClass.name} -> KeepAliveService")
                }
            }
            val stopHook = object : MethodHook() {
                override fun beforeCall(frame: Pine.CallFrame) {
                    val svc = frame.thisObject as? Service ?: return
                    if (!VirtualServices.isManaged(svc)) return
                    KeepAliveService.demote(host)
                    frame.result = null
                    Log.i(MultiOpenApp.TAG, "stopForeground intercepted for virtual service ${svc.javaClass.name}")
                }
            }
            val intT: Class<*> = Int::class.javaPrimitiveType!!
            val boolT: Class<*> = Boolean::class.javaPrimitiveType!!
            val notifT: Class<*> = android.app.Notification::class.java
            // startForeground 两个重载：(int, Notification) 全版本；(int, Notification, int) API 29+
            hookQuietly(Service::class.java, "startForeground", arrayOf(intT, notifT), startHook)
            hookQuietly(Service::class.java, "startForeground", arrayOf(intT, notifT, intT), startHook)
            // stopForeground 两个重载：(boolean) 全版本（API 33 废弃）；(int) API 24+
            hookQuietly(Service::class.java, "stopForeground", arrayOf(boolT), stopHook)
            hookQuietly(Service::class.java, "stopForeground", arrayOf(intT), stopHook)
            fgInstalled = true
            Log.i(MultiOpenApp.TAG, "ART hook installed: Service.startForeground/stopForeground routing")
        } catch (t: Throwable) {
            Log.e(MultiOpenApp.TAG, "ART foreground-routing hook install failed", t)
        }
    }

    /** 某个重载不存在/hook 失败不致命：记一条 warn 继续 */
    private fun hookQuietly(clazz: Class<*>, name: String, params: Array<Class<*>>, hook: MethodHook) {
        try {
            val m = clazz.getDeclaredMethod(name, *params)
            Pine.hook(m, hook)
        } catch (t: Throwable) {
            Log.w(MultiOpenApp.TAG, "hook $name(${params.joinToString { it.simpleName }}) 跳过: $t")
        }
    }
}
