package com.example.multiopen

import android.app.Activity
import android.content.res.Resources
import android.os.Bundle
import com.example.multiopen.api.PluginActivity

/** 宿主侧的"壳"Activity：加载插件类并转发生命周期 */
class ProxyActivity : Activity() {
    private var plugin: PluginActivity? = null
    private var runtime: PluginRuntime? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val apk = intent.getStringExtra(EXTRA_INSTANCE)!!
        val app = VirtualCore.list(this).first { it.instanceId == apk }
        runtime = PluginRuntime(this, app)
        plugin = (runtime!!.classLoader.loadClass(app.mainClass).getDeclaredConstructor().newInstance() as PluginActivity)
            .also { it.attach(this); it.onCreate(savedInstanceState) }
    }

    override fun getResources(): Resources = runtime?.resources ?: super.getResources()
    override fun getClassLoader(): ClassLoader = runtime?.classLoader ?: super.getClassLoader()
    override fun getFilesDir() = runtime?.let { VirtualCore.dataDir(it.app) } ?: super.getFilesDir()

    override fun onStart() { super.onStart(); plugin?.onStart() }
    override fun onResume() { super.onResume(); plugin?.onResume() }
    override fun onPause() { plugin?.onPause(); super.onPause() }
    override fun onStop() { plugin?.onStop(); super.onStop() }
    override fun onDestroy() { plugin?.onDestroy(); super.onDestroy() }

    companion object { const val EXTRA_INSTANCE = "instance" }
}
