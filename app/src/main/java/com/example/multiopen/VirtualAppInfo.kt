package com.example.multiopen

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo

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
        host.packageManager.getPackageArchiveInfo(app.apkFile.absolutePath, 0)?.applicationInfo?.also { patch(it, app) }

    fun packageInfo(host: Context, app: VirtualApp, flags: Int): PackageInfo? =
        host.packageManager.getPackageArchiveInfo(app.apkFile.absolutePath, flags)?.also { pi ->
            pi.applicationInfo?.let { patch(it, app) }
        }
}
