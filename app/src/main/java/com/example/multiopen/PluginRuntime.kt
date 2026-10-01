package com.example.multiopen

import android.content.Context
import android.content.res.AssetManager
import android.content.res.Resources
import dalvik.system.DexClassLoader
import java.io.File

/** 为一个虚拟应用创建 ClassLoader 与 Resources */
class PluginRuntime(host: Context, val app: VirtualApp, isolated: Boolean = false) {
    val classLoader: ClassLoader
    val resources: Resources

    init {
        val optDir = File(app.apkFile.parentFile, "oat").apply { mkdirs() }
        // isolated=false：parent 用宿主 ClassLoader，plugin-api 的类共享（PluginActivity 模式）
        // isolated=true ：parent 用 BootClassLoader，插件自带所有依赖，行为接近独立 App（普通 APK 模式）
        val parent = if (isolated) host.classLoader.parent else host.classLoader
        // librarySearchPath 指向实例自己解压出来的 so，System.loadLibrary 才能找到（微信等 native 依赖的前提）
        val libPath = app.nativeLibDir.takeIf { it.isDirectory && it.list()?.isNotEmpty() == true }?.absolutePath
        classLoader = DexClassLoader(app.apkFile.absolutePath, optDir.absolutePath, libPath, parent)
        val am = AssetManager::class.java.getDeclaredConstructor().newInstance()
        AssetManager::class.java.getMethod("addAssetPath", String::class.java).invoke(am, app.apkFile.absolutePath)
        resources = Resources(am, host.resources.displayMetrics, host.resources.configuration)
    }
}
