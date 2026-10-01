package com.example.multiopen

import android.os.Build
import android.util.Log
import java.lang.reflect.Field
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy

/**
 * Hook 进程里 IActivityManager 的 binder 代理（AMS 的客户端存根）。
 *
 * 为什么需要：我们希望插件读到的 getPackageName() 是它自己的包名（如 com.tencent.mm），
 * 但系统侧并没有真正安装这个包，宿主进程的 uid 属于宿主包。一旦某个虚拟包名随 binder 调用
 * 传到 AMS（Intent.setPackage、各种 callingPackage 等），AMS 会因“包名不属于该 uid”抛
 * SecurityException，或因“包不存在”解析失败。
 *
 * 做法：用动态代理包住原 IActivityManager，拦截每个调用，把参数里凡是等于“已安装虚拟包名”的
 * 字符串统一换成宿主包名，再转发给原对象。虚拟包名（com.tencent.mm 之类）几乎不可能作为非包名
 * 语义出现在 AMS 参数里，所以这种按值归一是安全的折中（VirtualApp 等框架同样思路）。
 *
 * 兼容：API 26+ 用 ActivityManager.IActivityManagerSingleton；API 24/25 用
 * ActivityManagerNative.gDefault。两者都是 android.util.Singleton，持有字段 mInstance。
 */
object ActivityManagerHook {
    @Volatile private var installed = false

    @Synchronized
    fun install(hostPackage: String, isVirtual: (String) -> Boolean) {
        if (installed) return
        try {
            val (singleton, instanceField) = resolveSingleton()
            // 先触发 Singleton.get()，确保 mInstance 已初始化再替换
            val original = runCatching {
                Class.forName("android.util.Singleton").getDeclaredMethod("get")
                    .apply { isAccessible = true }.invoke(singleton)
            }.getOrNull() ?: instanceField.get(singleton)
            if (original == null) { Log.w(MultiOpenApp.TAG, "IActivityManager 尚未就绪，稍后重试"); return }
            if (Proxy.isProxyClass(original.javaClass)) { installed = true; return } // 已 hook

            val iam = original.javaClass.interfaces.firstOrNull { it.name == "android.app.IActivityManager" }
                ?: Class.forName("android.app.IActivityManager")
            val proxy = Proxy.newProxyInstance(iam.classLoader, arrayOf(iam)) { _, method, args ->
                args?.let { a ->
                    for (i in a.indices) {
                        val v = a[i]
                        if (v is String && isVirtual(v)) a[i] = hostPackage
                    }
                }
                try {
                    method.invoke(original, *(args ?: emptyArray()))
                } catch (e: InvocationTargetException) {
                    throw e.cause ?: e
                }
            }
            instanceField.set(singleton, proxy)
            installed = true
            Log.i(MultiOpenApp.TAG, "IActivityManager hooked")
        } catch (t: Throwable) {
            Log.e(MultiOpenApp.TAG, "ActivityManagerHook 安装失败", t)
        }
    }

    /** 返回 (Singleton 实例, 它的 mInstance 字段) */
    private fun resolveSingleton(): Pair<Any, Field> {
        val singleton = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Class.forName("android.app.ActivityManager")
                .getDeclaredField("IActivityManagerSingleton").apply { isAccessible = true }.get(null)!!
        } else {
            Class.forName("android.app.ActivityManagerNative")
                .getDeclaredField("gDefault").apply { isAccessible = true }.get(null)!!
        }
        val instanceField = Class.forName("android.util.Singleton")
            .getDeclaredField("mInstance").apply { isAccessible = true }
        return singleton to instanceField
    }
}
