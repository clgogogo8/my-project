package com.example.multiopen

import android.content.ContentProvider
import android.content.Context
import android.content.pm.ProviderInfo
import android.util.Log
import java.util.concurrent.ConcurrentHashMap

/**
 * 在实例激活时实例化插件声明的 ContentProvider 并触发其 onCreate（通过公开 API attachInfo）。很多库靠
 * provider 的 onCreate 做初始化（AndroidX Startup、各 SDK 的自启 provider、FileProvider 的 paths）。
 * 按 authority 登记，供进程内按 authority 查找。每个实例只装一次。
 *
 * 局限：尚未 hook content:// 的跨组件解析（ContentResolver.query 到插件 provider 仍走系统 AMS，找不到
 * 未安装的 authority）。这里只完成进程内实例化/onCreate 与本地查找入口 [get]，resolve 重定向待后续。
 */
object VirtualProviders {
    private val installed = ConcurrentHashMap.newKeySet<String>()
    private val byAuthority = ConcurrentHashMap<String, ContentProvider>()

    /** 进程内按 authority 查找已装的插件 provider（后续 ContentResolver hook 会用到） */
    fun get(authority: String): ContentProvider? = byAuthority[authority]

    @Synchronized
    fun ensure(host: Context, rt: PluginRuntime) {
        if (!installed.add(rt.app.instanceId)) return
        val manifest = runCatching { ManifestParser.parse(rt.app.apkFile) }.getOrNull() ?: return
        for (p in manifest.providers) {
            if (p.name.isEmpty() || p.authorities.isEmpty()) continue
            try {
                val provider = rt.classLoader.loadClass(p.name).getDeclaredConstructor().newInstance() as ContentProvider
                val id = rt.app.instanceId
                val vctx = VirtualContext(host.applicationContext, id, VirtualCore.dataDir(rt.app), rt) { VirtualApplications.get(id) }
                // 优先用从 APK 现解的真实 ProviderInfo（带 grantUriPermissions / exported / meta-data），
                // FileProvider.attachInfo 会校验这些；解析不到再降级手工构造
                val info = (VirtualAppInfo.componentInfo(host, rt.app, "getProviderInfo", p.name) as? ProviderInfo)
                    ?: ProviderInfo().apply {
                        name = p.name
                        packageName = rt.app.packageName
                        authority = p.authorities.joinToString(";")
                        exported = false
                        applicationInfo = VirtualAppInfo.applicationInfo(host, rt.app)
                    }
                provider.attachInfo(vctx, info) // 公开 API；内部会调用 onCreate()
                p.authorities.forEach { byAuthority[it] = provider }
                Log.i(MultiOpenApp.TAG, "provider installed: ${p.name} ${p.authorities}")
            } catch (t: Throwable) {
                Log.e(MultiOpenApp.TAG, "install provider ${p.name} 失败", t)
            }
        }
    }
}
