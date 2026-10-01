package com.example.multiopen

import android.content.res.AssetManager
import org.xmlpull.v1.XmlPullParser
import java.io.File

data class ApkManifest(
    val packageName: String,
    val label: String?,
    val activities: List<String>,
    /** 带 MAIN + LAUNCHER intent-filter 的 Activity，找不到则为 null */
    val launcher: String?,
    /** application 级 android:theme 的资源 ID（插件资源表里的 ID），无则 0 */
    val applicationClass: String?,
    val appTheme: Int,
    val activityThemes: Map<String, Int>,
)

/** 解析 APK 里的二进制 AndroidManifest.xml（不依赖系统安装） */
object ManifestParser {
    private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"

    fun parse(apk: File): ApkManifest {
        val am = AssetManager::class.java.getDeclaredConstructor().newInstance()
        val cookie = AssetManager::class.java.getMethod("addAssetPath", String::class.java)
            .invoke(am, apk.absolutePath) as Int
        require(cookie != 0) { "无法读取 APK: ${apk.name}" }

        var pkg = ""
        var label: String? = null
        val activities = mutableListOf<String>()
        var launcher: String? = null
        var appTheme = 0
        var appClass: String? = null
        val themes = mutableMapOf<String, Int>()

        am.openXmlResourceParser(cookie, "AndroidManifest.xml").use { p ->
            var curActivity: String? = null
            var isMain = false
            var isLauncher = false
            var ev = p.eventType
            while (ev != XmlPullParser.END_DOCUMENT) {
                if (ev == XmlPullParser.START_TAG) {
                    when (p.name) {
                        "manifest" -> pkg = p.getAttributeValue(null, "package").orEmpty()
                        "application" -> {
                            label = p.getAttributeValue(ANDROID_NS, "label")
                            appTheme = p.getAttributeResourceValue(ANDROID_NS, "theme", 0)
                            appClass = p.getAttributeValue(ANDROID_NS, "name")?.let { full(pkg, it) }
                        }
                        "activity", "activity-alias" -> {
                            curActivity = full(pkg, p.getAttributeValue(ANDROID_NS, "name"))
                            if (p.name == "activity") activities += curActivity
                            themes[curActivity] = p.getAttributeResourceValue(ANDROID_NS, "theme", 0)
                            isMain = false; isLauncher = false
                        }
                        "action" -> if (p.getAttributeValue(ANDROID_NS, "name") == "android.intent.action.MAIN") isMain = true
                        "category" -> if (p.getAttributeValue(ANDROID_NS, "name") == "android.intent.category.LAUNCHER") isLauncher = true
                    }
                } else if (ev == XmlPullParser.END_TAG) {
                    if (p.name == "intent-filter" && isMain && isLauncher && launcher == null) launcher = curActivity
                    if (p.name == "intent-filter") { isMain = false; isLauncher = false }
                    if (p.name == "activity" || p.name == "activity-alias") curActivity = null
                }
                ev = p.next()
            }
        }
        return ApkManifest(pkg, label, activities, launcher, appClass, appTheme, themes)
    }

    private fun full(pkg: String, name: String?): String = when {
        name == null -> ""
        name.startsWith(".") -> pkg + name
        !name.contains('.') -> "$pkg.$name"
        else -> name
    }
}
