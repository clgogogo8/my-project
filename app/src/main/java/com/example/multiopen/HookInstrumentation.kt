package com.example.multiopen

import android.app.Activity
import android.app.Application
import android.app.Instrumentation
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Bundle
import android.os.PersistableBundle
import android.util.Log
import android.view.ContextThemeWrapper

/**
 * 启动流程：宿主 startActivity(StubActivity + extras) → 系统创建 Stub →
 * newActivity() 里返回插件的真实 Activity 实例 → callActivityOnCreate() 里换 Resources/Theme/Context。
 * 插件内部的 startActivity 由子类 ExecHookInstrumentation（Java）转到 [rewriteIntent]。
 */
open class HookInstrumentation(protected val ctx: Context, protected val base: Instrumentation) : Instrumentation() {

    init {
        // 把原 Instrumentation 的内部状态（mThread、mAppContext 等）复制过来
        for (f in Instrumentation::class.java.declaredFields) {
            if (java.lang.reflect.Modifier.isStatic(f.modifiers)) continue
            try { f.isAccessible = true; f.set(this, f.get(base)) } catch (t: Throwable) { Log.w(TAG, "copy ${f.name}: $t") }
        }
    }

    override fun newActivity(cl: ClassLoader?, className: String?, intent: Intent?): Activity {
        val instance = intent?.getStringExtra(StubActivity.EXTRA_INSTANCE)
        val real = intent?.getStringExtra(StubActivity.EXTRA_CLASS)
        if (className != null && className in StubActivity.classNames && instance != null && real != null) {
            val rt = VirtualRuntimes.get(ctx, instance, isolated = true)
            VirtualApplications.ensure(ctx, rt) // 插件的 Application 必须先于它的第一个 Activity 存在
            Log.i(TAG, "newActivity: stub -> $real")
            return rt.classLoader.loadClass(real).getDeclaredConstructor().newInstance() as Activity
        }
        return base.newActivity(cl, className, intent)
    }

    override fun callActivityOnCreate(activity: Activity, icicle: Bundle?) {
        patch(activity)
        base.callActivityOnCreate(activity, icicle)
    }

    override fun callActivityOnCreate(activity: Activity, icicle: Bundle?, persistentState: PersistableBundle?) {
        patch(activity)
        base.callActivityOnCreate(activity, icicle, persistentState)
    }

    /**
     * 插件内部启动另一个 Activity：如果 Intent 的显式组件是这个插件自己的 Activity，
     * 就改成启动桩（并带上实例与真实类名）。其它 Intent 原样放行。
     */
    open fun rewriteIntent(who: Context?, intent: Intent?): Intent? {
        val comp = intent?.component ?: return intent
        val instance = (who as? Activity)?.intent?.getStringExtra(StubActivity.EXTRA_INSTANCE) ?: return intent
        return try {
            val rt = VirtualRuntimes.get(ctx, instance, isolated = true)
            if (comp.packageName != ctx.packageName && comp.packageName != rt.app.packageName) return intent
            val cls = if (comp.className.startsWith(".")) rt.app.packageName + comp.className else comp.className
            val isPluginActivity = try { Activity::class.java.isAssignableFrom(rt.classLoader.loadClass(cls)) } catch (_: Throwable) { false }
            if (!isPluginActivity) return intent
            Log.i(TAG, "rewriteIntent: $cls -> stub (flags=0x${Integer.toHexString(intent.flags)})")
            val launchMode = rt.manifest.activityLaunchModes[cls] ?: 0
            // 透明/悬浮主题的目标用透明桩（窗口透明要由桩的 manifest 主题在 attach 时决定），否则按 launchMode
            val stubName = if (isTranslucentTheme(rt, rt.app.themeFor(cls))) StubActivity.nextTranslucentStub()
                           else StubActivity.nextStub(launchMode)
            Intent(intent)
                .setComponent(ComponentName(ctx.packageName, stubName))
                .putExtra(StubActivity.EXTRA_INSTANCE, instance)
                .putExtra(StubActivity.EXTRA_CLASS, cls)
        } catch (t: Throwable) {
            Log.e(TAG, "rewriteIntent failed", t)
            intent
        }
    }

    /** 把插件 Activity 的 Resources / Theme / Context / Application 换成插件自己的 */
    private fun patch(activity: Activity) {
        val instance = activity.intent?.getStringExtra(StubActivity.EXTRA_INSTANCE) ?: return
        if (activity is StubActivity) return
        try {
            val rt = VirtualRuntimes.get(ctx, instance, isolated = true)
            val baseCtx = activity.baseContext
            setField(baseCtx.javaClass, baseCtx, "mResources", rt.resources)
            setField(baseCtx.javaClass, baseCtx, "mTheme", null)
            setField(ContextThemeWrapper::class.java, activity, "mResources", null)
            setField(ContextThemeWrapper::class.java, activity, "mTheme", null)
            setField(ContextThemeWrapper::class.java, activity, "mThemeResource", 0)
            activity.setTheme(rt.app.themeFor(activity.javaClass.name).takeIf { it != 0 } ?: android.R.style.Theme_Material_Light_DarkActionBar)
            // 按插件清单里的 screenOrientation 锁方向（桩 Activity 本身没声明方向，这里补上）
            runCatching { rt.manifest.activityOrientations[activity.javaClass.name] }.getOrNull()?.takeIf { it != -1 }
                ?.let { runCatching { activity.requestedOrientation = it } }
            // Activity.getApplication() 返回插件自己的 Application
            VirtualApplications.get(instance)?.let { setField(Activity::class.java, activity, "mApplication", it) }
            // 最后一步：包一层 Context，重定向数据目录、ClassLoader、applicationContext
            setField(ContextWrapper::class.java, activity, "mBase",
                VirtualContext(baseCtx, instance, VirtualCore.dataDir(rt.app), rt) { VirtualApplications.get(instance) })
        } catch (t: Throwable) {
            Log.e(TAG, "patch failed", t)
        }
    }

    /** 用插件资源解析主题的 windowIsTranslucent / windowIsFloating，判断该 Activity 是否透明 */
    private fun isTranslucentTheme(rt: PluginRuntime, themeId: Int): Boolean = try {
        if (themeId == 0) false else {
            val theme = rt.resources.newTheme().apply { applyStyle(themeId, true) }
            val a = theme.obtainStyledAttributes(
                intArrayOf(android.R.attr.windowIsTranslucent, android.R.attr.windowIsFloating))
            val r = a.getBoolean(0, false) || a.getBoolean(1, false)
            a.recycle()
            r
        }
    } catch (t: Throwable) { false }

    private fun setField(clazz: Class<*>, obj: Any, name: String, value: Any?) {
        var c: Class<*>? = clazz
        while (c != null) {
            try { c.getDeclaredField(name).apply { isAccessible = true }.set(obj, value); return } catch (_: NoSuchFieldException) { c = c.superclass }
        }
        Log.w(TAG, "field not found: $name on ${clazz.name}")
    }

    companion object { const val TAG = "MultiOpen" }
}
