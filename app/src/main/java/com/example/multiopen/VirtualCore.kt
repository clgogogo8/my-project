package com.example.multiopen

import android.content.Context
import android.net.Uri
import java.io.File

data class VirtualApp(val instanceId: String, val apkFile: File, val label: String, val mainClass: String)

/** 管理虚拟应用的安装与实例目录。每个实例有独立的 base.apk 与数据目录，从而实现"多开"。 */
object VirtualCore {
    private fun root(ctx: Context) = File(ctx.filesDir, "virtual").apply { mkdirs() }

    /** 把 APK 复制进沙箱；同一个 APK 可安装多次，得到不同 instanceId */
    fun install(ctx: Context, uri: Uri, mainClass: String): VirtualApp {
        val id = System.currentTimeMillis().toString()
        val dir = File(root(ctx), id).apply { mkdirs() }
        val apk = File(dir, "base.apk")
        ctx.contentResolver.openInputStream(uri)!!.use { i -> apk.outputStream().use { i.copyTo(it) } }
        apk.setReadOnly() // Android 14+ 要求动态加载的 dex 文件只读
        File(dir, "data").mkdirs()
        File(dir, "main_class").writeText(mainClass)
        return load(apk)
    }

    fun list(ctx: Context): List<VirtualApp> =
        root(ctx).listFiles().orEmpty().map { File(it, "base.apk") }.filter { it.exists() }.map { load(it) }

    fun dataDir(app: VirtualApp) = File(app.apkFile.parentFile, "data")

    private fun load(apk: File): VirtualApp {
        val dir = apk.parentFile!!
        return VirtualApp(dir.name, apk, "实例 ${dir.name.takeLast(5)}", File(dir, "main_class").readText())
    }
}
