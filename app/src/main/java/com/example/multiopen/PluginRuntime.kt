package com.example.multiopen

import android.content.Context
import android.content.res.AssetManager
import android.content.res.Resources
import dalvik.system.DexClassLoader
import java.io.File

/** 为一个虚拟应用创建 ClassLoader 与 Resources */
class PluginRuntime(host: Context, val app: VirtualApp) {
    val classLoader: ClassLoader
    val resources: Resources

    init {
        val optDir = File(app.apkFile.parentFile, "oat").apply { mkdirs() }
        // parent 用宿主 ClassLoader，这样 plugin-api 的类是共享的
        classLoader = DexClassLoader(app.apkFile.absolutePath, optDir.absolutePath, null, host.classLoader)
        val am = AssetManager::class.java.getDeclaredConstructor().newInstance()
        AssetManager::class.java.getMethod("addAssetPath", String::class.java).invoke(am, app.apkFile.absolutePath)
        resources = Resources(am, host.resources.displayMetrics, host.resources.configuration)
    }
}
