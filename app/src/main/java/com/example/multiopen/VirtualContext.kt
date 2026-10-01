package com.example.multiopen

import android.app.Application
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.res.AssetManager
import android.content.res.Resources
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * 包在插件 Activity 外层的 Context，把数据相关路径重定向到实例私有目录，实现多开的数据隔离。
 * getApplicationInfo() 伪装成插件自己的（包名/数据目录/native 目录/APK 路径），很多 App 和库读这里拿路径。
 * getPackageName() 返回插件包名：本地读取是安全的，而真正过 binder 的调用由 ActivityManagerHook 把
 * 误入 AMS 的虚拟包名归一回宿主包名兜底（getOpPackageName 是 @hide，ContextWrapper 仍转发给宿主 base）。
 * 仍未处理：用插件包名查 PackageManager 会 NameNotFound（需 hook PMS 代理），外部存储重定向。
 */
class VirtualContext(
    base: Context,
    private val instanceId: String,
    private val root: File,
    private val runtime: PluginRuntime? = null,
    private val appProvider: () -> Application? = { null },
) : ContextWrapper(base) {
    override fun getResources(): Resources = runtime?.resources ?: super.getResources()
    override fun getAssets(): AssetManager = runtime?.resources?.assets ?: super.getAssets()
    override fun getClassLoader(): ClassLoader = runtime?.classLoader ?: super.getClassLoader()
    override fun getApplicationContext(): Context = appProvider() ?: super.getApplicationContext()
    override fun getPackageName(): String = runtime?.app?.packageName ?: super.getPackageName()

    /** 用插件 APK 自身的 ApplicationInfo，并把 so/数据/APK 路径改到本实例目录 */
    private val appInfo: ApplicationInfo by lazy {
        runtime?.app?.let { VirtualAppInfo.applicationInfo(baseContext, it) } ?: super.getApplicationInfo()
    }
    override fun getApplicationInfo(): ApplicationInfo = appInfo
    override fun getPackageCodePath(): String = runtime?.app?.apkFile?.absolutePath ?: super.getPackageCodePath()
    override fun getPackageResourcePath(): String = runtime?.app?.apkFile?.absolutePath ?: super.getPackageResourcePath()

    private fun dir(name: String) = File(root, name).apply { mkdirs() }
    private val databasesDir get() = dir("databases")

    override fun getDataDir(): File = root.apply { mkdirs() }
    override fun getFilesDir(): File = dir("files")
    override fun getCacheDir(): File = dir("cache")
    override fun getCodeCacheDir(): File = dir("code_cache")
    override fun getNoBackupFilesDir(): File = dir("no_backup")
    override fun getFileStreamPath(name: String): File = File(filesDir, name)
    override fun fileList(): Array<String> = filesDir.list() ?: emptyArray()
    override fun deleteFile(name: String): Boolean = getFileStreamPath(name).delete()
    override fun openFileInput(name: String): FileInputStream = FileInputStream(getFileStreamPath(name))
    override fun openFileOutput(name: String, mode: Int): FileOutputStream =
        FileOutputStream(getFileStreamPath(name), (mode and Context.MODE_APPEND) != 0)

    override fun getDatabasePath(name: String): File =
        if (name.startsWith("/")) File(name) else File(databasesDir, name)
    override fun databaseList(): Array<String> = databasesDir.list() ?: emptyArray()
    override fun deleteDatabase(name: String): Boolean = SQLiteDatabase.deleteDatabase(getDatabasePath(name))
    override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?): SQLiteDatabase =
        SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name), factory)
    override fun openOrCreateDatabase(
        name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?, errorHandler: DatabaseErrorHandler?,
    ): SQLiteDatabase =
        SQLiteDatabase.openDatabase(getDatabasePath(name).path, factory, SQLiteDatabase.CREATE_IF_NECESSARY, errorHandler)

    /** SharedPreferences 仍存在宿主的 shared_prefs 目录，但文件名带上实例 ID 前缀 */
    override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences =
        super.getSharedPreferences("virtual_${instanceId}_${name ?: "default"}", mode)
}
