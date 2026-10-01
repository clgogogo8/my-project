package com.example.multiopen

import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.PersistableBundle
import android.util.Log

/**
 * 启动流程：宿主 startActivity(StubActivity + extras) → 系统创建 Stub →
 * newActivity() 里返回插件的真实 Activity 实例 → callActivityOnCreate() 里把它的 Resources/Theme 换成插件的。
 * 只用到 Instrumentation 的公开方法加少量字段反射，比 Hook AMS 稳，对 Android 9–14 通用。
 */
class HookInstrumentation(private val ctx: Context, private val base: Instrumentation) : Instrumentation() {

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
        if (className == StubActivity::class.java.name && instance != null && real != null) {
            val rt = VirtualRuntimes.get(ctx, instance, isolated = true)
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

    /** 把插件 Activity 的 Resources 与 Theme 换成插件自己的 */
    private fun patch(activity: Activity) {
        val instance = activity.intent?.getStringExtra(StubActivity.EXTRA_INSTANCE) ?: return
        if (activity is StubActivity) return
        try {
            val rt = VirtualRuntimes.get(ctx, instance, isolated = true)
            val baseCtx = activity.baseContext
            setField(baseCtx.javaClass, baseCtx, "mResources", rt.resources)
            setField(baseCtx.javaClass, baseCtx, "mTheme", null)
            val wrapper = android.view.ContextThemeWrapper::class.java
            setField(wrapper, activity, "mResources", null)
            setField(wrapper, activity, "mTheme", null)
            setField(wrapper, activity, "mThemeResource", 0)
            activity.setTheme(rt.app.themeRes.takeIf { it != 0 } ?: android.R.style.Theme_Material_Light_DarkActionBar)
        } catch (t: Throwable) {
            Log.e(TAG, "patch failed", t)
        }
    }

    private fun setField(clazz: Class<*>, obj: Any, name: String, value: Any?) {
        var c: Class<*>? = clazz
        while (c != null) {
            try { c.getDeclaredField(name).apply { isAccessible = true }.set(obj, value); return } catch (_: NoSuchFieldException) { c = c.superclass }
        }
        Log.w(TAG, "field not found: $name on ${clazz.name}")
    }

    companion object { const val TAG = "MultiOpen" }
}
