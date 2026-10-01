package com.example.multiopen

import android.content.Context
import android.net.Uri
import java.io.File

data class VirtualApp(
    val instanceId: String, val apkFile: File, val label: String, val packageName: String, val mainClass: String,
    val applicationClass: String?, val appTheme: Int, val themes: Map<String, Int>,
) {
    /** 某个 Activity 的主题：先取它自己的，没有则取 application 的，都没有为 0 */
    fun themeFor(activityClass: String): Int = themes[activityClass]?.takeIf { it != 0 } ?: appTheme

    /** 解压 native 库的目录；没有 so 时目录为空（但始终存在，便于拼 librarySearchPath） */
    val nativeLibDir: File get() = File(apkFile.parentFile, "lib")
}

/** 管理虚拟应用的安装与实例目录。每个实例有独立的 base.apk 与数据目录，从而实现"多开"。 */
object VirtualCore {
    private fun root(ctx: Context) = File(ctx.filesDir, "virtual").apply { mkdirs() }

    /** 把 APK 复制进沙箱；同一个 APK 可安装多次，得到不同 instanceId */
    fun install(ctx: Context, uri: Uri): VirtualApp {
        val id = System.currentTimeMillis().toString()
        val dir = File(root(ctx), id).apply { mkdirs() }
        val apk = File(dir, "base.apk")
        ctx.contentResolver.openInputStream(uri)!!.use { i -> apk.outputStream().use { i.copyTo(it) } }
        apk.setReadOnly() // Android 14+ 要求动态加载的 dex 文件只读
        File(dir, "data").mkdirs()
        try { NativeLibs.extract(apk, File(dir, "lib")) } catch (e: Exception) { android.util.Log.w("MultiOpen", "extract native libs failed", e) }
        val m = try { ManifestParser.parse(apk) } catch (e: Exception) { dir.deleteRecursively(); throw e }
        val launcher = m.launcher ?: m.activities.firstOrNull()
        if (launcher == null) { dir.deleteRecursively(); error("APK 中没有找到任何 Activity") }
        File(dir, "main_class").writeText(launcher)
        File(dir, "package").writeText(m.packageName)
        File(dir, "application").writeText(m.applicationClass.orEmpty())
        File(dir, "themes").writeText(buildString {
            appendLine("@app=${m.appTheme}")
            m.activityThemes.forEach { (k, v) -> appendLine("$k=$v") }
        })
        return load(apk)
    }

    fun list(ctx: Context): List<VirtualApp> =
        root(ctx).listFiles().orEmpty().map { File(it, "base.apk") }.filter { it.exists() }.map { load(it) }

    fun dataDir(app: VirtualApp) = File(app.apkFile.parentFile, "data")

    private fun load(apk: File): VirtualApp {
        val dir = apk.parentFile!!
        val pkg = File(dir, "package").readText()
        val themes = File(dir, "themes").takeIf { it.exists() }?.readLines().orEmpty()
            .mapNotNull { l -> l.split("=", limit = 2).takeIf { it.size == 2 }?.let { it[0] to (it[1].toIntOrNull() ?: 0) } }.toMap()
        return VirtualApp(dir.name, apk, "$pkg #${dir.name.takeLast(5)}", pkg, File(dir, "main_class").readText(),
            File(dir, "application").takeIf { it.exists() }?.readText()?.trim()?.ifEmpty { null },
            themes["@app"] ?: 0, themes.filterKeys { it != "@app" })
    }
}
