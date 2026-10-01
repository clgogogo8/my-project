package com.example.multiopen

import android.app.Application
import android.content.Context
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
 * 暂未处理：getPackageName（改了会让系统服务的包名校验失败）、外部存储、getApplicationContext()（仍是宿主）。
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
