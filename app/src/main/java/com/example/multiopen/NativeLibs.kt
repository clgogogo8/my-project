package com.example.multiopen

import android.os.Build
import android.util.Log
import java.io.File
import java.util.zip.ZipFile

/**
 * 从 APK 里解出 native 库（lib/<abi>/ 目录下的 .so 文件）到实例的 lib 目录。
 * 普通系统安装时由 PackageManager 完成这一步；多开里我们自己做，否则 System.loadLibrary 找不到 so，
 * 像微信这种重度依赖 native 的 App 一启动就崩。
 */
object NativeLibs {
    /**
     * 按设备支持的 ABI 优先级，从 APK 中选一个匹配的 abi 目录解压。
     * @return 解压出的 lib 目录；APK 里没有 native 库则返回 null。
     */
    fun extract(apk: File, outDir: File): File? {
        val abis = Build.SUPPORTED_ABIS?.toList().orEmpty().ifEmpty { listOf("armeabi-v7a") }
        ZipFile(apk).use { zip ->
            // APK 里实际包含哪些 abi
            val present = zip.entries().asSequence()
                .mapNotNull { e -> Regex("^lib/([^/]+)/").find(e.name)?.groupValues?.get(1) }
                .toSet()
            if (present.isEmpty()) return null
            val abi = abis.firstOrNull { it in present }
                ?: present.first().also { Log.w(MultiOpenApp.TAG, "no matching ABI in $abis, fallback to $it") }

            outDir.mkdirs()
            var count = 0
            zip.entries().asSequence()
                .filter { it.name.startsWith("lib/$abi/") && it.name.endsWith(".so") && !it.isDirectory }
                .forEach { e ->
                    val out = File(outDir, e.name.substringAfterLast('/'))
                    zip.getInputStream(e).use { i -> out.outputStream().use { i.copyTo(it) } }
                    count++
                }
            Log.i(MultiOpenApp.TAG, "extracted $count so ($abi) -> $outDir")
            return if (count > 0) outDir else null
        }
    }
}
