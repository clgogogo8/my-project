package com.example.multi

import android.app.Application

/** 自定义 Application：每次进程内创建时，把"Application 启动次数"记到 applicationContext 的 prefs 里 */
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val p = getSharedPreferences("app", MODE_PRIVATE)
        p.edit().putInt("app_starts", p.getInt("app_starts", 0) + 1).commit()
    }
}
