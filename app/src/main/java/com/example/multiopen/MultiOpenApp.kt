package com.example.multiopen

import android.app.Application
import android.content.Context
import android.os.Build
import android.util.Log
import org.lsposed.hiddenapibypass.HiddenApiBypass

class MultiOpenApp : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        // Android 9+ 隐藏 API 限制；必须在任何反射之前解除
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try { HiddenApiBypass.addHiddenApiExemptions("L") } catch (t: Throwable) { Log.e(TAG, "hidden api bypass failed", t) }
        }
    }

    override fun onCreate() {
        super.onCreate()
        try { Hooks.install(this) } catch (t: Throwable) { Log.e(TAG, "hook install failed", t) }
    }

    companion object { const val TAG = "MultiOpen" }
}
