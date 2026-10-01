package com.example.multiopen

import android.app.Application
import android.content.Context
import android.os.Build
import android.util.Log
import org.lsposed.hiddenapibypass.HiddenApiBypass

class MultiOpenApp : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        // Android 9+ 隐藏 API 限制；必须在任何反射之前解除
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try { HiddenApiBypass.addHiddenApiExemptions("L") } catch (t: Throwable) { Log.e(TAG, "hidden api bypass failed", t) }
        }
    }

    override fun onCreate() {
        super.onCreate()
        try { Hooks.install(this) } catch (t: Throwable) { Log.e(TAG, "hook install failed", t) }
        try {
            VirtualCore.list(this) // 预热：把已安装的虚拟包名加载进 knownPackages
            ActivityManagerHook.install(this) { it in VirtualCore.knownPackages }
            val isVirtual: (String) -> Boolean = { it in VirtualCore.knownPackages }
            val resolve: (String) -> VirtualApp? = { pkg -> VirtualCore.list(this).firstOrNull { it.packageName == pkg } }
            PackageManagerHook.install(this, isVirtual, resolve)
            // 更底层地包装 ServiceManager 的 package binder，覆盖微信自己从 ServiceManager 另拿 binder 的路径
            ServiceManagerHook.install(this, isVirtual, resolve)
            // ART 方法级 hook：拦微信绕过代理、经 ApplicationPackageManager.getInstallerPackageName 打真实系统的调用
            ArtHook.install(this, isVirtual)
        } catch (t: Throwable) { Log.e(TAG, "hook install failed", t) }
    }

    companion object { const val TAG = "MultiOpen" }
}
