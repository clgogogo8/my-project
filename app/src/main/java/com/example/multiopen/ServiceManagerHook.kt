package com.example.multiopen

import android.content.Context
import android.os.IBinder
import android.util.Log
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy

/**
 * 更底层的 PackageManager 拦截：包装 ServiceManager 缓存里的 "package" binder。
 *
 * 微信自己在 sPackageManager 之后又包了一层 IPackageManager 代理（栈里的 ig5.n1），它通过
 * ServiceManager.getService("package") + IPackageManager.Stub.asInterface() 直接拿真实 binder，
 * 绕过了 PackageManagerHook 替换的 sPackageManager。结果 getInstallerPackageName("com.tencent.mm")
 * 打到真实系统 → "Unknown package" → Cronet 的 BuildInfo 初始化失败 → libcronet SIGTRAP。
 *
 * 这里把 "package" binder 换成一个代理：queryLocalInterface(IPackageManager) 返回我们的 IPackageManager
 * 代理（asInterface 会直接采用它），其余方法转发真实 binder。这样任何 asInterface("package") 都拿到我们的
 * 拦截层，虚拟包查询不再打到真实系统。
 */
object ServiceManagerHook {
    @Volatile private var installed = false
    private const val IPM_DESCRIPTOR = "android.content.pm.IPackageManager"

    @Synchronized
    fun install(host: Context, isVirtual: (String) -> Boolean, resolve: (String) -> VirtualApp?) {
        if (installed) return
        try {
            val smClass = Class.forName("android.os.ServiceManager")
            val realBinder = smClass.getMethod("getService", String::class.java).invoke(null, "package") as? IBinder
                ?: run { Log.w(MultiOpenApp.TAG, "ServiceManager 无 package binder"); return }
            if (Proxy.isProxyClass(realBinder.javaClass)) { installed = true; return }

            val ipmClass = Class.forName(IPM_DESCRIPTOR)
            val realIpm = Class.forName("android.content.pm.IPackageManager\$Stub")
                .getMethod("asInterface", IBinder::class.java).invoke(null, realBinder)!!
            val ourIpm = PackageManagerHook.makeProxy(host, isVirtual, resolve, realIpm, ipmClass)

            val wrapped = Proxy.newProxyInstance(IBinder::class.java.classLoader, arrayOf(IBinder::class.java)) { _, method, args ->
                if (method.name == "queryLocalInterface" && args?.getOrNull(0) == IPM_DESCRIPTOR) {
                    ourIpm // asInterface 看到本地接口就直接用它，不再 new Stub.Proxy(真实 binder)
                } else try {
                    method.invoke(realBinder, *(args ?: emptyArray()))
                } catch (e: InvocationTargetException) { throw e.cause ?: e }
            } as IBinder

            @Suppress("UNCHECKED_CAST")
            val sCache = smClass.getDeclaredField("sCache").apply { isAccessible = true }
                .get(null) as MutableMap<String, IBinder>
            sCache["package"] = wrapped
            installed = true
            Log.i(MultiOpenApp.TAG, "ServiceManager package binder wrapped")
        } catch (t: Throwable) {
            Log.e(MultiOpenApp.TAG, "ServiceManagerHook 安装失败", t)
        }
    }
}
