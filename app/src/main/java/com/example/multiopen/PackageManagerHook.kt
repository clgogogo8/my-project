package com.example.multiopen

import android.content.ComponentName
import android.content.Context
import android.util.Log
import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy

/**
 * Hook 进程里的 IPackageManager 代理（替换 ActivityThread.sPackageManager 及已创建的
 * ApplicationPackageManager.mPM）。因为我们把 getPackageName() 伪装成了插件包名，应用再用它查
 * PackageManager（微信大量 getPackageInfo(getPackageName()) / getApplicationInfo(...)）时，系统里
 * 并没有真正安装这个包，会 NameNotFound。这里拦截对「虚拟包名」的查询，用 VirtualAppInfo 现解 APK
 * 构造结果返回；其余调用透传给原代理。
 *
 * 局限：IPackageManager 是进程级单例，拿不到「哪个实例在问」，同一包多开时返回的是其中某个实例的
 * 路径。真正的数据隔离仍靠 VirtualContext（文件 IO 都经它按实例重定向），PMS 返回的路径字段实际很少
 * 被用于读写。拦截 getPackageInfo / getApplicationInfo（按包名）与 getActivityInfo / getServiceInfo /
 * getProviderInfo / getReceiverInfo（按 ComponentName），其余透传。
 */
object PackageManagerHook {
    @Volatile private var installed = false

    private class Box(val value: Any?)

    @Synchronized
    fun install(host: Context, isVirtual: (String) -> Boolean, resolve: (String) -> VirtualApp?) {
        if (installed) return
        try {
            val atClass = Class.forName("android.app.ActivityThread")
            atClass.getMethod("currentActivityThread").invoke(null)
            val field = atClass.getDeclaredField("sPackageManager").apply { isAccessible = true }
            val original = field.get(null) ?: run { Log.w(MultiOpenApp.TAG, "sPackageManager 尚未就绪"); return }
            if (Proxy.isProxyClass(original.javaClass)) { installed = true; return }

            val ipm = original.javaClass.interfaces.firstOrNull { it.name == "android.content.pm.IPackageManager" }
                ?: Class.forName("android.content.pm.IPackageManager")
            val handler = InvocationHandler { _, method, args ->
                val box = runCatching { handle(host, isVirtual, resolve, method.name, args) }
                    .onFailure { Log.w(MultiOpenApp.TAG, "PM hook ${method.name} 构造失败，透传", it) }.getOrNull()
                if (box != null) box.value
                else try { method.invoke(original, *(args ?: emptyArray())) }
                catch (e: InvocationTargetException) { throw e.cause ?: e }
            }
            val proxy = Proxy.newProxyInstance(ipm.classLoader, arrayOf(ipm), handler)
            field.set(null, proxy)
            // 已创建的 ApplicationPackageManager 内部还缓存着原代理，一并换掉
            runCatching {
                val pm = host.packageManager
                pm.javaClass.getDeclaredField("mPM").apply { isAccessible = true }.set(pm, proxy)
            }
            installed = true
            Log.i(MultiOpenApp.TAG, "IPackageManager hooked")
        } catch (t: Throwable) {
            Log.e(MultiOpenApp.TAG, "PackageManagerHook 安装失败", t)
        }
    }

    /** 命中虚拟包且构造成功 → Box(结果)；否则 null → 透传。 */
    private fun handle(
        host: Context, isVirtual: (String) -> Boolean, resolve: (String) -> VirtualApp?,
        name: String, args: Array<Any?>?,
    ): Box? {
        val first = args?.getOrNull(0) ?: return null
        when (name) {
            "getPackageInfo", "getApplicationInfo" -> {
                val pkg = first as? String ?: return null
                if (!isVirtual(pkg)) return null
                val app = resolve(pkg) ?: return null
                // 兼容多版本：flags 在 API 33 起是 long，之前是 int；第一个数字参数即 flags（userId 在其后）
                val flags = args.drop(1).filterIsInstance<Number>().firstOrNull()?.toInt() ?: 0
                return if (name == "getPackageInfo") VirtualAppInfo.packageInfo(host, app, flags)?.let { Box(it) }
                else VirtualAppInfo.applicationInfo(host, app)?.let { Box(it) }
            }
            // 组件信息查询：第一个参数是 ComponentName（androidx.startup 的 InitializationProvider 走 getProviderInfo）
            "getActivityInfo", "getServiceInfo", "getProviderInfo", "getReceiverInfo" -> {
                val comp = first as? ComponentName ?: return null
                if (!isVirtual(comp.packageName)) return null
                val app = resolve(comp.packageName) ?: return null
                return VirtualAppInfo.componentInfo(host, app, name, comp.className)?.let { Box(it) }
            }
            else -> return null
        }
    }
}
