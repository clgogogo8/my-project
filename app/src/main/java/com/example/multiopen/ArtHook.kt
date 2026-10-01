package com.example.multiopen

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
}
