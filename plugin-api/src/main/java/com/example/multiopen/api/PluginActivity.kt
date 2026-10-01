package com.example.multiopen.api

import android.app.Activity
import android.os.Bundle

/**
 * MVP 阶段：插件 Activity 不是真正的 Activity，由宿主的 ProxyActivity 转发生命周期。
 * 后续阶段会改为 Hook 系统服务，从而直接运行未经修改的第三方 APK。
 */
abstract class PluginActivity {
    /** 宿主代理 Activity，用它来 setContentView / 取 Context / finish */
    lateinit var host: Activity
        internal set

    fun attach(host: Activity) { this.host = host }

    open fun onCreate(savedInstanceState: Bundle?) {}
    open fun onStart() {}
    open fun onResume() {}
    open fun onPause() {}
    open fun onStop() {}
    open fun onDestroy() {}
}
