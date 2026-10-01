package com.example.multiopen

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.ComponentInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager

/**
 * 构造「虚拟应用自己的」ApplicationInfo / PackageInfo：用宿主 PackageManager 直接解析实例的 APK
 * （getPackageArchiveInfo 读文件、不查系统已安装表，不会 NameNotFound），再把路径字段改到实例目录。
 * VirtualContext 和 PackageManagerHook 共用这里，保证两处返回一致。
 */
object VirtualAppInfo {
    /** 把 so / 数据 / APK 路径改到本实例 */
    fun patch(info: ApplicationInfo, app: VirtualApp) {
        info.packageName = app.packageName
        info.sourceDir = app.apkFile.absolutePath
        info.publicSourceDir = app.apkFile.absolutePath
        info.dataDir = VirtualCore.dataDir(app).absolutePath
        info.nativeLibraryDir = app.nativeLibDir.absolutePath
    }

    fun applicationInfo(host: Context, app: VirtualApp): ApplicationInfo? =
        // 必须带 GET_META_DATA：Tinker 等框架在 Application.attachBaseContext 里读 application 级 meta-data
        // （TINKER_ID 等），metaData 为 null 会直接抛 "baseRevision must not be null"
        host.packageManager.getPackageArchiveInfo(app.apkFile.absolutePath, PackageManager.GET_META_DATA)
            ?.applicationInfo?.also { patch(it, app) }

    fun packageInfo(host: Context, app: VirtualApp, flags: Int): PackageInfo? =
        host.packageManager.getPackageArchiveInfo(app.apkFile.absolutePath, flags)?.also { pi ->
            pi.applicationInfo?.let { patch(it, app) }
        }

    /**
     * 按方法名（getActivityInfo / getServiceInfo / getProviderInfo / getReceiverInfo）和类名，
     * 从实例 APK 里找出对应组件的信息。带上 GET_META_DATA，供 androidx.startup 等读 provider 的 meta-data。
     * 返回的具体类型是 ActivityInfo / ServiceInfo / ProviderInfo / ActivityInfo 之一（均为 ComponentInfo 子类）。
     */
    fun componentInfo(host: Context, app: VirtualApp, methodName: String, className: String): ComponentInfo? {
        val flag = flagFor(methodName) ?: return null
        val pi = host.packageManager.getPackageArchiveInfo(app.apkFile.absolutePath, flag or PackageManager.GET_META_DATA) ?: return null
        val ci: ComponentInfo? = when (methodName) {
            "getActivityInfo" -> pi.activities?.firstOrNull { it.name == className }
            "getReceiverInfo" -> pi.receivers?.firstOrNull { it.name == className }
            "getServiceInfo" -> pi.services?.firstOrNull { it.name == className }
            "getProviderInfo" -> pi.providers?.firstOrNull { it.name == className }
            else -> null
        }
        return ci?.also { it.applicationInfo?.let { ai -> patch(ai, app) } }
    }

    private fun flagFor(methodName: String): Int? = when (methodName) {
        "getActivityInfo" -> PackageManager.GET_ACTIVITIES
        "getReceiverInfo" -> PackageManager.GET_RECEIVERS
        "getServiceInfo" -> PackageManager.GET_SERVICES
        "getProviderInfo" -> PackageManager.GET_PROVIDERS
        else -> null
    }
}
