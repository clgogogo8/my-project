package com.example.multiopen

import android.content.res.AssetManager
import org.xmlpull.v1.XmlPullParser
import java.io.File

/** 静态广播：类名 + 它声明的 action（动态注册时要用） */
data class ReceiverInfo(val name: String, val actions: List<String>)

/** ContentProvider：类名 + authorities（可有多个，manifest 里用 ; 分隔） */
data class ProviderInfo(val name: String, val authorities: List<String>)

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
    /** 每个 Activity 的 launchMode（0=standard 1=singleTop 2=singleTask 3=singleInstance），供后续桩池按 launchMode 分配 */
    val activityLaunchModes: Map<String, Int>,
    val services: List<String>,
    val receivers: List<ReceiverInfo>,
    val providers: List<ProviderInfo>,
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
        val launchModes = mutableMapOf<String, Int>()
        val services = mutableListOf<String>()
        val receivers = mutableListOf<ReceiverInfo>()
        val providers = mutableListOf<ProviderInfo>()

        am.openXmlResourceParser(cookie, "AndroidManifest.xml").use { p ->
            var curActivity: String? = null
            var isMain = false
            var isLauncher = false
            var curReceiver: String? = null
            val curReceiverActions = mutableListOf<String>()
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
                            launchModes[curActivity] = p.getAttributeIntValue(ANDROID_NS, "launchMode", 0)
                            isMain = false; isLauncher = false
                        }
                        "service" -> services += full(pkg, p.getAttributeValue(ANDROID_NS, "name"))
                        "receiver" -> { curReceiver = full(pkg, p.getAttributeValue(ANDROID_NS, "name")); curReceiverActions.clear() }
                        "provider" -> providers += ProviderInfo(
                            full(pkg, p.getAttributeValue(ANDROID_NS, "name")),
                            p.getAttributeValue(ANDROID_NS, "authorities").orEmpty().split(";").filter { it.isNotEmpty() },
                        )
                        "action" -> {
                            val an = p.getAttributeValue(ANDROID_NS, "name")
                            if (an == "android.intent.action.MAIN") isMain = true
                            if (curReceiver != null && an != null) curReceiverActions += an
                        }
                        "category" -> if (p.getAttributeValue(ANDROID_NS, "name") == "android.intent.category.LAUNCHER") isLauncher = true
                    }
                } else if (ev == XmlPullParser.END_TAG) {
                    if (p.name == "intent-filter" && isMain && isLauncher && launcher == null) launcher = curActivity
                    if (p.name == "intent-filter") { isMain = false; isLauncher = false }
                    if (p.name == "activity" || p.name == "activity-alias") curActivity = null
                    if (p.name == "receiver") {
                        curReceiver?.let { receivers += ReceiverInfo(it, curReceiverActions.toList()) }
                        curReceiver = null; curReceiverActions.clear()
                    }
                }
                ev = p.next()
            }
        }
        return ApkManifest(pkg, label, activities, launcher, appClass, appTheme, themes, launchModes, services, receivers, providers)
    }

    private fun full(pkg: String, name: String?): String = when {
        name == null -> ""
        name.startsWith(".") -> pkg + name
        !name.contains('.') -> "$pkg.$name"
        else -> name
    }
}
